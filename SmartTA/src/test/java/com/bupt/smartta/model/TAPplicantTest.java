package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAPplicant 模型类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>默认构造器：skills 初始化为 ArrayList</li>
 *   <li>全参数构造器：所有字段赋值正确，createdAt 为当日日期</li>
 *   <li>getMatchedSkillCount()：完全匹配/部分匹配/无匹配/null 保护</li>
 *   <li>computeAIScore()：技能/GPA/可用时间三维度权重计算</li>
 *   <li>getEmail() / getYearOfStudy()：null 值保护返回空字符串</li>
 *   <li>setSkills()：null 参数保护</li>
 * </ul>
 */
@DisplayName("TAPplicant 模型测试")
class TAPplicantTest {

    // ─────────────────────────────────────────────────────────
    // 构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("默认构造器：skills 初始化为非 null 的空列表")
    void defaultConstructor_skillsNotNull() {
        TAPplicant a = new TAPplicant();
        assertNotNull(a.getSkills());
        assertTrue(a.getSkills().isEmpty());
    }

    @Test
    @DisplayName("全参数构造器：所有字段正确赋值")
    void fullConstructor_allFields() {
        List<String> skills = Arrays.asList("Java", "Python");
        TAPplicant a = new TAPplicant("A001", "Li Hua", "lihua@bupt.cn",
                "Year 3", 3.8, skills, 15);

        assertEquals("A001", a.getId());
        assertEquals("Li Hua", a.getName());
        assertEquals("lihua@bupt.cn", a.getEmail());
        assertEquals("Year 3", a.getYearOfStudy());
        assertEquals(3.8, a.getGpa());
        assertEquals(Arrays.asList("Java", "Python"), a.getSkills());
        assertEquals(15, a.getHoursAvailable());
        assertNotNull(a.getCreatedAt());
    }

    @Test
    @DisplayName("全参数构造器：skills 为 null 时初始化为空列表")
    void fullConstructor_skillsNull() {
        TAPplicant a = new TAPplicant("A001", "Li Hua", "lihua@bupt.cn",
                "Year 3", 3.8, null, 15);
        assertNotNull(a.getSkills());
        assertTrue(a.getSkills().isEmpty());
    }

    // ─────────────────────────────────────────────────────────
    // getMatchedSkillCount() 测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMatchedSkillCount()")
    class MatchedSkillTests {

