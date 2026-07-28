package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.Question;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试记录 CRUD 服务 (复刻对齐清单 §2)。
 * 无 LLM 依赖，封装 InterviewMapper + QuestionMapper 调用。
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewMapper interviewMapper;
    private final QuestionMapper questionMapper;

    public InterviewService(InterviewMapper interviewMapper, QuestionMapper questionMapper) {
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
    }

    /** 新建面试记录。 */
    public Interview create(Interview interview) {
        if (interview == null) {
            return null;
        }
        try {
            if (interview.getCreatedAt() == null) {
                interview.setCreatedAt(LocalDateTime.now());
            }
            interviewMapper.insert(interview);
            return interview;
        } catch (Exception e) {
            log.warn("create interview failed: {}", e.getMessage());
            return null;
        }
    }

    /** 更新面试记录。 */
    public boolean update(Interview interview) {
        if (interview == null || interview.getId() == null) {
            return false;
        }
        try {
            return interviewMapper.updateById(interview) > 0;
        } catch (Exception e) {
            log.warn("update interview failed: {}", e.getMessage());
            return false;
        }
    }

    /** 更新面试状态。 */
    public boolean updateStatus(Long id, String status) {
        if (id == null) {
            return false;
        }
        try {
            Interview update = new Interview();
            update.setId(id);
            update.setStatus(status);
            return interviewMapper.updateById(update) > 0;
        } catch (Exception e) {
            log.warn("updateStatus interview failed: {}", e.getMessage());
            return false;
        }
    }

    /** 按主键查询面试记录。 */
    public Interview getById(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return interviewMapper.selectById(id);
        } catch (Exception e) {
            log.warn("getById interview failed: {}", e.getMessage());
            return null;
        }
    }

    /** 按岗位 ID 查询面试记录。 */
    public List<Interview> listByJobId(Long jobId) {
        if (jobId == null) {
            return List.of();
        }
        try {
            return interviewMapper.selectByJobId(jobId);
        } catch (Exception e) {
            log.warn("listByJobId interview failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按简历 ID 查询面试记录。 */
    public List<Interview> listByResumeId(Long resumeId) {
        if (resumeId == null) {
            return List.of();
        }
        try {
            return interviewMapper.selectByResumeId(resumeId);
        } catch (Exception e) {
            log.warn("listByResumeId interview failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 查询全部面试记录。 */
    public List<Interview> listAll() {
        try {
            return interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                    .orderByDesc(Interview::getCreatedAt));
        } catch (Exception e) {
            log.warn("listAll interview failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按岗位 ID 删除面试记录。 */
    public boolean deleteByJobId(Long jobId) {
        if (jobId == null) {
            return false;
        }
        try {
            return interviewMapper.deleteByJobId(jobId) > 0;
        } catch (Exception e) {
            log.warn("deleteByJobId interview failed: {}", e.getMessage());
            return false;
        }
    }

    /** 按简历 ID 删除面试记录。 */
    public boolean deleteByResumeId(Long resumeId) {
        if (resumeId == null) {
            return false;
        }
        try {
            return interviewMapper.deleteByResumeId(resumeId) > 0;
        } catch (Exception e) {
            log.warn("deleteByResumeId interview failed: {}", e.getMessage());
            return false;
        }
    }

    /** 新增单条面试题。 */
    public Question addQuestion(Question question) {
        if (question == null) {
            return null;
        }
        try {
            if (question.getCreatedAt() == null) {
                question.setCreatedAt(LocalDateTime.now());
            }
            questionMapper.insert(question);
            return question;
        } catch (Exception e) {
            log.warn("addQuestion failed: {}", e.getMessage());
            return null;
        }
    }

    /** 批量新增面试题。 */
    public int batchAddQuestions(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return 0;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            for (Question q : questions) {
                if (q.getCreatedAt() == null) {
                    q.setCreatedAt(now);
                }
            }
            return questionMapper.batchInsert(questions);
        } catch (Exception e) {
            log.warn("batchAddQuestions failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 标记面试题为 HR 采纳。 */
    public boolean adoptQuestion(Long questionId) {
        if (questionId == null) {
            return false;
        }
        try {
            return questionMapper.adoptQuestion(questionId) > 0;
        } catch (Exception e) {
            log.warn("adoptQuestion failed: {}", e.getMessage());
            return false;
        }
    }

    /** 查询面试下的全部面试题。 */
    public List<Question> listQuestions(Long interviewId) {
        if (interviewId == null) {
            return List.of();
        }
        try {
            return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getInterviewId, interviewId)
                    .orderByAsc(Question::getId));
        } catch (Exception e) {
            log.warn("listQuestions failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 查询面试下 HR 采纳的面试题。 */
    public List<Question> listAdoptedQuestions(Long interviewId) {
        if (interviewId == null) {
            return List.of();
        }
        try {
            return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getInterviewId, interviewId)
                    .eq(Question::getHrAdopted, true)
                    .orderByAsc(Question::getId));
        } catch (Exception e) {
            log.warn("listAdoptedQuestions failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按主键查询面试题。 */
    public Question getQuestionById(Long questionId) {
        if (questionId == null) {
            return null;
        }
        try {
            return questionMapper.selectById(questionId);
        } catch (Exception e) {
            log.warn("getQuestionById failed: {}", e.getMessage());
            return null;
        }
    }
}
