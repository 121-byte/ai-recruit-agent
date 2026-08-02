package com.example.recruit.module.resume.application;

import com.example.recruit.dal.entity.DocumentChunk;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.DocumentChunkMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档语义分块 + 向量化服务 (复刻对齐参考 §二-4)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>签名 {@code chunkAndEmbedResume(Resume resume)} (传对象, 非 id)</li>
 *   <li>切分对齐 5 类语义段: basic_info/skills/work_exp/projects/education</li>
 *   <li>从 parsedJson.structuredData 或顶层 skills/work_experience/education 提取</li>
 *   <li>parsedJson 空时 rawText 整体作 'full' 单块</li>
 * </ul>
 */
@Service
public class DocumentChunkService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkService.class);

    private final DocumentChunkMapper documentChunkMapper;
    private final JobProfileMapper jobProfileMapper;
    private final ResumeMapper resumeMapper;
    private final EmbeddingService embeddingService;

    public DocumentChunkService(DocumentChunkMapper documentChunkMapper,
                               JobProfileMapper jobProfileMapper,
                               ResumeMapper resumeMapper,
                               EmbeddingService embeddingService) {
        this.documentChunkMapper = documentChunkMapper;
        this.jobProfileMapper = jobProfileMapper;
        this.resumeMapper = resumeMapper;
        this.embeddingService = embeddingService;
    }

    /**
     * 对岗位分块并向量化入库。
     * 先清理旧分块, 再从 parsed_json 按 5 类语义段 (basic_info/skills/work_exp/projects/education)
     * 重建, chunk_type 与简历分块同名, 供 chunk↔chunk 召回。
     */
    public int chunkAndEmbedJob(Long jobId) {
        if (jobId == null) {
            return 0;
        }
        try {
            JobProfile job = jobProfileMapper.selectById(jobId);
            if (job == null) {
                return 0;
            }
            // 清理旧分块
            try {
                documentChunkMapper.deleteByParent("job", jobId);
            } catch (Exception ignored) {
            }
            List<DocumentChunk> chunks = new ArrayList<>();
            int idx = 0;

            JsonNode parsed = job.getParsedJson();
            if (parsed == null || parsed.isMissingNode() || parsed.isNull()) {
                // 无结构化结果: 回退用标题+JD 整体作 'full' 单块
                String summaryText = joinNonBlank(" / ", job.getTitle(), job.getDepartment());
                String jd = job.getJdText();
                if (jd != null && !jd.isBlank()) {
                    summaryText = summaryText.isBlank() ? jd : summaryText + ": " + jd;
                }
                if (!summaryText.isBlank()) {
                    chunks.add(buildChunk("job", jobId, idx++, "full", summaryText));
                }
                return embedAndInsert(chunks);
            }

            // basic_info: 标题/部门/职级/地点/类别/经验/学历拼一块
            JsonNode pos = parsed.path("positionInfo");
            StringBuilder bi = new StringBuilder();
            appendField(bi, "岗位", job.getTitle());
            appendField(bi, "部门", textOf(pos, "department"));
            appendField(bi, "职级", textOf(pos, "level"));
            appendField(bi, "地点", textOf(pos, "location"));
            appendField(bi, "类别", job.getCategory() != null ? job.getCategory() : textOf(pos, "category"));
            JsonNode expMin = pos.path("experienceMin");
            JsonNode expMax = pos.path("experienceMax");
            if (expMin.isNumber() || expMax.isNumber()) {
                appendField(bi, "经验要求",
                        (expMin.isNumber() ? expMin.asInt() + "" : "") + "-" +
                        (expMax.isNumber() ? expMax.asInt() + "" : "") + "年");
            }
            appendField(bi, "学历", textOf(pos, "education"));
            if (bi.length() > 0) {
                chunks.add(buildChunk("job", jobId, idx++, "basic_info", bi.toString()));
            }

            // skills: 逐条一块, 与简历 skills 同 chunk_type
            JsonNode skills = parsed.path("skills");
            if (skills.isArray()) {
                for (JsonNode s : skills) {
                    String name = textOf(s, "name");
                    if (name == null) continue;
                    StringBuilder sb = new StringBuilder(name);
                    int level = intOf(s, "requiredLevel");
                    if (level > 0) sb.append("(要求").append(level).append("级");
                    int years = intOf(s, "years");
                    if (years > 0) sb.append(",").append(years).append("年");
                    sb.append(")");
                    double w = doubleOf(s, "weight");
                    if (w > 0) sb.append("权重").append(w);
                    chunks.add(buildChunk("job", jobId, idx++, "skills", sb.toString()));
                }
            }

            // responsibilities: 逐条一块, chunk_type=work_exp (对应简历工作经历)
            idx = addJobArrayAsChunks(parsed, "responsibilities", jobId, idx, "work_exp", chunks,
                    n -> joinNonBlank(": ", textOf(n, "name"), textOf(n, "description")));

            // projectContext: 逐条一块, chunk_type=projects
            idx = addJobArrayAsChunks(parsed, "projectContext", jobId, idx, "projects", chunks,
                    n -> joinNonBlank(": ", textOf(n, "name"), textOf(n, "description")));

            // education: 一块
            JsonNode edu = parsed.path("education");
            String eduText = edu.isObject() ? joinNonBlank(" / ",
                    textOf(edu, "degree"), textOf(edu, "major"), textOf(edu, "school")) : null;
            if (eduText != null) {
                chunks.add(buildChunk("job", jobId, idx++, "education", eduText));
            }

            return embedAndInsert(chunks);
        } catch (Exception e) {
            log.warn("chunkAndEmbedJob failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 将 parsed_json 中某数组字段的每个元素提取为独立分块, 文本由 formatter 决定。 */
    private int addJobArrayAsChunks(JsonNode parsed, String fieldName, Long parentId, int idx,
                                    String chunkType, List<DocumentChunk> chunks,
                                    java.util.function.Function<JsonNode, String> formatter) {
        JsonNode arr = parsed.path(fieldName);
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return idx;
        }
        for (JsonNode el : arr) {
            String text = formatter.apply(el);
            if (text != null && !text.isBlank()) {
                chunks.add(buildChunk("job", parentId, idx++, chunkType, text));
            }
        }
        return idx;
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append("; ");
        sb.append(label).append(": ").append(value);
    }

    private String textOf(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        String s = n.isTextual() ? n.asText() : n.toString();
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private int intOf(JsonNode parent, String field) {
        JsonNode n = parent == null ? null : parent.get(field);
        if (n == null || !n.isNumber()) return 0;
        return n.asInt();
    }

    private double doubleOf(JsonNode parent, String field) {
        JsonNode n = parent == null ? null : parent.get(field);
        if (n == null || !n.isNumber()) return 0;
        return n.asDouble();
    }

    private String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    /**
     * 对简历分块并向量化入库 (对齐参考: 传 Resume 对象, 非 id)。
     * 先清理旧分块, 再按 5 类语义段 basic_info/skills/work_exp/projects/education 重建。
     */
    public int chunkAndEmbedResume(Resume resume) {
        if (resume == null || resume.getId() == null) {
            return 0;
        }
        Long resumeId = resume.getId();
        try {
            // 清理旧分块
            try {
                documentChunkMapper.deleteByParent("resume", resumeId);
            } catch (Exception ignored) {
            }
            List<DocumentChunk> chunks = new ArrayList<>();
            int idx = 0;

            JsonNode parsed = resume.getParsedJson();
            if (parsed == null || parsed.isMissingNode() || parsed.isNull()) {
                // parsed_json 为空, 回退从 raw_text 整体作 'full' 单块 (对齐参考)
                String raw = resume.getRawText();
                if (raw != null && !raw.isBlank()) {
                    chunks.add(buildChunk("resume", resumeId, idx++, "full", raw));
                }
                if (resume.getCandidateName() != null && !resume.getCandidateName().isBlank()) {
                    chunks.add(buildChunk("resume", resumeId, idx++, "basic_info",
                            "候选人: " + resume.getCandidateName()));
                }
            } else {
                // 尝试从 structuredData 子节点提取 (参考格式)
                JsonNode sd = parsed.path("structuredData");
                JsonNode source = sd.isObject() ? sd : parsed;

                // basic_info: 姓名 + intended_position + work_years + summary
                idx = addBasicInfo(resume, source, resumeId, idx, chunks);

                // skills: 从 skills 数组逐条分块
                idx = addArrayAsChunks(source, "skills", "resume", resumeId, idx, "skills", chunks);

                // work_exp: 从 work_experience / workExperience 数组逐条分块
                idx = addArrayAsChunks(source, "work_experience", "resume", resumeId, idx, "work_exp", chunks);
                if (idx == 0 || chunks.isEmpty()) {
                    idx = addArrayAsChunks(source, "workExperience", "resume", resumeId, idx, "work_exp", chunks);
                }

                // projects: 从 projects 数组逐条分块
                idx = addArrayAsChunks(source, "projects", "resume", resumeId, idx, "projects", chunks);

                // education: 从 education 数组逐条分块
                idx = addArrayAsChunks(source, "education", "resume", resumeId, idx, "education", chunks);
            }

            return embedAndInsert(chunks);
        } catch (Exception e) {
            log.warn("chunkAndEmbedResume failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 兼容旧调用: 按 id 加载 Resume 后转发。 */
    public int chunkAndEmbedResume(Long resumeId) {
        if (resumeId == null) return 0;
        Resume resume = resumeMapper.selectById(resumeId);
        return chunkAndEmbedResume(resume);
    }

    // ─────────────────── 5 类语义段提取 ───────────────────

    /** basic_info: 候选人姓名 + intended_position + work_years + summary。 */
    private int addBasicInfo(Resume resume, JsonNode source, Long parentId, int idx,
                             List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        if (resume.getCandidateName() != null && !resume.getCandidateName().isBlank()) {
            sb.append("姓名: ").append(resume.getCandidateName());
        }
        String pos = getText(source, "intended_position");
        if (pos != null && !pos.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("意向岗位: ").append(pos);
        }
        String years = getText(source, "work_years");
        if (years != null && !years.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("工作年限: ").append(years);
        }
        String summary = getText(source, "summary");
        if (summary != null && !summary.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("摘要: ").append(summary);
        }
        if (sb.length() > 0) {
            chunks.add(buildChunk("resume", parentId, idx++, "basic_info", sb.toString()));
        }
        return idx;
    }

    // ─────────────────── 工具方法 ───────────────────

    /** 构造分块对象 (不含向量, 待填充)。 */
    private DocumentChunk buildChunk(String parentType, Long parentId, int chunkIndex,
                                    String chunkType, String content) {
        DocumentChunk c = new DocumentChunk();
        c.setParentType(parentType);
        c.setParentId(parentId);
        c.setChunkIndex(chunkIndex);
        c.setChunkType(chunkType);
        c.setContent(content);
        return c;
    }

    /** 对每个分块向量化并批量写入, 返回写入条数。 */
    private int embedAndInsert(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        for (DocumentChunk c : chunks) {
            try {
                String content = c.getContent();
                if (content == null || content.isBlank()) {
                    c.setEmbedding(new float[embeddingService.dimension()]);
                } else {
                    c.setEmbedding(embeddingService.embed(content));
                }
            } catch (Exception e) {
                log.debug("embed chunk failed: {}", e.getMessage());
                c.setEmbedding(new float[embeddingService.dimension()]);
            }
        }
        try {
            return documentChunkMapper.batchInsert(chunks);
        } catch (Exception e) {
            log.warn("batchInsert chunks failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 将 JsonNode 某数组字段的每个元素提取为独立分块。
     */
    private int addArrayAsChunks(JsonNode parent, String fieldName, String parentType,
                                 Long parentId, int idx, String chunkType,
                                 List<DocumentChunk> chunks) {
        if (parent == null) {
            return idx;
        }
        JsonNode arr = parent.get(fieldName);
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return idx;
        }
        for (JsonNode el : arr) {
            String text = nodeToText(el);
            if (text != null && !text.isBlank()) {
                chunks.add(buildChunk(parentType, parentId, idx++, chunkType, text));
            }
        }
        return idx;
    }

    /** JsonNode → 文本: 字符串用 asText, 对象/数组用 toString。 */
    private String nodeToText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    /** 宽松获取 JsonNode 标量字段文本。 */
    private String getText(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        return n.isTextual() ? n.asText() : n.toString();
    }
}
