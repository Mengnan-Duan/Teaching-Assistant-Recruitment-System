package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Position 模型类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>默认构造器：requiredSkills 初始化为 ArrayList、status="Open"、filledSlots=0</li>
 *   <li>全参数构造器：所有字段赋值正确</li>
 *   <li>getRemainingSlots()：totalSlots - filledSlots，负数时返回 0</li>
 *   <li>isOpen()：status="Open" 且剩余席位 > 0 才为 true</li>
 *   <li>getRequiredSkillsStr()：列表转逗号分隔字符串</li>
 *   <li>setter / getter：正常读写</li>
 * </ul>
 */
@DisplayName("Position 模型测试")
class PositionTest {

    // ─────────────────────────────────────────────────────────
    // 构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("默认构造器：skills 初始化为空列表，status=Open，filledSlots=0")
    void defaultConstructor_initialState() {
        Position p = new Position();
        assertNotNull(p.getRequiredSkills());
        assertTrue(p.getRequiredSkills().isEmpty());
        assertEquals("Open", p.getStatus());
        assertEquals(0, p.getFilledSlots());
    }

    @Test
    @DisplayName("全参数构造器：所有字段正确赋值")
    void fullConstructor_allFields() {
        List<String> skills = Arrays.asList("Java", "Python", "Agile");
        Position p = new Position("CS101", "Intro to CS", skills,
                10, 5, "2026-06-30", "Prof. Wang");

        assertEquals("CS101", p.getCode());
        assertEquals("Intro to CS", p.getName());
        assertEquals(Arrays.asList("Java", "Python", "Agile"), p.getRequiredSkills());
        assertEquals(10, p.getHoursPerWeek());
        assertEquals(5, p.getTotalSlots());
        assertEquals("2026-06-30", p.getDeadline());
        assertEquals("Prof. Wang", p.getPostedBy());
        assertEquals("Open", p.getStatus());
        assertEquals(0, p.getFilledSlots());
        assertNotNull(p.getPostedAt());
    }

    @Test
    @DisplayName("全参数构造器：skills 为 null 时初始化为空列表")
    void fullConstructor_skillsNull() {
        Position p = new Position("CS101", "Intro CS", null, 10, 5, "2026-06-30", "Prof. Wang");
        assertNotNull(p.getRequiredSkills());
        assertTrue(p.getRequiredSkills().isEmpty());
    }

    // ─────────────────────────────────────────────────────────
    // getRemainingSlots() 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRemainingSlots：正数情况 totalSlots > filledSlots")
    void remainingSlots_positive() {
        Position p = new Position();
        p.setTotalSlots(5);
        p.setFilledSlots(2);
        assertEquals(3, p.getRemainingSlots());
    }

    @Test
    @DisplayName("getRemainingSlots：零情况 totalSlots == filledSlots")
    void remainingSlots_zero() {
        Position p = new Position();
        p.setTotalSlots(5);
        p.setFilledSlots(5);
        assertEquals(0, p.getRemainingSlots());
    }

    @Test
    @DisplayName("getRemainingSlots：负数保护 totalSlots < filledSlots")
    void remainingSlots_negative_protected() {
        Position p = new Position();
        p.setTotalSlots(5);
        p.setFilledSlots(10);
        assertEquals(0, p.getRemainingSlots());
    }

    @ParameterizedTest
    @CsvSource({
            "10, 3, 7",
            "8,  0, 8",
            "8,  8, 0",
            "3,  5, 0"
    })
    @DisplayName("getRemainingSlots：参数化边界用例")
    void remainingSlots_parametric(int total, int filled, int expected) {
        Position p = new Position();
        p.setTotalSlots(total);
        p.setFilledSlots(filled);
        assertEquals(expected, p.getRemainingSlots());
    }

    // ─────────────────────────────────────────────────────────
    // isOpen() 测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isOpen() 组合逻辑")
    class IsOpenTests {

