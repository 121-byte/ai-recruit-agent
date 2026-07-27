package com.example.recruit.llm;

import com.example.recruit.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 简历文件解析工具 (复刻自文档 §9.5)。
 *
 * <p>支持 PDF / DOC / DOCX / TXT 等格式。<b>markitdown 优先，PDFBox/POI 兜底</b>：
 * <ol>
 *   <li>主：通过 ProcessBuilder 调用 Python markitdown 脚本 ({@code app.fileparser.script-path})，
 *       由 markitdown 统一将各类文档转为 Markdown 文本，质量最高</li>
 *   <li>兜底：markitdown 不可用（脚本缺失/python 缺失/非零退出/输出为空）时，
 *       按扩展名用 PDFBox / POI 纯 Java 解析</li>
 * </ol>
 *
 * <p>失败时返回文件名提示，不抛异常，保证上传链路不中断。
 */
@Component
public class FileParserUtil {

    private static final Logger log = LoggerFactory.getLogger(FileParserUtil.class);

    private final AppProperties props;

    public FileParserUtil(AppProperties props) {
        this.props = props;
    }

    /**
     * 解析文件为纯文本 (markitdown 优先, PDFBox/POI 兜底)。
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

        // 主：markitdown
        Optional<String> md = parseWithMarkitdown(fileName, content);
        if (md.isPresent() && !md.get().isBlank()) {
            return md.get();
        }

        // 兜底：PDFBox / POI
        try {
            if (lower.endsWith(".pdf")) {
                return parsePdf(content);
            }
            if (lower.endsWith(".docx")) {
                return parseDocx(content);
            }
            if (lower.endsWith(".doc")) {
                return parseDoc(content);
            }
            // 未知扩展名, 尝试按 UTF-8 文本返回
            return new String(content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Fallback parse {} failed: {}", fileName, e.getMessage());
            return "[解析失败: " + fileName + " - " + e.getMessage() + "]";
        }
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

    /** POI HWPFDocument 解析 DOC 旧格式 (兜底)。 */
    private String parseDoc(byte[] content) throws Exception {
        try (InputStream is = new java.io.ByteArrayInputStream(content);
             org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(is)) {
            return doc.getDocumentText();
        }
    }
}
