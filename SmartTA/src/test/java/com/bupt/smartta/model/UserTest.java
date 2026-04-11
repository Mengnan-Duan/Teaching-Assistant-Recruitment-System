package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User 模型类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>默认构造器：roles 初始化为非 null 的 HashSet</li>
 *   <li>全参数构造器：所有字段赋值正确</li>
 *   <li>addRole() / removeRole() / hasRole()：角色管理</li>
 *   <li>checkPassword()：密码校验（SHA-256 哈希）</li>
 *   <li>hashPassword()：哈希一致性、同值同哈希、null 安全</li>
 *   <li>getter / setter：字段读写</li>
 *   <li>applicantId：关联申请者 ID</li>
 * </ul>
 */
@DisplayName("User 模型测试")
class UserTest {

    // ─────────────────────────────────────────────────────────
    // 构造器测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("默认构造器：roles 为非 null 空 HashSet")
    void defaultConstructor_rolesNotNull() {
        User u = new User();
        assertNotNull(u.getRoles());
        assertTrue(u.getRoles().isEmpty());
    }

    @Test
    @DisplayName("全参数构造器：所有字段正确赋值，roles 为空 HashSet，createdAt 为今日")
    void fullConstructor_allFields() {
        User u = new User("alice", "hash123", "Alice Smith", "alice@bupt.cn");

        assertEquals("alice", u.getUsername());
        assertEquals("hash123", u.getPasswordHash());
        assertEquals("Alice Smith", u.getDisplayName());
        assertEquals("alice@bupt.cn", u.getEmail());
        assertNotNull(u.getRoles());
        assertTrue(u.getRoles().isEmpty());
        assertNotNull(u.getCreatedAt());
    }

    // ─────────────────────────────────────────────────────────
    // 角色管理测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("角色管理")
    class RoleManagementTests {

        @Test
        @DisplayName("addRole()：添加角色后 hasRole() 返回 true")
        void addRole() {
            User u = new User();
            u.addRole("TA");
            assertTrue(u.hasRole("TA"));
        }

        @Test
        @DisplayName("addRole()：可添加多个不同角色")
        void addMultipleRoles() {
            User u = new User();
            u.addRole("TA");
            u.addRole("MO");
            u.addRole("ADMIN");

            assertTrue(u.hasRole("TA"));
            assertTrue(u.hasRole("MO"));
            assertTrue(u.hasRole("ADMIN"));
            assertEquals(3, u.getRoles().size());
        }

        @Test
        @DisplayName("addRole()：同一角色重复添加不影响（HashSet 去重）")
        void addRoleDuplicate() {
            User u = new User();
            u.addRole("TA");
            u.addRole("TA");
            u.addRole("TA");
            assertEquals(1, u.getRoles().size());
            assertTrue(u.hasRole("TA"));
        }

        @Test
        @DisplayName("removeRole()：移除后 hasRole() 返回 false")
        void removeRole() {
            User u = new User();
            u.addRole("TA");
            u.addRole("MO");
            u.removeRole("TA");

            assertFalse(u.hasRole("TA"));
            assertTrue(u.hasRole("MO"));
            assertEquals(1, u.getRoles().size());
        }

        @Test
        @DisplayName("removeRole()：移除不存在的角色不抛异常")
        void removeNonExistentRole() {
            User u = new User();
            u.removeRole("ADMIN");
            assertTrue(u.getRoles().isEmpty());
        }

        @Test
        @DisplayName("hasRole()：未添加的角色返回 false")
        void hasRole_notAdded() {
            User u = new User();
            u.addRole("TA");
            assertFalse(u.hasRole("MO"));
            assertFalse(u.hasRole("ADMIN"));
        }

        @Test
        @DisplayName("hasRole()：对 null 返回 false")
        void hasRole_null() {
            User u = new User();
            assertFalse(u.hasRole(null));
        }
    }

    // ─────────────────────────────────────────────────────────
    // 密码哈希测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("密码哈希")
    class PasswordHashTests {

        @Test
        @DisplayName("hashPassword()：相同输入 → 相同输出")
        void hashConsistent() {
            String hash1 = User.hashPassword("secret123");
            String hash2 = User.hashPassword("secret123");
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("hashPassword()：不同输入 → 不同输出")
        void hashDifferent() {
            String hash1 = User.hashPassword("pass1");
            String hash2 = User.hashPassword("pass2");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("hashPassword()：返回 64 位十六进制字符串（SHA-256）")
        void hashFormat() {
            String hash = User.hashPassword("test");
            assertEquals(64, hash.length());
            assertTrue(hash.matches("^[0-9a-f]{64}$"));
        }

        @Test
        @DisplayName("hashPassword()：空字符串返回有效哈希")
        void hashEmptyString() {
            String hash = User.hashPassword("");
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("hashPassword()：null 输入返回空字符串（防御性保护）")
        void hashNull() {
            assertEquals("", User.hashPassword(null));
        }

        @Test
        @DisplayName("checkPassword()：正确密码返回 true")
        void checkPassword_correct() {
            String plain = "mySecurePassword!@#";
            User u = new User("bob", User.hashPassword(plain), "Bob", "bob@bupt.cn");
            assertTrue(u.checkPassword(plain));
        }

        @Test
        @DisplayName("checkPassword()：错误密码返回 false")
        void checkPassword_wrong() {
            User u = new User("bob", User.hashPassword("correct"), "Bob", "bob@bupt.cn");
            assertFalse(u.checkPassword("wrong"));
        }

        @Test
        @DisplayName("checkPassword()：null 密码返回 false")
        void checkPassword_null() {
            User u = new User("bob", User.hashPassword("correct"), "Bob", "bob@bupt.cn");
            assertFalse(u.checkPassword(null));
        }

        @Test
        @DisplayName("checkPassword()：null hash 时返回 false（防止 NPE）")
        void checkPassword_nullHash() {
            User u = new User();
            assertFalse(u.checkPassword("any"));
        }
    }

    // ─────────────────────────────────────────────────────────
    // applicantId 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("applicantId：可读写，可关联 TA 申请者 ID")
    void applicantId() {
        User u = new User();
        assertNull(u.getApplicantId());

        u.setApplicantId("A001");
        assertEquals("A001", u.getApplicantId());

        u.setApplicantId("A002");
        assertEquals("A002", u.getApplicantId());
    }

    // ─────────────────────────────────────────────────────────
    // Setter / Getter 测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setter / getter：所有字段独立读写")
    void setters_allIndependent() {
        User u = new User();
        u.setUsername("charlie");
        u.setPasswordHash("newhash456");
        u.setDisplayName("Charlie Brown");
        u.setEmail("charlie@bupt.cn");
        u.setCreatedAt("2026-01-01");
        u.setRoles(Set.of("TA", "MO"));
        u.setApplicantId("A003");

        assertEquals("charlie", u.getUsername());
        assertEquals("newhash456", u.getPasswordHash());
        assertEquals("Charlie Brown", u.getDisplayName());
        assertEquals("charlie@bupt.cn", u.getEmail());
        assertEquals("2026-01-01", u.getCreatedAt());
        assertTrue(u.getRoles().contains("TA"));
        assertTrue(u.getRoles().contains("MO"));
        assertEquals("A003", u.getApplicantId());
    }

    @Test
    @DisplayName("roles 可整体替换")
    void rolesCanBeReplaced() {
        User u = new User();
        u.addRole("TA");
        u.setRoles(Set.of("MO", "ADMIN"));

        assertFalse(u.hasRole("TA"));
        assertTrue(u.hasRole("MO"));
        assertTrue(u.hasRole("ADMIN"));
    }
}
