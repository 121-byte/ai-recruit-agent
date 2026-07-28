package com.example.recruit.controller;

import com.example.recruit.dal.entity.AgentTrace;
import com.example.recruit.dal.entity.InterviewReport;
import com.example.recruit.dal.mapper.InterviewReportMapper;
import com.example.recruit.service.AgentTraceService;
import com.example.recruit.service.CandidateMatchService;
import com.example.recruit.service.InterviewService;
import com.example.recruit.service.OutreachService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
    private final OutreachService outreachService;
    private final InterviewService interviewService;
    private final CandidateMatchService candidateMatchService;
    private final InterviewReportMapper interviewReportMapper;

    public DashboardController(AgentTraceService agentTraceService,
                               OutreachService outreachService,
                               InterviewService interviewService,
                               CandidateMatchService candidateMatchService,
                               InterviewReportMapper interviewReportMapper) {
        this.agentTraceService = agentTraceService;
        this.outreachService = outreachService;
        this.interviewService = interviewService;
        this.candidateMatchService = candidateMatchService;
        this.interviewReportMapper = interviewReportMapper;
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

    private long countReports() {
        try {
            return interviewReportMapper.selectList(null).size();
        } catch (Exception e) {
            return 0L;
        }
    }
}
