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
import java.util.Iterator;
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

    /** 宽松获取 JsonNode 标量字段文本。 */
    private String getText(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        return n.isTextual() ? n.asText() : n.toString();
    }
}
