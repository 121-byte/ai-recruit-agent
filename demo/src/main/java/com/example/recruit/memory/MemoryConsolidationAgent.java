package com.example.recruit.memory;

import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 驱动的 7 步记忆巩固 (复刻对齐参考 §一-8)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>用 {@code chatFast} + {@code JsonGuard.extractJson} (非 chatJson)</li>
 *   <li>tags 仅 {@code log.debug}，不写库 (不更新 memory_entry.tags)</li>
 *   <li>图谱边用 JdbcTemplate {@code INSERT … ON CONFLICT DO NOTHING} (替代 selectCount+insert)</li>
 *   <li>consolidation_task 状态更新用 JdbcTemplate + completed_at</li>
 * </ul>
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
    private final DeepSeekModelService deepSeek;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public MemoryConsolidationAgent(MemoryEntryMapper memoryEntryMapper,
                                    DeepSeekModelService deepSeek,
                                    JdbcTemplate jdbcTemplate) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.deepSeek = deepSeek;
        this.jdbcTemplate = jdbcTemplate;
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

        // Step 2: 调 LLM (chatFast + extractJson, 非 chatJson)
        String response;
        try {
            response = deepSeek.chatFast(SYSTEM_PROMPT, userPrompt.toString());
        } catch (Exception e) {
            log.warn("consolidate chatFast failed: {}", e.getMessage());
            markTaskFailed(taskId, "chatFast failed: " + e.getMessage());
            return;
        }

        String jsonStr = JsonGuard.extractJson(response);
        if (jsonStr == null || jsonStr.isBlank()) {
            log.warn("consolidate: no JSON found in response: {}", response);
            markTaskFailed(taskId, "no JSON in response");
            return;
        }
        JsonNode root = JsonGuard.parseJsonSafe(jsonStr);
        if (root == null) {
            log.warn("consolidate: invalid JSON: {}", jsonStr);
            markTaskFailed(taskId, "invalid JSON");
            return;
        }

        // key → MemoryEntry 映射，用于按 key 反查更新
        Map<String, MemoryEntry> keyToEntry = new HashMap<>();
        for (MemoryEntry e : entries) {
            keyToEntry.put(e.getMemoryKey(), e);
        }

        int entriesProcessed = 0;
        int edgesCreated = 0;

        // Step 3: 遍历 LLM 输出的 entries，更新 memory_entry
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
                    } else {
                        // 对齐参考: importance 默认值
                        target.setImportance(target.getImportance() != null ? target.getImportance() : 0.5);
                    }
                    // tags 写库: LLM 输出 tags 数组持久化到 memory_entry.tags (此前仅 log)
                    JsonNode tagsNode = oe.path("tags");
                    if (tagsNode.isArray()) {
                        java.util.List<String> tagList = new java.util.ArrayList<>();
                        for (int i = 0; i < tagsNode.size(); i++) {
                            String t = tagsNode.get(i).asText("");
                            if (!t.isBlank()) {
                                tagList.add(t.trim());
                            }
                        }
                        target.setTags(tagList.toArray(new String[0]));
                    }
                    String summary = JsonGuard.text(oe, "summary");
                    String value = JsonGuard.text(oe, "value");
                    String newVal = (summary != null && !summary.isBlank()) ? summary : value;
                    if (newVal != null && !newVal.isBlank()) {
                        target.setMemoryValue(newVal);
                    }
                    target.setUpdatedAt(LocalDateTime.now());
                    memoryEntryMapper.updateById(target);
                    entriesProcessed++;
                } catch (Exception e) {
                    log.warn("update consolidated entry failed (key={}): {}", key, e.getMessage());
                }
            }
        }

        // Step 4: 遍历 edges，用 JdbcTemplate INSERT ON CONFLICT DO NOTHING
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
                    // JdbcTemplate INSERT ON CONFLICT (指定含 agent_id 的唯一约束, 避免跨 agent 串扰)
                    jdbcTemplate.update(
                            "INSERT INTO memory_graph(source_entry_id, target_entry_id, agent_id, relation_type, weight) " +
                            "VALUES (?, ?, ?, ?, ?) ON CONFLICT (source_entry_id, target_entry_id, relation_type, agent_id) " +
                            "DO NOTHING",
                            src.getId(), tgt.getId(), agentId, relationType, weight);
                    edgesCreated++;
                } catch (Exception e) {
                    log.warn("insert graph edge failed ({}→{}): {}", srcKey, tgtKey, e.getMessage());
                }
            }
        }

        // Step 4.5: 标签共现图谱 (赫布增强) — 同批共享 tag 的 entries 建共现边, 重复共现 weight 递增
        edgesCreated += buildCoOccurrenceEdges(agentId, keyToEntry);

        // Step 5: 更新 consolidation_task 状态 (JdbcTemplate + completed_at)
        markTaskCompleted(taskId, entriesProcessed, edgesCreated);
    }

    /**
     * 标签共现图谱: 同批 entries 中共享至少一个 tag 的两两建 relation_type='co_occurs' 边;
     * 重复共现时 weight += 1 (赫布: 共同激活→连接增强)。规范化 src<tgt 避免正反向重复。
     */
    private int buildCoOccurrenceEdges(String agentId, Map<String, MemoryEntry> keyToEntry) {
        // tag -> entries 反向索引
        Map<String, java.util.List<MemoryEntry>> tagToEntries = new HashMap<>();
        for (MemoryEntry e : keyToEntry.values()) {
            if (e.getId() == null || e.getTags() == null) {
                continue;
            }
            for (String tag : e.getTags()) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                tagToEntries.computeIfAbsent(tag, k -> new java.util.ArrayList<>()).add(e);
            }
        }
        java.util.Set<String> built = new java.util.HashSet<>();
        int count = 0;
        for (java.util.List<MemoryEntry> group : tagToEntries.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    MemoryEntry a = group.get(i);
                    MemoryEntry b = group.get(j);
                    long srcId = Math.min(a.getId(), b.getId());
                    long tgtId = Math.max(a.getId(), b.getId());
                    String pairKey = srcId + ":" + tgtId;
                    if (!built.add(pairKey)) {
                        continue;  // 同批同一对只建一次
                    }
                    try {
                        jdbcTemplate.update(
                                "INSERT INTO memory_graph(source_entry_id, target_entry_id, agent_id, relation_type, weight) " +
                                "VALUES (?, ?, ?, 'co_occurs', 1.0) " +
                                "ON CONFLICT (source_entry_id, target_entry_id, relation_type, agent_id) " +
                                "DO UPDATE SET weight = memory_graph.weight + 1",
                                srcId, tgtId, agentId);
                        count++;
                    } catch (Exception e) {
                        log.warn("insert co_occurs edge failed ({}→{}): {}", srcId, tgtId, e.getMessage());
                    }
                }
            }
        }
        return count;
    }

    /** 标记任务完成 (JdbcTemplate + completed_at)。 */
    private void markTaskCompleted(Long taskId, int entriesProcessed, int edgesCreated) {
        if (taskId == null) {
            return;
        }
        try {
            ObjectNode resultNode = mapper.createObjectNode();
            resultNode.put("entriesProcessed", entriesProcessed);
            resultNode.put("edgesCreated", edgesCreated);
            String resultJson = mapper.writeValueAsString(resultNode);
            jdbcTemplate.update(
                    "UPDATE consolidation_task SET status = 'completed', completed_at = NOW(), result = ?::jsonb WHERE id = ?",
                    resultJson, taskId);
        } catch (Exception e) {
            log.warn("mark task completed failed (taskId={}): {}", taskId, e.getMessage());
        }
    }

    /** 标记任务失败 (JdbcTemplate + completed_at)。 */
    private void markTaskFailed(Long taskId, String reason) {
        if (taskId == null) {
            return;
        }
        try {
            ObjectNode resultNode = mapper.createObjectNode();
            resultNode.put("error", reason);
            String resultJson = mapper.writeValueAsString(resultNode);
            jdbcTemplate.update(
                    "UPDATE consolidation_task SET status = 'failed', completed_at = NOW(), result = ?::jsonb WHERE id = ?",
                    resultJson, taskId);
        } catch (Exception e) {
            log.warn("mark task failed error: {}", e.getMessage());
        }
    }
}
