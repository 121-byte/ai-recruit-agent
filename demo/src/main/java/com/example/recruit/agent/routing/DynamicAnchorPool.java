package com.example.recruit.agent.routing;

import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态锚点池 (复刻自文档 §4.3 DynamicAnchorPool)。
 *
 * <p>管理 {@code Map<IntentType, LinkedHashSet<AnchorEntry>>}，每类意图最多 200 条动态锚点。
 * LLM 分类置信度 ≥ 0.7 的用户输入会回写为新锚点，使系统在使用中自学习。
 *
 * <p>核心机制：
 * <ul>
 *   <li>语义去重：与新文本 cosine > 0.95 的同类型锚点视为重复，跳过</li>
 *   <li>LRU 淘汰：超过 MAX_PER_TYPE 时移除最旧 (LinkedHashSet 插入顺序)</li>
 *   <li>持久化：每 50 次写入异步 flush 到 data/intent-anchors.json</li>
 * </ul>
 */
@Component
public class DynamicAnchorPool {

    private static final Logger log = LoggerFactory.getLogger(DynamicAnchorPool.class);

    private static final int MAX_PER_TYPE = 200;
    private static final int FLUSH_INTERVAL = 50;
    private static final double SEMANTIC_DEDUP_THRESHOLD = 0.95;

    private static final String DATA_FILE = "data/intent-anchors.json";

    private final ObjectMapper mapper = new ObjectMapper();

    /** 每类一个 LinkedHashSet (LRU 顺序)。线程安全包装。 */
    private final Map<IntentType, LinkedHashSet<AnchorEntry>> pool = new ConcurrentHashMap<>();

    private int writeCount = 0;

    @PostConstruct
    void init() {
        for (IntentType t : IntentType.values()) {
            pool.put(t, new LinkedHashSet<>());
        }
        load();
    }

    /**
     * 添加动态锚点。
     *
     * @return true 若实际新增 (未触发语义去重)
     */
    public synchronized boolean add(IntentType type, String text, float[] embedding) {
        if (text == null || text.isBlank() || embedding == null || type == null) {
            return false;
        }
        LinkedHashSet<AnchorEntry> set = pool.get(type);
        if (set == null) {
            return false;
        }

        // 1. 语义去重
        for (AnchorEntry existing : set) {
            if (FloatVectorTypeHandler.cosine(existing.embedding, embedding) > SEMANTIC_DEDUP_THRESHOLD) {
                // 命中即更新 lastHitTime，但不新增
                existing.lastHitTime = System.currentTimeMillis();
                return false;
            }
        }

        // 2. 新增 (LinkedHashSet LRU)
        AnchorEntry entry = new AnchorEntry();
        entry.text = text;
        entry.embedding = embedding;
        entry.createdAt = System.currentTimeMillis();
        entry.lastHitTime = entry.createdAt;
        set.add(entry);

        // 3. 超容量移除最旧
        while (set.size() > MAX_PER_TYPE) {
            Iterator<AnchorEntry> it = set.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }

        // 4. 周期性异步 flush
        writeCount++;
        if (writeCount % FLUSH_INTERVAL == 0) {
            new Thread(this::flush, "anchor-flush").start();
        }
        return true;
    }

    /** 返回某类意图的全部动态锚点 (供 IntentRouter 遍历匹配)。 */
    public LinkedHashSet<AnchorEntry> getAnchors(IntentType type) {
        return pool.getOrDefault(type, new LinkedHashSet<>());
    }

    /** 持久化到 data/intent-anchors.json (异步调用)。 */
    public synchronized void flush() {
        try {
            File file = new File(DATA_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("Failed to mkdirs for {}", parent);
            }
            ObjectNode root = mapper.createObjectNode();
            for (Map.Entry<IntentType, LinkedHashSet<AnchorEntry>> e : pool.entrySet()) {
                ArrayNode arr = root.putArray(e.getKey().name());
                for (AnchorEntry entry : e.getValue()) {
                    ObjectNode o = arr.addObject();
                    o.put("text", entry.text);
                    o.put("createdAt", entry.createdAt);
                    o.put("lastHitTime", entry.lastHitTime);
                    // embedding 存为 JSON 数组
                    ArrayNode emb = o.putArray("embedding");
                    for (float v : entry.embedding) {
                        emb.add(v);
                    }
                }
            }
            Files.writeString(Path.of(DATA_FILE), mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root));
            log.debug("DynamicAnchorPool flushed: {} types", pool.size());
        } catch (Exception e) {
            log.warn("Flush dynamic anchors failed: {}", e.getMessage());
        }
    }

    /** 启动时从 JSON 加载历史锚点；文件中无 embedding 则由 IntentRouter 补算。 */
    @SuppressWarnings("unchecked")
    public synchronized void load() {
        try {
            File file = new File(DATA_FILE);
            if (!file.exists()) {
                return;
            }
            var root = mapper.readTree(file);
            root.fields().forEachRemaining(entry -> {
                IntentType type;
                try {
                    type = IntentType.valueOf(entry.getKey());
                } catch (IllegalArgumentException ex) {
                    return;
                }
                LinkedHashSet<AnchorEntry> set = pool.get(type);
                if (set == null) {
                    return;
                }
                for (var node : entry.getValue()) {
                    AnchorEntry a = new AnchorEntry();
                    a.text = node.path("text").asText();
                    a.createdAt = node.path("createdAt").asLong();
                    a.lastHitTime = node.path("lastHitTime").asLong(a.createdAt);
                    var embNode = node.path("embedding");
                    if (embNode.isArray() && embNode.size() > 0) {
                        float[] emb = new float[embNode.size()];
                        for (int i = 0; i < embNode.size(); i++) {
                            emb[i] = (float) embNode.get(i).asDouble();
                        }
                        a.embedding = emb;
                        set.add(a);
                    }
                    // 无 embedding 的锚点: 由 IntentRouter 在 initAnchors() 中补算
                }
            });
            log.info("DynamicAnchorPool loaded from {}", DATA_FILE);
        } catch (Exception e) {
            log.warn("Load dynamic anchors failed: {}", e.getMessage());
        }
    }

    /** 锚点条目 (复刻自文档 §4.3 AnchorEntry)。 */
    public static class AnchorEntry {
        public String text;
        public float[] embedding;
        public long createdAt;
        public long lastHitTime;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnchorEntry that)) return false;
            return text != null && text.equals(that.text);
        }

        @Override
        public int hashCode() {
            return text == null ? 0 : text.hashCode();
        }
    }

    @PreDestroy
    void destroy() {
        flush();
    }
}
