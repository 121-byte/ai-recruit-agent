package com.example.recruit.service;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.example.recruit.domain.analysis.ComparisonResult;
import com.example.recruit.domain.analysis.ImplicitInsights;
import com.example.recruit.domain.analysis.PotentialAssessment;
import com.example.recruit.domain.analysis.ResumeAnalysisResult;
import com.example.recruit.domain.analysis.RiskAssessment;
import com.example.recruit.domain.analysis.StructuredData;
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

        // 第1轮: 结构化提取
        log.info("Round 1: Structural extraction for resume {}", resumeId);
        StructuredData structured = round1StructuralExtraction(rawText, resumeId);
        backfillResumeFromStructured(resume, structured);

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
                - basicInfo: { name, phone, email, education(最高学历), school, major, graduationYear, intendedPosition(意向岗位/求职目标) }
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
     * 将第1轮 basicInfo 回写到 resume 表, 只填充 null 字段, 不覆盖正则已提取的值。
     */
    private void backfillResumeFromStructured(Resume resume, StructuredData structured) {
        try {
            JsonNode structuredNode = structured.toJsonNode();
            JsonNode basicInfo = structuredNode.get("basicInfo");
            if (basicInfo == null || !basicInfo.isObject()) {
                log.warn("No basicInfo found in structured data for resume {}", resume.getId());
                return;
            }
            int filled = 0;
            if (resume.getPhone() == null && basicInfo.has("phone") && !basicInfo.get("phone").isNull()) {
                resume.setPhone(basicInfo.get("phone").asText()); filled++;
            }
            if (resume.getEmail() == null && basicInfo.has("email") && !basicInfo.get("email").isNull()) {
                resume.setEmail(basicInfo.get("email").asText()); filled++;
            }
            if (resume.getEducation() == null && basicInfo.has("education") && !basicInfo.get("education").isNull()) {
                resume.setEducation(basicInfo.get("education").asText()); filled++;
            }
            if (resume.getSchool() == null && basicInfo.has("school") && !basicInfo.get("school").isNull()) {
                resume.setSchool(basicInfo.get("school").asText()); filled++;
            }
            if (resume.getMajor() == null && basicInfo.has("major") && !basicInfo.get("major").isNull()) {
                resume.setMajor(basicInfo.get("major").asText()); filled++;
            }
            if (resume.getYearsExperience() == null
                    && basicInfo.has("graduationYear") && !basicInfo.get("graduationYear").isNull()) {
                int gradYear = basicInfo.get("graduationYear").asInt();
                int currentYear = java.time.Year.now().getValue();
                resume.setYearsExperience(Math.max(0, currentYear - gradYear)); filled++;
            }
            if (resume.getIntendedPosition() == null
                    && basicInfo.has("intendedPosition") && !basicInfo.get("intendedPosition").isNull()) {
                resume.setIntendedPosition(basicInfo.get("intendedPosition").asText()); filled++;
            }
            if (filled > 0) {
                log.info("Backfilled {} fields from structured data for resume {}", filled, resume.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to backfill resume from structured data: {}", e.getMessage());
        }
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
