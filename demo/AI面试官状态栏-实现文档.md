# AI 面试官 —— Agent 状态栏（Agent Status Bar）业务实现文档

> 本文档面向「在另一个同类 AI 招聘系统项目中复现本功能」的 AI / 开发者。
> 仅记录**业务功能代码实现**，**不含**大模型 API Key / base-url / 模型名、Embedding/Rerank 配置、PostgreSQL/Redis/RabbitMQ 连接等任何**本地基础设施配置**——这些由各项目自身的本地配置文件承载，请按你自己的环境填写。

---

## 1. 背景与目标

### 1.1 要解决的问题
AI 面试官在多轮问答中容易陷入三类陷阱：
- **状态遗忘**：记不清"已经问了几题、问过哪些维度"；
- **死磕一个点**：反复追问同一考察点，不主动换维度；
- **节奏失控**：剩余时间不多还在深挖，或超时仍不收尾。

根源在于：模型擅长从上下文里"检索"信息，但不擅长"自动数一遍 / 就地总结"。每次要"统计提问次数 / 推断阶段"都得把整段历史重新扫描现算，随上下文变长代价涨且算错。

### 1.2 方案
给面试官模型装一块**用代码维护的、贴在上下文末尾的实时仪表盘**——每次调大模型前，注入一段结构化 `<agent_status>` 状态摘要，让模型"瞥一眼"就知道当前面试进展，据此决定追问 / 换维度 / 收尾。模型从"被硬截断"变成"自己知道到顶了该换策略"。

### 1.3 思想来源
《大模型应用开发：基于上下文工程》Chapter 2「Agent 状态栏」一节。其核心类比：状态栏像手机屏幕顶部的电量/时间/信号——不是 App 主内容，但随时能看一眼掌握设备状态。

---

## 2. 设计原则（四条铁律，必须遵守）

| 铁律 | 说明 | 本实现如何落实 |
|------|------|----------------|
| **① 用代码维护，不用大模型** | 让 LLM 去批量统计长历史，反而比不用状态栏还差；20 行正则打趴前沿大模型 | 状态栏所有读数由关键词/计数/正则算出，**绝不调用 LLM** |
| **② 放上下文末尾、`<agent_status>` 标签包裹、不改 System Prompt** | 改 System Prompt 会破坏 KV Cache；状态是动态的，追加在末尾才不破坏已缓存的前缀 | 调用方把状态栏拼到送模型的 **user 内容末尾**，System Prompt 保持不动 |
| **③ 读数 + 操作策略成对给出** | 实测"只给读数几乎不改变行为"，必须配一句"该怎么做" | 每条状态栏都带一行**策略**（如"剩余充足可深挖""到顶立即换维度"） |
| **④ 结构化键值对而非散文** | 写成 `技术题×3` 模型一眼定位；散文还得让它重新解析，等于回到"扫描" | 用 `当前面试阶段: XX` / `技术题×N` 等键值对 |

> 补充收益（来自书里 2.4 万次评测）：对弱模型补**准确率**（+40~54 个百分点），对强模型补**效率**（思考量/延迟/成本各降约一个数量级），且思考量随上下文增长由"持续增长"变为"基本恒定"。

---

## 3. 涉及数据（业务数据结构，非基础设施）

状态栏的输入是一场面试会话 `InterviewSession`。复用既有表/实体，**不新增表**。

### 3.1 实体 `InterviewSession`
```java
@Data
@TableName(value = "interview_session", autoResultMap = true)
public class InterviewSession {
    @TableId(type = IdType.AUTO) private Long id;
    private Long interviewId;
    /** 对话消息列表 (JSONB) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode messages;
    private Integer currentRound;      // 当前轮次
    /** 难度等级: easy/medium/hard */
    private String difficultyLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.2 `messages` 的 JSONB 结构
状态栏只读 `role="interviewer"` 的消息内容来分类计数，结构如下：
```json
{
  "messages": [
    { "role": "interviewer", "content": "请先做个自我介绍", "timestamp": "..." },
    { "role": "candidate",   "content": "我是后端工程师...", "timestamp": "..." },
    { "role": "interviewer", "content": "讲讲你项目中 Redis 缓存的设计", "timestamp": "..." }
  ]
}
```

---

## 4. 后端实现（Spring Boot + MyBatis-Plus，纯新增，无侵入）

### 4.1 新建状态栏组件 `InterviewStatusBar`
路径：`com.example.recruit.service.InterviewStatusBar`

用纯 Java 代码算出 5 个维度的状态栏文本，并暴露 `build(session)` 与 `appendTo(user, session)` 两个方法。

```java
package com.example.recruit.service;

