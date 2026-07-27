package com.example.recruit.agent.routing;

import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 意图路由器 (复刻自文档 §4.2)。
 *
 * <p>三段架构：<b>分层置信度 + 动态锚点自学习 + Top-2 LLM 验证</b>。
 *
 * <ol>
 *   <li>Embedding 匹配：对用户输入与所有静态/动态锚点算余弦相似度</li>
 *   <li>高置信度 (≥0.85) → 直接返回，零 LLM 调用</li>
 *   <li>中置信度 (0.65-0.85) → Top-2 二选一 LLM 验证 (~60 tokens prompt)</li>
 *   <li>低置信度 (&lt;0.65) → 全量五分类 LLM (~200 tokens prompt)</li>
 *   <li>LLM 结果 confidence ≥ 0.7 → 回写为动态锚点 (自学习)</li>
 * </ol>
 *
 * <p>Top-2 验证在保持准确率的同时节省 ~48% token (文档 §6.7 评估)。
 */
@Component
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    private static final double HIGH_THRESHOLD = 0.85;
    private static final double MID_THRESHOLD = 0.65;
    private static final double DYNAMIC_WRITEBACK_CONFIDENCE = 0.7;

    private final EmbeddingService embeddingService;
    private final DynamicAnchorPool dynamicAnchorPool;
    private final DeepSeekModelService deepSeekModelService;

    /** 五类意图静态锚点 (复刻自文档 §4.2)。 */
    private static final Map<IntentType, List<String>> ANCHOR_SENTENCES = Map.of(
            IntentType.CHITCHAT, List.of(
                    "你好", "您好", "你是谁", "你能做什么", "谢谢", "再见",
                    "介绍一下你自己", "今天天气怎么样", "帮我什么忙"),
            IntentType.SINGLE_TOOL, List.of(
                    "分析岗位1", "匹配候选人", "生成面试题", "查看简历3",
                    "搜索Java最新趋势", "分析岗位的技能要求", "查看岗位列表"),
            IntentType.COMPOSITE, List.of(
                    "帮我招一个前端工程师", "全流程招聘P7后端",
                    "从筛选到面试Java候选人", "帮我找一个会React的前端",
                    "帮我完成整个招聘流程"),
            IntentType.HITL, List.of(
                    "发offer给张三", "淘汰这批候选人", "批量邀约5个候选人",
                    "确认入职", "拒绝候选人"),
            IntentType.BATCH_INDEPENDENT, List.of(
                    "分析岗位1、2、3并分别匹配候选人",
                    "解析这些简历并生成面试题", "分别分析这三个岗位")
    );

    /** 静态锚点向量缓存。 */
    private final Map<IntentType, List<float[]>> anchorEmbeddings = new EnumMap<>(IntentType.class);

    private static final String TOP2_PROMPT_TEMPLATE = """
            你是意图分类器。用户输入可能是以下两种意图之一，请选择更匹配的一个：
            - %s: %s
            - %s: %s
            请以JSON输出: {"type":"%s|%s","confidence":0.0-1.0}
            """;

    private static final String CLASSIFY_PROMPT = """
            你是招聘系统的意图分类器。请将用户输入分类为以下五类之一：
            - CHITCHAT: 闲聊/问候/能力咨询 (你好/你是谁/谢谢)
            - SINGLE_TOOL: 单一工具操作 (分析岗位X/匹配候选人/生成面试题/搜索简历)
            - COMPOSITE: 需要多步骤全流程 (帮我招一个XX/全流程招聘/从筛选到面试)
            - HITL: 需要人工确认的高危操作 (发offer/淘汰候选人/批量邀约/确认入职)
            - BATCH_INDEPENDENT: 多个独立任务的批量执行 (分析岗位1、2、3并分别匹配/分别分析这些)
            用户输入: %s
            请以JSON输出: {"type":"CHITCHAT|SINGLE_TOOL|COMPOSITE|HITL|BATCH_INDEPENDENT","confidence":0.0-1.0,"reason":"简短理由"}
            """;

    public IntentRouter(EmbeddingService embeddingService,
                         DynamicAnchorPool dynamicAnchorPool,
                         DeepSeekModelService deepSeekModelService) {
        this.embeddingService = embeddingService;
        this.dynamicAnchorPool = dynamicAnchorPool;
        this.deepSeekModelService = deepSeekModelService;
    }

    @PostConstruct
    void initAnchors() {
        // 1. 对每类静态锚点句算 embedding 并缓存
        for (IntentType type : IntentType.values()) {
            List<String> sentences = ANCHOR_SENTENCES.getOrDefault(type, List.of());
            List<float[]> embs = new java.util.ArrayList<>(sentences.size());
            for (String s : sentences) {
                embs.add(embeddingService.embed(s));
            }
            anchorEmbeddings.put(type, embs);
        }
        // 2. 为动态锚点池中无 embedding 的条目补算 (load() 时未存向量的)
        for (IntentType type : IntentType.values()) {
            for (DynamicAnchorPool.AnchorEntry entry : dynamicAnchorPool.getAnchors(type)) {
                if (entry.embedding == null || entry.embedding.length == 0) {
                    entry.embedding = embeddingService.embed(entry.text);
                }
            }
        }
        log.info("IntentRouter initialized: {} static anchors across {} types",
                ANCHOR_SENTENCES.values().stream().mapToInt(List::size).sum(),
                IntentType.values().length);
    }

    /**
     * 分类用户输入 (复刻自文档 §4.2 classify)。
     */
    public Intent classify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Intent.of(IntentType.CHITCHAT, 1.0);
        }

        EmbeddingMatchResult match = matchWithEmbedding(userMessage);

        // 高置信度：直接返回，零 LLM 调用
        if (match.bestScore() >= HIGH_THRESHOLD) {
            return Intent.of(match.bestType(), match.bestScore());
        }

        // 中置信度：Top-2 LLM 二选一验证
        if (match.bestScore() >= MID_THRESHOLD) {
            Intent llmIntent = classifyWithLlmTop2(userMessage, match.bestType(), match.secondType());
            maybeWriteback(llmIntent, userMessage, match.userEmbedding());
            return llmIntent;
        }

        // 低置信度：LLM 全量五分类
        Intent llmIntent = classifyWithLlm(userMessage);
        maybeWriteback(llmIntent, userMessage, match.userEmbedding());
        return llmIntent;
    }

    /**
     * 对用户输入与所有静态/动态锚点算余弦相似度，返回最佳与次佳 (不同意图)。
     * (复刻自文档 §4.2 matchWithEmbedding)
     */
    private EmbeddingMatchResult matchWithEmbedding(String userMessage) {
        float[] userEmb = embeddingService.embed(userMessage);

        IntentType bestType = IntentType.CHITCHAT;
        double bestScore = -1;
        IntentType secondType = IntentType.CHITCHAT;
        double secondScore = -1;

        // 遍历每类意图，记录该类最高分；从不同类型中选次高
        for (IntentType type : IntentType.values()) {
            double typeBest = -1;
            // 静态锚点
            List<float[]> statics = anchorEmbeddings.getOrDefault(type, List.of());
            for (float[] anchor : statics) {
                double score = FloatVectorCosine(userEmb, anchor);
                if (score > typeBest) {
                    typeBest = score;
                }
            }
            // 动态锚点
            for (DynamicAnchorPool.AnchorEntry entry : dynamicAnchorPool.getAnchors(type)) {
                if (entry.embedding != null && entry.embedding.length > 0) {
                    double score = FloatVectorCosine(userEmb, entry.embedding);
                    if (score > typeBest) {
                        typeBest = score;
                    }
                }
            }

            if (typeBest > bestScore) {
                // 当前最高降为次高 (不同意图)
                secondScore = bestScore;
                secondType = bestType;
                bestScore = typeBest;
                bestType = type;
            } else if (typeBest > secondScore) {
                secondScore = typeBest;
                secondType = type;
            }
        }

        return new EmbeddingMatchResult(bestType, Math.max(0, bestScore), secondType, Math.max(0, secondScore), userEmb);
    }

    /**
     * Top-2 LLM 二选一验证 (复刻自文档 §4.2 classifyWithLlmTop2)。
     * 仅发送 2 个候选意图，prompt 约 60 tokens，比五分类短 70%。
     */
    private Intent classifyWithLlmTop2(String userMessage, IntentType candidate1, IntentType candidate2) {
        String prompt = String.format(TOP2_PROMPT_TEMPLATE,
                candidate1, describe(candidate1),
                candidate2, describe(candidate2),
                candidate1, candidate2);
        String reply = deepSeekModelService.chatFast(prompt, "用户输入: " + userMessage);
        JsonNode node = JsonGuard.parseJsonSafe(reply);
        if (node != null) {
            String typeStr = JsonGuard.text(node, "type");
            double conf = node.path("confidence").asDouble(0.5);
            try {
                IntentType t = IntentType.valueOf(typeStr);
                return Intent.of(t, conf);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // 解析失败：回退到 embedding 最佳
        return Intent.of(candidate1, MID_THRESHOLD);
    }

    /** 全量五分类 LLM (复刻自文档 §4.2 classifyWithLlm)。 */
    private Intent classifyWithLlm(String userMessage) {
        String prompt = String.format(CLASSIFY_PROMPT, userMessage);
        String reply = deepSeekModelService.chatFast("你是招聘系统的意图分类器。", prompt);
        JsonNode node = JsonGuard.parseJsonSafe(reply);
        if (node != null) {
            String typeStr = JsonGuard.text(node, "type");
            double conf = node.path("confidence").asDouble(0.5);
            try {
                return Intent.of(IntentType.valueOf(typeStr), conf);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Intent.of(IntentType.SINGLE_TOOL, 0.5);
    }

    /**
     * LLM 结果 confidence ≥ 0.7 时回写为动态锚点 (复刻自文档 §4.2 maybeWriteback)。
     */
    private void maybeWriteback(Intent intent, String userMessage, float[] userEmbedding) {
        if (intent.confidence() >= DYNAMIC_WRITEBACK_CONFIDENCE) {
            float[] emb = userEmbedding != null ? userEmbedding : embeddingService.embed(userMessage);
            dynamicAnchorPool.add(intent.type(), userMessage, emb);
        }
    }

    private String describe(IntentType type) {
        return switch (type) {
            case CHITCHAT -> "闲聊/问候/能力咨询";
            case SINGLE_TOOL -> "单一工具操作 (分析岗位/匹配/出题/搜索)";
            case COMPOSITE -> "多步骤全流程招聘";
            case HITL -> "需人工确认的高危操作";
            case BATCH_INDEPENDENT -> "多个独立任务批量执行";
        };
    }

    private static double FloatVectorCosine(float[] a, float[] b) {
        // 复用 FloatVectorTypeHandler 的实现，避免循环依赖
        return com.example.recruit.dal.handler.FloatVectorTypeHandler.cosine(a, b);
    }

    @PreDestroy
    void destroy() {
        dynamicAnchorPool.flush();
    }
}
