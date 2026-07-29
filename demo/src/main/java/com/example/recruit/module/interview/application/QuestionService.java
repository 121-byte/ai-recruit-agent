package com.example.recruit.module.interview.application;

import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Question;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.QuestionMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试出题业务服务 (复刻对齐清单 §4.8)。
 *
 * <p>根据岗位 JD 与候选人简历，LLM 生成 5 道面试题（technical×2 / project×2 / behavioral×1，
 * 含 follow_ups），写入 question 表并返回列表。Tool 层不再持有 Mapper/LLM 依赖。
 */
@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final InterviewMapper interviewMapper;
    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final QuestionMapper questionMapper;
    private final DeepSeekModelService deepSeek;

    public QuestionService(InterviewMapper interviewMapper,
                           JobProfileMapper jobMapper,
                           ResumeMapper resumeMapper,
                           QuestionMapper questionMapper,
                           DeepSeekModelService deepSeek) {
        this.interviewMapper = interviewMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.questionMapper = questionMapper;
        this.deepSeek = deepSeek;
    }

    /**
     * 为面试生成 5 道面试题（technical×2 / project×2 / behavioral×1，含 follow_ups），
     * 批量写入 question 表，返回题目列表。
     */
    public List<Question> generateQuestions(Long interviewId) {
        List<Question> result = new ArrayList<>();
        if (interviewId == null) {
            return result;
        }
        Interview iv = interviewMapper.selectById(interviewId);
        if (iv == null) {
            return result;
        }
        JobProfile job = iv.getJobId() == null ? null : jobMapper.selectById(iv.getJobId());
        Resume resume = iv.getResumeId() == null ? null : resumeMapper.selectById(iv.getResumeId());

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

        try {
            String reply = deepSeek.chatJson(sys, user.toString());
            JsonNode node = JsonGuard.parseJsonSafe(reply);
            JsonNode arr = node == null ? null : node.path("questions");
            if (arr != null && arr.isArray()) {
                LocalDateTime now = LocalDateTime.now();
                for (JsonNode q : arr) {
                    Question entity = new Question();
                    entity.setInterviewId(interviewId);
                    entity.setType(q.path("type").asText("technical"));
                    entity.setContent(q.path("content").asText(""));
                    entity.setFollowUps(q.path("follow_ups"));
                    entity.setHrAdopted(false);
                    entity.setCreatedAt(now);
                    try {
                        questionMapper.insert(entity);
                        result.add(entity);
                    } catch (Exception e) {
                        log.warn("insert question failed: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("generateQuestions failed: {}", e.getMessage());
        }
        return result;
    }
}
