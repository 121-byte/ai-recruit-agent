package com.example.recruit.infra.retrieval;

import com.example.recruit.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RerankServiceTest {

    @Test
    void rerankWithScoreReordersEvenWhenTopNEqualsDocumentCount() {
        AppProperties props = new AppProperties();
        props.getMock().setEnabled(true);
        RerankService service = new RerankService(props);

        List<RerankService.RerankResult> result = service.rerankWithScore(
                "Java Spring Redis",
                List.of("Python data analysis", "Java backend Spring Redis"),
                2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).index()).isEqualTo(1);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }
}
