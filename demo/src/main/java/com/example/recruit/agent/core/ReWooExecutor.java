package com.example.recruit.agent.core;

import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.infra.llm.ChatResult;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ReWOO 批量执行器：规划、并行工具执行、结果汇总。 */
@Service
public class ReWooExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReWooExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    public record BatchTask(String tool, JsonNode args, String description) {}
    public record BatchResult(String tool, String description, String result) {}
    public record BatchExecution(List<BatchTask> tasks, String result, int inputTokens, int outputTokens) {}

    public String execute(String userMessage) {
        return executeWithUsage(userMessage).result();
    }

    /** 执行一次批量任务；规划和汇总的真实 usage 一并返回。 */
    public BatchExecution executeWithUsage(String userMessage) {
        ChatResult planResult = planWithUsage(userMessage);
        List<BatchTask> tasks = parseTasks(planResult.content());
        if (tasks.isEmpty()) {
            return new BatchExecution(tasks, "未能从输入中规划出可执行的子任务。",
                    planResult.inputTokens(), planResult.outputTokens());
        }

        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        for (BatchTask task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> executeTask(task), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<BatchResult> results = new ArrayList<>();
        for (CompletableFuture<BatchResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                results.add(new BatchResult("unknown", "", "执行失败: " + e.getMessage()));
            }
        }

        ChatResult aggregateResult = aggregateWithUsage(userMessage, results);
        return new BatchExecution(tasks, aggregateResult.content(),
                planResult.inputTokens() + aggregateResult.inputTokens(),
                planResult.outputTokens() + aggregateResult.outputTokens());
    }

    private ChatResult planWithUsage(String userMessage) {
        String sys = """
                你是批量任务规划器。请将用户输入分解为独立的子任务列表。
                可用工具: analyzeJob(jobId), matchCandidates(jobId), generateQuestions(interviewId), analyzeResume(resumeId)
                以 JSON 输出: {"tasks":[{"tool":"analyzeJob","args":{"jobId":1},"description":"分析岗位1"}]}""";
        try {
            return deepSeekModelService.chatJsonWithUsage(sys, "用户输入: " + userMessage);
        } catch (Exception e) {
            log.warn("ReWOO plan failed: {}", e.getMessage());
            return new ChatResult("", 0, 0);
        }
    }

    private List<BatchTask> parseTasks(String reply) {
        JsonNode node = JsonGuard.parseJsonSafe(reply);
        JsonNode tasks = node == null ? null : node.path("tasks");
        List<BatchTask> result = new ArrayList<>();
        if (tasks != null && tasks.isArray()) {
            for (JsonNode task : tasks) {
                result.add(new BatchTask(task.path("tool").asText(), task.path("args"),
                        task.path("description").asText("")));
            }
        }
        return result;
    }

    private BatchResult executeTask(BatchTask task) {
        String result;
        try {
            result = switch (task.tool()) {
                case "analyzeJob" -> toJson(jobAnalysisTool.analyzeJob(argLong(task.args(), "jobId")));
                case "matchCandidates" -> toJson(candidateMatchingTool.matchCandidates(argLong(task.args(), "jobId")));
                case "generateQuestions" -> toJson(interviewQuestionTool.generateQuestions(argLong(task.args(), "interviewId")));
                case "analyzeResume" -> toJson(resumeAnalysisTool.analyzeResume(argLong(task.args(), "resumeId")));
                default -> "未知工具: " + task.tool();
            };
        } catch (Exception e) {
            result = "执行失败: " + e.getMessage();
        }
        return new BatchResult(task.tool(), task.description(), result);
    }

    private ChatResult aggregateWithUsage(String userMessage, List<BatchResult> results) {
        StringBuilder text = new StringBuilder();
        for (BatchResult result : results) {
            text.append("## ").append(result.description().isBlank() ? result.tool() : result.description()).append("\n");
            text.append(result.result()).append("\n\n");
        }
        try {
            return deepSeekModelService.chatWithUsage("你是结果汇总器。请将工具结果合并为连贯报告，保留关键数据，不要编造。",
                    "用户需求: " + userMessage + "\n\n工具结果:\n" + text);
        } catch (Exception e) {
            return new ChatResult(text.toString(), 0, 0);
        }
    }

    private Long argLong(JsonNode args, String field) {
        if (args == null) return null;
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 仅用于非会话场景的预览。会话流应调用 executeWithUsage，避免重复规划。 */
    public List<BatchTask> planOnly(String userMessage) {
        return parseTasks(planWithUsage(userMessage).content());
    }
}
