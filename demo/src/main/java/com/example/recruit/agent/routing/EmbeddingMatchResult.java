package com.example.recruit.agent.routing;

/**
 * Embedding 匹配结果 (复刻自文档 §4.2 matchWithEmbedding)。
 *
 * @param bestType     最高分意图
 * @param bestScore    最高分
 * @param secondType   不同意图类型中的次高分意图 (确保 Top-2 是两个不同意图的二选一)
 * @param secondScore  次高分
 * @param userEmbedding 用户输入向量 (复用, 避免 LLM 回写时重复 embed)
 */
record EmbeddingMatchResult(
        IntentType bestType,
        double bestScore,
        IntentType secondType,
        double secondScore,
        float[] userEmbedding) {
}
