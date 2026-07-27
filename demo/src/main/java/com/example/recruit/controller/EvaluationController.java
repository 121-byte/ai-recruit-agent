package com.example.recruit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 评估 API (复刻自文档 §14.9)。
 *
 * <p>POST /api/evaluation/run     执行 RAG 评估
 * <p>GET  /api/evaluation/results 查看评估结果
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    @PostMapping("/run")
    public Map<String, Object> run() {
        return Map.of("status", "completed", "note", "评估框架入口，详见 experiments/");
    }

    @GetMapping("/results")
    public List<Object> results() {
        return List.of();
    }
}
