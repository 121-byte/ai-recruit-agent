package com.example.recruit.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.Question;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.QuestionMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试出题工具 (复刻自文档 §8.5 InterviewQuestionTool)。
 */
@Component
public class InterviewQuestionTool {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InterviewMapper interviewMapper;
    private final QuestionMapper questionMapper;
    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final DeepSeekModelService deepSeekModelService;

    public InterviewQuestionTool(InterviewMapper interviewMapper,
                                   QuestionMapper questionMapper,
                                   JobProfileMapper jobMapper,
                                   ResumeMapper resumeMapper,
                                   DeepSeekModelService deepSeekModelService) {
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.deepSeekModelService = deepSeekModelService;
    }

    @Tool(
            name = "generateQuestions",
            description = "为面试生成面试题（技术/项目/行为三类），含追问。返回生成的题目列表。",
            concurrencySafe = false)
    public Map<String, Object> generateQuestions(
            @ToolParam(name = "interviewId", description = "面试 ID（来自 matchCandidates 输出）")
            Long interviewId) {

        Interview iv = interviewMapper.selectById(interviewId);
        if (iv == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "面试不存在: interviewId=" + interviewId);
            return r;
        }
        var job = iv.getJobId() == null ? null : jobMapper.selectById(iv.getJobId());
        var resume = iv.getResumeId() == null ? null : resumeMapper.selectById(iv.getResumeId());

        String sys = """
                你是技术面试官。根据岗位 JD 与候选人简历，生成 5 道面试题，覆盖:
                - technical: 技术深度题 (2 道)
                - project: 项目经验题 (2 道)
                - behavioral: 行为面题 (1 道)
                每题含 content 和 follow_ups (追问列表)。严格以 JSON 输出:
                {"questions":[{"type":"technical","content":"...","follow_ups":["...","..."]}, ...]}""";
        StringBuilder user = new StringBuilder();
        if (job != null) {
            user.append("岗位: ").append(job.getTitle()).append("\nJD: ").append(job.getJdText()).append("\n");
        }
        if (resume != null) {
            user.append("候选人: ").append(resume.getCandidateName()).append("\n简历: ").append(resume.getRawText());
        }

        List<Map<String, Object>> questions = new ArrayList<>();
        try {
            String reply = deepSeekModelService.chatJson(sys, user.toString());
            JsonNode node = JsonGuard.parseJsonSafe(reply);
            JsonNode arr = node == null ? null : node.path("questions");
            if (arr != null && arr.isArray()) {
                for (JsonNode q : arr) {
                    Question entity = new Question();
                    entity.setInterviewId(interviewId);
                    entity.setType(q.path("type").asText("technical"));
                    entity.setContent(q.path("content").asText());
                    entity.setFollowUps(q.path("follow_ups"));
                    entity.setHrAdopted(false);
                    entity.setCreatedAt(LocalDateTime.now());
                    try {
                        questionMapper.insert(entity);
                    } catch (Exception ignored) {
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("question_id", entity.getId());
                    item.put("type", entity.getType());
                    item.put("content", entity.getContent());
                    item.put("follow_ups", entity.getFollowUps());
                    questions.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("generateQuestions failed: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("interview_id", interviewId);
        out.put("count", questions.size());
        out.put("questions", questions);
        return out;
    }

    @Tool(
            name = "getQuestions",
            description = "查看某场面试已有的面试题。",
            readOnly = true,
            concurrencySafe = true)
    public List<Map<String, Object>> getQuestions(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId) {
        List<Question> list = questionMapper.selectList(
                new LambdaQueryWrapper<Question>().eq(Question::getInterviewId, interviewId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Question q : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_id", q.getId());
            item.put("type", q.getType());
            item.put("content", q.getContent());
            item.put("follow_ups", q.getFollowUps());
            item.put("hr_adopted", q.getHrAdopted());
            result.add(item);
        }
        return result;
    }
}
