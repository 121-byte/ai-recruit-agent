package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.module.resume.application.ResumeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumeSearchToolTest {

    @Test
    void searchResumesReturnsExplicitNotFoundWhenServiceResultIsEmpty() {
        ResumeService resumeService = mock(ResumeService.class);
        when(resumeService.search("Li Luyang", null, null, null, null, null)).thenReturn(List.of());

        ResumeSearchTool tool = new ResumeSearchTool(resumeService);

        List<Map<String, Object>> result = tool.searchResumes("Li Luyang", null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("found", false);
        assertThat(result.get(0).get("message")).asString().contains("tool executed normally");
        assertThat(result.get(0)).containsEntry("query_name", "Li Luyang");
    }

    @Test
    void searchResumesIncludesFieldsUsefulForCandidateIntroduction() {
        Resume resume = new Resume();
        resume.setId(5L);
        resume.setCandidateName("Li Luyang");
        resume.setStatus("parsed");
        resume.setSchool("Peking University");
        resume.setEducation("Bachelor");
        resume.setMajor("Computer Science");
        resume.setYearsExperience(3);
        resume.setIntendedPosition("Java Backend Developer");

        ResumeService resumeService = mock(ResumeService.class);
        when(resumeService.search("Li Luyang", null, null, null, null, null)).thenReturn(List.of(resume));

        ResumeSearchTool tool = new ResumeSearchTool(resumeService);

        List<Map<String, Object>> result = tool.searchResumes("Li Luyang", null, null, null, null, null);

        assertThat(result).hasSize(1);
        Map<String, Object> item = result.get(0);
        assertThat(item).containsEntry("found", true);
        assertThat(item).containsEntry("resume_id", 5L);
        assertThat(item).containsEntry("name", "Li Luyang");
        assertThat(item).containsEntry("school", "Peking University");
        assertThat(item).containsEntry("education", "Bachelor");
        assertThat(item).containsEntry("major", "Computer Science");
        assertThat(item).containsEntry("years_experience", 3);
        assertThat(item).containsEntry("intended_position", "Java Backend Developer");
        assertThat(item.get("summary")).asString().contains("school:Peking University", "intended_position:Java Backend Developer");
    }
}
