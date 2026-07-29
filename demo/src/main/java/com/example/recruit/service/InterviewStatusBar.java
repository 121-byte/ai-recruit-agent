package com.example.recruit.service;

import com.example.recruit.dal.entity.InterviewSession;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 面试官 —— Agent 状态栏 (Agent Status Bar)。
 * 在每次送模型的上下文末尾注入一段结构化「运行时状态摘要」，让面试官模型「瞥一眼」
 * 就知道当前面试进展，据此决定「继续追问 / 换维度 / 收尾」。
 *
 * <p>四条铁律：
 * <ol>
 *   <li>用代码维护，不用大模型（关键词/计数/正则，绝不调 LLM）</li>
 *   <li>放上下文末尾、{@code <agent_status>} 标签包裹、不改 System Prompt（保护 KV Cache）</li>
 *   <li>读数 + 操作策略成对给出（光有读数不改变行为）</li>
 *   <li>结构化键值对而非散文</li>
 * </ol>
 */
@Component
public class InterviewStatusBar {

    private static final Logger log = LoggerFactory.getLogger(InterviewStatusBar.class);

    /** 默认面试总时长（分钟），可由 application.properties 的 interview.total-minutes 覆盖。 */
    @Value("${interview.total-minutes:30}")
    private int totalMinutes;

    /** 同一考察点允许连续追问的次数上限（到顶即应换维度）。 */
    @Value("${interview.follow-up-limit:3}")
    private int followUpLimit;

    /** 收尾阈值：剩余时长低于此值时，策略优先收尾与开放题。 */
    @Value("${interview.wrap-up-threshold:5}")
    private int wrapUpThreshold;

    // ── 维度关键词（命中其一即归该维度；按技术优先匹配） ──
    private static final String[] TECH_KEYWORDS = {
            "算法", "数据结构", "复杂度", "框架", "数据库", "索引", "事务", "SQL", "系统设计",
            "接口", "API", "并发", "多线程", "锁", "性能", "优化", "编程", "代码", "源码",
            "原理", "JVM", "GC", "Java", "Python", "Spring", "Redis", "Kafka", "微服务",
            "分布式", "缓存", "消息队列", "设计模式", "网络", "TCP", "HTTP", "操作系统", "Linux"
    };
    private static final String[] BEHAVIOR_KEYWORDS = {
            "项目经历", "项目", "团队", "协作", "沟通", "冲突", "挑战", "困难", "压力", "失败",
            "成就", "学习", "成长", "职业规划", "职业", "离职", "期望", "自我介绍", "优势", "缺点"
    };

    private enum Dimension { TECH, BEHAVIOR, OTHER }

    /** 构造状态栏文本（含 <agent_status> 标签）。session 为空时返回空串。 */
    public String build(InterviewSession session) {
        if (session == null || session.getMessages() == null) {
            return "";
        }
        List<String> interviewerTexts = collectInterviewerTexts(session.getMessages());
        int[] counts = countByDimension(interviewerTexts); // [tech, behavior, other]
        int stageRound = session.getCurrentRound() == null ? 1 : session.getCurrentRound();
        int totalAsked = counts[0] + counts[1] + counts[2];

        String stage = inferStage(stageRound, totalAsked);
        int remainingMin = computeRemainingMinutes(session.getCreatedAt());
        int followUpDepth = computeFollowUpDepth(interviewerTexts);
        String strategy = inferStrategy(remainingMin, followUpDepth);

        StringBuilder sb = new StringBuilder();
        sb.append("<agent_status>\n");
        sb.append("当前面试阶段: ").append(stage).append('\n');
        sb.append("已提问: 技术题×").append(counts[0])
          .append("、行为题×").append(counts[1])
          .append("、其他×").append(counts[2]).append('\n');
        sb.append("候选人剩余时长: ").append(remainingMin).append("min").append('\n');
        sb.append("本场已追问同点: ").append(followUpDepth).append('/').append(followUpLimit).append('\n');
        sb.append("策略: ").append(strategy).append('\n');
        sb.append("</agent_status>");
        return sb.toString();
    }

