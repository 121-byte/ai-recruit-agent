package com.example.recruit.module.interview.application;

import com.example.recruit.dal.entity.InterviewSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InterviewStatusBar} 单元测试 —— 纯 JUnit 5，无 Spring/DB。
 * 用反射注入 @Value 配置，构造含多轮对话的 InterviewSession 验证读数。
 */
class InterviewStatusBarTest {

    private static final ObjectMapper M = new ObjectMapper();

    private InterviewStatusBar newBar(int totalMin, int followUpLimit, int wrapUp) throws Exception {
        InterviewStatusBar bar = new InterviewStatusBar();
        set(bar, "totalMinutes", totalMin);
        set(bar, "followUpLimit", followUpLimit);
        set(bar, "wrapUpThreshold", wrapUp);
        return bar;
    }

    private static void set(Object o, String name, Object val) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, val);
    }

    private ObjectNode msg(String role, String content) {
        ObjectNode m = M.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        m.put("timestamp", LocalDateTime.now().toString());
        return m;
    }

    /** 一场含多轮对话的会话：行为(自我介绍)/技术(Redis)/技术(Spring)/行为(挑战)/行为(冲突)。 */
    private InterviewSession buildSession(int round, LocalDateTime createdAt) {
        InterviewSession s = new InterviewSession();
        ObjectNode root = M.createObjectNode();
        ArrayNode arr = root.putArray("messages");
        arr.add(msg("interviewer", "请先做个自我介绍"));
        arr.add(msg("interviewer", "讲讲你项目中 Redis 缓存的设计"));
        arr.add(msg("interviewer", "聊聊 Spring 框架的原理"));
        arr.add(msg("interviewer", "工作中最大的挑战是什么"));
        arr.add(msg("interviewer", "团队里遇到过冲突吗"));
        s.setMessages(root);
        s.setCurrentRound(round);
        s.setDifficultyLevel("medium");
        s.setCreatedAt(createdAt);
        return s;
    }

    @Test
    void buildsExpectedStatusBar() throws Exception {
        InterviewStatusBar bar = newBar(30, 3, 5);
        InterviewSession s = buildSession(4, LocalDateTime.now().plusMinutes(1));

        String out = bar.build(s);

        assertTrue(out.contains("<agent_status>"));
        // round=4, totalAsked=5 → 行为综合
        assertTrue(out.contains("当前面试阶段: 行为综合"));
        // 技术题×2 (Redis, Spring) 行为题×3 (自我介绍, 挑战, 冲突)
        assertTrue(out.contains("技术题×2"));
        assertTrue(out.contains("行为题×3"));
        assertTrue(out.contains("其他×0"));
        // 末尾两条行为题 → 已追问同点 2/3
        assertTrue(out.contains("已追问同点: 2/3"));
        // createdAt 略晚于 now → 返回 totalMinutes
        assertTrue(out.contains("剩余时长: 30min"));
        // depth=2 < followUpLimit=3 → 未触发「立即切换维度」，但策略应含「换维度」
        assertTrue(out.contains("换维度"));
        assertFalse(out.contains("立即切换维度"));
    }

    @Test
    void strategySwitchesDimensionAtLimit() throws Exception {
        InterviewStatusBar bar = newBar(30, 3, 5);
        InterviewSession s = new InterviewSession();
        ObjectNode root = M.createObjectNode();
        ArrayNode arr = root.putArray("messages");
        arr.add(msg("interviewer", "讲讲 Redis 缓存原理"));
        arr.add(msg("interviewer", "聊聊 Spring 框架"));
        arr.add(msg("interviewer", "数据库索引怎么优化"));
        s.setMessages(root);
        s.setCurrentRound(5);
        s.setCreatedAt(LocalDateTime.now().plusMinutes(1));

        String out = bar.build(s);

        // 连续 3 条技术题 → 已追问同点 3/3 + 策略含「立即切换维度」
        assertTrue(out.contains("已追问同点: 3/3"));
        assertTrue(out.contains("立即切换维度"));
    }

    @Test
    void emptySessionReturnsBlank() throws Exception {
        InterviewStatusBar bar = newBar(30, 3, 5);
        InterviewSession s = new InterviewSession();
        ObjectNode root = M.createObjectNode();
        root.putArray("messages"); // 空数组
        s.setMessages(root);
        s.setCurrentRound(1);
        s.setCreatedAt(LocalDateTime.now().plusMinutes(1));

        String out = bar.build(s);

        // 无 interviewer 消息 → 技术题×0；剩余 30min；不含 null 字面量
        assertFalse(out.contains("null"));
        assertTrue(out.contains("技术题×0"));
        assertTrue(out.contains("剩余时长: 30min"));
    }

    @Test
    void appendToConcatenatesAtTail() throws Exception {
        InterviewStatusBar bar = newBar(30, 3, 5);
        InterviewSession s = buildSession(4, LocalDateTime.now().plusMinutes(1));
        String user = "回答: 我用 Redis 做了缓存";

        String out = bar.appendTo(user, s);

        // 原 user 内容在最前，<agent_status> 拼接在其后
        assertTrue(out.startsWith(user));
        int barStart = out.indexOf("<agent_status>");
        int userEnd = out.indexOf(user) + user.length();
        assertTrue(barStart > userEnd);
        assertTrue(out.contains("</agent_status>"));
    }
}
