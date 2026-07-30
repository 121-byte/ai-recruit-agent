package com.example.recruit.module.resume.application;

import com.example.recruit.common.PositionCategories;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.example.recruit.module.resume.domain.ComparisonResult;
import com.example.recruit.module.resume.domain.ImplicitInsights;
import com.example.recruit.module.resume.domain.PotentialAssessment;
import com.example.recruit.module.resume.domain.ResumeAnalysisResult;
import com.example.recruit.module.resume.domain.RiskAssessment;
import com.example.recruit.module.resume.domain.StructuredData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * 简历分析业务服务 (4+1 轮链式深度解析)。
 *
 * <p>轮次: round1 结构化 → 回写独立列 → round2 隐性 → round3 风险 → round4 潜力 → 自校验。
 * 各轮独立调 LLM, 某轮失败记 warn 不阻断。embedding + 语义分块由 AnalysisTaskConsumer 负责。
 */
@Service
public class ResumeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeepSeekModelService deepSeek;
    private final ResumeMapper resumeMapper;

    public ResumeAnalysisService(DeepSeekModelService deepSeek, ResumeMapper resumeMapper) {
        this.deepSeek = deepSeek;
        this.resumeMapper = resumeMapper;
    }

    @Transactional
    public ResumeAnalysisResult analyzeFull(Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new IllegalArgumentException("Resume not found: " + resumeId);
        }
        String rawText = resume.getRawText();
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalStateException("Resume raw text is empty: " + resumeId);
        }

        // 第1轮: 结构化提取, 并用 LLM 结果覆盖 resume 独立列(正则导入值不准, 以 LLM 为准)
        log.info("Round 1: Structural extraction for resume {}", resumeId);
        StructuredData structured = round1StructuralExtraction(rawText, resumeId);
        overwriteResumeFromStructured(resume, structured);

        // 第2轮: 隐性挖掘
        log.info("Round 2: Implicit mining for resume {}", resumeId);
        ImplicitInsights implicit = round2ImplicitMining(rawText, structured, resumeId);

        // 第3轮: 风险识别
        log.info("Round 3: Risk identification for resume {}", resumeId);
        RiskAssessment risk = round3RiskIdentification(rawText, structured, resumeId);

        // 第4轮: 潜力评估
        log.info("Round 4: Potential assessment for resume {}", resumeId);
        PotentialAssessment potential = round4PotentialAssessment(rawText, structured, implicit, resumeId);

        // 第5轮: 自校验
        String validation = selfValidate(structured, implicit, risk, potential);

        ResumeAnalysisResult result = new ResumeAnalysisResult();
        result.setResumeId(resumeId);
        result.setStructuredData(structured);
        result.setImplicitInsights(implicit);
        result.setRiskAssessment(risk);
        result.setPotentialAssessment(potential);
        result.setValidation(validation);
        result.setAnalyzedAt(new Date());

        // 校验: 若所有子结果含 _error, 记 error
        try {
            JsonNode resultNode = JsonGuard.parseAndValidate(result.toJsonNode().toString());
            boolean anyOk = false;
            for (Iterator<String> it = resultNode.fieldNames(); it.hasNext(); ) {
                JsonNode child = resultNode.get(it.next());
                if (child != null && child.isObject() && child.has("_error")) {
                    log.warn("Round field contains error: {}", child.get("_error").asText());
                } else {
                    anyOk = true;
                }
            }
            if (!anyOk) {
                log.error("All analysis rounds failed for resume {}", resumeId);
            }
        } catch (Exception e) {
            log.warn("validate result failed: {}", e.getMessage());
        }

        // 写回 resume (parsedJson 为 JsonNode)
        try {
            resume.setParsedJson(MAPPER.readTree(result.toJsonNode().toString()));
        } catch (Exception e) {
            log.warn("Failed to set parsedJson: {}", e.getMessage());
        }
        resume.setStatus("analyzed");
        resumeMapper.updateById(resume);

        log.info("Resume analysis completed: resumeId={}", resumeId);
        return result;
    }

    private StructuredData round1StructuralExtraction(String rawText, Long resumeId) {
        String sys = """
                你是一个专业的简历解析助手。请从以下简历文本中提取结构化信息，以JSON格式返回。
                需要提取的字段:
                - basicInfo: { name, phone, email, education(最高学历), school, major, graduationYear(毕业年份), workYears(工作年限,整数), intendedPosition(具体意向岗位/求职目标,如"Java工程师"), intendedPositionCategory(意向岗位所属类别) }
                intendedPositionCategory 必须从以下固定类别中选一个返回，不要自行编造: """ + PositionCategories.CATEGORY_PROMPT + """
                例如: Java工程师→技术, UI设计→设计, 招聘专员→人事, 销售代表→销售。
                - skills: [ { name, level(熟练度1-5), years } ]
                - workExperience: [ { company, title, period(时间段), responsibilities[], achievements[] } ]
                - projects: [ { name, role, techStack[], description, highlights[] } ]
                - education: [ { school, degree, major, startYear, endYear } ]
                - certifications: [ { name, issuer, year } ]
                请确保提取准确，不要编造信息。如果某个字段无法从简历中提取，请设为null。
                请直接返回JSON，不要包含其他解释文本。""";
        String response = deepSeek.chatJson(sys, rawText);
        JsonNode validated = JsonGuard.parseAndValidate(response);
        if (JsonGuard.hasError(validated)) {
            log.warn("Round 1: LLM returned invalid JSON for resume {}", resumeId);
        }
        return StructuredData.fromJson(validated.toString());
    }

    private ImplicitInsights round2ImplicitMining(String rawText, StructuredData structured, Long resumeId) {
        String sys = """
                你是一个资深招聘顾问。请基于以下简历内容进行隐性能力挖掘，以JSON格式返回。
                需要分析的维度:
                - projectDepth: 项目深度评分1-10，及依据（技术复杂度、业务影响力）
                - leadership: 领导力体现（团队规模、决策角色、指导他人）
                - crossTeamCollaboration: 跨团队协作能力体现
                - problemSolving: 问题解决能力证据
                - careerProgression: 职业发展轨迹分析（是否持续成长、跳槽合理性）
                - learningAbility: 学习能力体现（新技术采用、跨领域发展）
                - communicationSkill: 沟通表达能力（基于简历描述细节）
                输出格式（必须严格遵守）：每个维度统一为对象 {"score": 1-10整数, "reasoning": "依据文字"}；键名用上述英文键名, 值内文字用中文; 不要出现纯字符串值。
                请直接返回JSON。""";
        String user = "简历结构化数据:\n" + structured.toJson() + "\n\n原始简历:\n" + rawText;
        String response = deepSeek.chatJson(sys, user);
        JsonNode validated = JsonGuard.parseAndValidate(response);
        if (JsonGuard.hasError(validated)) {
            log.warn("Round 2: LLM returned invalid JSON for resume {}", resumeId);
        }
        return ImplicitInsights.fromJson(validated.toString());
    }

    private RiskAssessment round3RiskIdentification(String rawText, StructuredData structured, Long resumeId) {
        String sys = """
                你是一个招聘风险分析师。请基于以下简历内容识别潜在风险，以JSON格式返回。
                需要检查的风险点:
                - employmentGaps: 就业空窗期（时间段、时长、解释）
                - exaggerationRisks: 夸大风险（描述与实际不符、模糊表述、用词夸张）
                - jobHopping: 频繁跳槽评估（近3年平均在职时长、行业平均水平对比）
                - skillInflation: 技能膨胀（声称精通大量技术但无实际项目佐证）
                - educationRisks: 教育背景风险（学历断层、学校真实性疑点）
                - stabilityRisk: 稳定性风险评估（综合评分1-10）
                - overallRiskLevel: 总体风险等级（LOW/MEDIUM/HIGH）
                输出格式（必须严格遵守）：exaggerationRisks/jobHopping/skillInflation/educationRisks/stabilityRisk 统一为 {"level": "低/中/高", "reasoning": "依据"}; employmentGaps 为 {"gaps": ["时间段..."], "reasoning": "解释"}; overallRiskLevel 为 "LOW"/"MEDIUM"/"HIGH"; 键名用上述英文键名, 值内文字用中文。
                请直接返回JSON。""";
        String user = "简历结构化数据:\n" + structured.toJson() + "\n\n原始简历:\n" + rawText;
        String response = deepSeek.chatJson(sys, user);
        JsonNode validated = JsonGuard.parseAndValidate(response);
        if (JsonGuard.hasError(validated)) {
            log.warn("Round 3: LLM returned invalid JSON for resume {}", resumeId);
        }
        return RiskAssessment.fromJson(validated.toString());
    }

    private PotentialAssessment round4PotentialAssessment(
            String rawText, StructuredData structured, ImplicitInsights implicit, Long resumeId) {
        String sys = """
                你是一个人才评估专家。请基于以下信息评估候选人的成长潜力和文化匹配度，以JSON格式返回。
                评估维度:
                - growthPotential: 成长潜力评分1-10，及依据（学习曲线、行业趋势匹配、复合能力）
                - cultureFit: 文化匹配度（参照：创新、协作、结果导向、持续学习）
                - careerStage: 职业阶段评估（起步期/成长期/成熟期/转型期）
                - recommendedRoles: 最适合的岗位类型（技术专家/架构师/技术管理/项目管理）
                - developmentSuggestions: 发展建议（提升方向、培训建议）
                - overallRating: 综合评级（S/A/B/C）
                输出格式（必须严格遵守）：growthPotential/cultureFit 统一为 {"score": 1-10整数, "reasoning": "依据"}; careerStage 为文字字符串; recommendedRoles/developmentSuggestions 为字符串数组; overallRating 为 "S"/"A"/"B"/"C"; 键名用上述英文键名, 值内文字用中文。
                请直接返回JSON。""";
        String user = "结构化数据:\n" + structured.toJson() + "\n\n隐性能力:\n" + implicit.toJson()
                + "\n\n原始简历:\n" + rawText;
        String response = deepSeek.chatJson(sys, user);
        JsonNode validated = JsonGuard.parseAndValidate(response);
        if (JsonGuard.hasError(validated)) {
            log.warn("Round 4: LLM returned invalid JSON for resume {}", resumeId);
        }
        return PotentialAssessment.fromJson(validated.toString());
    }

    private String selfValidate(StructuredData structured, ImplicitInsights implicit,
                                 RiskAssessment risk, PotentialAssessment potential) {
        String sys = """
                请校验以下简历解析结果的完整性和准确性，给出校验结论。
                检查点:
                1. 结构化数据是否完整（无关键字段缺失）
                2. 隐性挖掘是否有依据（不是凭空推测）
                3. 风险评估是否合理（是否有过度解读）
                4. 潜力评估是否客观（是否与原始数据一致）
                请给出校验结论：PASS（通过）/ WARN（警告）/ FAIL（失败），并说明理由。""";
        String user = "结构化数据:\n" + structured.toJson() + "\n\n隐性挖掘:\n" + implicit.toJson()
                + "\n\n风险评估:\n" + risk.toJson() + "\n\n潜力评估:\n" + potential.toJson();
        return deepSeek.chat(sys, user);
    }

    /**
     * 用第1轮 LLM 结构化结果覆盖 resume 表的独立列。
     *
     * <p>导入时 QuickInfoExtractor 用正则已填过这些列, 但正则对多栏排版/无标签简历易出错
     * (尤其姓名第一行兜底、意向岗位、年限推算), 故以 LLM 结构化结果覆盖更准的值。
     * 安全护栏: LLM 未给出(空/null)的列保留正则原值, 不清空。
     */
    private void overwriteResumeFromStructured(Resume resume, StructuredData structured) {
        try {
            JsonNode structuredNode = structured.toJsonNode();
            JsonNode basicInfo = structuredNode.get("basicInfo");
            if (basicInfo == null || !basicInfo.isObject()) {
                log.warn("No basicInfo found in structured data for resume {}", resume.getId());
                return;
            }
            int overwritten = 0;

            String name = nonBlankText(basicInfo, "name");
            if (name != null) { resume.setCandidateName(name); overwritten++; }

            String phone = nonBlankText(basicInfo, "phone");
            if (phone != null) { resume.setPhone(phone); overwritten++; }

            String email = nonBlankText(basicInfo, "email");
            if (email != null) { resume.setEmail(email); overwritten++; }

            String education = nonBlankText(basicInfo, "education");
            if (education != null) { resume.setEducation(education); overwritten++; }

            String school = nonBlankText(basicInfo, "school");
            if (school != null) { resume.setSchool(school); overwritten++; }

            String major = nonBlankText(basicInfo, "major");
            if (major != null) { resume.setMajor(major); overwritten++; }

            String intendedPosition = nonBlankText(basicInfo, "intendedPosition");
            // 意向岗位列存"类别"(技术/人事/...), 用于分类筛选; 具体岗位名保留在 structuredData.basicInfo.intendedPosition
            String category = nonBlankText(basicInfo, "intendedPositionCategory");
            if (category != null) {
                resume.setIntendedPosition(PositionCategories.normalize(category));
                overwritten++;
            } else if (intendedPosition != null) {
                // LLM 未给类别时, 兜底用具体岗位名, 至少不空
                resume.setIntendedPosition(intendedPosition);
                overwritten++;
            }

            // 工作年限: 优先 LLM 直接给的 workYears/work_years; 否则由 graduationYear 推算
            Integer years = nonNullInt(basicInfo, "workYears", "work_years");
            if (years == null) {
                Integer gradYear = nonNullInt(basicInfo, "graduationYear");
                if (gradYear != null) {
                    int currentYear = java.time.Year.now().getValue();
                    years = Math.max(0, currentYear - gradYear);
                }
            }
            if (years != null) { resume.setYearsExperience(years); overwritten++; }

            if (overwritten > 0) {
                log.info("Overwritten {} fields from structured data for resume {}", overwritten, resume.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to overwrite resume from structured data: {}", e.getMessage());
        }
    }

    /** 取 JsonNode 标量字段, 返回去空白后的非空文本, 否则 null。 */
    private String nonBlankText(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        String s = n.isTextual() ? n.asText() : n.toString();
        s = s.trim();
        // 去掉 JSON 字符串外层引号
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.isEmpty() ? null : s;
    }

    /** 取多个候选字段中第一个可解析为整数的值, 否则 null。 */
    private Integer nonNullInt(JsonNode parent, String... fields) {
        for (String f : fields) {
            if (parent == null) continue;
            JsonNode n = parent.get(f);
            if (n == null || n.isNull() || n.isMissingNode()) continue;
            try {
                if (n.isNumber()) return n.asInt();
                String s = n.isTextual() ? n.asText().trim() : n.toString();
                s = s.replaceAll("[^0-9-]", "");
                if (s.isEmpty() || s.equals("-")) return null;
                return Integer.parseInt(s);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public ComparisonResult compareResumes(List<Long> resumeIds) {
        List<Resume> resumes = new ArrayList<>();
        for (Long id : resumeIds) {
            try {
                Resume r = resumeMapper.selectById(id);
                if (r != null) {
                    resumes.add(r);
                }
            } catch (Exception e) {
                log.warn("load resume {} failed: {}", id, e.getMessage());
            }
        }
        if (resumes.isEmpty()) {
            throw new IllegalArgumentException("No resumes found for ids: " + resumeIds);
        }

        // 前置检查: 确保所有简历已解析
        for (Resume r : resumes) {
            if (r.getParsedJson() == null) {
                analyzeFull(r.getId());
            }
        }

        StringBuilder sys = new StringBuilder();
        sys.append("""
                你是一位资深的技术招聘专家。请对比以下候选人的简历信息，从多个维度进行详细横向对比分析。
                请严格按照以下JSON格式返回结果，不要包含任何其他解释文本，直接返回JSON:
                {
                  "dimensions": [ { "name": "技术能力", "candidates": [ { "name": "候选人姓名", "score": 8, "level": "A", "summary": "一句话概括", "details": "详细分析" } ] } ],
                  "ranking": [ { "rank": 1, "name": "候选人姓名", "totalScore": 42, "level": "S", "reason": "排名理由" } ],
                  "analysis": { "候选人姓名": { "strengths": ["..."], "weaknesses": ["..."], "suggestion": "录用建议" } }
                }
                要求: 维度包括 技术能力/项目经验/教育背景/工作稳定性/成长潜力 共5个; score 1-10; level S(9-10)/A(7-8)/B(5-6)/C(3-4); ranking 按总分降序; strengths/weaknesses 各至少2条。""");
        StringBuilder user = new StringBuilder();
        for (int i = 0; i < resumes.size(); i++) {
            Resume r = resumes.get(i);
            user.append("候选人").append(i + 1).append(": ").append(r.getCandidateName()).append("\n");
            user.append(r.getParsedJson() != null ? r.getParsedJson().toString() : r.getRawText()).append("\n\n");
        }

        String aiResult = deepSeek.chat(sys.toString(), user.toString());
        aiResult = JsonGuard.extractJson(aiResult);
        log.info("Resume comparison completed for {} resumes", resumes.size());

        ComparisonResult result = new ComparisonResult();
        result.setResumeIds(resumeIds);
        result.setComparisonResult(aiResult);
        result.setComparedAt(new Date());
        return result;
    }
}
