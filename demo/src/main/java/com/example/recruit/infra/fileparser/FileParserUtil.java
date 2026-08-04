package com.example.recruit.infra.fileparser;

import com.example.recruit.config.AppProperties;
import com.example.recruit.infra.llm.VisionOcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 简历文件解析工具 (复刻自文档 §9.5)。
 *
 * <p>支持 PDF / DOC / DOCX / TXT 等格式。<b>按格式分流</b>：
 * <ul>
 *   <li><b>TXT</b>：直接 UTF-8 读取</li>
 *   <li><b>PDF</b>：多模态三级降级链
 *     <ol>
 *       <li>PDFBox 提取文本 &gt; 50 字符 → 走现有路径 (95% 正常 PDF)</li>
 *       <li>PDFBox 提取 ≤ 50 字符 → 疑似扫描件 → 视觉模型 OCR
 *         (渲染图片调 qwen-image; 视觉不可用/失败 → markitdown)</li>
 *       <li>PDFBox 异常 → markitdown 兜底</li>
 *     </ol></li>
 *   <li><b>DOCX/DOC/PPT/XLS/HTML 等有语义结构的文档</b>：markitdown 优先 (保留语义结构), POI 兜底</li>
 * </ul>
 *
 * <p>分流原因：markitdown 对 PDF 会把版式硬转成 Markdown 表格, 对简历多栏排版易产生碎片化管道符输出;
 * 而 PDFBox 的 {@code PDFTextStripper} 给纯文本, 对 LLM 结构化抽取更干净。
 * 扫描件 PDF (图片型) PDFBox 提取文本为空/极短, 此时用视觉模型 OCR 兜底, 解决无文本层 PDF。
 *
 * <p>失败时返回文件名提示, 不抛异常, 保证上传链路不中断。
 */
@Component
public class FileParserUtil {

    private static final Logger log = LoggerFactory.getLogger(FileParserUtil.class);

    /** PDFBox 文本短于此阈值视为疑似扫描件 (无文本层), 触发视觉 OCR。 */
    private static final int SCANNED_TEXT_THRESHOLD = 50;
    /** 视觉 OCR 最多渲染页数 (简历通常 1-3 页)。 */
    private static final int OCR_MAX_PAGES = 5;
    /** PDF 渲染 DPI (清晰度与 base64 体积的平衡)。 */
    private static final int RENDER_DPI = 200;

    private final AppProperties props;
    private final VisionOcrService visionOcrService;

    public FileParserUtil(AppProperties props, VisionOcrService visionOcrService) {
        this.props = props;
        this.visionOcrService = visionOcrService;
    }

