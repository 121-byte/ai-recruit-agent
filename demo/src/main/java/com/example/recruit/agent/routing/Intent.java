package com.example.recruit.agent.routing;

/**
 * 意图分类结果 (复刻自文档 §4.2)。
 *
 * @param type      意图类型
 * @param confidence 置信度 0.0-1.0
 */
public record Intent(IntentType type, double confidence) {

    public static Intent of(IntentType type, double confidence) {
        return new Intent(type, confidence);
    }
}
