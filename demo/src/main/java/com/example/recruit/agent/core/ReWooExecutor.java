package com.example.recruit.agent.core;

import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.agent.tool.ResumeSearchTool;
import com.example.recruit.infra.llm.ChatResult;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ReWOO batch executor: plan independent tasks, run tools in parallel, and aggregate results.
 */
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
    private final ResumeSearchTool resumeSearchTool;

    public ReWooExecutor(DeepSeekModelService deepSeekModelService,
                         JobAnalysisTool jobAnalysisTool,
                         CandidateMatchingTool candidateMatchingTool,
                         InterviewQuestionTool interviewQuestionTool,
                         ResumeAnalysisTool resumeAnalysisTool,
                         ResumeSearchTool resumeSearchTool) {
        this.deepSeekModelService = deepSeekModelService;
        this.jobAnalysisTool = jobAnalysisTool;
        this.candidateMatchingTool = candidateMatchingTool;
        this.interviewQuestionTool = interviewQuestionTool;
        this.resumeAnalysisTool = resumeAnalysisTool;
        this.resumeSearchTool = resumeSearchTool;
    }

    public record BatchTask(String tool, JsonNode args, String description) {}
    public record BatchResult(String tool, String description, String result) {}
    public record BatchExecution(List<BatchTask> tasks, String result, int inputTokens, int outputTokens) {}

    public String execute(String userMessage) {
        return executeWithUsage(userMessage).result();
    }

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
                你是招聘系统的批量任务规划器。请把用户输入拆成可以并行执行的独立子任务。
                可用工具:
                - analyzeJob(jobId): 查看岗位详情。仅当用户明确提供岗位 ID 时使用。
                - matchCandidates(jobId): 为岗位匹配候选人。仅当用户明确提供岗位 ID 时使用。
                - generateQuestions(interviewId): 生成面试题。仅当用户明确提供面试 ID 时使用。
                - searchResumes(name, school, education, major, intendedPosition, minExperience): 搜索简历。
                - analyzeResume(resumeId): 查看简历详情。仅当用户明确提供简历 ID 时使用。
                - analyzeResumeByName(name): 按候选人姓名搜索简历，并查看最匹配简历的详情。

                规划规则:
                1. 用户说“查看/介绍/分析 某某 的详情/简历/情况”且给的是姓名时，必须使用 analyzeResumeByName。
                2. 用户一次提到多个候选人姓名时，每个候选人生成一个独立的 analyzeResumeByName 任务。
                3. 不要把姓名猜成 resumeId；只有明确出现“简历ID/ID=数字”时才用 analyzeResume。
                4. 只输出 JSON，不要输出解释文字。

                JSON 格式:
                {"tasks":[{"tool":"analyzeResumeByName","args":{"name":"李璐阳"},"description":"查看李璐阳的简历详情"}]}
                """;
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
                case "searchResumes" -> toJson(searchResumes(task.args()));
                case "analyzeResume" -> toJson(resumeAnalysisTool.analyzeResume(argLong(task.args(), "resumeId")));
                case "analyzeResumeByName" -> toJson(analyzeResumeByName(argString(task.args(), "name")));
                default -> "未知工具: " + task.tool();
            };
        } catch (Exception e) {
            result = "执行失败: " + e.getMessage();
        }
        return new BatchResult(task.tool(), task.description(), result);
    }

    private List<Map<String, Object>> searchResumes(JsonNode args) {
        return resumeSearchTool.searchResumes(
                argString(args, "name"),
                argString(args, "school"),
                argString(args, "education"),
                argString(args, "major"),
                argString(args, "intendedPosition"),
                argInteger(args, "minExperience"));
    }

    private Map<String, Object> analyzeResumeByName(String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query_name", name);

        if (name == null || name.isBlank()) {
            result.put("found", false);
            result.put("message", "缺少候选人姓名，无法查看简历详情。");
            return result;
        }

        List<Map<String, Object>> searchResults = resumeSearchTool.searchResumes(
                name.trim(), null, null, null, null, null);
        result.put("search_results", searchResults);

        Map<String, Object> matched = firstFoundResume(searchResults);
        if (matched == null) {
            result.put("found", false);
            result.put("message", "未找到候选人简历: " + name.trim());
            return result;
        }

        Long resumeId = toLong(matched.get("resume_id"));
        if (resumeId == null) {
            result.put("found", false);
            result.put("matched_candidate", matched);
            result.put("message", "已找到候选人，但搜索结果缺少 resume_id，无法查看详情。");
            return result;
        }

        result.put("found", true);
        result.put("resume_id", resumeId);
        result.put("matched_candidate", matched);
        result.put("detail", resumeAnalysisTool.analyzeResume(resumeId));
        return result;
    }

    private Map<String, Object> firstFoundResume(List<Map<String, Object>> searchResults) {
        if (searchResults == null) {
            return null;
        }
        for (Map<String, Object> item : searchResults) {
            if (Boolean.TRUE.equals(item.get("found"))) {
                return item;
            }
        }
        return null;
    }

    private ChatResult aggregateWithUsage(String userMessage, List<BatchResult> results) {
        StringBuilder text = new StringBuilder();
        for (BatchResult result : results) {
            text.append("## ").append(result.description().isBlank() ? result.tool() : result.description()).append("\n");
            text.append(result.result()).append("\n\n");
        }
        try {
            return deepSeekModelService.chatWithUsage(
                    "你是结果汇总器。请将工具结果合并为连贯报告，保留关键数据，不要编造。若某个候选人未找到，只说明该候选人未找到，不要否定其他已找到候选人。",
                    "用户需求: " + userMessage + "\n\n工具结果:\n" + text);
        } catch (Exception e) {
            return new ChatResult(text.toString(), 0, 0);
        }
    }

    private Long argLong(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private Integer argInteger(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private String argString(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode value = args.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public List<BatchTask> planOnly(String userMessage) {
        return parseTasks(planWithUsage(userMessage).content());
    }
}