    /**
     * 解析文件为纯文本 (按格式分流)。
     *
     * <p>TXT 直接读；PDF 走多模态三级降级链；其余格式 markitdown 优先 (POI 兜底)。
     *
     * @param fileName 文件名（用于判断扩展名 + 临时文件后缀）
     * @param content  文件字节内容
     */
    public String parse(String fileName, byte[] content) {
        if (fileName == null || content == null) {
            return "";
        }
        String lower = fileName.toLowerCase();

        // TXT 直接返回, 无需走解析器
        if (lower.endsWith(".txt")) {
            return new String(content, StandardCharsets.UTF_8);
        }

        // PDF: 多模态三级降级链
        if (lower.endsWith(".pdf")) {
            return parsePdfWithFallback(fileName, content);
        }

        // 其余格式 (DOCX/DOC/PPT/XLS/HTML...): markitdown 优先 (保留语义结构), POI 兜底
        Optional<String> md = parseWithMarkitdown(fileName, content);
        if (md.isPresent() && !md.get().isBlank()) {
            return md.get();
        }
        try {
            if (lower.endsWith(".docx")) {
                return parseDocx(content);
            }
            // .doc (旧格式) 由 markitdown 主路径处理; POI HWPF 已随 poi-scratchpad 移除
            // 未知扩展名, 尝试按 UTF-8 文本返回
            return new String(content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Fallback parse {} failed: {}", fileName, e.getMessage());
            return "[解析失败: " + fileName + " - " + e.getMessage() + "]";
        }
    }

    /**
     * PDF 多模态三级降级链:
     * <ol>
     *   <li>PDFBox 文本 &gt; 50 字符 → 返回 (正常 PDF)</li>
     *   <li>≤ 50 字符 (疑似扫描件) → 视觉模型 OCR (可用→渲染图片→OCR; 不可用/失败→markitdown)</li>
     *   <li>PDFBox 异常 → markitdown 兜底</li>
     * </ol>
     */
    private String parsePdfWithFallback(String fileName, byte[] content) {
        String pdfText = null;
        try {
            pdfText = parsePdf(content);
            if (pdfText != null && pdfText.trim().length() > SCANNED_TEXT_THRESHOLD) {
                return pdfText;   // 现有路径: 正常文本层 PDF
            }
            log.debug("PDFBox text too short ({}), suspected scanned PDF: {}",
                    pdfText == null ? 0 : pdfText.trim().length(), fileName);
        } catch (Exception e) {
            // PDFBox 异常 → markitdown 兜底 (无法 loadPDF, 视觉渲染同样会失败, 不再试视觉)
            log.warn("PDFBox parse {} failed, fallback markitdown: {}", fileName, e.getMessage());
            return markitdownOrError(fileName, content);
        }

        // 文本过短: 视觉模型 OCR (可用→渲染图片→OCR; 不可用/失败→markitdown)
        if (visionOcrService.available()) {
            try {
                List<String> dataUrls = renderPdfPages(content, OCR_MAX_PAGES);
                if (!dataUrls.isEmpty()) {
                    String ocrText = visionOcrService.ocrImages(dataUrls);
                    if (ocrText != null && !ocrText.isBlank()) {
                        return ocrText;
                    }
                    log.debug("vision OCR returned empty for {}, fallback markitdown", fileName);
                }
            } catch (Exception e) {
                log.warn("vision OCR failed for {}, fallback markitdown: {}", fileName, e.getMessage());
            }
        } else {
            log.debug("vision OCR unavailable, fallback markitdown for {}", fileName);
        }
        return markitdownOrError(fileName, content);
    }

    /** markitdown 兜底; 仍失败返回错误提示。 */
    private String markitdownOrError(String fileName, byte[] content) {
        Optional<String> md = parseWithMarkitdown(fileName, content);
        if (md.isPresent() && !md.get().isBlank()) {
            return md.get();
        }
        return "[解析失败: " + fileName + "]";
    }

    /**
     * 用 PDFBox 将 PDF 各页渲染为 PNG data URL (base64), 供视觉模型 OCR。
     *
     * @param content  PDF 字节
     * @param maxPages 最多渲染页数
     */
    private List<String> renderPdfPages(byte[] content, int maxPages) throws Exception {
        List<String> dataUrls = new ArrayList<>();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(content)) {
            int pages = Math.min(doc.getNumberOfPages(), maxPages);
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, RENDER_DPI);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                dataUrls.add("data:image/png;base64," + b64);
            }
        }
        return dataUrls;
    }

    /**
     * 调用 Python markitdown 脚本解析 (文档 §9.5)。
     * 将真实文件字节写入临时文件（保留原扩展名，markitdown 按扩展名嗅探），
     * ProcessBuilder 执行脚本，读 stdout。
     *
     * @return 成功且输出非空时返回文本；不可用/失败时返回 {@link Optional#empty()}。
     */
    private Optional<String> parseWithMarkitdown(String fileName, byte[] content) {
        Path script = Path.of(props.getFileparser().getScriptPath());
        if (!script.toFile().exists()) {
            log.debug("markitdown script not found, fallback to PDFBox/POI: {}", script);
            return Optional.empty();
        }

        File temp = new File(System.getProperty("java.io.tmpdir"),
                "resume_" + System.nanoTime() + "_" + fileName);
        try {
            Files.write(temp.toPath(), content);   // 写入真实文件字节
            ProcessBuilder pb = new ProcessBuilder(
                    props.getFileparser().getPythonCommand(),
                    script.toAbsolutePath().toString(),
                    temp.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String out;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = reader.lines().collect(Collectors.joining("\n"));
            }
            int code = p.waitFor();
            if (code != 0 || out == null || out.isBlank()) {
                log.debug("markitdown exit={} output empty, fallback. file={}", code, fileName);
                return Optional.empty();
            }
            return Optional.of(out);
        } catch (Exception e) {
            log.debug("markitdown parse failed, fallback: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (!temp.delete()) {
                temp.deleteOnExit();
            }
        }
    }

    /** PDFBox 3.x 解析 PDF (兜底)。 */
    private String parsePdf(byte[] content) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(content)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** POI XWPFDocument 解析 DOCX (兜底)。 */
    private String parseDocx(byte[] content) throws Exception {
        try (InputStream is = new java.io.ByteArrayInputStream(content);
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            return sb.toString();
        }
    }
}
