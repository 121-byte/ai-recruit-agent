package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.module.resume.application.ResumeAnalysisService;
import com.example.recruit.module.resume.application.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeAnalysisToolTest {

    @Test
    void analyzeResumeReturnsCachedParsedJsonWithoutReanalysis() throws Exception {
        Resume resume = new Resume();
        resume.setId(5L);
        resume.setCandidateName("Li Luyang");
        resume.setStatus("analyzed");
        resume.setSchool("Peking University");
        resume.setEducation("Bachelor");
        resume.setMajor("Computer Science");
        resume.setYearsExperience(3);
        resume.setIntendedPosition("Java Backend Developer");
        resume.setParsedJson(new ObjectMapper().readTree("""
                {
                  "structuredData": {"skills": ["Java", "Spring Boot"]},
                  "implicitInsights": {"summary": "backend project experience"},
                  "riskAssessment": {"risk": "none"},
                  "potentialAssessment": {"potential": "high"},
                  "validation": "ok"
                }
                """));

        ResumeService resumeService = mock(ResumeService.class);
        ResumeAnalysisService resumeAnalysisService = mock(ResumeAnalysisService.class);
        when(resumeService.getById(5L)).thenReturn(resume);

        ResumeAnalysisTool tool = new ResumeAnalysisTool(resumeAnalysisService, resumeService);

        Map<String, Object> result = tool.analyzeResume(5L);

        assertThat(result).containsEntry("resumeId", 5L);
        assertThat(result).containsEntry("name", "Li Luyang");
        assertThat(result).containsEntry("source", "cached_parsed_json");
        assertThat(result.get("structuredData")).asString().contains("Spring Boot");
        verify(resumeAnalysisService, never()).analyzeFull(5L);
    }

    @Test
    void analyzeResumeReturnsBasicDatabaseDetailWhenNoParsedJson() {
        Resume resume = new Resume();
        resume.setId(6L);
        resume.setCandidateName("Zhang San");
        resume.setSchool("Peking University");
        resume.setRawText("Java backend resume");

        ResumeService resumeService = mock(ResumeService.class);
        ResumeAnalysisService resumeAnalysisService = mock(ResumeAnalysisService.class);
        when(resumeService.getById(6L)).thenReturn(resume);

        ResumeAnalysisTool tool = new ResumeAnalysisTool(resumeAnalysisService, resumeService);

        Map<String, Object> result = tool.analyzeResume(6L);

        assertThat(result).containsEntry("resumeId", 6L);
        assertThat(result).containsEntry("source", "database_basic");
        assertThat(result).containsEntry("parsed_profile_available", false);
        assertThat(result.get("rawText")).asString().contains("Java backend");
        verify(resumeAnalysisService, never()).analyzeFull(6L);
    }
}
