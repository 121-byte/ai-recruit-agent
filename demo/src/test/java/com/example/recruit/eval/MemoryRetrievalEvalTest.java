package com.example.recruit.eval;

import com.example.recruit.config.AppProperties;
import com.example.recruit.memory.HybridMemoryRetriever;
import com.example.recruit.memory.HybridMemoryRetriever.ScoredMemory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 记忆检索离线评估脚本（实验脚本，非框架）。
 *
 * <p>读取 {@code classpath:eval/memory-cases.json}，对每条查询调用
 * {@link HybridMemoryRetriever#retrieveReadOnly(String, String)}，取返回的有序记忆列表，
 * 与标注的相关 memory_key 集合对比，计算：
 * <ul>
 *   <li>Precision@5（Top-5 中相关比例，考虑位置不折价，保持简单）</li>
 *   <li>Recall@5（相关记忆被检索到 Top-5 的比例）</li>
 *   <li>三路 source 占比（vector / keyword / graph 在结果中的分布）</li>
 * </ul>
 * 跨查询取宏平均。
 *
 * <p><b>不</b>做 Context Precision/Recall/Faithfulness 等 LLM-as-a-Judge 指标——
 * 用"标注相关 memory_key → Precision/Recall@K"替代，纯确定性。
 *
 * <p><b>运行前提</b>：{@code app.mock.enabled=false} + 真实 embedding/rerank key
 * + 远程 PG/Redis。mock 模式下结果无参考价值。
 *
 * <p>运行：{@code mvn test -Dtest=MemoryRetrievalEvalTest}，输出 {@code demo/eval-reports/memory-<日期>.md}。
 * 查询数 < 5 时仅出报告不算门禁、测试仍 PASS。
 *
 * <p><b>数据准备</b>：
 * <ol>
 *   <li>确保 memory_entry 表有数据（通过对话积累，agentId 格式为 {@code hr:{userId}}）</li>
 *   <li>查询可用记忆：{@code SELECT agent_id, memory_key, substr(memory_value,1,40) FROM memory_entry WHERE agent_id = 'hr:1' LIMIT 20}</li>
 *   <li>填充 memory-cases.json：设置 agentId、query、relevantKeys（相关记忆 key 列表）</li>
 * </ol>
 */
@SpringBootTest
class MemoryRetrievalEvalTest {

    @Autowired
    private HybridMemoryRetriever retriever;
    @Autowired
    private AppProperties appProperties;

    private final ObjectMapper mapper = new ObjectMapper();

    record MemoryCase(@JsonProperty("agentId") String agentId,
                      @JsonProperty("query") String query,
                      @JsonProperty("relevantKeys") List<String> relevantKeys,
                      @JsonProperty("relevantIds") List<Long> relevantIds) {
    }

    @Test
    void eval() throws Exception {
        List<MemoryCase> cases = mapper.readValue(
                getClass().getResourceAsStream("/eval/memory-cases.json"),
                new TypeReference<>() {});

        List<Double> pAt5 = new ArrayList<>(), rAt5 = new ArrayList<>();
        Map<String, int[]> sourceTotals = new LinkedHashMap<>();
        sourceTotals.put("vector", new int[]{0});
        sourceTotals.put("keyword", new int[]{0});
        sourceTotals.put("graph", new int[]{0});
        List<String[]> perQuery = new ArrayList<>();

        for (MemoryCase c : cases) {
            Set<String> relevantKeys = c.relevantKeys() == null ? Set.of() : Set.copyOf(c.relevantKeys());
            Set<Long> relevantIds = c.relevantIds() == null ? Set.of() : Set.copyOf(c.relevantIds());
            boolean useKeyLabels = !relevantKeys.isEmpty();
            List<ScoredMemory> result;
            try {
                result = retriever.retrieveReadOnly(c.agentId(), c.query());
            } catch (Exception e) {
                perQuery.add(new String[]{c.query(), c.agentId(), "0", "0", "(异常: " + e.getMessage() + ")"});
                pAt5.add(0.0);
                rAt5.add(0.0);
                continue;
            }
            if (result == null) result = List.of();

            // source 占比（按全量返回结果统计; 多路命中按 + 拆分, 各路各计一次, 反映真实召回贡献）
            for (ScoredMemory sm : result) {
                String src = sm.source == null ? "unknown" : sm.source;
                for (String part : src.split("\\+")) {
                    sourceTotals.computeIfAbsent(part, k -> new int[]{0})[0]++;
                }
            }

            List<Long> rankedIds = new ArrayList<>();
            List<String> rankedKeys = new ArrayList<>();
            for (ScoredMemory sm : result) {
                if (sm.entry != null && sm.entry.getId() != null) {
                    rankedIds.add(sm.entry.getId());
                }
                if (sm.entry != null && sm.entry.getMemoryKey() != null) {
                    rankedKeys.add(sm.entry.getMemoryKey());
                }
            }

            int topK = Math.min(5, useKeyLabels ? rankedKeys.size() : rankedIds.size());
            int relInTop = 0;
            for (int i = 0; i < topK; i++) {
                if (useKeyLabels && relevantKeys.contains(rankedKeys.get(i))) {
                    relInTop++;
                } else if (!useKeyLabels && relevantIds.contains(rankedIds.get(i))) {
                    relInTop++;
                }
            }
            double p = topK > 0 ? (double) relInTop / topK : 0;
            int relevantSize = useKeyLabels ? relevantKeys.size() : relevantIds.size();
            double r = relevantSize > 0 ? (double) relInTop / relevantSize : 0;
            pAt5.add(p);
            rAt5.add(r);
            perQuery.add(new String[]{
                    c.query(), c.agentId(),
                    String.format(Locale.ROOT, "%.4f", p),
                    String.format(Locale.ROOT, "%.4f", r),
                    "size=" + rankedIds.size() + ", labels=" + (useKeyLabels ? "key" : "id")
                            + ", topKeys=" + String.join("/", rankedKeys.stream().limit(5).toList())
            });
        }

        String report = renderReport(cases.size(), avg(pAt5), avg(rAt5), sourceTotals, perQuery);
        System.out.print(report);

        Path dir = Path.of("eval-reports");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("memory-" + LocalDate.now() + ".md"), report);
    }

    static double avg(List<Double> xs) {
        return xs.isEmpty() ? 0 : xs.stream().mapToDouble(d -> d).average().orElse(0);
    }

    private String renderReport(int queryCount, double pAt5, double rAt5,
                                Map<String, int[]> sourceTotals, List<String[]> perQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 记忆检索评估报告\n\n查询数: ").append(queryCount)
                .append("（跨查询宏平均）\n\n");
        sb.append("| 指标 | 值 |\n|---|---|\n");
        sb.append("| **Precision@5** | ").append(fmt(pAt5)).append(" |\n");
        sb.append("| Recall@5 | ").append(fmt(rAt5)).append(" |\n\n");
        sb.append("## 参数\n\n");
        sb.append("| 参数 | 值 |\n|---|---|\n");
        AppProperties.Memory memory = appProperties.getMemory();
        sb.append("| vectorMinSimilarity | ").append(fmt(memory.getVectorMinSimilarity())).append(" |\n");
        sb.append("| finalMinDirectMatchScore | ").append(fmt(memory.getFinalMinDirectMatchScore())).append(" |\n\n");

        int total = sourceTotals.values().stream().mapToInt(a -> a[0]).sum();
        sb.append("## 三路 source 占比（多路命中按路径各计一次; 占比 = 该路命中 / 全部路径命中总数）\n\n");
        sb.append("| source | 命中条数 | 占比 |\n|---|---|---|\n");
        for (Map.Entry<String, int[]> e : sourceTotals.entrySet()) {
            int cnt = e.getValue()[0];
            double ratio = total > 0 ? (double) cnt / total : 0;
            sb.append("| ").append(e.getKey()).append(" | ").append(cnt)
                    .append(" | ").append(fmt(ratio)).append(" |\n");
        }
        sb.append("| 合计(路径命中次数) | ").append(total).append(" | 1.0000 |\n\n");

        sb.append("## 逐查询结果\n\n");
        sb.append("| 查询 | agentId | Precision@5 | Recall@5 | 备注 |\n|---|---|---|---|---|\n");
        for (String[] r : perQuery) {
            sb.append("| ").append(esc(r[0])).append(" | ").append(r[1])
                    .append(" | ").append(r[2]).append(" | ").append(r[3])
                    .append(" | ").append(r[4]).append(" |\n");
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
