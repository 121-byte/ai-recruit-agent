package com.example.recruit.infra.fileparser;

import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 快速信息提取器 (14 正则 + 时间段推算 + header 提取)。
 *
 * <p>从简历 raw_text 用正则快速提取关键信息 (姓名/电话/邮箱/学历/学校/专业/年限/意向岗位),
 * 用于上传时预填充 resume 独立列, 毫秒级, 不调 LLM。
 */
public final class QuickInfoExtractor {

    private QuickInfoExtractor() {
    }

    // 姓名: 标签提取 → fallback 第一行
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:姓名|名字|Candidate[：:\\s]*Name)\\s*[：:\\s]*\\s*([\\u4e00-\\u9fa5a-zA-Z·.]+)",
            Pattern.CASE_INSENSITIVE);

    // 电话: 11 位手机号
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d{9})");

    // 邮箱: 标准格式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)");

    // 学历: 括号内学位 → fallback 关键词
    private static final Pattern DEGREE_PAREN_PATTERN = Pattern.compile(
            "[（(]\\s*(博士|硕士|研究生|本科|学士|大专|专科)\\s*[）)]");
    private static final Pattern DEGREE_KEYWORD_PATTERN = Pattern.compile(
            "(博士|硕士|研究生|本科|学士|大专|专科)");

    // 学校: XX大学 / XX学院
    private static final Pattern SCHOOL_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5a-zA-Z]+(?:大学|学院))");

    // 专业: 括号前内容 → fallback 关键词
    private static final Pattern MAJOR_WITH_DEGREE_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5a-zA-Z]+)\\s*[（(]\\s*(?:博士|硕士|研究生|本科|学士|大专|专科)\\s*[）)]");
    private static final Pattern MAJOR_KEYWORD_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5a-zA-Z]+(?:专业|工程|科学|技术|管理|安全|网络|计算机|数据))");

    // 工作年限: "X年经验" → fallback 时间段推算
    private static final Pattern EXPERIENCE_PATTERN = Pattern.compile(
            "(\\d+)\\s*年\\s*(?:工作)?(?:经验|经历)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PERIOD_PATTERN = Pattern.compile(
            "(20\\d{2})[.\\-/]\\s*(?:0?[1-9]|1[0-2])");

    // 意向岗位: 标签提取 → fallback header 区域提取
    private static final Pattern INTENDED_POSITION_LABEL_PATTERN = Pattern.compile(
            "(?:意向岗位|求职岗位|应聘岗位|期望职位|目标岗位|求职意向|Position|Target[\\s:]*(?:position|role))" +
            "[：:\\s]*\\s*([\\u4e00-\\u9fa5a-zA-Z/·+]+)",
            Pattern.CASE_INSENSITIVE);

    // 第一行 fallback 姓名
    private static final Pattern FIRST_LINE_NAME = Pattern.compile(
            "^[\\s\\u00a0]*([\\u4e00-\\u9fa5a-zA-Z·.]+)");

    /** 提取结果。 */
    public static class Info {
        private final String name;
        private final String phone;
        private final String email;
        private final String education;
        private final String school;
        private final String major;
        private final Integer yearsExperience;
        private final String intendedPosition;

        public Info(String name, String phone, String email, String education, String school,
                    String major, Integer yearsExperience, String intendedPosition) {
            this.name = name;
            this.phone = phone;
            this.email = email;
            this.education = education;
            this.school = school;
            this.major = major;
            this.yearsExperience = yearsExperience;
            this.intendedPosition = intendedPosition;
        }

        public String getName() { return name; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getEducation() { return education; }
        public String getSchool() { return school; }
        public String getMajor() { return major; }
        public Integer getYearsExperience() { return yearsExperience; }
        public String getIntendedPosition() { return intendedPosition; }
    }

    /** 从简历 raw_text 提取 8 个字段。 */
    public static Info extract(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new Info(null, null, null, null, null, null, null, null);
        }

        // 姓名: 标签 → fallback 第一行
        String name = extractFirst(rawText, NAME_PATTERN);
        if (name == null) {
            Matcher m = FIRST_LINE_NAME.matcher(rawText.trim());
            if (m.find()) {
                name = m.group(1).trim();
            }
        }

        String phone = extractFirst(rawText, PHONE_PATTERN);
        String email = extractFirst(rawText, EMAIL_PATTERN);

        // 学历: 括号 → fallback 关键词
        String education = extractFirst(rawText, DEGREE_PAREN_PATTERN);
        if (education == null) {
            education = extractFirst(rawText, DEGREE_KEYWORD_PATTERN);
        }

        String school = extractFirst(rawText, SCHOOL_PATTERN);

        // 专业: 括号前 → fallback 关键词
        String major = extractFirst(rawText, MAJOR_WITH_DEGREE_PATTERN);
        if (major == null) {
            major = extractFirst(rawText, MAJOR_KEYWORD_PATTERN);
        }

        // 意向岗位: 标签 → fallback header 提取
        String intendedPosition = extractFirst(rawText, INTENDED_POSITION_LABEL_PATTERN);
        if (intendedPosition == null) {
            intendedPosition = extractIntendedPositionFromHeader(rawText, name);
        }

        // 工作年限: "X年经验" → fallback 时间段推算
        Integer yearsExp = null;
        String expStr = extractFirst(rawText, EXPERIENCE_PATTERN);
        if (expStr != null) {
            try {
                yearsExp = Integer.parseInt(expStr);
            } catch (NumberFormatException ignored) {
            }
        } else {
            yearsExp = estimateYearsFromTimePeriods(rawText);
        }

        return new Info(name, phone, email, education, school, major, yearsExp, intendedPosition);
    }

    /** 从简历 header 区域提取意向岗位。 */
    private static String extractIntendedPositionFromHeader(String rawText, String name) {
        String[] lines = rawText.split("\\n", 5);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals(name)) {
                continue;
            }
            if (containsJobKeyword(trimmed) && trimmed.length() <= 30) {
                return trimmed;
            }
        }
        return null;
    }

    private static boolean containsJobKeyword(String text) {
        return text.matches(".*(?:工程师|开发|架构师|经理|主管|总监|分析师|设计|顾问|" +
                "管理|运营|产品|项目|测试|运维|前端|后端|全栈|算法|数据|安全|" +
                "销售|市场|策划|助理|专员|负责人|leader|developer|engineer).*");
    }

    /** 从时间段推算工作年限。 */
    private static Integer estimateYearsFromTimePeriods(String rawText) {
        Matcher m = TIME_PERIOD_PATTERN.matcher(rawText);
        int earliest = Integer.MAX_VALUE;
        int latest = Integer.MIN_VALUE;
        while (m.find()) {
            int year = Integer.parseInt(m.group(1));
            earliest = Math.min(earliest, year);
            latest = Math.max(latest, year);
        }
        if (earliest == Integer.MAX_VALUE) {
            return null;
        }
        int currentYear = Year.now().getValue();
        int endYear = Math.max(latest, currentYear);
        return Math.max(0, endYear - earliest);
    }

    private static String extractFirst(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}