        @Test
        @DisplayName("Open 且有剩余席位 → true")
        void openWithSlots() {
            Position p = new Position();
            p.setStatus("Open");
            p.setTotalSlots(5);
            p.setFilledSlots(2);
            assertTrue(p.isOpen());
        }

        @Test
        @DisplayName("Open 但无剩余席位 → false")
        void openNoSlots() {
            Position p = new Position();
            p.setStatus("Open");
            p.setTotalSlots(5);
            p.setFilledSlots(5);
            assertFalse(p.isOpen());
        }

        @Test
        @DisplayName("Closed 有剩余席位 → false")
        void closedHasSlots() {
            Position p = new Position();
            p.setStatus("Closed");
            p.setTotalSlots(5);
            p.setFilledSlots(2);
            assertFalse(p.isOpen());
        }

        @Test
        @DisplayName("非 Open 状态 → false")
        void otherStatus() {
            Position p = new Position();
            p.setStatus("Pending");
            p.setTotalSlots(5);
            p.setFilledSlots(0);
            assertFalse(p.isOpen());
        }
    }

    // ─────────────────────────────────────────────────────────
    // getRequiredSkillsStr() 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRequiredSkillsStr：多技能返回逗号分隔")
    void skillsStr_multipleSkills() {
        Position p = new Position();
        p.setRequiredSkills(Arrays.asList("Java", "Python", "SQL"));
        assertEquals("Java, Python, SQL", p.getRequiredSkillsStr());
    }

    @Test
    @DisplayName("getRequiredSkillsStr：单技能无逗号")
    void skillsStr_singleSkill() {
        Position p = new Position();
        p.setRequiredSkills(List.of("Java"));
        assertEquals("Java", p.getRequiredSkillsStr());
    }

    @Test
    @DisplayName("getRequiredSkillsStr：空列表返回空字符串")
    void skillsStr_empty() {
        Position p = new Position();
        assertEquals("", p.getRequiredSkillsStr());
    }

    // ─────────────────────────────────────────────────────────
    // Setter / Getter 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setter / getter：所有字段独立读写")
    void setters_allIndependent() {
        Position p = new Position();
        p.setCode("CS202");
        p.setName("Advanced Algorithms");
        p.setHoursPerWeek(15);
        p.setTotalSlots(3);
        p.setFilledSlots(1);
        p.setDeadline("2026-07-01");
        p.setDescription("Advanced course for senior students.");
        p.setPostedBy("Prof. Chen");
        p.setPostedByUsername("chenlu");
        p.setPostedAt("2026-03-01");
        p.setStatus("Closed");

        assertEquals("CS202", p.getCode());
        assertEquals("Advanced Algorithms", p.getName());
        assertEquals(15, p.getHoursPerWeek());
        assertEquals(3, p.getTotalSlots());
        assertEquals(1, p.getFilledSlots());
        assertEquals("2026-07-01", p.getDeadline());
        assertEquals("Advanced course for senior students.", p.getDescription());
        assertEquals("Prof. Chen", p.getPostedBy());
        assertEquals("chenlu", p.getPostedByUsername());
        assertEquals("2026-03-01", p.getPostedAt());
        assertEquals("Closed", p.getStatus());
    }

    @Test
    @DisplayName("status 可动态修改")
    void status_canChange() {
        Position p = new Position();
        assertEquals("Open", p.getStatus());
        p.setStatus("Closed");
        assertEquals("Closed", p.getStatus());
    }

    @Test
    @DisplayName("filledSlots 可动态修改，影响 remainingSlots 和 isOpen")
    void filledSlots_dynamicChange() {
        Position p = new Position();
        p.setTotalSlots(5);
        assertEquals(5, p.getRemainingSlots());
        assertTrue(p.isOpen());

        p.setFilledSlots(5);
        assertEquals(0, p.getRemainingSlots());
        assertFalse(p.isOpen());
    }
}
