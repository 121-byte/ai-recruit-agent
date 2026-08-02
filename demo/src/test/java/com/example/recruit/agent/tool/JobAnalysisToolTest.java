package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.module.job.application.JobAnalysisService;
import com.example.recruit.module.job.application.JobProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobAnalysisToolTest {

    @Test
    void analyzeJobReturnsCachedParsedJsonWithoutReanalysis() throws Exception {
        JobProfile job = new JobProfile();
        job.setId(7L);
        job.setTitle("Java Backend Developer");
        job.setStatus("active");
        job.setDepartment("Engineering");
        job.setParsedJson(new ObjectMapper().readTree("""
                {
                  "positionInfo": {"title": "Java Backend Developer"},
                  "skills": {"must": ["Java", "Spring Boot"]},
                  "requirements": {"experience": "3 years"}
                }
                """));

        JobProfileService jobProfileService = mock(JobProfileService.class);
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        when(jobProfileService.getById(7L)).thenReturn(job);

        JobAnalysisTool tool = new JobAnalysisTool(jobProfileService, jobAnalysisService);

        Map<String, Object> result = tool.analyzeJob(7L);

        assertThat(result).containsEntry("job_id", 7L);
        assertThat(result).containsEntry("source", "cached_parsed_json");
        assertThat(result.get("skills").toString()).contains("Spring Boot");
        verify(jobAnalysisService, never()).analyze(7L);
    }

    @Test
    void analyzeJobReturnsBasicDatabaseDetailWhenNoParsedJson() {
        JobProfile job = new JobProfile();
        job.setId(8L);
        job.setTitle("Java Backend Developer");
        job.setJdText("Java, Spring Boot, Redis");

        JobProfileService jobProfileService = mock(JobProfileService.class);
        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        when(jobProfileService.getById(8L)).thenReturn(job);

        JobAnalysisTool tool = new JobAnalysisTool(jobProfileService, jobAnalysisService);

        Map<String, Object> result = tool.analyzeJob(8L);

        assertThat(result).containsEntry("job_id", 8L);
        assertThat(result).containsEntry("source", "database_basic");
        assertThat(result).containsEntry("parsed_profile_available", false);
        assertThat(result.get("jd_text")).asString().contains("Spring Boot");
        verify(jobAnalysisService, never()).analyze(8L);
    }
}
