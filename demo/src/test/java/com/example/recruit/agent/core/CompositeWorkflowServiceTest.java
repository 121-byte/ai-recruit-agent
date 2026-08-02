package com.example.recruit.agent.core;

import com.example.recruit.agent.tool.CandidateMatchingTool;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeWorkflowServiceTest {

    @Test
    void executesDependentMultiAgentWorkflowWithSharedMemory() {
        DeepSeekModelService model = mock(DeepSeekModelService.class);
        ResumeSearchTool resumeSearchTool = mock(ResumeSearchTool.class);
        ResumeAnalysisTool resumeAnalysisTool = mock(ResumeAnalysisTool.class);
        JobAnalysisTool jobAnalysisTool = mock(JobAnalysisTool.class);
        CandidateMatchingTool candidateMatchingTool = mock(CandidateMatchingTool.class);

        when(model.chatJsonWithUsage(anyString(), anyString())).thenReturn(new ChatResult("""
                {
                  "steps": [
                    {
                      "id": "s1",
                      "agent": "ResumeAgent",
                      "task": "find_and_analyze_resume",
                      "args": {"name": "李璐阳"},
                      "dependsOn": [],
                      "description": "查看李璐阳简历详情"
                    },
                    {
                      "id": "s2",
                      "agent": "JobAgent",
                      "task": "analyze_job_by_ordinal",
                      "args": {"ordinal": 2},
                      "dependsOn": [],
                      "description": "查看第二个岗位"
                    },
                    {
                      "id": "s3",
                      "agent": "MatchAgent",
                      "task": "check_resume_job_match",
                      "args": {"resumeRef": "$s1.resume_id", "jobRef": "$s2.job_id"},
                      "dependsOn": ["s1", "s2"],
                      "description": "判断是否匹配"
                    }
                  ]
                }
                """, 10, 5));
        when(model.chatWithUsage(anyString(), anyString())).thenReturn(new ChatResult("最终汇总", 20, 8));

        Map<String, Object> resume = new LinkedHashMap<>();
        resume.put("found", true);
        resume.put("resume_id", 5L);
        resume.put("name", "李璐阳");
        when(resumeSearchTool.searchResumes("李璐阳", null, null, null, null, null)).thenReturn(List.of(resume));
        when(resumeAnalysisTool.analyzeResume(5L)).thenReturn(Map.of("resumeId", 5L, "source", "cached_parsed_json"));

        Map<String, Object> firstJob = new LinkedHashMap<>();
        firstJob.put("job_id", 1L);
        firstJob.put("title", "前端工程师");
        Map<String, Object> secondJob = new LinkedHashMap<>();
        secondJob.put("job_id", 2L);
        secondJob.put("title", "Java后端工程师");
        when(jobAnalysisTool.listJobs()).thenReturn(List.of(firstJob, secondJob));
        when(jobAnalysisTool.analyzeJob(2L)).thenReturn(Map.of("job_id", 2L, "title", "Java后端工程师"));

        Map<String, Object> matchedCandidate = new LinkedHashMap<>();
        matchedCandidate.put("resume_id", 5L);
        matchedCandidate.put("name", "李璐阳");
        matchedCandidate.put("overall_score", 88.0);
        Map<String, Object> matchResult = new LinkedHashMap<>();
        matchResult.put("job_id", 2L);
        matchResult.put("candidates", List.of(matchedCandidate));
        when(candidateMatchingTool.matchCandidates(2L)).thenReturn(matchResult);

        CompositeWorkflowService service = new CompositeWorkflowService(model, resumeSearchTool,
                resumeAnalysisTool, jobAnalysisTool, candidateMatchingTool);

        CompositeWorkflowService.WorkflowExecution execution =
                service.executeWithUsage("查看一下李璐阳的简历详情，然后查看一下岗位，再根据岗位找到第二个岗位，看一下是否匹配");

        assertThat(execution.supported()).isTrue();
        assertThat(execution.steps()).hasSize(3);
        assertThat(execution.results()).hasSize(3);
        assertThat(execution.results().get(2).result()).containsEntry("matched", true);
        verify(resumeSearchTool).searchResumes("李璐阳", null, null, null, null, null);
        verify(resumeAnalysisTool).analyzeResume(5L);
        verify(jobAnalysisTool).listJobs();
        verify(jobAnalysisTool).analyzeJob(2L);
        verify(candidateMatchingTool).matchCandidates(2L);
    }

    @Test
    void fallsBackToHeuristicPlanWhenPlannerReturnsNonJson() {
        DeepSeekModelService model = mock(DeepSeekModelService.class);
        ResumeSearchTool resumeSearchTool = mock(ResumeSearchTool.class);
        ResumeAnalysisTool resumeAnalysisTool = mock(ResumeAnalysisTool.class);
        JobAnalysisTool jobAnalysisTool = mock(JobAnalysisTool.class);
        CandidateMatchingTool candidateMatchingTool = mock(CandidateMatchingTool.class);

        when(model.chatJsonWithUsage(anyString(), anyString())).thenReturn(new ChatResult("not json", 0, 0));
        when(model.chatWithUsage(anyString(), anyString())).thenReturn(new ChatResult("最终汇总", 0, 0));
        when(resumeSearchTool.searchResumes("李璐阳", null, null, null, null, null))
                .thenReturn(List.of(Map.of("found", true, "resume_id", 5L, "name", "李璐阳")));
        when(resumeAnalysisTool.analyzeResume(5L)).thenReturn(Map.of("resumeId", 5L));
        when(jobAnalysisTool.listJobs()).thenReturn(List.of(
                Map.of("job_id", 1L, "title", "A"),
                Map.of("job_id", 2L, "title", "B")));
        when(jobAnalysisTool.analyzeJob(2L)).thenReturn(Map.of("job_id", 2L));
        when(candidateMatchingTool.matchCandidates(2L)).thenReturn(Map.of(
                "candidates", List.of(Map.of("resume_id", 5L, "overall_score", 90))));

        CompositeWorkflowService service = new CompositeWorkflowService(model, resumeSearchTool,
                resumeAnalysisTool, jobAnalysisTool, candidateMatchingTool);

        CompositeWorkflowService.WorkflowExecution execution =
                service.executeWithUsage("查看一下李璐阳的简历详情，然后查看第二个岗位是否匹配");

        assertThat(execution.supported()).isTrue();
        assertThat(execution.steps()).extracting(CompositeWorkflowService.WorkflowStep::agent)
                .containsExactly("ResumeAgent", "JobAgent", "MatchAgent");
    }
}
