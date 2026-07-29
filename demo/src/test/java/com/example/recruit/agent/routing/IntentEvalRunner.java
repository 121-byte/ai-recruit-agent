package com.example.recruit.agent.routing;

import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.retrieval.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Intent 路由 / 离线评估测试 (OpenSpec p5-tests §2 task 7)。
 *
 * <p>原计划实现完整五策略离线评估 (IntentEvalRunner)，但评估数据集缺失，
 * 故简化为 {@link IntentRouter} 单元测试 (任务允许)：mock EmbeddingService +
 * DeepSeekModelService，隔离真实向量服务/LLM，验证 {@link IntentRouter#classify}
 * 对高置信度输入直接命中 CHITCHAT、对空/空白输入回退 CHITCHAT。
 *
 * <p>EmbeddingService 返回基于文本字节的确定性归一化向量 (复刻
 * {@link EmbeddingService} 的 mockEmbed 思路)：相同文本 → 相同向量 → 余弦=1.0，
 * 使 "你好" 与 CHITCHAT 静态锚点 "你好" 高置信度命中 (≥0.85)，零 LLM 调用。
 *
 * <p>注：文件名遵循任务约定 {@code IntentEvalRunner.java}；测试类名
 * {@code IntentRouterTest} 以匹配 maven-surefire 默认测试发现模式。
 */
@ExtendWith(MockitoExtension.class)
class IntentRouterTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private DeepSeekModelService deepSeekModelService;

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        router = new IntentRouter(embeddingService, deepSeekModelService);

        // 确定性归一化向量: 相同文本 → 相同向量 → 余弦 1.0
        when(embeddingService.embed(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0, String.class);
            return deterministicVector(text == null ? "" : text);
        });

        // 手动触发 @PostConstruct initAnchors()(单元测试下 Spring 不会自动调用)
        router.initAnchors();
    }

    private static float[] deterministicVector(String text) {
        int dim = 1024;
        float[] v = new float[dim];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            return v;
        }
        double norm = 0;
        for (int i = 0; i < dim; i++) {
            int b = bytes[i % bytes.length] & 0xFF;
            int idx = (i * 31 + b) % 256;
            v[i] = (idx - 128) / 128.0f;
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }

    @Test
    void classify_highConfidenceGreeting_returnsChitchat() {
        // "你好" 与 CHITCHAT 静态锚点 "你好" 完全相同 → 余弦 1.0 ≥ 0.85, 零 LLM 调用
        Intent intent = router.classify("你好");
        assertNotNull(intent, "classify 应返回非 null Intent");
        assertEquals(IntentType.CHITCHAT, intent.type(),
                "问候应分类为 CHITCHAT: " + intent);
    }

    @Test
    void classify_nullInput_returnsChitchat() {
        Intent intent = router.classify(null);
        assertNotNull(intent);
        assertEquals(IntentType.CHITCHAT, intent.type(), "null 输入回退 CHITCHAT");
        assertEquals(1.0, intent.confidence(), 0.0001, "空输入置信度应为 1.0");
    }

    @Test
    void classify_blankInput_returnsChitchat() {
        Intent intent = router.classify("   ");
        assertNotNull(intent);
        assertEquals(IntentType.CHITCHAT, intent.type(), "空白输入回退 CHITCHAT");
    }

    @Test
    void classify_anotherGreeting_returnsChitchat() {
        // "你好" 命中; "您好" 是另一个 CHITCHAT 锚点但向量不同, 仍属 CHITCHAT 类
        Intent intent = router.classify("您好");
        assertNotNull(intent);
        // "您好" 与锚点 "您好" 完全相同 → 余弦 1.0 → CHITCHAT
        assertEquals(IntentType.CHITCHAT, intent.type());
    }
}
