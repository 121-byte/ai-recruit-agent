package com.example.recruit.agent.routing;

import com.example.recruit.config.AppProperties;
import com.example.recruit.infra.llm.ChatResult;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.retrieval.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 意图路由 + 动态锚点回写测试: 验证低置信全量分类路径 LLM 高置信结果直接回写 pool。
 * 全零 embedding 使所有向量匹配 score=0 (低置信), 强制走 classifyWithLlm 全量五分类路径。
 */
@ExtendWith(MockitoExtension.class)
class IntentRouterDynamicAnchorTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private DeepSeekModelService deepSeekModelService;

    @TempDir
    Path tempDir;

    private IntentRouter newRouterWithPool() {
        when(embeddingService.embed(anyString())).thenReturn(new float[1024]); // 全零 → 余弦 0 → 低置信
        AppProperties props = new AppProperties();
        DynamicAnchorPool pool = new DynamicAnchorPool(props, tempDir.resolve("ir.json").toString());
        IntentRouter router = new IntentRouter(embeddingService, deepSeekModelService, pool);
        router.initAnchors(); // 手动触发 @PostConstruct
        return router;
    }

    @Test
    void lowConfidence_highConfLlm_chitchat_writesAnchor() {
        when(deepSeekModelService.chatFastWithUsage(anyString(), anyString()))
                .thenReturn(new ChatResult("{\"type\":\"CHITCHAT\",\"confidence\":0.9}", 10, 2));
        IntentRouter router = newRouterWithPool();

        router.classifyWithUsage("帮我匹配下候选人");
        assertEquals(1, router.anchorPoolSize(IntentType.CHITCHAT),
                "低置信+LLM高置信(conf≥0.9)+CHITCHAT 应直接回写动态锚点");
    }

    @Test
    void lowConfidence_hitl_doesNotWriteAnchor() {
        when(deepSeekModelService.chatFastWithUsage(anyString(), anyString()))
                .thenReturn(new ChatResult("{\"type\":\"HITL\",\"confidence\":0.95}", 10, 2));
        IntentRouter router = newRouterWithPool();

        router.classifyWithUsage("发 offer 给张三");
        assertEquals(0, router.anchorPoolSize(IntentType.HITL),
                "HITL 即使高置信也不回写 (直放本已禁)");
    }

    @Test
    void lowConfidence_lowConfLlm_doesNotWriteAnchor() {
        when(deepSeekModelService.chatFastWithUsage(anyString(), anyString()))
                .thenReturn(new ChatResult("{\"type\":\"SINGLE_TOOL\",\"confidence\":0.5}", 10, 2));
        IntentRouter router = newRouterWithPool();

        router.classifyWithUsage("随便看看");
        assertEquals(0, router.anchorPoolSize(IntentType.SINGLE_TOOL),
                "LLM 置信度低于阈值 (0.9) 不回写");
    }
}
