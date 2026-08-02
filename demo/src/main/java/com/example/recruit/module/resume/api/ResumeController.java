package com.example.recruit.module.resume.api;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.infra.fileparser.FileParserUtil;
import com.example.recruit.infra.fileparser.QuickInfoExtractor;
import com.example.recruit.module.resume.application.ResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历 CRUD API (复刻自对齐清单 §5.10, @RequestMapping("/api/resumes"))。
 *
 * <p>6 个权威端点:
 * <ul>
 *   <li>POST           创建简历</li>
 *   <li>PUT  /{id}     更新简历</li>
 *   <li>DELETE /{id}   删除简历</li>
 *   <li>GET  /{id}     简历详情</li>
 *   <li>GET            简历列表</li>
 *   <li>POST /upload   上传简历文件</li>
 * </ul>
 *
 * <p>注意: POST /{id}/analyze 与 POST /compare 由 {@link ResumeAnalysisController} 提供,
 * 本 Controller 不再提供 /{id}/analyze 以避免路径冲突。
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final FileParserUtil fileParserUtil;

    public ResumeController(ResumeService resumeService, FileParserUtil fileParserUtil) {
        this.resumeService = resumeService;
        this.fileParserUtil = fileParserUtil;
    }

    @GetMapping
    public List<Resume> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String intendedPosition,
            @RequestParam(required = false) String education) {
        boolean hasParams = (keyword != null && !keyword.isBlank())
                || (status != null && !status.isBlank())
                || (intendedPosition != null && !intendedPosition.isBlank())
                || (education != null && !education.isBlank());
        if (hasParams) {
            return resumeService.search(keyword, status, intendedPosition, education);
        }
        return resumeService.listAll();
    }

    @GetMapping("/intended-positions")
    public List<String> intendedPositions() {
        return resumeService.listIntendedPositions();
    }

    @GetMapping("/educations")
    public List<String> educations() {
        return resumeService.listEducations();
    }

    /** 固定岗位类别 (技术/人事/...), 用于意向岗位分类筛选。 */
    @GetMapping("/debug/agent-search")
    public Map<String, Object> debugAgentSearch(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String school,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String intendedPosition,
            @RequestParam(required = false) Integer minExperience) {
        List<Resume> resumes = resumeService.search(name, school, education, major, intendedPosition, minExperience);
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("name", valueOrNull(name));
        query.put("school", valueOrNull(school));
        query.put("education", valueOrNull(education));
        query.put("major", valueOrNull(major));
        query.put("intendedPosition", valueOrNull(intendedPosition));
        query.put("minExperience", minExperience);
        resp.put("query", query);
        resp.put("count", resumes.size());
        resp.put("found", !resumes.isEmpty());
        resp.put("results", resumes.stream()
                .map(this::toDebugResume)
                .toList());
        return resp;
    }

    @GetMapping("/position-categories")
    public List<String> positionCategories() {
        return resumeService.listPositionCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> get(@PathVariable Long id) {
        return ResponseEntity.of(java.util.Optional.ofNullable(resumeService.getById(id)));
    }

    /** POST —— 创建简历。 */
    @PostMapping
    public ResponseEntity<Resume> create(@RequestBody Resume body) {
        return ResponseEntity.ok(resumeService.create(body));
    }

    /** PUT /{id} —— 更新简历。 */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Resume body) {
        body.setId(id);
        boolean ok = resumeService.update(body);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("updated", ok);
        return ResponseEntity.ok(resp);
    }

    /** DELETE /{id} —— 删除简历。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean ok = resumeService.delete(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("deleted", ok);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (file == null || file.isEmpty()) {
            resp.put("uploaded", false);
            resp.put("message", "file is empty");
            return resp;
        }
        try {
            String fileName = file.getOriginalFilename();
            String rawText = fileParserUtil.parse(fileName, file.getBytes());
            QuickInfoExtractor.Info info = QuickInfoExtractor.extract(rawText);
            Resume resume = new Resume();
            resume.setCandidateName(info.getName() != null ? info.getName() : candidateNameFromFile(fileName));
            resume.setPhone(info.getPhone());
            resume.setEmail(info.getEmail());
            resume.setEducation(info.getEducation());
            resume.setSchool(info.getSchool());
            resume.setMajor(info.getMajor());
            resume.setYearsExperience(info.getYearsExperience());
            resume.setIntendedPosition(info.getIntendedPosition());
            resume.setRawText(rawText);
            resume.setStatus("parsed");
            Resume created = resumeService.create(resume);
            resp.put("uploaded", true);
            resp.put("resume", created);
            resp.put("fileName", fileName);
            resp.put("size", file.getSize());
        } catch (Exception e) {
            resp.put("uploaded", false);
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    @GetMapping("/search")
    public List<Resume> search(@RequestParam("query") String query) {
        try {
            return resumeService.search(query, null, null, null, null, null);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String candidateNameFromFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "未命名候选人";
        }
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.replaceAll("[_\\-]+", " ").trim();
    }

    private Map<String, Object> toDebugResume(Resume resume) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", resume.getId());
        item.put("candidateName", resume.getCandidateName());
        item.put("status", resume.getStatus());
        item.put("school", resume.getSchool());
        item.put("education", resume.getEducation());
        item.put("major", resume.getMajor());
        item.put("yearsExperience", resume.getYearsExperience());
        item.put("intendedPosition", resume.getIntendedPosition());
        item.put("rawTextPreview", preview(resume.getRawText(), 160));
        return item;
    }

    private static String preview(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= limit ? compact : compact.substring(0, limit);
    }

    private static Object valueOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
