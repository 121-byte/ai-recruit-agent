package com.example.recruit.infra.llm;

/**
 * LLM 调用结果（带 token 使用量）。复刻自文档 §9.1 ChatResult。
 */
public record ChatResult(String content, int inputTokens, int outputTokens) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
