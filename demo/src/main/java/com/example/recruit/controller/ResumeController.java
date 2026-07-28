package com.example.recruit.controller;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.service.ResumeService;
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

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public List<Resume> list() {
        try {
            return resumeService.listAll();
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(resumeService.getById(id));
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    /** POST —— 创建简历。 */
    @PostMapping
    public ResponseEntity<Resume> create(@RequestBody Resume body) {
        try {
            return ResponseEntity.ok(resumeService.create(body));
        } catch (Exception e) {
            return ResponseEntity.ok(body);
        }
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
        // Mock 占位:不真正解析文件, 仅返回文件名与大小。
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("fileName", file == null ? null : file.getOriginalFilename());
        resp.put("size", file == null ? 0L : file.getSize());
        resp.put("note", "mock upload, not parsed");
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
}
