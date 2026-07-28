package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Outreach;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.OutreachMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.DeepSeekModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人触达业务服务 (复刻对齐清单 §4.4)。
 *
 * <p>封装触达记录的 CRUD、批量发送、个性化消息生成与状态流转。
 * Tool 层不再持有 Mapper/LLM 依赖。
 */
@Service
public class OutreachService {

    private static final Logger log = LoggerFactory.getLogger(OutreachService.class);

    private final OutreachMapper outreachMapper;
    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final DeepSeekModelService deepSeek;

    public OutreachService(OutreachMapper outreachMapper,
                           JobProfileMapper jobMapper,
                           ResumeMapper resumeMapper,
                           DeepSeekModelService deepSeek) {
        this.outreachMapper = outreachMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.deepSeek = deepSeek;
    }

    /** 新建单条触达记录。 */
    public Outreach create(Outreach outreach) {
        if (outreach == null) {
            return null;
        }
        try {
            if (outreach.getStatus() == null) {
                outreach.setStatus("draft");
            }
            if (outreach.getCreatedAt() == null) {
                outreach.setCreatedAt(LocalDateTime.now());
            }
            outreachMapper.insert(outreach);
            return outreach;
        } catch (Exception e) {
            log.warn("create outreach failed: {}", e.getMessage());
            return null;
        }
    }