        @Test
        @DisplayName("完全匹配：所有技能均匹配")
        void allSkillsMatched() {
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("Java", "Python", "SQL"));
            List<String> required = Arrays.asList("Java", "Python", "SQL");
            assertEquals(3, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("部分匹配：仅部分技能匹配")
        void partialMatch() {
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("Java", "Python"));
            List<String> required = Arrays.asList("Java", "C++", "Agile", "Python");
            assertEquals(2, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("无匹配：技能完全不重合")
        void noMatch() {
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("Java", "Python"));
            List<String> required = Arrays.asList("C++", "Agile", "React");
            assertEquals(0, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("python"));
            List<String> required = Arrays.asList("Python");
            assertEquals(1, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("申请人无技能：返回 0")
        void applicantNoSkills() {
            TAPplicant a = new TAPplicant();
            a.setSkills(Collections.emptyList());
            List<String> required = Arrays.asList("Java", "Python");
            assertEquals(0, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("职位无技能要求（null）：返回 0")
        void positionNull() {
            List<String> required = null;
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("Java", "Python"));
            assertEquals(0, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("职位无技能要求（空列表）：返回 0")
        void positionEmpty() {
            List<String> required = Collections.emptyList();
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("Java", "Python"));
            assertEquals(0, a.getMatchedSkillCount(required));
        }

        @Test
        @DisplayName("职位无技能要求（空白字符串列表）：返回 0")
        void positionBlank() {
            List<String> required = Arrays.asList("  ", "\t");
            TAPplicant a = new TAPplicant();
            a.setSkills(Arrays.asList("Java", "Python"));
            assertEquals(0, a.getMatchedSkillCount(required));
        }
    }

    // ─────────────────────────────────────────────────────────
    // computeAIScore() 测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("computeAIScore()")
    class AiScoreTests {

        @Test
        @DisplayName("全满分候选人：全部匹配 → 接近 100")
        void perfectCandidate() {
            TAPplicant a = new TAPplicant();
            a.setGpa(4.0);
            a.setHoursAvailable(20);
            a.setSkills(Arrays.asList("Java", "Python"));
            List<String> required = Arrays.asList("Java", "Python");
            double score = a.computeAIScore(required, 20);
            assertEquals(100.0, score);
        }

        @Test
        @DisplayName("零 GPA 候选人：gpaScore = 0")
        void zeroGpa() {
            TAPplicant a = new TAPplicant();
            a.setGpa(0.0);
            a.setHoursAvailable(20);
            a.setSkills(Arrays.asList("Java"));
            List<String> required = Arrays.asList("Java");
            double score = a.computeAIScore(required, 20);
            // skill 100 + gpa 0 + avail 100 → 0.4*100 + 0.3*0 + 0.3*100 = 70
            assertEquals(70.0, score);
        }

        @Test
        @DisplayName("零可用时间候选人：availScore = 0")
        void zeroHours() {
            TAPplicant a = new TAPplicant();
            a.setGpa(4.0);
            a.setHoursAvailable(0);
            a.setSkills(Arrays.asList("Java"));
            List<String> required = Arrays.asList("Java");
            double score = a.computeAIScore(required, 20);
            // skill 100 + gpa 100 + avail 0 → 0.4*100 + 0.3*100 + 0.3*0 = 70
            assertEquals(70.0, score);
        }

        @Test
        @DisplayName("无技能匹配候选人：skillScore = 0")
        void noSkillMatch() {
            TAPplicant a = new TAPplicant();
            a.setGpa(4.0);
            a.setHoursAvailable(20);
            a.setSkills(Arrays.asList("Go", "Rust"));
            List<String> required = Arrays.asList("Java", "Python");
            double score = a.computeAIScore(required, 20);
            // skill 0 + gpa 100 + avail 100 → 0.4*0 + 0.3*100 + 0.3*100 = 60
            assertEquals(60.0, score);
        }

        @Test
        @DisplayName("超出 20h/周：availScore 封顶 100")
        void hoursExceedCap() {
            TAPplicant a = new TAPplicant();
            a.setGpa(4.0);
            a.setHoursAvailable(40);
            a.setSkills(Arrays.asList("Java"));
            List<String> required = Arrays.asList("Java");
            double score = a.computeAIScore(required, 20);
            assertEquals(100.0, score);
        }

        @Test
        @DisplayName("null requiredSkills：skillScore = 0")
        void nullRequiredSkills() {
            TAPplicant a = new TAPplicant();
            a.setGpa(4.0);
            a.setHoursAvailable(20);
            a.setSkills(Arrays.asList("Java"));
            double score = a.computeAIScore(null, 20);
            assertEquals(60.0, score);
        }

        @Test
        @DisplayName("AI 分数四舍五入到整数")
        void scoreRounded() {
            TAPplicant a = new TAPplicant();
            a.setGpa(3.7);
            a.setHoursAvailable(12);
            a.setSkills(Arrays.asList("Java"));
            List<String> required = Arrays.asList("Java");
            double score = a.computeAIScore(required, 15);
            assertEquals(Math.round(score), (long) score);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 空值保护测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("空值保护")
    class NullProtectionTests {

        @Test
        @DisplayName("getEmail()：null → 返回空字符串")
        void emailNull() {
            TAPplicant a = new TAPplicant();
            assertEquals("", a.getEmail());
        }

        @Test
        @DisplayName("getEmail()：正常值 → 原样返回")
        void emailNormal() {
            TAPplicant a = new TAPplicant();
            a.setEmail("test@bupt.cn");
            assertEquals("test@bupt.cn", a.getEmail());
        }

        @Test
        @DisplayName("getYearOfStudy()：null → 返回空字符串")
        void yearNull() {
            TAPplicant a = new TAPplicant();
            assertEquals("", a.getYearOfStudy());
        }

        @Test
        @DisplayName("setSkills()：null → 初始化为空列表")
        void skillsSetterNull() {
            TAPplicant a = new TAPplicant();
            a.setSkills(null);
            assertNotNull(a.getSkills());
            assertTrue(a.getSkills().isEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────
    // Setter / Getter 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setter / getter：所有字段独立读写")
    void setters_allIndependent() {
        TAPplicant a = new TAPplicant();
        a.setId("A999");
        a.setName("Wang Fang");
        a.setEmail("wangfang@bupt.cn");
        a.setYearOfStudy("Year 4");
        a.setGpa(3.95);
        a.setHoursAvailable(18);
        a.setCvFileName("wangfang_cv.pdf");
        a.setCreatedAt("2026-01-01");

        assertEquals("A999", a.getId());
        assertEquals("Wang Fang", a.getName());
        assertEquals("wangfang@bupt.cn", a.getEmail());
        assertEquals("Year 4", a.getYearOfStudy());
        assertEquals(3.95, a.getGpa());
        assertEquals(18, a.getHoursAvailable());
        assertEquals("wangfang_cv.pdf", a.getCvFileName());
        assertEquals("2026-01-01", a.getCreatedAt());
    }

    @Test
    @DisplayName("getSkills()：首次调用空列表，后续不受影响")
    void getSkills_firstCallNotMutated() {
        TAPplicant a = new TAPplicant();
        List<String> s1 = a.getSkills();
        List<String> s2 = a.getSkills();
        assertSame(s1, s2);
    }
}