import com.example.recruit.dal.entity.InterviewSession;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 面试官 —— Agent 状态栏 (Agent Status Bar)。
 * 在每次送模型的上下文末尾注入一段结构化「运行时状态摘要」，让面试官模型「瞥一眼」
 * 就知道当前面试进展，据此决定「继续追问 / 换维度 / 收尾」。
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
```

### 4.2 状态栏算出的 5 个维度

| 维度 | 计算方式 | 例 |
|------|----------|-----|
| 当前面试阶段 | 按 `currentRound` + 已问题数推断：开场 / 技术深挖 / 行为综合 / 收尾 | `技术深挖` |
| 已提问维度计数 | 扫所有 `interviewer` 消息，关键词分类计数 | `技术题×2、行为题×1、其他×0` |
| 候选人剩余时长 | 总时长(默认30min) − 已用时长，下限 0 | `25min` |
| 本场已追问同点深度 | 从最后一条问题向前扫，连续同维度题数 / 上限 | `2/3` |
| 操作策略 | 依剩余时长与追问深度动态生成 | `同一考察点追问达 3 次未深入即换维度；剩余充足可适度深挖核心难点` |

### 4.3 接入面试官服务 `InterviewAgentService`（5 处 LLM 调用）
路径：`com.example.recruit.service.InterviewAgentService`

**第 1 步**：注入组件（新增构造器参数）
```java
private final InterviewStatusBar statusBar;

public InterviewAgentService(InterviewMapper interviewMapper,
                              InterviewSessionMapper sessionMapper,
                              InterviewReportMapper reportMapper,
                              DeepSeekModelService deepSeek,
                              InterviewStatusBar statusBar) {   // ← 新增
    this.interviewMapper = interviewMapper;
    this.sessionMapper = sessionMapper;
    this.reportMapper = reportMapper;
    this.deepSeek = deepSeek;
    this.statusBar = statusBar;                                  // ← 新增
}
```

**第 2 步**：在 5 处"调用大模型"的地方，用 `statusBar.appendTo(原user内容, session)` 把状态栏拼到 user 末尾。状态栏放末尾、不改 System Prompt（保护 KV Cache）。

**① 启动面试 `startInitialInterview`**（生成开场白）
```java
String sys = "你是资深 AI 面试官。请用专业、友好的语气开场，并提出第一道面试题。";
String reply;
try {
    reply = deepSeek.chat(sys, statusBar.appendTo("岗位相关面试，请开始。", session));
} catch (Exception e) {
    log.warn("generate opening failed: {}", e.getMessage());
    reply = "[开场白生成失败] 请开始你的自我介绍。";
}
```

**② 评估回答 `processAnswer`**（非流式，最关键处）——注意先回填含最新回答的 messages 再算状态栏：
```java
// 追加候选人回答
ObjectNode messages = appendMessage(session.getMessages(), "candidate", answer == null ? "" : answer);
// 状态栏需反映「含最新回答」的对话，先回填再据此计算
session.setMessages(messages);

String sys = "你是 AI 面试官。请评估候选人回答，给出评分(0-100)、追问或下一题。以JSON输出: {\"score\":80,\"feedback\":\"...\",\"next\":\"追问/下一题内容\"}";
if (difficulty != null && !difficulty.isBlank()) {
    sys += " 难度等级: " + difficulty;
}
String reply;
try {
    reply = deepSeek.chatJson(sys, statusBar.appendTo("回答: " + (answer == null ? "" : answer), session));
} catch (Exception e) {
    log.warn("evaluate answer failed: {}", e.getMessage());
    reply = "";
}
```

**③ 流式评估 `streamProcessAnswer`**
```java
String sys = "你是 AI 面试官。请评估候选人回答并给出追问，流式输出。";
String user = statusBar.appendTo("回答: " + answer, session);
return deepSeek.chatStream(sys, user)
        .map(delta -> ServerSentEvent.<String>builder().event("text").data(delta).build())
        .onErrorResume(e -> { ... });
```

**④ 面试官辅助 `getAssistSuggestion`**（取该面试最近会话算状态栏）
```java
String sys = "你是面试官辅助助手。基于面试上下文给出提示建议，以 JSON 输出: {\"suggestion\":\"...\",\"focus_points\":[\"...\"]}";
InterviewSession assistSession = findSessionByInterviewId(interviewId);
String assistUser = statusBar.appendTo("面试 ID: " + interviewId, assistSession);
String reply;
try {
    reply = deepSeek.chatJson(sys, assistUser);
} catch (Exception e) { log.warn("getAssistSuggestion failed: {}", e.getMessage()); reply = ""; }
```

**⑤ 面试报告 `getReport`**（取会话算"已提问统计"辅助总结）
```java
String sys = """
        你是面试评估专家。基于面试对话生成报告，以 JSON 输出:
        {"overall_score":0,"tech_score":0,...,"hiring_suggestion":"...","summary":"..."}""";
