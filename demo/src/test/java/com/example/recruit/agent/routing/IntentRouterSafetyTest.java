package com.example.recruit.agent.routing;

import com.example.recruit.infra.llm.ChatResult;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.retrieval.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntentRouterSafetyTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private DeepSeekModelService deepSeekModelService;

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        router = new IntentRouter(embeddingService, deepSeekModelService);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});
    }

    @Test
    void hitlVectorMatch_stillUsesLlmConfirmation() throws Exception {
        setAnchors(Map.of(
                IntentType.HITL, List.of(new float[]{1f, 0f}),
                IntentType.CHITCHAT, List.of(new float[]{0f, 1f})
        ));
        when(deepSeekModelService.chatFastWithUsage(anyString(), anyString()))
                .thenReturn(new ChatResult("{\"type\":\"HITL\",\"confidence\":0.99}", 10, 2));

        IntentRouter.IntentWithUsage result = router.classifyWithUsage("发 offer");

        assertEquals(IntentType.HITL, result.intent().type());
        verify(deepSeekModelService).chatFastWithUsage(anyString(), anyString());
    }

    @Test
    void closeVectorScores_useLlmInsteadOfDirectRoute() throws Exception {
        setAnchors(Map.of(
                IntentType.CHITCHAT, List.of(vector(0.91f)),
                IntentType.SINGLE_TOOL, List.of(vector(0.85f))
        ));
        when(deepSeekModelService.chatFastWithUsage(anyString(), anyString()))
                .thenReturn(new ChatResult("{\"type\":\"CHITCHAT\",\"confidence\":0.9}", 10, 2));

        IntentRouter.IntentWithUsage result = router.classifyWithUsage("帮我处理一下");

        assertEquals(IntentType.CHITCHAT, result.intent().type());
        verify(deepSeekModelService).chatFastWithUsage(anyString(), anyString());
    }

    @Test
    void invalidLlmOutput_returnsClarifyInsteadOfToolRoute() throws Exception {
        setAnchors(Map.of(IntentType.CHITCHAT, List.of(new float[]{0f, 1f})));
        when(deepSeekModelService.chatFastWithUsage(anyString(), anyString()))
                .thenReturn(new ChatResult("not-json", 10, 2));

        IntentRouter.IntentWithUsage result = router.classifyWithUsage("无法识别的请求");

        assertEquals(IntentType.CLARIFY, result.intent().type());
    }

    private void setAnchors(Map<IntentType, List<float[]>> configured) throws Exception {
        Field field = IntentRouter.class.getDeclaredField("anchorEmbeddings");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<IntentType, List<float[]>> anchors = (Map<IntentType, List<float[]>>) field.get(router);
        anchors.clear();
        anchors.putAll(new EnumMap<>(IntentType.class));
        anchors.putAll(configured);
    }

    private static float[] vector(float cosine) {
        return new float[]{cosine, (float) Math.sqrt(1 - cosine * cosine)};
    }
}
