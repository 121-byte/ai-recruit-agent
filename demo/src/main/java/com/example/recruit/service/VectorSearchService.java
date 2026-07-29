package com.example.recruit.service;

import com.example.recruit.dal.entity.DocumentChunk;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.DocumentChunkMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.infra.retrieval.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量检索服务 (复刻对齐参考 §二-3)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>主路径改分块级召回: 先 countByParentType("resume") 判定, >0 走 searchByChunks</li>
 *   <li>searchByChunks: 调 searchByVector/searchByVectorWithFilter 得 List&lt;Map&gt; parent_id+dist
 *       → 提取 parent_id → resumeMapper.selectByIds 按相似度顺序装载</li>
 *   <li>无 chunk 或空降级 searchInMemory (全表 selectList + Java cosine + sort + limit)</li>
 *   <li>删除/废弃 searchChunks 公共方法 (并入 searchByChunks 私有)</li>
 * </ul>
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final ResumeMapper resumeMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingService embeddingService;

    public VectorSearchService(ResumeMapper resumeMapper,
                                DocumentChunkMapper documentChunkMapper,
                                EmbeddingService embeddingService) {
        this.resumeMapper = resumeMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.embeddingService = embeddingService;
    }

    /**
     * 候选人向量召回 (无方向过滤)。
     */
    public List<Resume> searchCandidates(float[] jobEmbedding, int topK) {
        return searchCandidates(jobEmbedding, topK, List.of());
    }

    /**
     * 候选人向量召回 + 方向预过滤。
     * 主路径: 先 countByParentType("resume") 判定, >0 走分块级召回, 否则降级内存计算。
     */
    public List<Resume> searchCandidates(float[] jobEmbedding, int topK, List<String> positionFilters) {
        if (jobEmbedding == null || jobEmbedding.length == 0) {
            return List.of();
        }
        String literal = FloatVectorTypeHandler.literal(jobEmbedding);
        try {
            // 先判定是否有分块数据
            long chunkCount = documentChunkMapper.countByParentType("resume");
            if (chunkCount > 0) {
                List<Resume> chunkResults = searchByChunks(literal, topK, positionFilters);
                if (!chunkResults.isEmpty()) {
                    return chunkResults;
                }
                // 分块检索空结果, 降级内存
                log.debug("chunk search empty, falling back to in-memory");
            }
            return searchInMemory(literal, topK, positionFilters);
        } catch (Exception e) {
            log.warn("searchCandidates failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 分块级召回: searchByVector/searchByVectorWithFilter → 提取 parent_id → selectByIds 装载。
     */
    private List<Resume> searchByChunks(String literal, int topK, List<String> positionFilters) {
        List<Map<String, Object>> rows;
        if (positionFilters == null || positionFilters.isEmpty()) {
            rows = documentChunkMapper.searchByVector(literal, "resume", topK);
        } else {
            String filtersCsv = positionFilters.stream()
                    .map(f -> "'" + f.replace("'", "''") + "'")
                    .collect(Collectors.joining(","));
            String filtersLike = positionFilters.stream()
                    .map(f -> "%" + f.replace("'", "''") + "%")
                    .collect(Collectors.joining("|"));
            String filtersRegex = positionFilters.stream()
                    .map(f -> f.replace("'", "''"))
                    .collect(Collectors.joining("|"));
            rows = documentChunkMapper.searchByVectorWithFilter(
                    literal, "resume", filtersCsv, filtersLike, filtersRegex, topK);
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 提取 parent_id 按相似度顺序 (dist 升序)
        List<Long> parentIds = new ArrayList<>();
        Map<Long, Double> distMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object pidObj = row.get("parent_id");
            if (pidObj == null) continue;
            Long pid = ((Number) pidObj).longValue();
            parentIds.add(pid);
            Object distObj = row.get("dist");
            distMap.put(pid, distObj != null ? ((Number) distObj).doubleValue() : 1.0);
        }
        if (parentIds.isEmpty()) {
            return List.of();
        }
        // 按 dist 升序排列 parentIds
        parentIds.sort(Comparator.comparingDouble(pid -> distMap.getOrDefault(pid, 1.0)));
        // 批量装载
        String idsSql = parentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Resume> resumes = resumeMapper.selectByIds(idsSql);
        if (resumes == null) {
            return List.of();
        }
        // 按相似度顺序重排
        Map<Long, Resume> resumeMap = new HashMap<>();
        for (Resume r : resumes) {
            if (r.getId() != null) {
                resumeMap.put(r.getId(), r);
            }
        }
        List<Resume> result = new ArrayList<>();
        for (Long pid : parentIds) {
            Resume r = resumeMap.get(pid);
            if (r != null) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * 降级: 无 chunk 时全表 selectList + Java cosine + sort + limit。
     */
    private List<Resume> searchInMemory(String literal, int topK, List<String> positionFilters) {
        try {
            List<Resume> all = resumeMapper.selectList(null);
            if (all == null || all.isEmpty()) {
                return List.of();
            }
            // 解析 literal 为 float[] 用于 Java cosine
            float[] queryVec = parseLiteral(literal);
            // 过滤 + 评分
            List<Resume> scored = new ArrayList<>();
            for (Resume r : all) {
                if (r.getEmbedding() == null) continue;
                if (positionFilters != null && !positionFilters.isEmpty()) {
                    boolean match = false;
                    for (String f : positionFilters) {
                        String ijpos = r.getParsedJson() != null
                                ? r.getParsedJson().path("intended_position").asText("") : "";
                        if (ijpos != null && ijpos.toLowerCase().contains(f.toLowerCase())) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) continue;
                }
                scored.add(r);
            }
            scored.sort((a, b) -> Double.compare(
                    FloatVectorTypeHandler.cosine(queryVec, b.getEmbedding()),
                    FloatVectorTypeHandler.cosine(queryVec, a.getEmbedding())));
            return scored.size() > topK ? new ArrayList<>(scored.subList(0, topK)) : scored;
        } catch (Exception e) {
            log.warn("searchInMemory failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 工具: 解析 pgvector literal "[v1,v2,...]" 为 float[]。 */
    private float[] parseLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return new float[0];
        }
        String s = literal.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return new float[0];
        String[] parts = s.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    /**
     * 工具方法: 两向量余弦相似度 (复用 {@link FloatVectorTypeHandler#cosine})。
     * <b>仅用于评分/比较, 不用于召回主路径</b>。
     */
    public double cosineSimilarity(float[] a, float[] b) {
        return FloatVectorTypeHandler.cosine(a, b);
    }
}