    /** 把状态栏拼接到给定 user 内容末尾（若状态栏为空则原样返回）。 */
    public String appendTo(String userContent, InterviewSession session) {
        String bar = build(session);
        if (bar != null && !bar.isEmpty()) {
            // 调试/运维用：记录实际注入到送模型 user 内容末尾的状态栏原文
            log.info("[StatusBar] injected into model prompt:\n{}", bar);
        }
        if (bar == null || bar.isEmpty()) {
            return userContent == null ? "" : userContent;
        }
        return (userContent == null ? "" : userContent) + "\n\n" + bar;
    }

    // ─────────────────── 内部计算（纯代码，无 LLM） ───────────────────

    /** 收集所有 interviewer 角色的消息内容。 */
    private List<String> collectInterviewerTexts(JsonNode messagesRoot) {
        List<String> texts = new ArrayList<>();
        JsonNode arr = messagesRoot.path("messages");
        if (!arr.isArray()) {
            return texts;
        }
        for (JsonNode m : arr) {
            String role = m.path("role").asText("");
            if ("interviewer".equalsIgnoreCase(role)) {
                texts.add(m.path("content").asText(""));
            }
        }
        return texts;
    }

    /** 按维度统计提问数量，返回 [tech, behavior, other]。 */
    private int[] countByDimension(List<String> interviewerTexts) {
        int tech = 0, behavior = 0, other = 0;
        for (String text : interviewerTexts) {
            switch (classify(text)) {
                case TECH -> tech++;
                case BEHAVIOR -> behavior++;
                default -> other++;
            }
        }
        return new int[]{tech, behavior, other};
    }

    /** 关键词分类：技术优先，其次行为，命中任一关键词即归类；空文本记 OTHER。 */
    private Dimension classify(String text) {
        if (text == null || text.isBlank()) {
            return Dimension.OTHER;
        }
        for (String k : TECH_KEYWORDS) {
            if (text.contains(k)) return Dimension.TECH;
        }
        for (String k : BEHAVIOR_KEYWORDS) {
            if (text.contains(k)) return Dimension.BEHAVIOR;
        }
        return Dimension.OTHER;
    }

    /** 依据轮次与已问题数推断面试阶段。 */
    private String inferStage(int round, int totalAsked) {
        if (totalAsked <= 0 && round <= 1) return "开场";
        if (round >= 7 || totalAsked >= 7) return "收尾";
        if (round <= 3 && totalAsked <= 3) return "技术深挖";
        return "行为综合";
    }

    /** 剩余时长（分钟，下限 0）。 */
    private int computeRemainingMinutes(LocalDateTime createdAt) {
        if (createdAt == null) return totalMinutes;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(createdAt)) return totalMinutes;
        long elapsed = Duration.between(createdAt, now).toMinutes();
        return Math.max(0, totalMinutes - (int) elapsed);
    }

    /** 最近连续同一维度的追问深度：从最后一条问题向前扫，连续同维度则深度 +1，不同则停。 */
    private int computeFollowUpDepth(List<String> interviewerTexts) {
        if (interviewerTexts.isEmpty()) return 0;
        Dimension last = classify(interviewerTexts.get(interviewerTexts.size() - 1));
        if (last == Dimension.OTHER) return 0;
        int depth = 1;
        for (int i = interviewerTexts.size() - 2; i >= 0; i--) {
            if (classify(interviewerTexts.get(i)) == last) depth++;
            else break;
        }
        return depth;
    }

    /** 依读数生成「读数 → 动作」的策略说明（光有读数不改变行为，必须配可执行策略）。 */
    private String inferStrategy(int remainingMin, int followUpDepth) {
        List<String> parts = new ArrayList<>();
        if (followUpDepth >= followUpLimit) {
            parts.add("同一考察点追问已达上限，立即切换维度");
        } else {
            parts.add("同一考察点追问达 " + followUpLimit + " 次未深入即换维度");
        }
        if (remainingMin <= wrapUpThreshold) {
            parts.add("剩余 <" + (wrapUpThreshold + 1) + "min 优先收尾与开放题");
        } else {
            parts.add("剩余充足可适度深挖核心难点");
        }
        return String.join("；", parts);
    }
}
