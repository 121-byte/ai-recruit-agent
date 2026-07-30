package com.example.recruit.common;

import java.util.List;

/**
 * 岗位类别常量 (规范化的意向岗位/岗位分类)。
 *
 * <p>用于简历意向岗位的归类筛选与岗位 category 字段。简历分析时 LLM 必须从该集合中
 * 选一个返回, 避免出现 "Java工程师 / 前端 / 后端" 这种碎片化取值导致筛选项爆炸。
 */
public final class PositionCategories {

    private PositionCategories() {
    }

    /** 固定类别集合 (有序)。 */
    public static final List<String> CATEGORIES = List.of(
            "技术", "产品", "设计", "运营", "市场", "销售",
            "人事", "财务", "行政", "客服", "其他");

    /** 逗号分隔的类别串, 用于拼 LLM 提示词。 */
    public static final String CATEGORY_PROMPT = String.join(" / ", CATEGORIES);

    /**
     * 校验/归一化类别: 若属于固定集合原样返回, 否则返回 "其他"。
     */
    public static String normalize(String category) {
        if (category == null || category.isBlank()) {
            return "其他";
        }
        String trimmed = category.trim();
        return CATEGORIES.contains(trimmed) ? trimmed : "其他";
    }
}
