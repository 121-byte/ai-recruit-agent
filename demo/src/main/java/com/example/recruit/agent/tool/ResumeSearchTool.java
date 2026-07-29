package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.module.resume.application.ResumeService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用简历搜索工具 (复刻自文档 §8.2 ResumeSearchTool)。
 *
 * <p>P1 分层后只做：参数校验 + 调 {@link ResumeService#search} + 结果摘要。
 * Tool 不注入 Mapper、不写业务 SQL。多参数可选通用搜索，所有参数可选，LLM 按需组合。
 */
@Component
public class ResumeSearchTool {

    private final ResumeService resumeService;

    public ResumeSearchTool(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Tool(
            name = "searchResumes",
            description = "通用简历搜索：按姓名/学校/学历/专业/意向岗位/工作年限组合查询。参数全部可选，按需组合。返回简历列表（含 resume_id、姓名、意向岗位、技能摘要）。",
            readOnly = true,
            concurrencySafe = true)
    public List<Map<String, Object>> searchResumes(
            @ToolParam(name = "name", description = "候选人姓名（可选）")
            String name,
            @ToolParam(name = "school", description = "毕业院校关键词（可选）")
            String school,
            @ToolParam(name = "education", description = "学历如 本科/硕士/博士（可选）")
            String education,
            @ToolParam(name = "major", description = "专业关键词（可选）")
            String major,
            @ToolParam(name = "intendedPosition", description = "意向岗位关键词（可选）")
            String intendedPosition,
            @ToolParam(name = "minExperience", description = "最低工作年限（可选，整数）")
            Integer minExperience) {

        List<Resume> resumes = resumeService.search(name, school, education, major, intendedPosition, minExperience);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Resume r : resumes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resume_id", r.getId());
            item.put("name", r.getCandidateName());
            item.put("status", r.getStatus());
            item.put("summary", buildSummary(r));
            result.add(item);
        }
        return result;
    }

    /** 从 parsedJson 拼接简短摘要。 */
    private String buildSummary(Resume r) {
        try {
            var data = r.getParsedJson();
            if (data == null) {
                return r.getRawText() == null ? "" :
                        r.getRawText().substring(0, Math.min(60, r.getRawText().length()));
            }
            StringBuilder sb = new StringBuilder();
            String pos = data.path("intended_position").asText("");
            if (!pos.isEmpty()) {
                sb.append("意向:").append(pos).append("; ");
            }
            var skills = data.path("skills");
            if (skills.isArray()) {
                sb.append("技能:");
                for (int i = 0; i < Math.min(skills.size(), 5); i++) {
                    sb.append(skills.get(i).asText()).append(" ");
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
