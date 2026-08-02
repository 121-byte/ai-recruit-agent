package com.example.recruit.agent.core;

import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.agent.tool.ResumeSearchTool;
import com.example.recruit.infra.llm.ChatResult;
import com.example.recruit.infra.llm.DeepSeekModelService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReWooExecutorTest {

    @Test
    void analyzeResumeByNameSearchesResumeBeforeLoadingDetail() {
        DeepSeekModelService model = mock(DeepSeekModelService.class);
        JobAnalysisTool jobAnalysisTool = mock(JobAnalysisTool.class);
        CandidateMatchingTool candidateMatchingTool = mock(CandidateMatchingTool.class);
        InterviewQuestionTool interviewQuestionTool = mock(InterviewQuestionTool.class);
        ResumeAnalysisTool resumeAnalysisTool = mock(ResumeAnalysisTool.class);
        ResumeSearchTool resumeSearchTool = mock(ResumeSearchTool.class);

        when(model.chatJsonWithUsage(anyString(), anyString())).thenReturn(new ChatResult("""
                {"tasks":[{"tool":"analyzeResumeByName","args":{"name":"李璐阳"},"description":"查看李璐阳的简历详情"}]}
                """, 10, 4));
        when(model.chatWithUsage(anyString(), anyString())).thenReturn(new ChatResult("李璐阳详情汇总", 20, 8));

        Map<String, Object> found = new LinkedHashMap<>();
        found.put("found", true);
        found.put("resume_id", 5L);
        found.put("name", "李璐阳");
        found.put("school", "北京大学");
        when(resumeSearchTool.searchResumes("李璐阳", null, null, null, null, null)).thenReturn(List.of(found));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("resumeId", 5L);
        detail.put("name", "李璐阳");
        detail.put("source", "cached_parsed_json");
        when(resumeAnalysisTool.analyzeResume(5L)).thenReturn(detail);

        ReWooExecutor executor = new ReWooExecutor(model, jobAnalysisTool, candidateMatchingTool,
                interviewQuestionTool, resumeAnalysisTool, resumeSearchTool);

        ReWooExecutor.BatchExecution execution = executor.executeWithUsage("查看一下李璐阳的详情");

        assertThat(execution.tasks()).hasSize(1);
        assertThat(execution.tasks().get(0).tool()).isEqualTo("analyzeResumeByName");
        assertThat(execution.result()).isEqualTo("李璐阳详情汇总");
        verify(resumeSearchTool).searchResumes("李璐阳", null, null, null, null, null);
        verify(resumeAnalysisTool).analyzeResume(5L);
    }

    @Test
    void analyzeResumeByNameDoesNotStopOtherTasksWhenOneCandidateIsNotFound() {
        DeepSeekModelService model = mock(DeepSeekModelService.class);
        JobAnalysisTool jobAnalysisTool = mock(JobAnalysisTool.class);
        CandidateMatchingTool candidateMatchingTool = mock(CandidateMatchingTool.class);
        InterviewQuestionTool interviewQuestionTool = mock(InterviewQuestionTool.class);
        ResumeAnalysisTool resumeAnalysisTool = mock(ResumeAnalysisTool.class);
        ResumeSearchTool resumeSearchTool = mock(ResumeSearchTool.class);

        when(model.chatJsonWithUsage(anyString(), anyString())).thenReturn(new ChatResult("""
                {"tasks":[
                  {"tool":"analyzeResumeByName","args":{"name":"张三"},"description":"查看张三的简历详情"},
                  {"tool":"analyzeResumeByName","args":{"name":"李璐阳"},"description":"查看李璐阳的简历详情"}
                ]}
                """, 10, 4));
        when(model.chatWithUsage(anyString(), anyString())).thenReturn(new ChatResult("批量汇总", 20, 8));

        Map<String, Object> notFound = new LinkedHashMap<>();
        notFound.put("found", false);
        notFound.put("query_name", "张三");
        when(resumeSearchTool.searchResumes("张三", null, null, null, null, null)).thenReturn(List.of(notFound));

        Map<String, Object> found = new LinkedHashMap<>();
        found.put("found", true);
        found.put("resume_id", 5L);
        found.put("name", "李璐阳");
        when(resumeSearchTool.searchResumes("李璐阳", null, null, null, null, null)).thenReturn(List.of(found));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("resumeId", 5L);
        detail.put("source", "cached_parsed_json");
        when(resumeAnalysisTool.analyzeResume(5L)).thenReturn(detail);

        ReWooExecutor executor = new ReWooExecutor(model, jobAnalysisTool, candidateMatchingTool,
                interviewQuestionTool, resumeAnalysisTool, resumeSearchTool);

        ReWooExecutor.BatchExecution execution = executor.executeWithUsage("查看一下张三和李璐阳的详情");

        assertThat(execution.tasks()).hasSize(2);
        assertThat(execution.result()).isEqualTo("批量汇总");
        verify(resumeSearchTool).searchResumes("张三", null, null, null, null, null);
        verify(resumeSearchTool).searchResumes("李璐阳", null, null, null, null, null);
        verify(resumeAnalysisTool).analyzeResume(5L);
        verify(resumeAnalysisTool, never()).analyzeResume(null);
    }
}
