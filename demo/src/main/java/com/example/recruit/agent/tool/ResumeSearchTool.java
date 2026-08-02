package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.module.resume.application.ResumeService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * General resume search tool for agent-side candidate lookup.
 */
@Component
public class ResumeSearchTool {

    private static final Logger log = LoggerFactory.getLogger(ResumeSearchTool.class);

    private final ResumeService resumeService;

    public ResumeSearchTool(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Tool(
            name = "searchResumes",
            description = "Search resumes by optional candidate name, school, education, major, intended position, and minimum years of experience. Returns resume_id, candidate name, status, education, school, major, intended position, years of experience, and a short summary.",
            readOnly = true,
            concurrencySafe = true)
    public List<Map<String, Object>> searchResumes(
            @ToolParam(name = "name", description = "Candidate name, optional")
            String name,
            @ToolParam(name = "school", description = "School keyword, optional")
            String school,
            @ToolParam(name = "education", description = "Education keyword, optional")
            String education,
            @ToolParam(name = "major", description = "Major keyword, optional")
            String major,
            @ToolParam(name = "intendedPosition", description = "Intended position keyword, optional")
            String intendedPosition,
            @ToolParam(name = "minExperience", description = "Minimum years of experience, optional")
            Integer minExperience) {

        List<Resume> resumes = resumeService.search(name, school, education, major, intendedPosition, minExperience);
        log.info("searchResumes params: name={}, school={}, education={}, major={}, intendedPosition={}, minExperience={}, resultCount={}",
                safe(name), safe(school), safe(education), safe(major), safe(intendedPosition), minExperience, resumes.size());

        if (resumes.isEmpty()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("found", false);
            item.put("message", "No matching resume found. The tool executed normally. Ask the user to verify the name or search with broader conditions such as school, major, or intended position.");
            item.put("query_name", safe(name));
            item.put("query_school", safe(school));
            item.put("query_education", safe(education));
            item.put("query_major", safe(major));
            item.put("query_intended_position", safe(intendedPosition));
            item.put("query_min_experience", minExperience);
            return List.of(item);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Resume resume : resumes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("found", true);
            item.put("resume_id", resume.getId());
            item.put("name", resume.getCandidateName());
            item.put("status", resume.getStatus());
            item.put("school", resume.getSchool());
            item.put("education", resume.getEducation());
            item.put("major", resume.getMajor());
            item.put("years_experience", resume.getYearsExperience());
            item.put("intended_position", resume.getIntendedPosition());
            item.put("summary", buildSummary(resume));
            result.add(item);
        }
        return result;
    }

    private String buildSummary(Resume resume) {
        try {
            StringBuilder sb = new StringBuilder();
            append(sb, "education", resume.getEducation());
            append(sb, "school", resume.getSchool());
            append(sb, "major", resume.getMajor());
            if (resume.getYearsExperience() != null) {
                append(sb, "experience", resume.getYearsExperience() + " years");
            }
            append(sb, "intended_position", resume.getIntendedPosition());

            var data = resume.getParsedJson();
            if (data == null) {
                if (sb.length() > 0) {
                    return sb.toString();
                }
                return resume.getRawText() == null ? "" :
                        resume.getRawText().substring(0, Math.min(80, resume.getRawText().length()));
            }

            String pos = data.path("intended_position").asText("");
            if (!pos.isEmpty() && (resume.getIntendedPosition() == null || resume.getIntendedPosition().isBlank())) {
                append(sb, "intended_position", pos);
            }

            var skills = data.path("skills");
            if (skills.isArray() && !skills.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append("skills:");
                for (int i = 0; i < Math.min(skills.size(), 5); i++) {
                    if (i > 0) {
                        sb.append(" ");
                    }
                    sb.append(skills.get(i).asText());
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(label).append(":").append(value.trim());
    }

    private static String safe(String value) {
        return value == null ? null : value.trim();
    }
}