InterviewSession reportSession = findSessionByInterviewId(interviewId);
String reportUser = statusBar.appendTo("面试 ID: " + interviewId, reportSession);
String reply;
try {
    reply = deepSeek.chatJson(sys, reportUser);
} catch (Exception e) { log.warn("getReport chat failed: {}", e.getMessage()); reply = ""; }
```

### 4.4 单元测试 `InterviewStatusBarTest`
路径：`src/test/java/com/example/recruit/service/InterviewStatusBarTest.java`

纯 JUnit 5，无 Spring/DB。用反射注入 `@Value` 配置，构造含多轮对话的 `InterviewSession` 验证读数。
```java
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
        f.setAccessible(true); f.set(o, val);
    }
    private ObjectNode msg(String role, String content) { /* 构造 {role,content,timestamp} */ }

    // 构造一场含多轮对话的会话：行为(自我介绍)/技术(Spring)/技术(Redis)/行为(挑战)/行为(冲突)
    private InterviewSession buildSession(int round, LocalDateTime createdAt) { /* 填 messages 数组 */ }

    @Test
    void buildsExpectedStatusBar() {
        // round=4 → 当前面试阶段: 行为综合；技术题×2、行为题×3；末尾两条行为题 → 已追问同点 2/3；剩余 30min
    }
    @Test
    void strategySwitchesDimensionAtLimit() {
        // 连续 3 条技术题 → 已追问同点 3/3 + 策略含「立即切换维度」
    }
    @Test
    void emptySessionReturnsBlank() {
        // 无 interviewer 消息 → 技术题×0；剩余 30min；不含 null 字面量
    }
    @Test
    void appendToConcatenatesAtTail() {
        // 原 user 内容在最前，<agent_status> 拼接在其后
    }
}
```
> 完整测试源码见仓库 `InterviewStatusBarTest.java`。

---

## 5. 配置项（业务参数，均有默认值，可不配置）

放进 `application.properties`，全部是业务阈值，与基础设施无关：
```properties
# 面试总时长（分钟）
interview.total-minutes=30
# 同一考察点允许连续追问的次数上限（到顶即应换维度）
interview.follow-up-limit=3
# 收尾阈值（分钟）：剩余低于此值时策略优先收尾
interview.wrap-up-threshold=5
```

---

## 6. 一次注入长什么样（示例输出）

当一场面试已进行 2 轮（1 个行为题"自我介绍" + 1 个技术题"Redis 缓存设计"），刚开场约 5 分钟时，状态栏算出：

```
<agent_status>
当前面试阶段: 技术深挖
已提问: 技术题×1、行为题×1、其他×0
候选人剩余时长: 25min
本场已追问同点: 1/3
策略: 同一考察点追问达 3 次未深入即换维度；剩余充足可适度深挖核心难点
</agent_status>
```

最终送进大模型的内容 = `回答: <候选人回答>\n\n<agent_status>...</agent_status>`，System Prompt 不变。

---

## 7. 明确不在本文档范围内（基础设施，按各项目自填）

以下不纳入本规格，请在本地配置中自行填写：
- 大模型（Chat）API Key / base-url / 模型名
- Embedding、Rerank 配置
- PostgreSQL / Redis / RabbitMQ 连接串、库名

> 隔离建议：本地基础设施配置放在一个 `.gitignore` 的本地配置文件中，确保 `git pull` 不覆盖。

---

## 8. 端到端验证清单

- [ ] **V1** 单元测试全绿：阶段 / 技术题×N·行为题×N / 剩余时长 / 追问深度 / 策略 均符合预期。
- [ ] **V2** 后端可正常启动，`InterviewStatusBar` Bean 注入 `InterviewAgentService` 不报装配错误。
- [ ] **V3** `POST /api/interview-agent/interviews/{id}/start` 返回 200，开场白生成正常（启动注入生效）。
- [ ] **V4** `POST /api/interview-agent/sessions/{id}/answer` 返回 200，含 score/feedback/next（评估注入生效）。
- [ ] **V5** 后端日志出现 `[StatusBar] injected into model prompt:` 且内容含 `<agent_status>` 标签与各维度读数——证明状态栏确实注入到了送给模型的 user 内容。
- [ ] **V6** 模型行为符合策略预期：剩余充足时深挖核心难点、连续追问到顶时换维度、剩余不足时收尾。

---

## 9. 附录：已知问题（与状态栏无关，**未**在本功能中处理）

面试会话持久化存在一个**既有的 MyBatis-Plus × PostgreSQL jsonb 兼容问题**，不是本功能引入、也不影响状态栏（状态栏在持久化之前就执行了）：

- **现象**：`interview_session.messages` 列为 `jsonb`，但 MyBatis-Plus 的 `JacksonTypeHandler` 写库时把 `JsonNode` 序列化为 `varchar` 直传 PG，PG 不接受 `varchar→jsonb` 隐式转换，导致 `sessionMapper.insert(...)` 与 `updateById(...)` 抛错；而 `appendMessage(...)` 因 SQL 含 `#{messages}::jsonb` 显式 cast 不受影响。
- **影响**：面试会话无法用 `insert`/`updateById` 存库（读 jsonb 与 `appendMessage` 正常）；状态栏基于内存/已读出的 session 计算，不受影响。
- **可选修复方向（供参考，非本文档要求）**：
  1. JDBC URL 加 `stringtype=unspecified`（全局，改动最小）；
  2. 自定义 `JsonbTypeHandler`，写库时用 `PGobject(type=jsonb)` 绑定，把 `@TableField(typeHandler=...)` 换掉（针对性，放代码层不需动本地配置）。
