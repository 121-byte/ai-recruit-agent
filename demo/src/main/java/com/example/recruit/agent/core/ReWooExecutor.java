package com.example.recruit.agent.core;

import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ReWOO 并行执行器 (复刻自文档 §4.8 ReWooExecutor)。
 *
 * <p>ReWOO (Reasoning WithOut Observation) 三阶段批量执行器：
 * <ol>
 *   <li>Phase 1: 规划 — LLM 将用户输入分解为独立子任务列表</li>
 *   <li>Phase 2: 并行执行 — CompletableFuture 在 4 线程池并行调用工具</li>
 *   <li>Phase 3: 汇总 — LLM 合并多个工具执行结果为连贯报告</li>
 * </ol>
 *
 * <p>效果：传统 ReAct 处理 N 个独立任务需 N 次迭代，ReWOO 仅 2 次 LLM 调用
 * (Plan + Aggregate)，中间工具调用 4 线程并行。
 */
@Service
public class ReWooExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReWooExecutor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 4 线程并行执行 (文档 §4.8)。 */
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final DeepSeekModelService deepSeekModelService;
    private final JobAnalysisTool jobAnalysisTool;
    private final CandidateMatchingTool candidateMatchingTool;
    private final InterviewQuestionTool interviewQuestionTool;
    private final ResumeAnalysisTool resumeAnalysisTool;

    public ReWooExecutor(DeepSeekModelService deepSeekModelService,
                          JobAnalysisTool jobAnalysisTool,
                          CandidateMatchingTool candidateMatchingTool,
                          InterviewQuestionTool interviewQuestionTool,
                          ResumeAnalysisTool resumeAnalysisTool) {
        this.deepSeekModelService = deepSeekModelService;
        this.jobAnalysisTool = jobAnalysisTool;
        this.candidateMatchingTool = candidateMatchingTool;
        this.interviewQuestionTool = interviewQuestionTool;
        this.resumeAnalysisTool = resumeAnalysisTool;
    }

    /** 子任务 (文档 §4.8 BatchTask)。 */
    public record BatchTask(String tool, JsonNode args, String description) {}

    /** 子任务结果 (文档 §4.8 BatchResult)。 */
    public record BatchResult(String tool, String description, String result) {}

    /**
     * 执行批量任务，返回汇总报告。
     */
    public String execute(String userMessage) {
        // ── Phase 1: 规划 ──
        List<BatchTask> tasks = plan(userMessage);
        if (tasks.isEmpty()) {
            return "未能从输入中规划出可执行的子任务。";
        }

        // ── Phase 2: 并行执行 ──
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        for (BatchTask task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> executeTask(task), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<BatchResult> results = new ArrayList<>();
        for (CompletableFuture<BatchResult> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                results.add(new BatchResult("unknown", "", "执行失败: " + e.getMessage()));
            }
        }

        // ── Phase 3: 汇总 ──
        return aggregate(userMessage, results);
    }

    /** Phase 1: LLM 规划，输出子任务列表。 */
    private List<BatchTask> plan(String userMessage) {
        String sys = """
                你是批量任务规划器。请将用户输入分解为独立的子任务列表。
                可用工具: analyzeJob(jobId), matchCandidates(jobId), generateQuestions(interviewId), analyzeResume(resumeId)
                以 JSON 输出: {"tasks":[{"tool":"analyzeJob","args":{"jobId":1},"description":"分析岗位1"}]}""";
        try {
            String reply = deepSeekModelService.chatJson(sys, "用户输入: " + userMessage);
            JsonNode node = JsonGuard.parseJsonSafe(reply);
            JsonNode tasks = node == null ? null : node.path("tasks");
            List<BatchTask> result = new ArrayList<>();
            if (tasks != null && tasks.isArray()) {
                for (JsonNode t : tasks) {
                    result.add(new BatchTask(
                            t.path("tool").asText(),
                            t.path("args"),
                            t.path("description").asText("")));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("ReWOO plan failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Phase 2: 路由到对应工具执行 (文档 §4.8 executeTask)。 */
    private BatchResult executeTask(BatchTask task) {
        String tool = task.tool();
        JsonNode args = task.args();
        String result;
        try {
            result = switch (tool) {
                case "analyzeJob" -> toJson(jobAnalysisTool.analyzeJob(argLong(args, "jobId")));
                case "matchCandidates" -> toJson(candidateMatchingTool.matchCandidates(argLong(args, "jobId")));
                case "generateQuestions" -> toJson(interviewQuestionTool.generateQuestions(argLong(args, "interviewId")));
                case "analyzeResume" -> toJson(resumeAnalysisTool.analyzeResume(argLong(args, "resumeId")));
                default -> "未知工具: " + tool;
            };
        } catch (Exception e) {
            result = "执行失败: " + e.getMessage();
        }
        return new BatchResult(tool, task.description(), result);
    }

    /** Phase 3: LLM 汇总为连贯报告。 */
    private String aggregate(String userMessage, List<BatchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (BatchResult r : results) {
            sb.append("## ").append(r.description().isBlank() ? r.tool() : r.description()).append("\n");
            sb.append(r.result()).append("\n\n");
        }
        try {
            String sys = "你是结果汇总器。请将以下多个工具的执行结果合并为一份连贯的报告。保留关键数据，不要编造。";
            return deepSeekModelService.chat(sys, "用户需求: " + userMessage + "\n\n工具结果:\n" + sb);
        } catch (Exception e) {
            return sb.toString();
        }
    }

    private Long argLong(JsonNode args, String field) {
        if (args == null) return null;
        JsonNode v = args.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        return v.asLong();
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    /** 暴露规划结果供 SSE plan 事件使用。 */
    public List<BatchTask> planOnly(String userMessage) {
        return plan(userMessage);
    }
}
