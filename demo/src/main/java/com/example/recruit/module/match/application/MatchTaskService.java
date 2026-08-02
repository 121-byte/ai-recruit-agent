package com.example.recruit.module.match.application;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MatchTaskService {

    private static final Logger log = LoggerFactory.getLogger(MatchTaskService.class);

    private final CandidateMatchService candidateMatchService;
    private final ConcurrentMap<Long, MatchTaskState> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, new MatchThreadFactory());

    public MatchTaskService(CandidateMatchService candidateMatchService) {
        this.candidateMatchService = candidateMatchService;
    }

    public synchronized Map<String, Object> start(Long jobId) {
        return start(jobId, CandidateMatchService.MatchWeights.defaultWeights());
    }

    public synchronized Map<String, Object> start(Long jobId, CandidateMatchService.MatchWeights weights) {
        MatchTaskState current = tasks.get(jobId);
        if (current != null && current.isRunning()) {
            return current.toMap(false);
        }

        MatchTaskState task = MatchTaskState.running(jobId, weights);
        tasks.put(jobId, task);
        executor.submit(() -> runTask(task));
        return task.toMap(false);
    }

    public Map<String, Object> status(Long jobId) {
        MatchTaskState task = tasks.get(jobId);
        if (task == null) {
            Map<String, Object> idle = new LinkedHashMap<>();
            idle.put("job_id", jobId);
            idle.put("status", "IDLE");
            idle.put("running", false);
            idle.put("message", "暂无匹配任务");
            return idle;
        }
        return task.toMap(task.isSuccess());
    }

    private void runTask(MatchTaskState task) {
        try {
            Map<String, Object> result = candidateMatchService.matchForJob(task.jobId, task.weights);
            Object error = result.get("error");
            if (error != null) {
                task.failed(String.valueOf(error));
                return;
            }
            task.success(result);
        } catch (Exception e) {
            log.error("match task failed: jobId={}, taskId={}", task.jobId, task.taskId, e);
            task.failed(e.getMessage() == null ? "匹配任务失败" : e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static class MatchTaskState {
        private final String taskId;
        private final Long jobId;
        private final CandidateMatchService.MatchWeights weights;
        private final LocalDateTime startedAt;
        private volatile String status;
        private volatile String message;
        private volatile String error;
        private volatile LocalDateTime finishedAt;
        private volatile Map<String, Object> result;

        private MatchTaskState(Long jobId, CandidateMatchService.MatchWeights weights) {
            this.taskId = UUID.randomUUID().toString();
            this.jobId = jobId;
            this.weights = CandidateMatchService.MatchWeights.normalize(weights);
            this.startedAt = LocalDateTime.now();
            this.status = "RUNNING";
            this.message = "匹配任务执行中";
        }

        static MatchTaskState running(Long jobId, CandidateMatchService.MatchWeights weights) {
            return new MatchTaskState(jobId, weights);
        }

        boolean isRunning() {
            return "RUNNING".equals(status);
        }

        boolean isSuccess() {
            return "SUCCESS".equals(status);
        }

        void success(Map<String, Object> result) {
            this.result = result;
            this.status = "SUCCESS";
            this.message = "匹配完成";
            this.finishedAt = LocalDateTime.now();
        }

        void failed(String error) {
            this.error = error;
            this.status = "FAILED";
            this.message = "匹配失败";
            this.finishedAt = LocalDateTime.now();
        }

        Map<String, Object> toMap(boolean includeResult) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("task_id", taskId);
            out.put("job_id", jobId);
            out.put("status", status);
            out.put("running", isRunning());
            out.put("message", message);
            out.put("started_at", startedAt);
            out.put("finished_at", finishedAt);
            out.put("error", error);
            out.put("weights", weights.toPercentMap());
            if (includeResult && result != null) {
                out.put("result", result);
            }
            return out;
        }
    }

    private static class MatchThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "match-task-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
