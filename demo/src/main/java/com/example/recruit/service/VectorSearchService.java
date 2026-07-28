package com.example.recruit.service;

import com.example.recruit.dal.entity.DocumentChunk;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.DocumentChunkMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量检索服务 (复刻对齐清单 §6.3 + P0 VectorSearchService)。
 *
 * <p>封装 pgvector 向量召回：简历/分块的语义召回统一走原生 SQL（`<=>` HNSW 余弦），
 * <b>MUST NOT</b> 在 Java 端对全表算 cosine 召回（cosineSimilarity 仅作工具，不用于召回主路径）。
 *
 * <p>调用方传 float[] 向量，本服务内部转 {@link FloatVectorTypeHandler#literal(float[])}
 * 字面量字符串交由 Mapper 的 `?::vector` 绑定。
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
     *
     * @param jobEmbedding 岗位/查询向量 (1024 维)
     * @param topK         召回数量
     */
    public List<Resume> searchCandidates(float[] jobEmbedding, int topK) {
        return searchCandidates(jobEmbedding, topK, List.of());
    }

    /**
     * 候选人向量召回 + 方向预过滤。filters 为空或过滤后为空时回退无过滤召回。
     */
    public List<Resume> searchCandidates(float[] jobEmbedding, int topK, List<String> positionFilters) {
        if (jobEmbedding == null || jobEmbedding.length == 0) {
            return List.of();
        }
        String literal = FloatVectorTypeHandler.literal(jobEmbedding);
        try {
            if (positionFilters == null || positionFilters.isEmpty()) {
                return resumeMapper.searchByVector(literal, topK);
            }
            // 过滤词: 构建 'a','b' 形式 (CSV) 与正则 (Java|后端)
            String filtersCsv = positionFilters.stream()
                    .map(f -> "'" + f.replace("'", "''") + "'")
                    .collect(Collectors.joining(","));
            String filtersRegex = positionFilters.stream()
                    .map(f -> f.replace("'", "''"))
                    .collect(Collectors.joining("|"));
            List<Resume> filtered = resumeMapper.searchByVectorWithFilter(literal, filtersCsv, filtersRegex, topK);
            if (filtered == null || filtered.isEmpty()) {
                // 方向过滤无结果, 回退无过滤
                return resumeMapper.searchByVector(literal, topK);
            }
            return new ArrayList<>(filtered);
        } catch (Exception e) {
            log.warn("searchCandidates failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 文档分块向量召回 (按 parentType)。
     */
    public List<DocumentChunk> searchChunks(float[] queryEmbedding, String parentType, int topK) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        try {
            return documentChunkMapper.searchByVector(
                    FloatVectorTypeHandler.literal(queryEmbedding), parentType, topK);
        } catch (Exception e) {
            log.warn("searchChunks failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 工具方法: 两向量余弦相似度 (复用 {@link FloatVectorTypeHandler#cosine})。
     * <b>仅用于评分/比较, 不用于召回主路径</b>。
     */
    public double cosineSimilarity(float[] a, float[] b) {
        return FloatVectorTypeHandler.cosine(a, b);
    }
}
