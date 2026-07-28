package com.example.recruit.service;

import com.example.recruit.dal.entity.DocumentChunk;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.DocumentChunkMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 文档语义分块 + 向量化服务 (复刻对齐清单 §3.2)。
 *
 * <p>将 JobProfile / Resume 的结构化字段按 skill/experience/education/summary 提取分块文本,
 * 每块调用 EmbeddingService.embed 生成向量后批量写入 document_chunk。
 * parsed_json 为 null 时回退从 raw_text 取前 500 字作 summary 分块。
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
     * 先清理旧分块, 再按 summary/skill/experience/education 重建。
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
                // 旧分块清理失败不阻断重建
            }
            List<DocumentChunk> chunks = new ArrayList<>();
            int idx = 0;

            // summary: 标题 + 部门 + JD 文本
            StringBuilder summary = new StringBuilder();
            if (job.getTitle() != null) {
                summary.append(job.getTitle());
            }
            if (job.getDepartment() != null) {
                summary.append(" / ").append(job.getDepartment());
            }
            String jdText = job.getJdText();
            if (jdText != null && !jdText.isBlank()) {
                summary.append(": ").append(jdText);
            }
            String summaryText = summary.toString();
            if (!summaryText.isBlank()) {
                chunks.add(buildChunk("job", jobId, idx++, "summary", summaryText));
            }

            // skill: 从 weightMatrix 提取技能权重键
            idx = addJsonKeysAsChunks(job.getWeightMatrix(), "job", jobId, idx, "skill", chunks);

            // experience / education: JobProfile 无对应字段, 从 growthPath 提取作为 experience
            idx = addJsonKeysAsChunks(job.getGrowthPath(), "job", jobId, idx, "experience", chunks);

            // roleGraph 作为 experience 补充
            idx = addJsonKeysAsChunks(job.getRoleGraph(), "job", jobId, idx, "experience", chunks);

            // 向量化并批量写入
            return embedAndInsert(chunks);
        } catch (Exception e) {
            log.warn("chunkAndEmbedJob failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 对简历分块并向量化入库。
     * 先清理旧分块, 再按 skill/experience/education/summary 重建。
     */
    public int chunkAndEmbedResume(Long resumeId) {
        if (resumeId == null) {
            return 0;
        }
        try {
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                return 0;
            }
            // 清理旧分块
            try {
                documentChunkMapper.deleteByParent("resume", resumeId);
            } catch (Exception ignored) {
                // 旧分块清理失败不阻断重建
            }
            List<DocumentChunk> chunks = new ArrayList<>();
            int idx = 0;

            JsonNode parsed = resume.getParsedJson();
            if (parsed == null || parsed.isMissingNode() || parsed.isNull()) {
                // parsed_json 为空, 回退从 raw_text 取前 500 字作 summary 分块
                String raw = resume.getRawText();
                if (raw != null && !raw.isBlank()) {
                    String text = raw.length() > 500 ? raw.substring(0, 500) : raw;
                    chunks.add(buildChunk("resume", resumeId, idx++, "summary", text));
                }
                // 候选人姓名作 summary 补充
                if (resume.getCandidateName() != null && !resume.getCandidateName().isBlank()) {
                    chunks.add(buildChunk("resume", resumeId, idx++, "summary",
                            "候选人: " + resume.getCandidateName()));
                }
            } else {
                // summary
                idx = addFieldAsChunk(parsed, "summary", "resume", resumeId, idx, "summary", chunks, false);
                // intended_position → summary 补充
                idx = addFieldAsChunk(parsed, "intended_position", "resume", resumeId, idx, "summary", chunks, false);
                // work_years → summary 补充
                idx = addFieldAsChunk(parsed, "work_years", "resume", resumeId, idx, "summary", chunks, false);
                // skill: skills 数组逐条分块
                idx = addArrayAsChunks(parsed, "skills", "resume", resumeId, idx, "skill", chunks);
                // experience: work_experience 数组逐条分块
                idx = addArrayAsChunks(parsed, "work_experience", "resume", resumeId, idx, "experience", chunks);
                // education: education 数组逐条分块
                idx = addArrayAsChunks(parsed, "education", "resume", resumeId, idx, "education", chunks);
            }

            return embedAndInsert(chunks);
        } catch (Exception e) {
            log.warn("chunkAndEmbedResume failed: {}", e.getMessage());
            return 0;
        }
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
     * 数组元素为对象时用其 toString 作内容, 为字符串时用 asText。
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

    /** 将 JsonNode 某标量字段提取为单个分块 (可选拼接字段名)。 */
    private int addFieldAsChunk(JsonNode parent, String fieldName, String parentType,
                               Long parentId, int idx, String chunkType,
                               List<DocumentChunk> chunks, boolean withFieldName) {
        if (parent == null) {
            return idx;
        }
        JsonNode node = parent.get(fieldName);
        if (node == null || node.isNull()) {
            return idx;
        }
        String text = nodeToText(node);
        if (text == null || text.isBlank()) {
            return idx;
        }
        String content = withFieldName ? fieldName + ": " + text : text;
        chunks.add(buildChunk(parentType, parentId, idx++, chunkType, content));
        return idx;
    }

    /** 将 JsonNode 对象的顶层键提取为分块 (键作内容, 用于 weightMatrix 等)。 */
    private int addJsonKeysAsChunks(JsonNode parent, String parentType, Long parentId,
                                    int idx, String chunkType, List<DocumentChunk> chunks) {
        if (parent == null || !parent.isObject()) {
            return idx;
        }
        Iterator<String> names = parent.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name != null && !name.isBlank()) {
                chunks.add(buildChunk(parentType, parentId, idx++, chunkType, name));
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
}
