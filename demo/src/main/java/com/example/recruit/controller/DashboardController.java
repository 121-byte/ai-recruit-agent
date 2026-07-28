package com.example.recruit.controller;

import com.example.recruit.dal.entity.AgentTrace;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.InterviewReport;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.InterviewReportMapper;
import com.example.recruit.service.AgentTraceService;
import com.example.recruit.service.CandidateMatchService;
import com.example.recruit.service.InterviewService;
import com.example.recruit.service.JobProfileService;
import com.example.recruit.service.OutreachService;
import com.example.recruit.service.ResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘 API (复刻自对齐清单 §5.3, @RequestMapping("/api/dashboard"))。
 *
 * <p>8 个权威端点:
 * <ul>
 *   <li>GET /traces/session/{sessionId}  会话 trace 步骤</li>
 *   <li>GET /traces/summary              trace 会话聚合</li>
 *   <li>GET /traces/tool-stats           各 Agent trace 统计</li>
 *   <li>GET /funnel                      触达漏斗</li>
 *   <li>GET /outreach-kanban             触达看板</li>
 *   <li>GET /report-overview             面试报告概览</li>
 *   <li>GET /cost-summary/{sessionId}   会话 token 成本</li>
 *   <li>GET /agent-metrics               Agent trace 计数聚合</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AgentTraceService agentTraceService;
    private final ResumeService resumeService;
    private final JobProfileService jobProfileService;
    private final OutreachService outreachService;
    private final InterviewService interviewService;
    private final CandidateMatchService candidateMatchService;
    private final InterviewReportMapper interviewReportMapper;

    public DashboardController(AgentTraceService agentTraceService,
                               ResumeService resumeService,
                               JobProfileService jobProfileService,
                               OutreachService outreachService,
                               InterviewService interviewService,
                               CandidateMatchService candidateMatchService,
                               InterviewReportMapper interviewReportMapper) {
        this.agentTraceService = agentTraceService;
        this.resumeService = resumeService;
        this.jobProfileService = jobProfileService;
        this.outreachService = outreachService;
        this.interviewService = interviewService;
        this.candidateMatchService = candidateMatchService;
        this.interviewReportMapper = interviewReportMapper;
    }

    /** GET /stats —— 仪表盘首页聚合数据。 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        List<Resume> resumes = safeList(resumeService::listAll);
        List<JobProfile> jobs = safeList(jobProfileService::listAll);
        List<Interview> interviews = safeList(interviewService::listAll);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("resumeCount", resumes.size());
        stats.put("jobCount", jobs.stream().filter(this::isOpenJob).count());
        stats.put("interviewCount", interviews.stream().filter(this::isActiveInterview).count());
        stats.put("matchCount", candidateMatchService.count());
        stats.put("reportCount", countReports());
        stats.put("recentActivities", buildRecentActivities(resumes, jobs, interviews));
        stats.put("upcomingInterviews", buildUpcomingInterviews(interviews));
        return ResponseEntity.ok(stats);
    }

    /** GET /traces/session/{sessionId} —— 会话 trace 步骤。 */
    @GetMapping("/traces/session/{sessionId}")
    public ResponseEntity<List<AgentTrace>> tracesBySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentTraceService.getSessionTrace(sessionId));
    }

    /** GET /traces/summary —— trace 会话聚合。 */
    @GetMapping("/traces/summary")
    public ResponseEntity<Map<String, Object>> tracesSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_sessions", agentTraceService.getAllSessionCount());
        summary.put("completed_sessions", agentTraceService.getCompletedSessions());
        summary.put("sessions_with_tool_calls", agentTraceService.getSessionsWithToolCalls());
        return ResponseEntity.ok(summary);
    }

    /** GET /traces/tool-stats —— 各 Agent trace 统计 (简化: 聚合已知 agent 计数)。 */
    @GetMapping("/traces/tool-stats")
    public ResponseEntity<Map<String, Object>> tracesToolStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Map<String, Object>> agents = new ArrayList<>();
        for (String agentName : new String[]{"RecruitmentAgent", "SupervisorAgent", "ReWooExecutor"}) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("agent", agentName);
            a.put("count", agentTraceService.countByAgent(agentName));
            agents.add(a);
        }
        stats.put("agents", agents);
        return ResponseEntity.ok(stats);
    }

    /** GET /funnel —— 触达漏斗 (全岗位聚合, 简化各阶段计数)。 */
    @GetMapping("/funnel")
    public ResponseEntity<Map<String, Long>> funnel() {
        return ResponseEntity.ok(outreachService.funnelByJob(null));
    }

    /** GET /outreach-kanban —— 触达看板 (全岗位聚合)。 */
    @GetMapping("/outreach-kanban")
    public ResponseEntity<Map<String, Long>> outreachKanban() {
        return ResponseEntity.ok(outreachService.kanbanStats(null));
    }

    /** GET /report-overview —— 面试报告概览 (interview 数 + report 数 + match 数)。 */
    @GetMapping("/report-overview")
    public ResponseEntity<Map<String, Object>> reportOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("interviews", countSafeList(interviewService::listAll));
        overview.put("matches", candidateMatchService.count());
        overview.put("reports", countReports());
        return ResponseEntity.ok(overview);
    }

    /** GET /cost-summary/{sessionId} —— 会话 token 成本 (复用 trace 步骤数)。 */
    @GetMapping("/cost-summary/{sessionId}")
    public ResponseEntity<Map<String, Object>> costSummary(@PathVariable String sessionId) {
        Map<String, Object> cost = new LinkedHashMap<>();
        List<AgentTrace> traces = agentTraceService.getSessionTrace(sessionId);
        int totalTokens = 0;
        for (AgentTrace t : traces) {
            if (t.getTokens() != null) {
                totalTokens += t.getTokens();
            }
        }
        cost.put("sessionId", sessionId);
        cost.put("steps", traces.size());
        cost.put("total_tokens", totalTokens);
        return ResponseEntity.ok(cost);
    }

    /** GET /agent-metrics —— Agent trace 计数聚合。 */
    @GetMapping("/agent-metrics")
    public ResponseEntity<Map<String, Object>> agentMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        List<Map<String, Object>> agents = new ArrayList<>();
        for (String agentName : new String[]{"RecruitmentAgent", "SupervisorAgent", "ReWooExecutor"}) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("agent", agentName);
            a.put("trace_count", agentTraceService.countByAgent(agentName));
            a.put("recent", agentTraceService.listByAgent(agentName).size());
            agents.add(a);
        }
        metrics.put("agents", agents);
        metrics.put("total_sessions", agentTraceService.getAllSessionCount());
        return ResponseEntity.ok(metrics);
    }

    // ─────────────────── 工具 ───────────────────

    private long countSafeList(java.util.function.Supplier<?> supplier) {
        try {
            Object result = supplier.get();
            if (result instanceof java.util.Collection<?> c) {
                return c.size();
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private <T> List<T> safeList(java.util.function.Supplier<List<T>> supplier) {
        try {
            List<T> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private long countReports() {
        try {
            return interviewReportMapper.selectList(null).size();
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean isOpenJob(JobProfile job) {
        return job != null && "active".equalsIgnoreCase(job.getStatus());
    }

    private boolean isActiveInterview(Interview interview) {
        if (interview == null) {
            return false;
        }
        String status = interview.getStatus();
        return status == null
                || "pending".equalsIgnoreCase(status)
                || "scheduled".equalsIgnoreCase(status);
    }

    private List<Map<String, Object>> buildRecentActivities(List<Resume> resumes,
                                                            List<JobProfile> jobs,
                                                            List<Interview> interviews) {
        List<ActivityItem> items = new ArrayList<>();
        for (Resume resume : resumes) {
            items.add(new ActivityItem(
                    resume.getCreatedAt(),
                    "accent",
                    "新简历上传 - " + defaultText(resume.getCandidateName(), "候选人") + " 已进入简历库"));
        }
        for (JobProfile job : jobs) {
            items.add(new ActivityItem(
                    job.getCreatedAt(),
                    "warn",
                    "岗位更新 - " + defaultText(job.getTitle(), "未命名岗位") + " 当前状态：" + defaultText(job.getStatus(), "未知")));
        }
        for (Interview interview : interviews) {
            LocalDateTime time = interview.getCreatedAt() != null ? interview.getCreatedAt() : interview.getScheduledAt();
            items.add(new ActivityItem(
                    time,
                    "success",
                    "面试安排 - " + candidateName(interview.getResumeId()) + " · " + jobTitle(interview.getJobId())));
        }

        return items.stream()
                .filter(item -> item.time() != null)
                .sorted(Comparator.comparing(ActivityItem::time).reversed())
                .limit(5)
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("dotClass", item.dotClass());
                    map.put("text", item.text());
                    map.put("time", relativeTime(item.time()));
                    return map;
                })
                .toList();
    }

    private List<Map<String, Object>> buildUpcomingInterviews(List<Interview> interviews) {
        LocalDateTime now = LocalDateTime.now();
        return interviews.stream()
                .filter(this::isActiveInterview)
                .filter(interview -> interview.getScheduledAt() != null && !interview.getScheduledAt().isBefore(now.minusMinutes(5)))
                .sorted(Comparator.comparing(Interview::getScheduledAt))
                .limit(5)
                .map(interview -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("candidateName", candidateName(interview.getResumeId()));
                    item.put("jobTitle", jobTitle(interview.getJobId()));
                    item.put("scheduledAt", formatInterviewTime(interview.getScheduledAt()));
                    item.put("badge", interviewBadge(interview.getScheduledAt()));
                    item.put("badgeClass", isToday(interview.getScheduledAt()) ? "today" : "soon");
                    return item;
                })
                .toList();
    }

    private String candidateName(Long resumeId) {
        Resume resume = resumeService.getById(resumeId);
        return resume == null ? "候选人" : defaultText(resume.getCandidateName(), "候选人");
    }

    private String jobTitle(Long jobId) {
        JobProfile job = jobProfileService.getById(jobId);
        return job == null ? "未关联岗位" : defaultText(job.getTitle(), "未命名岗位");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String relativeTime(LocalDateTime time) {
        Duration duration = Duration.between(time, LocalDateTime.now());
        if (duration.toMinutes() < 1) {
            return "刚刚";
        }
        if (duration.toHours() < 1) {
            return duration.toMinutes() + " 分钟前";
        }
        if (duration.toDays() < 1) {
            return duration.toHours() + " 小时前";
        }
        if (duration.toDays() == 1) {
            return "昨天";
        }
        return duration.toDays() + " 天前";
    }

    private String formatInterviewTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        String prefix = isToday(time) ? "今天" : time.getMonthValue() + "月" + time.getDayOfMonth() + "日";
        return prefix + " " + twoDigits(time.getHour()) + ":" + twoDigits(time.getMinute());
    }

    private String interviewBadge(LocalDateTime time) {
        if (time == null) {
            return "待定";
        }
        LocalDateTime now = LocalDateTime.now();
        if (isToday(time)) {
            long minutes = Duration.between(now, time).toMinutes();
            if (minutes > 0 && minutes <= 60) {
                return minutes + " 分钟后";
            }
            return "今天";
        }
        if (time.toLocalDate().equals(now.toLocalDate().plusDays(1))) {
            return "明天";
        }
        return time.getMonthValue() + "/" + time.getDayOfMonth();
    }

    private boolean isToday(LocalDateTime time) {
        return time != null && time.toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private record ActivityItem(LocalDateTime time, String dotClass, String text) {
    }
}
