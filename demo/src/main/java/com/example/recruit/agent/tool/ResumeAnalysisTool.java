package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.JsonGuard;
import com.example.recruit.llm.QuickInfoExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 简历分析工具 (复刻自文档 §8 ResumeAnalysisTool)。
 *
 * <p>用 LLM 将 raw_text 解析为结构化 parsed_json（姓名/意向/技能/工作经历/教育），
 * 写回 embedding。
 */
@Component
public class ResumeAnalysisTool {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResumeMapper resumeMapper;
    private final DeepSeekModelService deepSeekModelService;
    private final EmbeddingService embeddingService;
    private final QuickInfoExtractor quickInfoExtractor;

    public ResumeAnalysisTool(ResumeMapper resumeMapper,
                               DeepSeekModelService deepSeekModelService,
                               EmbeddingService embeddingService,
                               QuickInfoExtractor quickInfoExtractor) {
        this.resumeMapper = resumeMapper;
        this.deepSeekModelService = deepSeekModelService;
        this.embeddingService = embeddingService;
        this.quickInfoExtractor = quickInfoExtractor;
    }

    @Tool(
            name = "analyzeResume",
            description = "LLM 解析简历，提取结构化字段（姓名/意向岗位/工作年限/技能列表/工作经历/教育）并写回。",
            concurrencySafe = false)
    public Object analyzeResume(
            @ToolParam(name = "resumeId", description = "简历 ID")
            Long resumeId) {

        Resume r = resumeMapper.selectById(resumeId);
        if (r == null) {
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("error", "简历不存在: " + resumeId);
            return err;
        }

        // 先用正则快速预填
        ObjectNode merged = quickInfoExtractor.extract(r.getRawText());

        String sys = """
                你是简历解析器。从简历文本提取结构化信息，以 JSON 输出:
                {"name":"...","intended_position":"...","work_years":0,"skills":["..."],
                 "education":[{"school":"...","major":"...","degree":"..."}],
                 "work_experience":[{"company":"...","position":"...","duration":"...","description":"..."}]}
                不要 markdown 标记。""";
        try {
            String reply = deepSeekModelService.chatJson(sys, r.getRawText() == null ? "" : r.getRawText());
            JsonNode parsed = JsonGuard.parseJsonSafe(reply);
            if (parsed != null && parsed.isObject()) {
                // 合并: LLM 结果优先，LLM 缺字段则用 quickInfo 补
                for (String field : new String[]{"name", "phone", "email"}) {
                    JsonNode v = parsed.path(field);
                    if ((v.isMissingNode() || v.asText("").isEmpty()) && merged.has(field)) {
                        ((ObjectNode) parsed).set(field, merged.get(field));
                    }
                }
                r.setParsedJson(parsed);
            } else {
                r.setParsedJson(merged);
            }
            try {
                r.setEmbedding(embeddingService.embed(r.getRawText()));
            } catch (Throwable ignored) {
            }
            r.setStatus("reviewed");
            r.setUpdatedAt(LocalDateTime.now());
            resumeMapper.updateById(r);
        } catch (Exception e) {
            log.warn("analyzeResume failed: {}", e.getMessage());
        }

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("resume_id", r.getId());
        out.put("name", r.getCandidateName());
        out.put("parsed_json", r.getParsedJson());
        out.put("status", r.getStatus());
        return out;
    }
}
