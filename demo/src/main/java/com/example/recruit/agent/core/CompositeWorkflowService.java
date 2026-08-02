package com.example.recruit.agent.core;

import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.agent.tool.ResumeSearchTool;
import com.example.recruit.infra.llm.ChatResult;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured multi-agent workflow for composite recruitment requests.
 *
 * <p>The LLM plans agent-level steps, while Java executes the plan with a shared working memory.
 */
@Service
public class CompositeWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(CompositeWorkflowService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern RESUME_NAME_PATTERN =
            Pattern.compile("(?:查看一下|查看|介绍一下|介绍|分析一下|分析)?([\\p{IsHan}]{2,5})(?:的)?(?:简历|详情|情况)");

    private final DeepSeekModelService deepSeekModelService;
    private final ResumeSearchTool resumeSearchTool;
    private final ResumeAnalysisTool resumeAnalysisTool;
    private final JobAnalysisTool jobAnalysisTool;
    private final CandidateMatchingTool candidateMatchingTool;

    public CompositeWorkflowService(DeepSeekModelService deepSeekModelService,
                                    ResumeSearchTool resumeSearchTool,
                                    ResumeAnalysisTool resumeAnalysisTool,
                                    JobAnalysisTool jobAnalysisTool,
                                    CandidateMatchingTool candidateMatchingTool) {
        this.deepSeekModelService = deepSeekModelService;
        this.resumeSearchTool = resumeSearchTool;
        this.resumeAnalysisTool = resumeAnalysisTool;
        this.jobAnalysisTool = jobAnalysisTool;
        this.candidateMatchingTool = candidateMatchingTool;
    }

    public record WorkflowStep(String id,
                               String agent,
                               String task,
                               JsonNode args,
                               List<String> dependsOn,
                               String description) {
    }

    public record WorkflowStepResult(String id,
                                     String agent,
                                     String task,
                                     String status,
                                     Map<String, Object> result,
                                     String error) {
    }

    public record WorkflowExecution(boolean supported,
                                    List<WorkflowStep> steps,
                                    List<WorkflowStepResult> results,
                                    String answer,
                                    int inputTokens,
                                    int outputTokens) {
    }

    public WorkflowExecution executeWithUsage(String userMessage) {
        ChatResult planResult = planWithUsage(userMessage);
        List<WorkflowStep> steps = parseSteps(planResult.content());
        if (steps.isEmpty()) {
            steps = fallbackPlan(userMessage);
        }
        if (steps.isEmpty()) {
            return new WorkflowExecution(false, List.of(), List.of(), "", planResult.inputTokens(), planResult.outputTokens());
        }

        List<WorkflowStepResult> results = executeSteps(steps);
        ChatResult summary = summarizeWithUsage(userMessage, steps, results);
        return new WorkflowExecution(true, steps, results, summary.content(),
                planResult.inputTokens() + summary.inputTokens(),
                planResult.outputTokens() + summary.outputTokens());
    }

    private ChatResult planWithUsage(String userMessage) {
        String sys = """
                你是招聘系统的多 Agent Supervisor，只负责生成结构化执行计划，不直接回答问题。
                可用专家 Agent 和任务:
                - ResumeAgent.find_resume(name): 按候选人姓名搜索简历，返回 resume_id。
                - ResumeAgent.analyze_resume(resumeId|resumeRef|name): 查看简历详情。
                - ResumeAgent.find_and_analyze_resume(name): 搜索候选人并查看简历详情。
                - JobAgent.list_jobs(): 查看岗位列表。
                - JobAgent.analyze_job(jobId|jobRef): 查看岗位详情。
                - JobAgent.analyze_job_by_ordinal(ordinal): 按岗位列表顺序选择第 N 个岗位并查看详情。
                - MatchAgent.match_candidates(jobId|jobRef): 为岗位匹配候选人。
                - MatchAgent.check_resume_job_match(resumeId|resumeRef, jobId|jobRef): 判断指定简历是否在该岗位匹配结果中。

                规划规则:
                1. 有前后依赖时必须写 dependsOn。
                2. 不要猜 resumeId/jobId；姓名先交给 ResumeAgent，"第二个岗位" 先交给 JobAgent。
                3. 后续步骤可以用 $stepId.field 引用前一步结果，例如 "$s1.resume_id"、"$s2.job_id"。
                4. 每个步骤只交给一个专家 Agent。
                5. 只输出 JSON，不要输出解释文字。

                JSON 格式:
                {"steps":[{"id":"s1","agent":"ResumeAgent","task":"find_and_analyze_resume","args":{"name":"李璐阳"},"dependsOn":[],"description":"查看李璐阳简历详情"}]}
                """;
        try {
            return deepSeekModelService.chatJsonWithUsage(sys, "用户需求: " + userMessage);
        } catch (Exception e) {
            log.warn("Composite workflow planning failed: {}", e.getMessage());
            return new ChatResult("", 0, 0);
        }
    }

    private List<WorkflowStep> parseSteps(String reply) {
        JsonNode root = JsonGuard.parseJsonSafe(reply);
        JsonNode steps = root == null ? null : root.path("steps");
        List<WorkflowStep> result = new ArrayList<>();
        if (steps == null || !steps.isArray()) {
            return result;
        }
        int index = 1;
        for (JsonNode step : steps) {
            String id = text(step, "id");
            String agent = text(step, "agent");
            String task = text(step, "task");
            if (id == null || id.isBlank()) {
                id = "s" + index;
            }
            if (agent == null || task == null || agent.isBlank() || task.isBlank()) {
                continue;
            }
            List<String> dependsOn = new ArrayList<>();
            JsonNode deps = step.path("dependsOn");
            if (deps.isArray()) {
                for (JsonNode dep : deps) {
                    if (!dep.asText("").isBlank()) {
                        dependsOn.add(dep.asText());
                    }
                }
            }
            JsonNode args = step.path("args");
            if (args.isMissingNode() || args.isNull()) {
                args = JsonNodeFactory.instance.objectNode();
            }
            result.add(new WorkflowStep(id, agent, task, args, dependsOn, text(step, "description")));
            index++;
        }
        return result;
    }

    private List<WorkflowStep> fallbackPlan(String userMessage) {
        String name = extractCandidateName(userMessage);
        Integer ordinal = extractOrdinal(userMessage);
        boolean wantsResume = containsAny(userMessage, "简历", "候选人", "详情", "情况");
        boolean wantsJob = containsAny(userMessage, "岗位", "职位", "JD");
        boolean wantsMatch = containsAny(userMessage, "匹配", "合适", "适合");
        if (name == null || !wantsResume || !wantsJob || !wantsMatch) {
            return List.of();
        }
        int jobOrdinal = ordinal == null ? 1 : ordinal;
        List<WorkflowStep> steps = new ArrayList<>();
        steps.add(new WorkflowStep("s1", "ResumeAgent", "find_and_analyze_resume",
                objectNode("name", name), List.of(), "查看" + name + "的简历详情"));
        steps.add(new WorkflowStep("s2", "JobAgent", "analyze_job_by_ordinal",
                objectNode("ordinal", jobOrdinal), List.of(), "查看第" + jobOrdinal + "个岗位详情"));
        steps.add(new WorkflowStep("s3", "MatchAgent", "check_resume_job_match",
                objectNode("resumeRef", "$s1.resume_id", "jobRef", "$s2.job_id"),
                List.of("s1", "s2"), "判断候选人与岗位是否匹配"));
        return steps;
    }

    private List<WorkflowStepResult> executeSteps(List<WorkflowStep> steps) {
        List<WorkflowStepResult> results = new ArrayList<>();
        Map<String, Map<String, Object>> memory = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();

        while (completed.size() < steps.size()) {
            boolean progressed = false;
            for (WorkflowStep step : steps) {
                if (completed.contains(step.id())) {
                    continue;
                }
                if (!completed.containsAll(step.dependsOn())) {
                    continue;
                }
                WorkflowStepResult result = executeStep(step, memory);
                results.add(result);
                memory.put(step.id(), result.result() == null ? Map.of() : result.result());
                completed.add(step.id());
                progressed = true;
            }
            if (!progressed) {
                for (WorkflowStep step : steps) {
                    if (!completed.contains(step.id())) {
                        results.add(errorResult(step, "无法执行，依赖步骤未完成或存在循环依赖: " + step.dependsOn()));
                        completed.add(step.id());
                    }
                }
            }
        }
        return results;
    }

    private WorkflowStepResult executeStep(WorkflowStep step, Map<String, Map<String, Object>> memory) {
        try {
            Map<String, Object> result = switch (step.agent() + "." + step.task()) {
                case "ResumeAgent.find_resume" -> findResume(step.args());
                case "ResumeAgent.analyze_resume" -> analyzeResume(step.args(), memory);
                case "ResumeAgent.find_and_analyze_resume" -> findAndAnalyzeResume(step.args());
                case "JobAgent.list_jobs" -> listJobs();
                case "JobAgent.analyze_job" -> analyzeJob(step.args(), memory);
                case "JobAgent.analyze_job_by_ordinal" -> analyzeJobByOrdinal(step.args());
                case "MatchAgent.match_candidates" -> matchCandidates(step.args(), memory);
                case "MatchAgent.check_resume_job_match" -> checkResumeJobMatch(step.args(), memory);
                default -> {
                    Map<String, Object> unsupported = new LinkedHashMap<>();
                    unsupported.put("unsupported", true);
                    unsupported.put("agent", step.agent());
                    unsupported.put("task", step.task());
                    unsupported.put("message", "当前复合工作流暂不支持该专家任务。");
                    yield unsupported;
                }
            };
            return new WorkflowStepResult(step.id(), step.agent(), step.task(), "success", result, null);
        } catch (Exception e) {
            log.warn("Composite workflow step failed: id={}, agent={}, task={}, error={}",
                    step.id(), step.agent(), step.task(), e.getMessage());
            return errorResult(step, e.getMessage());
        }
    }

    private Map<String, Object> findResume(JsonNode args) {
        String name = argString(args, "name");
        List<Map<String, Object>> searchResults = resumeSearchTool.searchResumes(name, null, null, null, null, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query_name", name);
        out.put("search_results", searchResults);
        Map<String, Object> found = firstFoundResume(searchResults);
        if (found == null) {
            out.put("found", false);
            out.put("message", "未找到候选人简历: " + name);
            return out;
        }
        out.put("found", true);
        out.put("resume_id", toLong(found.get("resume_id")));
        out.put("name", found.get("name"));
        out.put("candidate", found);
        return out;
    }

    private Map<String, Object> analyzeResume(JsonNode args, Map<String, Map<String, Object>> memory) {
        Long resumeId = argLong(args, "resumeId", memory);
        if (resumeId == null) {
            String name = argString(args, "name");
            if (name != null) {
                Map<String, Object> found = findResume(args);
                resumeId = toLong(found.get("resume_id"));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resume_id", resumeId);
        if (resumeId == null) {
            out.put("found", false);
            out.put("message", "缺少 resumeId，无法查看简历详情。");
            return out;
        }
        out.put("found", true);
        out.put("detail", resumeAnalysisTool.analyzeResume(resumeId));
        return out;
    }

    private Map<String, Object> findAndAnalyzeResume(JsonNode args) {
        Map<String, Object> out = findResume(args);
        Long resumeId = toLong(out.get("resume_id"));
        if (resumeId != null) {
            out.put("detail", resumeAnalysisTool.analyzeResume(resumeId));
        }
        return out;
    }

    private Map<String, Object> listJobs() {
        List<Map<String, Object>> jobs = jobAnalysisTool.listJobs();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobs", jobs);
        out.put("job_count", jobs.size());
        return out;
    }

    private Map<String, Object> analyzeJob(JsonNode args, Map<String, Map<String, Object>> memory) {
        Long jobId = argLong(args, "jobId", memory);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", jobId);
        if (jobId == null) {
            out.put("found", false);
            out.put("message", "缺少 jobId，无法查看岗位详情。");
            return out;
        }
        out.put("found", true);
        out.put("detail", jobAnalysisTool.analyzeJob(jobId));
        return out;
    }

    private Map<String, Object> analyzeJobByOrdinal(JsonNode args) {
        int ordinal = Math.max(1, argInteger(args, "ordinal", 1));
        List<Map<String, Object>> jobs = jobAnalysisTool.listJobs();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ordinal", ordinal);
        out.put("jobs", jobs);
        out.put("job_count", jobs.size());
        if (jobs.size() < ordinal) {
            out.put("found", false);
            out.put("message", "岗位列表不足 " + ordinal + " 个。");
            return out;
        }
        Map<String, Object> selected = jobs.get(ordinal - 1);
        Long jobId = toLong(firstNonNull(selected.get("job_id"), selected.get("jobId")));
        out.put("found", true);
        out.put("job_id", jobId);
        out.put("selected_job", selected);
        out.put("detail", jobId == null ? Map.of() : jobAnalysisTool.analyzeJob(jobId));
        return out;
    }

    private Map<String, Object> matchCandidates(JsonNode args, Map<String, Map<String, Object>> memory) {
        Long jobId = argLong(args, "jobId", memory);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", jobId);
        if (jobId == null) {
            out.put("message", "缺少 jobId，无法执行岗位匹配。");
            return out;
        }
        out.put("match_result", candidateMatchingTool.matchCandidates(jobId));
        return out;
    }

    private Map<String, Object> checkResumeJobMatch(JsonNode args, Map<String, Map<String, Object>> memory) {
        Long resumeId = argLong(args, "resumeId", memory);
        Long jobId = argLong(args, "jobId", memory);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resume_id", resumeId);
        out.put("job_id", jobId);
        if (resumeId == null || jobId == null) {
            out.put("matched", false);
            out.put("message", "缺少 resumeId 或 jobId，无法判断匹配。");
            return out;
        }
        Map<String, Object> matchResult = candidateMatchingTool.matchCandidates(jobId);
        out.put("match_result", matchResult);
        Map<String, Object> candidate = findCandidate(matchResult.get("candidates"), resumeId);
        out.put("matched", candidate != null);
        if (candidate == null) {
            out.put("message", "该候选人未进入当前岗位匹配结果。");
        } else {
            out.put("candidate_match", candidate);
            out.put("message", "该候选人出现在当前岗位匹配结果中。");
        }
        return out;
    }

    private ChatResult summarizeWithUsage(String userMessage, List<WorkflowStep> steps, List<WorkflowStepResult> results) {
        String payload;
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("steps", steps);
            data.put("results", results);
            payload = MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            payload = String.valueOf(results);
        }
        String sys = "你是招聘多 Agent 工作流的最终汇总专家。请用中文回答用户，保留关键 ID、岗位、候选人、匹配分数和未找到原因，不要编造。";
        try {
            return deepSeekModelService.chatWithUsage(sys, "用户需求: " + userMessage + "\n\n执行结果:\n" + payload);
        } catch (Exception e) {
            return new ChatResult(buildFallbackAnswer(results), 0, 0);
        }
    }

    private String buildFallbackAnswer(List<WorkflowStepResult> results) {
        StringBuilder out = new StringBuilder("复合任务执行完成。\n");
        for (WorkflowStepResult result : results) {
            out.append("- ")
                    .append(result.agent()).append(".").append(result.task())
                    .append(": ").append(result.status());
            if (result.error() != null) {
                out.append(", ").append(result.error());
            }
            out.append('\n');
        }
        return out.toString();
    }

    private WorkflowStepResult errorResult(WorkflowStep step, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return new WorkflowStepResult(step.id(), step.agent(), step.task(), "failed", result, message);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCandidate(Object candidates, Long resumeId) {
        if (!(candidates instanceof List<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Long itemResumeId = toLong(firstNonNull(map.get("resume_id"), map.get("resumeId")));
                if (resumeId.equals(itemResumeId)) {
                    return (Map<String, Object>) map;
                }
            }
        }
        return null;
    }

    private Long argLong(JsonNode args, String field, Map<String, Map<String, Object>> memory) {
        Object value = argValue(args, field, memory);
        if (value == null && field.endsWith("Id")) {
            value = argValue(args, field.substring(0, field.length() - 2) + "Ref", memory);
        }
        return toLong(value);
    }

    private Object argValue(JsonNode args, String field, Map<String, Map<String, Object>> memory) {
        if (args == null) {
            return null;
        }
        JsonNode node = args.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.startsWith("$")) {
                return resolveRef(text, memory);
            }
            return text;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.toString();
    }

    private Object resolveRef(String ref, Map<String, Map<String, Object>> memory) {
        String path = ref.substring(1);
        String[] parts = path.split("\\.");
        if (parts.length == 0) {
            return null;
        }
        Object current = memory.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            if (current instanceof Map<?, ?> map) {
                current = firstNonNull(map.get(parts[i]), map.get(toSnake(parts[i])), map.get(toCamel(parts[i])));
            } else if (current instanceof List<?> list) {
                try {
                    current = list.get(Integer.parseInt(parts[i]));
                } catch (Exception e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private int argInteger(JsonNode args, String field, int defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue);
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

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String extractCandidateName(String userMessage) {
        if (userMessage == null) {
            return null;
        }
        Matcher matcher = RESUME_NAME_PATTERN.matcher(userMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Integer extractOrdinal(String userMessage) {
        if (userMessage == null) {
            return null;
        }
        if (userMessage.contains("第二")) {
            return 2;
        }
        if (userMessage.contains("第三")) {
            return 3;
        }
        if (userMessage.contains("第一")) {
            return 1;
        }
        Matcher matcher = Pattern.compile("第(\\d+)个岗位").matcher(userMessage);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode objectNode(Object... kv) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            Object value = kv[i + 1];
            if (value instanceof Number number) {
                node.put(key, number.longValue());
            } else if (value instanceof Boolean bool) {
                node.put(key, bool);
            } else if (value != null) {
                node.put(key, String.valueOf(value));
            }
        }
        return node;
    }

    private String toSnake(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String toCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                out.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
