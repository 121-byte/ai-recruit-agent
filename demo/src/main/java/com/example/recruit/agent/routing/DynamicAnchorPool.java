package com.example.recruit.agent.routing;

import com.example.recruit.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 动态意图锚点池 (复刻自文档 §4.2 自学习)。
 *
 * <p>从真实用户输入学习新表达: 低置信全量分类路径 LLM 高置信结果直接 {@link #add} 入池;
 * 匹配阶段 {@link IntentRouter} 合并动态锚点计分。三道弱闸防学错:
 * <ol>
 *   <li>只在低置信全量分类路径 (新表达) 且 LLM confidence≥0.9 才回写;</li>
 *   <li>语义去重 (同桶 cosine≥阈值 视为重复, 刷新 lastHitTime 不新增);</li>
 *   <li>最久未命中淘汰: 超出上限时删 lastHitTime 最旧者, 淘汰过时模式保留近期有效。</li>
 * </ol>
 *
 * <p>条件 Bean: 仅当 {@code app.intent.dynamic-anchor.enabled=true} 时注册, 默认关闭灰度;
 * 出问题删 {@code data/intent-anchors.json} 重置即可。
 */
@Component
@ConditionalOnProperty(name = "app.intent.dynamic-anchor.enabled", havingValue = "true")
public class DynamicAnchorPool {

    private static final Logger log = LoggerFactory.getLogger(DynamicAnchorPool.class);
    private static final String DEFAULT_DATA_FILE = "data/intent-anchors.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AppProperties props;
    private final String dataFile;
    private final Map<IntentType, LinkedHashSet<AnchorEntry>> pool = new EnumMap<>(IntentType.class);

    @Autowired
    public DynamicAnchorPool(AppProperties props) {
        this(props, DEFAULT_DATA_FILE);
    }

    /** 测试用: 注入临时文件路径, 避免污染项目 data/ 目录。 */
    DynamicAnchorPool(AppProperties props, String dataFile) {
        this.props = props;
        this.dataFile = dataFile;
        for (IntentType type : IntentType.values()) {
            pool.put(type, new LinkedHashSet<>());
        }
    }

    @PostConstruct
    void init() {
        load();
    }

    /** 加入锚点 (语义去重 + 最久未命中淘汰)。命中已有锚点则刷新 lastHitTime 不新增。 */
    public synchronized boolean add(IntentType type, String text, float[] embedding) {
        if (type == null || text == null || text.isBlank() || embedding == null || embedding.length == 0) {
            return false;
        }
        double dedupThreshold = props.getIntent().getDynamicAnchor().getDedupThreshold();
        LinkedHashSet<AnchorEntry> anchors = pool.get(type);
        for (AnchorEntry anchor : anchors) {
            if (cosine(anchor.embedding, embedding) >= dedupThreshold) {
                anchor.lastHitTime = System.currentTimeMillis();
                return false;
            }
        }
        AnchorEntry entry = new AnchorEntry();
        entry.text = text;
        entry.embedding = embedding;
        entry.createdAt = System.currentTimeMillis();
        entry.lastHitTime = entry.createdAt;
        anchors.add(entry);
        evictIfNeeded(anchors);
        return true;
    }

    /** 返回该类锚点的浅拷贝快照, 避免匹配遍历与 flush 改集合并发异常。 */
    public List<AnchorEntry> getAnchors(IntentType type) {
        return new ArrayList<>(pool.getOrDefault(type, new LinkedHashSet<>()));
    }

    /** 定时兜底 flush, 方法体吞异常避免调度线程中断。 */
    @Scheduled(cron = "${app.intent.dynamic-anchor.flush-interval-cron:0 */5 * * * *}")
    public void scheduledFlush() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("Scheduled dynamic anchor flush failed: {}", e.getMessage());
        }
    }

    public synchronized void flush() {
        try {
            File file = new File(dataFile);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("Failed to create dynamic anchor directory: {}", parent);
            }
            ObjectNode root = mapper.createObjectNode();
            for (Map.Entry<IntentType, LinkedHashSet<AnchorEntry>> entry : pool.entrySet()) {
                ArrayNode values = root.putArray(entry.getKey().name());
                for (AnchorEntry anchor : entry.getValue()) {
                    ObjectNode node = values.addObject();
                    node.put("text", anchor.text);
                    node.put("createdAt", anchor.createdAt);
                    node.put("lastHitTime", anchor.lastHitTime);
                    ArrayNode vector = node.putArray("embedding");
                    for (float value : anchor.embedding) {
                        vector.add(value);
                    }
                }
            }
            Files.writeString(Path.of(dataFile), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception exception) {
            log.warn("Flush dynamic anchors failed: {}", exception.getMessage());
        }
    }

    public synchronized void load() {
        File file = new File(dataFile);
        if (!file.exists()) {
            return;
        }
        int totalLoaded = 0;
        try {
            JsonNode root = mapper.readTree(file);
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                IntentType type;
                try {
                    type = IntentType.valueOf(entry.getKey());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                LinkedHashSet<AnchorEntry> anchors = pool.get(type);
                if (anchors == null) {
                    continue;
                }
                for (JsonNode node : entry.getValue()) {
                    JsonNode vector = node.path("embedding");
                    if (!vector.isArray() || vector.isEmpty()) {
                        continue;
                    }
                    AnchorEntry e = new AnchorEntry();
                    e.text = node.path("text").asText();
                    e.createdAt = node.path("createdAt").asLong();
                    e.lastHitTime = node.path("lastHitTime").asLong(e.createdAt);
                    e.embedding = new float[vector.size()];
                    for (int i = 0; i < vector.size(); i++) {
                        e.embedding[i] = (float) vector.get(i).asDouble();
                    }
                    if (anchors.add(e)) {
                        totalLoaded++;
                    }
                }
            }
            log.info("DynamicAnchorPool loaded from {}: {} anchors", dataFile, totalLoaded);
        } catch (Exception exception) {
            log.warn("Load dynamic anchors failed: {}", exception.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        flush();
    }

    private void evictIfNeeded(LinkedHashSet<AnchorEntry> anchors) {
        int maxPerType = props.getIntent().getDynamicAnchor().getMaxPerType();
        while (anchors.size() > maxPerType) {
            // 淘汰 lastHitTime 最旧者 (最久未命中): 保留近期有效模式, 淘汰业务变化后过时的锚点
            AnchorEntry oldest = null;
            for (AnchorEntry a : anchors) {
                if (oldest == null || a.lastHitTime < oldest.lastHitTime) {
                    oldest = a;
                }
            }
            if (oldest != null) {
                anchors.remove(oldest);
            } else {
                break;
            }
        }
    }

    private double cosine(float[] left, float[] right) {
        return com.example.recruit.dal.handler.FloatVectorTypeHandler.cosine(left, right);
    }

    @Data
    public static class AnchorEntry {
        public String text;
        public float[] embedding;
        public long createdAt;
        public long lastHitTime;
    }
}
