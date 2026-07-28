package com.example.recruit.controller;

import com.example.recruit.dal.entity.InterviewReport;
import com.example.recruit.dal.entity.InterviewSession;
import com.example.recruit.service.InterviewAgentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 面试官 API (复刻自对齐清单 §5.4, @RequestMapping("/api/interview-agent"))。
 *
 * <p>6 个权威端点 (路径按 {interviews|sessions}/{id} 区分):
 * <ul>
 *   <li>POST /interviews/{id}/start          启动 AI 初面</li>
 *   <li>POST /sessions/{id}/answer           提交面试回答</li>
 *   <li>POST /sessions/{id}/answer/stream    流式评估回答</li>
 *   <li>POST /sessions/{id}/end              结束面试</li>
 *   <li>POST /interviews/{id}/assist         面试官辅助提示</li>
 *   <li>GET  /interviews/{id}/report         生成/获取面试报告</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/interview-agent")
public class InterviewAgentController {

    private final InterviewAgentService interviewAgentService;

    public InterviewAgentController(InterviewAgentService interviewAgentService) {
        this.interviewAgentService = interviewAgentService;
    }

    /** POST /interviews/{interviewId}/start —— 启动 AI 初面。 */
    @PostMapping("/interviews/{interviewId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable Long interviewId) {
        return ResponseEntity.ok(interviewAgentService.startInitialInterview(interviewId));
    }

    /** POST /sessions/{sessionId}/answer —— 提交面试回答 (非流式评估)。 */
    @PostMapping("/sessions/{sessionId}/answer")
    public ResponseEntity<Map<String, Object>> answer(@PathVariable Long sessionId,
                                                       @RequestBody Map<String, Object> body) {
        String answer = body.get("answer") == null ? null : String.valueOf(body.get("answer"));
        String difficulty = body.get("difficulty") == null ? null : String.valueOf(body.get("difficulty"));
        return ResponseEntity.ok(interviewAgentService.processAnswer(sessionId, answer, difficulty));
    }

    /** POST /sessions/{sessionId}/answer/stream —— 流式评估回答。 */
    @PostMapping(value = "/sessions/{sessionId}/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAnswer(@PathVariable Long sessionId,
                                                       @RequestBody Map<String, Object> body) {
        String answer = body.get("answer") == null ? "" : String.valueOf(body.get("answer"));
        return interviewAgentService.streamProcessAnswer(sessionId, answer);
    }

    /** POST /sessions/{sessionId}/end —— 结束面试。 */
    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<InterviewSession> endInterview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewAgentService.endInterview(sessionId));
    }

    /** POST /interviews/{interviewId}/assist —— 面试官辅助提示。 */
    @PostMapping("/interviews/{interviewId}/assist")
    public ResponseEntity<Map<String, Object>> assist(@PathVariable Long interviewId) {
        return ResponseEntity.ok(interviewAgentService.getAssistSuggestion(interviewId));
    }

    /** GET /interviews/{interviewId}/report —— 生成/获取面试报告。 */
    @GetMapping("/interviews/{interviewId}/report")
    public ResponseEntity<InterviewReport> report(@PathVariable Long interviewId) {
        return ResponseEntity.ok(interviewAgentService.getReport(interviewId));
    }
}
