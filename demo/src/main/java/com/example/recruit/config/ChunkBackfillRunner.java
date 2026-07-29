package com.example.recruit.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.DocumentChunk;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.DocumentChunkMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.infra.retrieval.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 文档分块回填 (复刻自文档 §6.4 ChunkBackfillRunner)。
 *
 * <p>启动时检查并补全已有简历/岗位的 document_chunk 分块数据。
 * Mock 模式或表不存在时静默跳过。
 */
@Configuration
public class ChunkBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ChunkBackfillRunner.class);

    @Bean
    public ApplicationRunner chunkBackfill(ResumeMapper resumeMapper,
                                            JobProfileMapper jobMapper,
                                            DocumentChunkMapper chunkMapper,
                                            EmbeddingService embeddingService) {
        return args -> {
            try {
                List<Resume> resumes = resumeMapper.selectList(null);
                int backfilled = 0;
                for (Resume r : resumes) {
                    Long count = chunkMapper.selectCount(
                            new LambdaQueryWrapper<DocumentChunk>()
                                    .eq(DocumentChunk::getParentType, "resume")
                                    .eq(DocumentChunk::getParentId, r.getId()));
                    if (count == null || count == 0) {
                        // 简略回填一个 summary chunk
                        DocumentChunk c = new DocumentChunk();
                        c.setParentType("resume");
                        c.setParentId(r.getId());
                        c.setChunkIndex(0);
                        c.setChunkType("summary");
                        c.setContent(r.getRawText() == null ? "" :
                                r.getRawText().substring(0, Math.min(500, r.getRawText().length())));
                        try {
                            c.setEmbedding(embeddingService.embed(c.getContent()));
                        } catch (Throwable ignored) {
                        }
                        chunkMapper.insert(c);
                        backfilled++;
                    }
                }
                if (backfilled > 0) {
                    log.info("ChunkBackfill: backfilled {} resume chunks", backfilled);
                }
            } catch (Exception e) {
                log.debug("ChunkBackfill skipped (table unavailable?): {}", e.getMessage());
            }
        };
    }
}
