package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemLog 模型类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>状态常量（OP_READ / OP_WRITE / OP_ERROR，STATUS_OK / STATUS_FAIL）</li>
 *   <li>getOpIcon()：每种操作类型返回对应图标字符</li>
 *   <li>构造器：timestamp 自动生成（当前时间），其他字段正确赋值</li>
 *   <li>getter / setter：字段读写</li>
 * </ul>
 */
@DisplayName("SystemLog 模型测试")
class SystemLogTest {

    // ─────────────────────────────────────────────────────────
    // 状态常量测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("状态常量")
    class ConstantsTests {

        @Test
        @DisplayName("操作类型常量：非空且互不相等")
        void operationConstants() {
            assertNotNull(SystemLog.OP_READ);
            assertNotNull(SystemLog.OP_WRITE);
            assertNotNull(SystemLog.OP_ERROR);
            assertNotEquals(SystemLog.OP_READ, SystemLog.OP_WRITE);
            assertNotEquals(SystemLog.OP_READ, SystemLog.OP_ERROR);
            assertNotEquals(SystemLog.OP_WRITE, SystemLog.OP_ERROR);
        }

        @Test
        @DisplayName("状态常量：非空且互不相等")
        void statusConstants() {
            assertNotNull(SystemLog.STATUS_OK);
            assertNotNull(SystemLog.STATUS_FAIL);
            assertNotEquals(SystemLog.STATUS_OK, SystemLog.STATUS_FAIL);
        }

        @Test
        @DisplayName("操作常量与 getOpIcon 返回值对应")
        void opIconMatchesEnum() {
            assertEquals("R", new SystemLog(SystemLog.OP_READ, "test.json", SystemLog.STATUS_OK).getOpIcon());
            assertEquals("W", new SystemLog(SystemLog.OP_WRITE, "test.json", SystemLog.STATUS_OK).getOpIcon());
            assertEquals("E", new SystemLog(SystemLog.OP_ERROR, "test.json", SystemLog.STATUS_FAIL, "err").getOpIcon());
        }
    }

    // ─────────────────────────────────────────────────────────
    // getOpIcon() 测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOpIcon()")
    class OpIconTests {

        @Test
        @DisplayName("OP_READ → R")
        void read() {
            SystemLog log = new SystemLog(SystemLog.OP_READ, "data.json", SystemLog.STATUS_OK);
            assertEquals("R", log.getOpIcon());
        }

        @Test
        @DisplayName("OP_WRITE → W")
        void write() {
            SystemLog log = new SystemLog(SystemLog.OP_WRITE, "data.json", SystemLog.STATUS_OK);
            assertEquals("W", log.getOpIcon());
        }

        @Test
        @DisplayName("OP_ERROR → E")
        void error() {
            SystemLog log = new SystemLog(SystemLog.OP_ERROR, "data.json", SystemLog.STATUS_FAIL, "NullPointer");
            assertEquals("E", log.getOpIcon());
        }

        @Test
        @DisplayName("未知操作 → ?")
        void unknown() {
            SystemLog log = new SystemLog("UNKNOWN_OP", "data.json", SystemLog.STATUS_OK);
            assertEquals("?", log.getOpIcon());
        }

        @Test
        @DisplayName("null 操作 → ?")
        void nullOp() {
            SystemLog log = new SystemLog();
            log.setOperation(null);
            assertEquals("?", log.getOpIcon());
        }
    }

    // ─────────────────────────────────────────────────────────
    // 构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("三参数构造器：timestamp 自动生成，其余字段正确赋值")
    void constructor_threeArgs() {
        SystemLog log = new SystemLog(SystemLog.OP_READ, "users.json", SystemLog.STATUS_OK);

        assertEquals(SystemLog.OP_READ, log.getOperation());
        assertEquals("users.json", log.getFileName());
        assertEquals(SystemLog.STATUS_OK, log.getStatus());
        assertNotNull(log.getTimestamp());
        assertNull(log.getDetail());

        assertNotNull(LocalDateTime.parse(log.getTimestamp(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    @Test
    @DisplayName("四参数构造器：包含 detail 字段")
    void constructor_fourArgs() {
        SystemLog log = new SystemLog(
                SystemLog.OP_ERROR, "config.json", SystemLog.STATUS_FAIL,
                "File not found at path /data/config.json"
        );

        assertEquals("File not found at path /data/config.json", log.getDetail());
        assertEquals(SystemLog.OP_ERROR, log.getOperation());
        assertEquals("config.json", log.getFileName());
        assertEquals(SystemLog.STATUS_FAIL, log.getStatus());
    }

    @Test
    @DisplayName("默认构造器：所有字段为 null")
    void defaultConstructor() {
        SystemLog log = new SystemLog();
        assertNull(log.getTimestamp());
        assertNull(log.getOperation());
        assertNull(log.getFileName());
        assertNull(log.getStatus());
        assertNull(log.getDetail());
    }

    // ─────────────────────────────────────────────────────────
    // Setter / Getter 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setter / getter：所有字段独立读写")
    void setters_allIndependent() {
        SystemLog log = new SystemLog();
        log.setTimestamp("2026-04-01 09:30:00");
        log.setOperation(SystemLog.OP_WRITE);
        log.setFileName("applications.json");
        log.setStatus(SystemLog.STATUS_OK);
        log.setDetail("Batch update completed");

        assertEquals("2026-04-01 09:30:00", log.getTimestamp());
        assertEquals(SystemLog.OP_WRITE, log.getOperation());
        assertEquals("applications.json", log.getFileName());
        assertEquals(SystemLog.STATUS_OK, log.getStatus());
        assertEquals("Batch update completed", log.getDetail());
    }

    // ─────────────────────────────────────────────────────────
    // 时间戳格式测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("自动生成的 timestamp 符合 yyyy-MM-dd HH:mm:ss 格式")
    void timestamp_format() {
        SystemLog log = new SystemLog(SystemLog.OP_READ, "test.json", SystemLog.STATUS_OK);
        String ts = log.getTimestamp();

        assertNotNull(ts);
        assertTrue(ts.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertNotNull(LocalDateTime.parse(ts,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
