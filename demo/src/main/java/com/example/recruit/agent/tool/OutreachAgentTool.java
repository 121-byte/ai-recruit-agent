package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Outreach;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.OutreachMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.DeepSeekModelService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 候选人触达工具 (复刻自文档 §8.7 OutreachAgentTool)。
 *
 * <p>生成个性化邀约消息，支持单发与批量。
 */
@Component
public class OutreachAgentTool {

    private static final Logger log = LoggerFactory.getLogger(OutreachAgentTool.class);

    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final OutreachMapper outreachMapper;
    private final DeepSeekModelService deepSeekModelService;

    public OutreachAgentTool(JobProfileMapper jobMapper,
                              ResumeMapper resumeMapper,
                              OutreachMapper outreachMapper,
                              DeepSeekModelService deepSeekModelService) {
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.outreachMapper = outreachMapper;
        this.deepSeekModelService = deepSeekModelService;
    }

    @Tool(
            name = "generateOutreach",
            description = "为单个候选人生成个性化邀约消息（结合岗位亮点 + 候选人背景）。",
            concurrencySafe = false)
    public Map<String, Object> generateOutreach(
            @ToolParam(name = "jobId", description = "岗位 ID")
            Long jobId,
            @ToolParam(name = "resumeId", description = "候选人简历 ID")
            Long resumeId) {

        JobProfile job = jobMapper.selectById(jobId);
        Resume resume = resumeMapper.selectById(resumeId);
        if (job == null || resume == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "岗位或简历不存在");
            return r;
        }
        String sys = "你是资深招聘 HR。请根据岗位亮点和候选人背景，撰写一封简短、真诚、有吸引力的邀约消息（150字内）。";
        String user = "岗位: " + job.getTitle() + " (薪资" + job.getSalaryMin() + "-" + job.getSalaryMax()
                + ", " + job.getLocation() + ")\n候选人: " + resume.getCandidateName()
                + "\n简历要点: " + (resume.getRawText() == null ? "" : resume.getRawText().substring(0, Math.min(200, resume.getRawText().length())));
        String message = deepSeekModelService.chat(sys, user);

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
            log.debug("insert outreach failed: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("outreach_id", o.getId());
        out.put("job_id", jobId);
        out.put("resume_id", resumeId);
        out.put("candidate_name", resume.getCandidateName());
        out.put("message", message);
        out.put("status", "draft");
        return out;
    }

    @Tool(
            name = "generateBatchOutreach",
            description = "批量生成邀约消息。resumeIds 为逗号分隔的简历 ID。",
            concurrencySafe = false)
    public Map<String, Object> generateBatchOutreach(
            @ToolParam(name = "jobId", description = "岗位 ID")
            Long jobId,
            @ToolParam(name = "resumeIds", description = "简历 ID 列表，逗号分隔，如 \"1,2,3\"")
            String resumeIds) {

        List<Long> ids = Arrays.stream(resumeIds == null ? new String[0] : resumeIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());

        String batchId = "batch-" + System.currentTimeMillis();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long rid : ids) {
            Map<String, Object> single = generateOutreach(jobId, rid);
            single.put("batch_id", batchId);
            items.add(single);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", jobId);
        out.put("batch_id", batchId);
        out.put("count", items.size());
        out.put("items", items);
        return out;
    }
}
