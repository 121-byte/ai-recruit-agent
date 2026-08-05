package com.example.recruit.eval;

import com.example.recruit.agent.routing.IntentRouter;
import com.example.recruit.agent.routing.IntentRouter.IntentWithUsage;
import com.example.recruit.agent.routing.IntentType;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 意图路由离线评估脚本（实验脚本，非框架）。
 *
 * <p>读取 {@code classpath:eval/intent-cases.json}, 按 expected 类切分: 每类前 15 条 = 训练集 (排除, 仅供调参参考),
 * 后 10 条 = 留出测试集 (held-out, 唯一测量对象)。本脚本只在 held-out 60 条上跑指标。
 *
 * <p><b>反过拟合纪律</b>: 调参只看训练集错例, held-out 只跑一次报数, 严禁对着 held-out 迭代正则/阈值,
 * 否则即训练集泄漏 (曲线拟合 eval, 对新输入无泛化)。HITL 召回率单独列: 招聘场景漏判高危操作代价非对称。
 *
 * <p>计算：Accuracy / Macro-F1 / 6×6 混淆矩阵 / HITL 召回率 / LLM 调用比例。
 *
 * <p><b>运行前提</b>：要测真实效果须 {@code app.mock.enabled=false} + 真实 DeepSeek/Embedding key
 * 并连远程 PG/Redis。mock 模式下量的是桩行为，无参考价值。
 *
 * <p>运行：{@code mvn test -Dtest=IntentEvalTest}，输出控制台表格 + {@code demo/eval-reports/intent-<日期>.md}。
 * 样本数 < 10 时仅出报告不算门禁、测试仍 PASS。
 */
@SpringBootTest
class IntentEvalTest {

    /** 每类留出测试条数 (其余进训练集, 不参与测量)。 */
    private static final int HELD_OUT_PER_CLASS = 10;

    @Autowired
    private IntentRouter intentRouter;

    private final ObjectMapper mapper = new ObjectMapper();

    record IntentCase(@JsonProperty("input") String input,
                      @JsonProperty("expected") String expected) {
    }

