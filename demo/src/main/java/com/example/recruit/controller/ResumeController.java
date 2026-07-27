package com.example.recruit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.ResumeMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历 API (复刻自文档 §14.5)。
 *
 * <p>GET    /api/resumes          简历列表
 * <p>GET    /api/resumes/{id}     简历详情
 * <p>POST   /api/resumes/upload   上传简历文件
 * <p>POST   /api/resumes/{id}/analyze LLM 分析简历
 * <p>GET    /api/resumes/search   搜索简历
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeMapper resumeMapper;
    private final ResumeAnalysisTool resumeAnalysisTool;

    public ResumeController(ResumeMapper resumeMapper, ResumeAnalysisTool resumeAnalysisTool) {
        this.resumeMapper = resumeMapper;
        this.resumeAnalysisTool = resumeAnalysisTool;
    }

    @GetMapping
    public List<Resume> list() {
        try {
            return resumeMapper.selectList(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/{id}")
    public Resume get(@PathVariable Long id) {
        try {
            return resumeMapper.selectById(id);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        // Mock 占位：不真正解析文件，仅返回文件名与大小。
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("fileName", file == null ? null : file.getOriginalFilename());
        resp.put("size", file == null ? 0L : file.getSize());
        resp.put("note", "mock upload, not parsed");
        return resp;
    }

    @PostMapping("/{id}/analyze")
    public Object analyze(@PathVariable Long id) {
        return resumeAnalysisTool.analyzeResume(id);
    }

    @GetMapping("/search")
    public List<Resume> search(@RequestParam("query") String query) {
        try {
            return resumeMapper.selectList(
                    new LambdaQueryWrapper<Resume>()
                            .like(Resume::getCandidateName, query));
        } catch (Exception e) {
            return List.of();
        }
    }
}
