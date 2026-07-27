package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.ConsolidationTask;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.entity.MemoryGraph;
import com.example.recruit.dal.mapper.ConsolidationTaskMapper;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.dal.mapper.MemoryGraphMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 驱动的 7 步记忆巩固 (复刻自文档 §5.5，190 行)。
 *
 * <p>步骤：
 * <ol>
 *   <li>分类 → category (preference/fact/note)</li>
 *   <li>冲突解决 → 同 key 不同 value，保留最新或合并</li>
 *   <li>标签提取 → tags JSON 数组</li>
 *   <li>图谱边 → source_key → target_key, relation_type</li>
 *   <li>合并重复 → 语义相似度 > 0.95 的合并</li>
 *   <li>重要性评分 → 0.0-1.0 (HR 显式偏好 = 0.8+, 自动提取 = 0.5)</li>
 *   <li>摘要 → 压缩描述</li>
 * </ol>
 *
 * <p>{@link #consolidate} 为 {@link Transactional}：解析 LLM 输出 JSON 后，
 * 更新 memory_entry (category/importance/memory_value=summary/embedding)，
 * 插入 memory_graph 边，最后更新 consolidation_task 状态为 completed。
 */
@Component
public class MemoryConsolidationAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationAgent.class);

    private static final String SYSTEM_PROMPT =
            "你是记忆巩固引擎。对下面给定的记忆条目列表执行 7 步巩固：\n" +
            "Step1 分类：每条标注 category ∈ {preference,fact,note}\n" +
            "Step2 冲突解决：同一 key 不同 value，保留最新或合并\n" +
            "Step3 标签提取：为每条抽取 tags 数组\n" +
            "Step4 图谱边：识别 source_key→target_key 的关联，relation_type, weight\n" +
            "Step5 合并重复：语义相似度 > 0.95 的合并为一条\n" +
            "Step6 重要性评分：0.0-1.0，HR 显式偏好=0.8+，自动提取≈0.5\n" +
            "Step7 摘要：将原始 value 压缩为简短 summary\n" +
            "输出 JSON：{\"entries\":[{\"key\":\"\",\"category\":\"\",\"tags\":[]," +
            "\"importance\":0.8,\"summary\":\"\",\"value\":\"\"}]," +
            "\"edges\":[{\"source_key\":\"\",\"target_key\":\"\",\"relation_type\":\"\",\"weight\":0.8}]}";

    private final MemoryEntryMapper memoryEntryMapper;
    private final MemoryGraphMapper memoryGraphMapper;
    private final DeepSeekModelService deepSeek;
    private final EmbeddingService embeddingService;
    private final ConsolidationTaskMapper consolidationTaskMapper;
    private final AppProperties appProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public MemoryConsolidationAgent(MemoryEntryMapper memoryEntryMapper,
                                    MemoryGraphMapper memoryGraphMapper,
                                    DeepSeekModelService deepSeek,
                                    EmbeddingService embeddingService,
                                    ConsolidationTaskMapper consolidationTaskMapper,
                                    AppProperties appProperties) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.memoryGraphMapper = memoryGraphMapper;
        this.deepSeek = deepSeek;
        this.embeddingService = embeddingService;
        this.consolidationTaskMapper = consolidationTaskMapper;
        this.appProperties = appProperties;
    }

    /**
     * 执行一次记忆巩固。事务保证：中途异常整体回滚。
     *
     * @param entries  待巩固的记忆条目
     * @param taskId   对应 consolidation_task 主键，可为 null
     * @param agentId  Agent 标识
     */
    @Transactional
    public void consolidate(List<MemoryEntry> entries, Long taskId, String agentId) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        // Step 1: 构造 Prompt
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("agent_id: ").append(agentId).append('\n');
        userPrompt.append("记忆条目：\n");
        for (int i = 0; i < entries.size(); i++) {
            MemoryEntry e = entries.get(i);
            userPrompt.append("[").append(i).append("] ")
                    .append("key=").append(e.getMemoryKey())
                    .append(" value=").append(e.getMemoryValue())
                    .append(" category=").append(e.getCategory())
                    .append(" importance=").append(e.getImportance())
                    .append('\n');
        }

        // Step 2: 调 LLM 执行 7 步巩固
        String json;
        try {
            json = deepSeek.chatJson(SYSTEM_PROMPT, userPrompt.toString());
        } catch (Exception e) {
            log.warn("consolidate chatJson failed: {}", e.getMessage());
            markTaskFailed(taskId, "chatJson failed: " + e.getMessage());
            return;
        }

        JsonNode root = JsonGuard.parseJsonSafe(json);
        if (root == null) {
            log.warn("consolidate: invalid JSON: {}", json);
            markTaskFailed(taskId, "invalid JSON");
            return;
        }

        // key → MemoryEntry 映射，用于按 key 反查更新
        Map<String, MemoryEntry> keyToEntry = new HashMap<>();
        for (MemoryEntry e : entries) {
            keyToEntry.put(e.getMemoryKey(), e);
        }

        // Step 4: 遍历 LLM 输出的 entries，更新 memory_entry
        JsonNode outEntries = root.path("entries");
        if (outEntries.isArray()) {
            for (JsonNode oe : outEntries) {
                String key = JsonGuard.text(oe, "key");
                if (key == null || key.isBlank()) {
                    continue;
                }
                MemoryEntry target = keyToEntry.get(key);
                if (target == null) {
                    continue;
                }
                try {
                    String category = JsonGuard.text(oe, "category");
                    if (category != null && !category.isBlank()) {
                        target.setCategory(category);
                    }
                    JsonNode impNode = oe.path("importance");
                    if (impNode.isNumber()) {
                        target.setImportance(impNode.asDouble());
                    }
                    JsonNode tagsNode = oe.path("tags");
                    if (tagsNode.isArray()) {
                        String[] tags = new String[tagsNode.size()];
                        for (int i = 0; i < tagsNode.size(); i++) {
                            tags[i] = tagsNode.get(i).asText("");
                        }
                        target.setTags(tags);
                    }
                    String summary = JsonGuard.text(oe, "summary");
                    String value = JsonGuard.text(oe, "value");
                    String newVal = (summary != null && !summary.isBlank()) ? summary : value;
                    if (newVal != null && !newVal.isBlank()) {
                        target.setMemoryValue(newVal);
                        try {
                            target.setEmbedding(embeddingService.embed(newVal));
                        } catch (Exception embEx) {
                            log.debug("re-embed during consolidate failed: {}", embEx.getMessage());
                        }
                    }
                    target.setUpdatedAt(LocalDateTime.now());
                    memoryEntryMapper.updateById(target);
                } catch (Exception e) {
                    log.warn("update consolidated entry failed (key={}): {}", key, e.getMessage());
                }
            }
        }

        // Step 5: 遍历 edges，插入 memory_graph
        JsonNode outEdges = root.path("edges");
        if (outEdges.isArray()) {
            for (JsonNode edge : outEdges) {
                String srcKey = JsonGuard.text(edge, "source_key");
                String tgtKey = JsonGuard.text(edge, "target_key");
                if (srcKey == null || tgtKey == null) {
                    continue;
                }
                MemoryEntry src = keyToEntry.get(srcKey);
                MemoryEntry tgt = keyToEntry.get(tgtKey);
                if (src == null || tgt == null || src.getId() == null || tgt.getId() == null) {
                    continue;
                }
                try {
                    String relationType = JsonGuard.text(edge, "relation_type");
                    if (relationType == null || relationType.isBlank()) {
                        relationType = "related_to";
                    }
                    double weight = 0.5;
                    JsonNode wNode = edge.path("weight");
                    if (wNode.isNumber()) {
                        weight = wNode.asDouble();
                    }
                    MemoryGraph graph = new MemoryGraph();
                    graph.setSourceEntryId(src.getId());
                    graph.setTargetEntryId(tgt.getId());
                    graph.setAgentId(agentId);
                    graph.setRelationType(relationType);
                    graph.setWeight(weight);
                    // 去重：同一 (source,target,relation) 不重复插入
                    Long dup = memoryGraphMapper.selectCount(
                            new LambdaQueryWrapper<MemoryGraph>()
                                    .eq(MemoryGraph::getSourceEntryId, src.getId())
                                    .eq(MemoryGraph::getTargetEntryId, tgt.getId())
                                    .eq(MemoryGraph::getRelationType, relationType));
                    if (dup == null || dup == 0) {
                        memoryGraphMapper.insert(graph);
                    }
                } catch (Exception e) {
                    log.warn("insert graph edge failed ({}→{}): {}", srcKey, tgtKey, e.getMessage());
                }
            }
        }

        // Step 6: 更新 consolidation_task 状态
        try {
            if (taskId != null) {
                ConsolidationTask task = consolidationTaskMapper.selectById(taskId);
                if (task != null) {
                    task.setStatus("completed");
                    ObjectNode resultNode = mapper.createObjectNode();
                    resultNode.put("entriesProcessed", outEntries.isArray() ? outEntries.size() : 0);
                    resultNode.put("edgesCreated", outEdges.isArray() ? outEdges.size() : 0);
                    task.setResult(resultNode);
                    task.setUpdatedAt(LocalDateTime.now());
                    consolidationTaskMapper.updateById(task);
                }
            }
        } catch (Exception e) {
            log.warn("update consolidation_task failed (taskId={}): {}", taskId, e.getMessage());
        }
    }

    private void markTaskFailed(Long taskId, String reason) {
        if (taskId == null) {
            return;
        }
        try {
            ConsolidationTask task = consolidationTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("failed");
                ObjectNode resultNode = mapper.createObjectNode();
                resultNode.put("error", reason);
                task.setResult(resultNode);
                task.setUpdatedAt(LocalDateTime.now());
                consolidationTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.warn("mark task failed error: {}", e.getMessage());
        }
    }

    // 供上游 Mock 模式判断
    @SuppressWarnings("unused")
    private boolean useMock() {
        return appProperties.useMock();
    }
}
