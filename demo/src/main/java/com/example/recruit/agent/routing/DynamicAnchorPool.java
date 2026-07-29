package com.example.recruit.agent.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 动态意图锚点池，保留为可选的路由增强能力。
 * 当前路由默认不注册该类为 Spring Bean，因此不会改变现有意图识别行为。
 */
public class DynamicAnchorPool {

    private static final Logger log = LoggerFactory.getLogger(DynamicAnchorPool.class);
    private static final int MAX_PER_TYPE = 200;
    private static final int FLUSH_INTERVAL = 50;
    private static final double SEMANTIC_DEDUP_THRESHOLD = 0.98;
    private static final String DATA_FILE = "data/intent-anchors.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<IntentType, LinkedHashSet<AnchorEntry>> pool = new EnumMap<>(IntentType.class);
    private int writeCount;

    public DynamicAnchorPool() {
        for (IntentType type : IntentType.values()) {
            pool.put(type, new LinkedHashSet<>());
        }
    }

    void init() {
        load();
    }

    public synchronized boolean add(IntentType type, String text, float[] embedding) {
        if (type == null || text == null || text.isBlank() || embedding == null || embedding.length == 0) {
            return false;
        }
        LinkedHashSet<AnchorEntry> anchors = pool.get(type);
        for (AnchorEntry anchor : anchors) {
            if (cosine(anchor.embedding, embedding) >= SEMANTIC_DEDUP_THRESHOLD) {
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
        while (anchors.size() > MAX_PER_TYPE) {
            anchors.remove(anchors.iterator().next());
        }
        if (++writeCount % FLUSH_INTERVAL == 0) {
            new Thread(this::flush, "anchor-flush").start();
        }
        return true;
    }

    public LinkedHashSet<AnchorEntry> getAnchors(IntentType type) {
        return pool.getOrDefault(type, new LinkedHashSet<>());
    }

    public synchronized void flush() {
        try {
            File file = new File(DATA_FILE);
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
            Files.writeString(Path.of(DATA_FILE), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception exception) {
            log.warn("Flush dynamic anchors failed: {}", exception.getMessage());
        }
    }

    public synchronized void load() {
        try {
            File file = new File(DATA_FILE);
            if (!file.exists()) {
                return;
            }
            mapper.readTree(file).fields().forEachRemaining(entry -> loadAnchors(entry.getKey(), entry.getValue()));
        } catch (Exception exception) {
            log.warn("Load dynamic anchors failed: {}", exception.getMessage());
        }
    }

    void destroy() {
        flush();
    }

    private void loadAnchors(String typeName, JsonNode values) {
        final IntentType type;
        try {
            type = IntentType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            return;
        }
        LinkedHashSet<AnchorEntry> anchors = pool.get(type);
        if (anchors == null) {
            return;
        }
        for (JsonNode node : values) {
            JsonNode vector = node.path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                continue;
            }
            AnchorEntry entry = new AnchorEntry();
            entry.text = node.path("text").asText();
            entry.createdAt = node.path("createdAt").asLong();
            entry.lastHitTime = node.path("lastHitTime").asLong(entry.createdAt);
            entry.embedding = new float[vector.size()];
            for (int index = 0; index < vector.size(); index++) {
                entry.embedding[index] = (float) vector.get(index).asDouble();
            }
            anchors.add(entry);
        }
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            return -1;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return leftNorm == 0 || rightNorm == 0 ? -1 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    @Data
    public static class AnchorEntry {
        public String text;
        public float[] embedding;
        public long createdAt;
        public long lastHitTime;
    }
}
