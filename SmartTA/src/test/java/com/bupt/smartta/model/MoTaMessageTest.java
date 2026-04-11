package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MoTaMessage 模型类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>create() 工厂方法：ID 生成、sentAt 时间戳、readByRecipient=false、字段赋值</li>
 *   <li>create() 边界保护：null body → ""，null displayName → fromUsername</li>
 *   <li>字段读写：所有 setter / getter</li>
 * </ul>
 */
@DisplayName("MoTaMessage 模型测试")
class MoTaMessageTest {

    // ─────────────────────────────────────────────────────────
    // create() 工厂方法测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("create()：生成非空 UUID 格式 ID")
    void create_idIsUuid() {
        MoTaMessage m = MoTaMessage.create(
                "mo01", "A001", "ta01", "Li Hua", "TA",
                "mo01", "Hello, please review my application."
        );
        assertNotNull(m.getId());
        assertFalse(m.getId().isEmpty());
        // 验证是有效的 UUID
        assertNotNull(UUID.fromString(m.getId()));
    }

    @Test
    @DisplayName("create()：每次调用生成不同的 ID")
    void create_idUnique() {
        MoTaMessage m1 = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", "msg");
        MoTaMessage m2 = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", "msg");
        assertNotEquals(m1.getId(), m2.getId());
    }

    @Test
    @DisplayName("create()：所有字段正确赋值")
    void create_allFields() {
        MoTaMessage m = MoTaMessage.create(
                "mo_chen", "A042", "ta_wang", "Wang Fang", "TA",
                "mo_chen", "I have submitted all required documents."
        );

        assertEquals("mo_chen", m.getMoUsername());
        assertEquals("A042", m.getTaApplicantId());
        assertEquals("ta_wang", m.getFromUsername());
        assertEquals("Wang Fang", m.getFromDisplayName());
        assertEquals("TA", m.getFromRole());
        assertEquals("mo_chen", m.getToUsername());
        assertEquals("I have submitted all required documents.", m.getBody());
        assertNotNull(m.getSentAt());
        assertFalse(m.isReadByRecipient());
    }

    @Test
    @DisplayName("create()：fromRole = MO 时正确记录")
    void create_fromRoleMo() {
        MoTaMessage m = MoTaMessage.create(
                "mo_chen", "A042", "mo_chen", "Prof. Chen", "MO",
                "ta_wang", "Your application has been reviewed."
        );
        assertEquals("MO", m.getFromRole());
        assertEquals("mo_chen", m.getFromUsername());
        assertEquals("Prof. Chen", m.getFromDisplayName());
    }

    @Test
    @DisplayName("create()：body 为 null → 默认为空字符串")
    void create_bodyNull() {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", null);
        assertEquals("", m.getBody());
    }

    @Test
    @DisplayName("create()：displayName 为 null → 使用 fromUsername")
    void create_displayNameNull() {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", null, "TA", "mo", "Hello");
        assertEquals("ta", m.getFromDisplayName());
    }

    @Test
    @DisplayName("create()：sentAt 为 ISO-8601 时间戳（Instant.toString）")
    void create_sentAtFormat() {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", "Hi");
        assertNotNull(Instant.parse(m.getSentAt()));
    }

    @Test
    @DisplayName("create()：readByRecipient 默认为 false")
    void create_defaultReadByRecipient() {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", "Hi");
        assertFalse(m.isReadByRecipient());
    }

    // ─────────────────────────────────────────────────────────
    // Setter / Getter 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setter / getter：所有字段独立读写")
    void setters_allIndependent() {
        MoTaMessage m = new MoTaMessage();

        m.setId("custom-id-123");
        m.setMoUsername("mo_test");
        m.setTaApplicantId("A555");
        m.setFromUsername("ta_test");
        m.setFromDisplayName("Test TA");
        m.setFromRole("TA");
        m.setToUsername("mo_test");
        m.setBody("Test message body");
        m.setSentAt("2026-04-01T10:00:00Z");
        m.setReadByRecipient(true);

        assertEquals("custom-id-123", m.getId());
        assertEquals("mo_test", m.getMoUsername());
        assertEquals("A555", m.getTaApplicantId());
        assertEquals("ta_test", m.getFromUsername());
        assertEquals("Test TA", m.getFromDisplayName());
        assertEquals("TA", m.getFromRole());
        assertEquals("mo_test", m.getToUsername());
        assertEquals("Test message body", m.getBody());
        assertEquals("2026-04-01T10:00:00Z", m.getSentAt());
        assertTrue(m.isReadByRecipient());
    }

    // ─────────────────────────────────────────────────────────
    // 消息状态测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("readByRecipient：默认值 false，可切换为 true")
    void readByRecipient_toggle() {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", "Hi");
        assertFalse(m.isReadByRecipient());

        m.setReadByRecipient(true);
        assertTrue(m.isReadByRecipient());

        m.setReadByRecipient(false);
        assertFalse(m.isReadByRecipient());
    }

    // ─────────────────────────────────────────────────────────
    // 消息内容边界测试
    // ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"", "Hi", "Chinese content", "emoji message"})
    @DisplayName("body：接受各种文本内容")
    void body_variousContent(String content) {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", content);
        assertEquals(content, m.getBody());
    }

    @ParameterizedTest
    @CsvSource({
            "MO, MO",
            "TA, TA",
            "Admin, Admin",
            "x, x"
    })
    @DisplayName("fromRole：接受任意非空字符串（模型层不校验合法性）")
    void fromRole_anyString(String input, String expected) {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", input, "mo", "msg");
        assertEquals(expected, m.getFromRole());
    }

    @Test
    @DisplayName("fromRole：可动态修改")
    void fromRole_canChange() {
        MoTaMessage m = MoTaMessage.create("mo", "A", "ta", "T", "TA", "mo", "msg");
        assertEquals("TA", m.getFromRole());
        m.setFromRole("MO");
        assertEquals("MO", m.getFromRole());
    }

    // ─────────────────────────────────────────────────────────
    // 默认构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("默认构造器：所有字段为 null，readByRecipient 为 false")
    void defaultConstructor_allNull() {
        MoTaMessage m = new MoTaMessage();
        assertNull(m.getId());
        assertNull(m.getMoUsername());
        assertNull(m.getTaApplicantId());
        assertNull(m.getFromUsername());
        assertNull(m.getFromDisplayName());
        assertNull(m.getFromRole());
        assertNull(m.getToUsername());
        assertNull(m.getBody());
        assertNull(m.getSentAt());
        assertFalse(m.isReadByRecipient());
    }
}