    @Test
    void eval() throws Exception {
        List<IntentCase> all = mapper.readValue(
                getClass().getResourceAsStream("/eval/intent-cases.json"),
                new TypeReference<>() {});
        // 按类切分: 每类后 HELD_OUT_PER_CLASS 条作 held-out 测量, 其余训练集排除
        Map<String, List<IntentCase>> byClass = new java.util.LinkedHashMap<>();
        for (IntentCase c : all) {
            byClass.computeIfAbsent(c.expected(), k -> new ArrayList<>()).add(c);
        }
        List<IntentCase> cases = new ArrayList<>();
        int trainTotal = 0;
        for (List<IntentCase> group : byClass.values()) {
            int splitAt = Math.max(0, group.size() - HELD_OUT_PER_CLASS);
            trainTotal += splitAt;
            cases.addAll(group.subList(splitAt, group.size()));
        }
        IntentType[] types = IntentType.values();
        int n = types.length;
        int[][] confusion = new int[n][n]; // [expected][predicted]
        int correct = 0, llmCalls = 0, errors = 0, total = cases.size();
        int[] expectedCount = new int[n];
        int[] predictedCount = new int[n];

        List<String[]> perCase = new ArrayList<>(); // input, expected, predicted, tokens, ok

        for (IntentCase c : cases) {
            int expIdx = indexOf(types, c.expected());
            if (expIdx < 0) {
                perCase.add(new String[]{c.input(), c.expected(), "(BAD_LABEL)", "0", "BAD"});
                continue;
            }
            expectedCount[expIdx]++;
            String predicted;
            int tokens;
            try {
                IntentWithUsage r = intentRouter.classifyWithUsage(c.input());
                IntentType pt = r.intent().type();
                predicted = pt.name();
                tokens = r.inputTokens() + r.outputTokens();
            } catch (Exception e) {
                predicted = "(ERROR)";
                tokens = 0;
                errors++;
            }
            int predIdx = indexOf(types, predicted);
            if (predIdx >= 0) {
                confusion[expIdx][predIdx]++;
                predictedCount[predIdx]++;
                if (predIdx == expIdx) correct++;
            } else {
                // 预测为 ERROR/未知 → 计入该类 FN（已在 expectedCount，不在 confusion）
                errors++;
            }
            if (tokens > 0) llmCalls++;
            perCase.add(new String[]{c.input(), c.expected(), predicted, String.valueOf(tokens),
                    (predIdx == expIdx) ? "OK" : "MISS"});
        }

        // 指标
        double accuracy = total > 0 ? (double) correct / total : 0;
        double[] precision = new double[n], recall = new double[n], f1 = new double[n];
        for (int i = 0; i < n; i++) {
            int tp = confusion[i][i];
            int fp = 0, fn = 0;
            for (int j = 0; j < n; j++) {
                if (j != i) fp += confusion[j][i]; // 预测为 i 但期望是别的
                if (j != i) fn += confusion[i][j]; // 期望 i 但预测成别的
            }
            // ERROR 样本也属期望类的 FN
            fn += expectedCount[i] - rowSum(confusion[i]);
            precision[i] = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
            recall[i] = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
            f1[i] = (precision[i] + recall[i]) > 0
                    ? 2 * precision[i] * recall[i] / (precision[i] + recall[i]) : 0;
        }
        double macroF1 = Arrays.stream(f1).average().orElse(0);
        int hitlIdx = indexOf(types, "HITL");
        double hitlRecall = recall[hitlIdx];
        double llmRatio = total > 0 ? (double) llmCalls / total : 0;

        String report = renderReport(types, confusion, expectedCount, predictedCount,
                precision, recall, f1, accuracy, macroF1, hitlRecall, llmRatio,
                total, correct, llmCalls, errors, trainTotal, perCase);

        System.out.print(report);

        Path dir = Path.of("eval-reports");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("intent-" + LocalDate.now() + ".md"), report);
    }

    private static int indexOf(IntentType[] types, String name) {
        if (name == null) return -1;
        for (int i = 0; i < types.length; i++) if (types[i].name().equals(name)) return i;
        return -1;
    }

    private static int rowSum(int[] row) {
        int s = 0;
        for (int v : row) s += v;
        return s;
    }

    private static String renderReport(IntentType[] types, int[][] confusion, int[] expectedCount,
                                       int[] predictedCount, double[] precision, double[] recall,
                                       double[] f1, double accuracy, double macroF1,
                                       double hitlRecall, double llmRatio, int total,
                                       int correct, int llmCalls, int errors, int trainTotal,
                                       List<String[]> perCase) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 意图路由评估报告 (held-out)\n\n")
                .append("**held-out 测试集: ").append(total).append(" 条** | 训练集排除: ")
                .append(trainTotal).append(" 条 (调参只看训练集, held-out 只跑一次报数, 严禁对着 held-out 迭代)\n\n")
                .append("held-out 正确: ").append(correct)
                .append(" | LLM 调用: ").append(llmCalls)
                .append(" | 异常: ").append(errors).append("\n\n");
        sb.append("| 指标 | 值 |\n|---|---|\n");
        sb.append("| Accuracy | ").append(fmt(accuracy)).append(" |\n");
        sb.append("| Macro-F1 | ").append(fmt(macroF1)).append(" |\n");
        sb.append("| **HITL 召回率** | ").append(fmt(hitlRecall)).append(" |\n");
        sb.append("| LLM 调用比例 | ").append(fmt(llmRatio)).append(" |\n\n");

        sb.append("## Per-class 指标\n\n");
        sb.append("| 类别 | 样本数 | Precision | Recall | F1 |\n|---|---|---|---|---|\n");
        for (int i = 0; i < types.length; i++) {
            sb.append("| ").append(types[i]).append(" | ").append(expectedCount[i])
                    .append(" | ").append(fmt(precision[i]))
                    .append(" | ").append(fmt(recall[i]))
                    .append(" | ").append(fmt(f1[i])).append(" |\n");
        }

        sb.append("\n## 混淆矩阵 (行=期望, 列=预测)\n\n");
        sb.append("| 期望 \\ 预测 |");
        for (IntentType t : types) sb.append(" ").append(t).append(" |");
        sb.append("\n|");
        for (int i = 0; i <= types.length; i++) sb.append("---|");
        sb.append("\n");
        for (int i = 0; i < types.length; i++) {
            sb.append("| ").append(types[i]).append(" |");
            for (int j = 0; j < types.length; j++) sb.append(" ").append(confusion[i][j]).append(" |");
            sb.append("\n");
        }

        sb.append("\n## 逐条结果\n\n");
        sb.append("| 输入 | 期望 | 预测 | tokens | 结果 |\n|---|---|---|---|---|\n");
        for (String[] r : perCase) {
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
