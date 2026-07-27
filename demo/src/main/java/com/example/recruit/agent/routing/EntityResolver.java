package com.example.recruit.agent.routing;

import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实体解析器 (复刻自文档 §二项目结构 EntityResolver)。
 *
 * <p>将用户消息中的实体引用解析为数据库 ID：
 * <ul>
 *   <li>"岗位1" → jobId=1</li>
 *   <li>"简历3" → resumeId=3</li>
 *   <li>"岗位1、2、3" → [1,2,3]</li>
 * </ul>
 */
@Component
public class EntityResolver {

    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;

    private static final Pattern JOB_PATTERN = Pattern.compile("岗位(\\d+)");
    private static final Pattern RESUME_PATTERN = Pattern.compile("简历(\\d+)");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

    public EntityResolver(JobProfileMapper jobMapper, ResumeMapper resumeMapper) {
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
    }

    /** 解析消息中第一个岗位引用。 */
    public Long resolveJobId(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = JOB_PATTERN.matcher(message);
        if (m.find()) {
            return Long.valueOf(m.group(1));
        }
        return null;
    }

    /** 解析消息中第一个简历引用。 */
    public Long resolveResumeId(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = RESUME_PATTERN.matcher(message);
        if (m.find()) {
            return Long.valueOf(m.group(1));
        }
        return null;
    }

    /** 解析消息中全部岗位 ID 引用 (支持 "1、2、3" 或 "1,2,3")。 */
    public List<Long> resolveJobIds(String message) {
        return resolveIds(message, "岗位");
    }

    public List<Long> resolveResumeIds(String message) {
        return resolveIds(message, "简历");
    }

    private List<Long> resolveIds(String message, String prefix) {
        List<Long> ids = new ArrayList<>();
        if (message == null) {
            return ids;
        }
        int idx = message.indexOf(prefix);
        if (idx < 0) {
            return ids;
        }
        // 从 prefix 后开始，提取连续数字 (支持顿号/逗号分隔)
        String tail = message.substring(idx + prefix.length());
        Matcher m = DIGIT_RUN.matcher(tail);
        while (m.find()) {
            ids.add(Long.valueOf(m.group()));
        }
        return ids;
    }

    /** 校验 jobId 是否存在。 */
    public boolean jobExists(Long jobId) {
        return jobId != null && jobMapper.selectById(jobId) != null;
    }

    public boolean resumeExists(Long resumeId) {
        return resumeId != null && resumeMapper.selectById(resumeId) != null;
    }
}