    /** 批量新建触达记录。 */
    public int batchCreate(List<Outreach> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            for (Outreach o : items) {
                if (o.getStatus() == null) {
                    o.setStatus("draft");
                }
                if (o.getCreatedAt() == null) {
                    o.setCreatedAt(now);
                }
            }
            return outreachMapper.batchInsert(items);
        } catch (Exception e) {
            log.warn("batchCreate outreach failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 按批次 ID 批量更新状态。 */
    public int batchUpdateStatus(String batchId, String status) {
        if (batchId == null || status == null) {
            return 0;
        }
        try {
            return outreachMapper.batchUpdateStatus(batchId, status);
        } catch (Exception e) {
            log.warn("batchUpdateStatus failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 确认批次发送：draft → sent。 */
    public int confirmBatchSend(String batchId) {
        return batchUpdateStatus(batchId, "sent");
    }

    /**
     * 为单个候选人生成个性化邀约消息并入库。
     * 返回包含 outreach_id 与消息体的 Map。
     */
    public Map<String, Object> generateAndCreatePersonalized(Long jobId, Long resumeId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (jobId == null || resumeId == null) {
            out.put("error", "jobId 或 resumeId 不能为空");
            return out;
        }
        JobProfile job = jobMapper.selectById(jobId);
        Resume resume = resumeMapper.selectById(resumeId);
        if (job == null || resume == null) {
            out.put("error", "岗位或简历不存在");
            return out;
        }
        String sys = "你是资深招聘 HR。请根据岗位亮点和候选人背景，撰写一封简短、真诚、有吸引力的邀约消息（150字内）。";
        String rawText = resume.getRawText() == null ? "" : resume.getRawText();
        String snippet = rawText.length() > 200 ? rawText.substring(0, 200) : rawText;
        String user = "岗位: " + job.getTitle() + " (薪资" + job.getSalaryMin() + "-" + job.getSalaryMax()
                + ", " + job.getLocation() + ")\n候选人: " + resume.getCandidateName()
                + "\n简历要点: " + snippet;
        String message;
        try {
            message = deepSeek.chat(sys, user);
        } catch (Exception e) {
            log.warn("generate outreach chat failed: {}", e.getMessage());
            message = "您好，我们正在招聘 " + job.getTitle() + " 岗位，期待与您沟通。";
        }

        Outreach o = new Outreach();
        o.setJobId(jobId);
        o.setResumeId(resumeId);
        o.setMessage(message);
        o.setStatus("draft");
        o.setBatchId("single-" + System.currentTimeMillis());
        o.setCreatedAt(LocalDateTime.now());
        try {
            outreachMapper.insert(o);
        } catch (Exception e) {
            log.warn("insert outreach failed: {}", e.getMessage());
        }

        out.put("outreach_id", o.getId());
        out.put("job_id", jobId);
        out.put("resume_id", resumeId);
        out.put("candidate_name", resume.getCandidateName());
        out.put("message", message);
        out.put("status", "draft");
        out.put("batch_id", o.getBatchId());
        return out;
    }

    /**
     * 批量生成个性化邀约消息并入库，共用同一 batchId。
     * 返回汇总 Map（含 batch_id、count、items）。
     */
    public Map<String, Object> generateAndCreateBatch(Long jobId, List<Long> resumeIds) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (jobId == null || resumeIds == null || resumeIds.isEmpty()) {
            out.put("error", "jobId 或 resumeIds 不能为空");
            return out;
        }
        String batchId = "batch-" + System.currentTimeMillis();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long rid : resumeIds) {
            try {
                Map<String, Object> single = generateAndCreatePersonalized(jobId, rid);
                single.put("batch_id", batchId);
                items.add(single);
            } catch (Exception e) {
                log.warn("generate batch item resume={} failed: {}", rid, e.getMessage());
            }
        }
        out.put("job_id", jobId);
        out.put("batch_id", batchId);
        out.put("count", items.size());
        out.put("items", items);
        return out;
    }

    /**
     * 状态流转：校验当前状态匹配 from 后更新为 to。
     * 返回是否成功。
     */
    public boolean transitionStatus(Long id, String from, String to) {
        if (id == null || to == null) {
            return false;
        }
        try {
            Outreach o = outreachMapper.selectById(id);
            if (o == null) {
                return false;
            }
            if (from != null && !from.equals(o.getStatus())) {
                log.warn("transitionStatus mismatch: id={} expected={} actual={}", id, from, o.getStatus());
                return false;
            }
            o.setStatus(to);
            return outreachMapper.updateById(o) > 0;
        } catch (Exception e) {
            log.warn("transitionStatus failed: {}", e.getMessage());
            return false;
        }
    }

    /** 按岗位统计各状态计数 Map。 */
    public Map<String, Long> funnelByJob(Long jobId) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("draft", 0L);
        result.put("sent", 0L);
        result.put("replied", 0L);
        result.put("ignored", 0L);
        if (jobId == null) {
            return result;
        }
        try {
            List<Outreach> list = outreachMapper.selectList(
                    new LambdaQueryWrapper<Outreach>().eq(Outreach::getJobId, jobId));
            for (Outreach o : list) {
                String s = o.getStatus() == null ? "draft" : o.getStatus();
                result.merge(s, 1L, Long::sum);
            }
        } catch (Exception e) {
            log.warn("funnelByJob failed: {}", e.getMessage());
        }
        return result;
    }

    /** 看板统计（同 funnelByJob）。 */
    public Map<String, Long> kanbanStats(Long jobId) {
        return funnelByJob(jobId);
    }

    /** 按状态统计记录数。 */
    public long countByStatus(String status) {
        if (status == null) {
            return 0;
        }
        try {
            return outreachMapper.countByStatus(status);
        } catch (Exception e) {
            log.warn("countByStatus failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 按状态查询记录列表。 */
    public List<Outreach> listByStatus(String status) {
        if (status == null) {
            return List.of();
        }
        try {
            return outreachMapper.selectList(
                    new LambdaQueryWrapper<Outreach>().eq(Outreach::getStatus, status));
        } catch (Exception e) {
            log.warn("listByStatus failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按岗位 ID 查询记录列表。 */
    public List<Outreach> listByJobId(Long jobId) {
        if (jobId == null) {
            return List.of();
        }
        try {
            return outreachMapper.selectList(
                    new LambdaQueryWrapper<Outreach>().eq(Outreach::getJobId, jobId));
        } catch (Exception e) {
            log.warn("listByJobId failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按批次 ID 查询记录列表。 */
    public List<Outreach> listByBatchId(String batchId) {
        if (batchId == null) {
            return List.of();
        }
        try {
            return outreachMapper.selectByBatchId(batchId);
        } catch (Exception e) {
            log.warn("listByBatchId failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按主键查询。 */
    public Outreach getById(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return outreachMapper.selectById(id);
        } catch (Exception e) {
            log.warn("getById outreach failed: {}", e.getMessage());
            return null;
        }
    }

    /** 更新单条状态。 */
    public boolean updateStatus(Long id, String status) {
        if (id == null || status == null) {
            return false;
        }
        try {
            Outreach o = new Outreach();
            o.setId(id);
            o.setStatus(status);
            return outreachMapper.updateById(o) > 0;
        } catch (Exception e) {
            log.warn("updateStatus outreach failed: {}", e.getMessage());
            return false;
        }
    }
}
