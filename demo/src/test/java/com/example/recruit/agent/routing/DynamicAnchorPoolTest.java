package com.example.recruit.agent.routing;

import com.example.recruit.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicAnchorPool 单元测试: 语义去重 + 最久未命中淘汰 + 快照隔离。
 */
class DynamicAnchorPoolTest {

    @TempDir
    Path tempDir;

    private DynamicAnchorPool newPool(int maxPerType) {
        AppProperties props = new AppProperties();
        props.getIntent().getDynamicAnchor().setMaxPerType(maxPerType);
        return new DynamicAnchorPool(props, tempDir.resolve("anchors.json").toString());
    }

    @Test
    void add_duplicateRefreshesLastHitTimeDoesNotDuplicate() {
        DynamicAnchorPool pool = newPool(10);
        float[] v1 = new float[]{1f, 0f, 0f};

        assertTrue(pool.add(IntentType.CHITCHAT, "text1", v1), "首次新增应返回 true");
        assertFalse(pool.add(IntentType.CHITCHAT, "text1-dup", v1), "同向量去重应返回 false (刷新 lastHitTime)");
        assertEquals(1, pool.getAnchors(IntentType.CHITCHAT).size(), "去重不应增加条数");
    }

    @Test
    void eviction_dropsLeastRecentlyHit() {
        DynamicAnchorPool pool = newPool(2);
        float[] v1 = {1f, 0f, 0f};
        float[] v2 = {0f, 1f, 0f};
        float[] v3 = {0f, 0f, 1f};

        pool.add(IntentType.CHITCHAT, "v1", v1);
        pool.add(IntentType.CHITCHAT, "v2", v2);

        // 手动设 lastHitTime: v1 最近命中 (大), v2 最久未命中 (小) → 淘汰应删 v2 而非最早插入的 v1
        List<DynamicAnchorPool.AnchorEntry> es = pool.getAnchors(IntentType.CHITCHAT);
        es.get(0).lastHitTime = 200; // v1
        es.get(1).lastHitTime = 100; // v2

        pool.add(IntentType.CHITCHAT, "v3", v3); // size=3 > 2 触发淘汰

        List<DynamicAnchorPool.AnchorEntry> remain = pool.getAnchors(IntentType.CHITCHAT);
        assertEquals(2, remain.size());
        assertEquals(1, remain.stream().filter(a -> a.text.equals("v1")).count(),
                "最近命中的 v1 应保留 (证明按最久未命中淘汰, 而非最早插入)");
        assertEquals(1, remain.stream().filter(a -> a.text.equals("v3")).count());
        assertEquals(0, remain.stream().filter(a -> a.text.equals("v2")).count(),
                "最久未命中的 v2 应被淘汰");
    }

    @Test
    void getAnchors_returnsSnapshotIsolation() {
        DynamicAnchorPool pool = newPool(10);
        pool.add(IntentType.SINGLE_TOOL, "x", new float[]{1f, 0f});
        List<DynamicAnchorPool.AnchorEntry> snapshot = pool.getAnchors(IntentType.SINGLE_TOOL);
        snapshot.clear();   // 修改快照不应影响池
        assertEquals(1, pool.getAnchors(IntentType.SINGLE_TOOL).size(), "快照修改不应影响原池");
    }
}
