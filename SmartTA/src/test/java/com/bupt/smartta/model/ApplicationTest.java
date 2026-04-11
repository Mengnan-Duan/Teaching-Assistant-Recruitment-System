package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Application 模型类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>构造函数：ID 生成规则、默认状态、appliedAt 时间戳、AI 分数</li>
 *   <li>状态常量值（STATUS_SUBMITTED / STATUS_REVIEW / STATUS_ACCEPTED / STATUS_REJECTED）</li>
 *   <li>setter / getter：基本读写</li>
 *   <li>ID 唯一性测试（构造器生成）</li>
 *   <li>AI 分数边界值测试</li>
 * </ul>
 */
@DisplayName("Application 模型测试")
class ApplicationTest {

    // ─────────────────────────────────────────────────────────
    // 构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("构造器：ID 格式为 {applicantId}_{positionCode}")
    void constructor_idFormat() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "Software Engineering", 85);
        assertEquals("A001_EBU6304", app.getId());
    }

    @Test
    @DisplayName("构造器：新建申请默认状态为 Submitted")
    void constructor_defaultStatus() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 85);
        assertEquals(Application.STATUS_SUBMITTED, app.getStatus());
        assertNotEquals(Application.STATUS_ACCEPTED, app.getStatus());
    }

    @Test
    @DisplayName("构造器：appliedAt 为今日日期字符串")
    void constructor_appliedAt_isToday() {
        String today = LocalDate.now().toString();
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 85);
        assertEquals(today, app.getAppliedAt());
    }

    @Test
    @DisplayName("构造器：AI 综合分数写入正确")
    void constructor_aiScore() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 90);
        assertEquals(90, app.getAiScore());
    }

    @Test
    @DisplayName("构造器：申请人姓名正确记录")
    void constructor_applicantName() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 85);
        assertEquals("Zhang Wei", app.getApplicantName());
    }

    @Test
    @DisplayName("构造器：职位代码和名称正确记录")
    void constructor_positionCodeAndName() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "Software Engineering", 85);
        assertEquals("EBU6304", app.getPositionCode());
        assertEquals("Software Engineering", app.getPositionName());
    }

    // ─────────────────────────────────────────────────────────
    // 默认构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("默认构造器：所有字段为 null 或 0")
    void defaultConstructor_allFieldsNullOrZero() {
        Application app = new Application();
        assertNull(app.getId());
        assertNull(app.getApplicantId());
        assertNull(app.getStatus());
        assertEquals(0, app.getAiScore());
    }

    // ─────────────────────────────────────────────────────────
    // 子分数测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("子分数：skillScore / gpaScore / availScore 可正常写入")
    void subScores_canBeSetAndRead() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 75);
        app.setSkillScore(80);
        app.setGpaScore(90);
        app.setAvailScore(60);
        assertEquals(80, app.getSkillScore());
        assertEquals(90, app.getGpaScore());
        assertEquals(60, app.getAvailScore());
    }

    // ─────────────────────────────────────────────────────────
    // AI 解释文本测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiExplanation：包含综合分数信息")
    void aiExplanation_containsScore() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 75);
        assertNotNull(app.getAiExplanation());
        assertTrue(app.getAiExplanation().contains("75"));
    }

    @Test
    @DisplayName("llmExplanation：可写入和读取")
    void llmExplanation_setAndGet() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 75);
        String explanation = "DeepSeek AI analysis: Excellent match for Java and Agile skills.";
        app.setLlmExplanation(explanation);
        assertEquals(explanation, app.getLlmExplanation());
    }

    // ─────────────────────────────────────────────────────────
    // 状态常量测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("状态常量：四个值非空且互不相等")
    void statusConstants_allDistinctAndNonEmpty() {
        assertNotNull(Application.STATUS_SUBMITTED);
        assertNotNull(Application.STATUS_REVIEW);
        assertNotNull(Application.STATUS_ACCEPTED);
        assertNotNull(Application.STATUS_REJECTED);
    }

    @Test
    @DisplayName("状态常量：四个值互不相等")
    void statusConstants_pairwiseDistinct() {
        List<String> values = List.of(
                Application.STATUS_SUBMITTED,
                Application.STATUS_REVIEW,
                Application.STATUS_ACCEPTED,
                Application.STATUS_REJECTED
        );
        long uniqueCount = values.stream().distinct().count();
        assertEquals(4, uniqueCount);
    }

    // ─────────────────────────────────────────────────────────
    // Setter / Getter 链式测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setter：所有字段可独立修改")
    void setters_independentUpdates() {
        Application app = new Application();
        app.setId("TEST_ID");
        app.setApplicantId("A999");
        app.setApplicantName("Test User");
        app.setPositionCode("TEST01");
        app.setPositionName("Test Course");
        app.setStatus("Under Review");
        app.setAiScore(88);
        app.setAppliedAt("2026-01-01");

        assertEquals("TEST_ID", app.getId());
        assertEquals("A999", app.getApplicantId());
        assertEquals("Test User", app.getApplicantName());
        assertEquals("TEST01", app.getPositionCode());
        assertEquals("Test Course", app.getPositionName());
        assertEquals("Under Review", app.getStatus());
        assertEquals(88, app.getAiScore());
        assertEquals("2026-01-01", app.getAppliedAt());
    }

    // ─────────────────────────────────────────────────────────
    // ID 唯一性测试（构造器生成）
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("不同申请者或职位生成的 ID 不相等")
    void id_uniqueness() {
        Application app1 = new Application("A001", "Zhang Wei", "EBU6304", "SE", 85);
        Application app2 = new Application("A002", "Li Mei",    "EBU6304", "SE", 90);
        Application app3 = new Application("A001", "Zhang Wei", "EBU5476", "DB",  78);

        assertNotEquals(app1.getId(), app2.getId());
        assertNotEquals(app1.getId(), app3.getId());
        assertNotEquals(app2.getId(), app3.getId());
    }

    // ─────────────────────────────────────────────────────────
    // 边界值测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("AI 分数边界：可为零")
    void aiScore_canBeZero() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", 0);
        assertEquals(0, app.getAiScore());
    }

    @Test
    @DisplayName("AI 分数边界：可为负数（模型层不校验，由业务层处理）")
    void aiScore_negativeAccepted() {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", -1);
        assertEquals(-1, app.getAiScore());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 50, 100, 65535})
    @DisplayName("AI 分数：任意整数均可接受")
    void aiScore_anyInteger(int score) {
        Application app = new Application("A001", "Zhang Wei", "EBU6304", "SE", score);
        assertEquals(score, app.getAiScore());
    }
}
