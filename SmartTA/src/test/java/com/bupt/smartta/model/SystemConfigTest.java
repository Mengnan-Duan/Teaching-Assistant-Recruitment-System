package com.bupt.smartta.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemConfig 模型类及其内部类的单元测试。
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>所有内部类构造器、getter / setter</li>
 *   <li>顶层 SystemConfig getter / setter</li>
 *   <li>嵌套类实例化链：DemoAccount → SystemConfig → VersionEntry 等</li>
 * </ul>
 */
@DisplayName("SystemConfig 模型测试")
class SystemConfigTest {

    // ─────────────────────────────────────────────────────────
    // DemoAccount 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DemoAccount")
    class DemoAccountTests {

        @Test
        @DisplayName("默认构造器 + setter 可正常赋值")
        void defaultConstructor() {
            SystemConfig.DemoAccount da = new SystemConfig.DemoAccount();
            da.setUsername("alice");
            da.setPassword("password123");
            da.setRole("TA");
            da.setDisplayName("Alice Smith");

            assertEquals("alice", da.getUsername());
            assertEquals("password123", da.getPassword());
            assertEquals("TA", da.getRole());
            assertEquals("Alice Smith", da.getDisplayName());
        }

        @Test
        @DisplayName("全参数构造器：所有字段正确赋值")
        void fullConstructor() {
            SystemConfig.DemoAccount da = new SystemConfig.DemoAccount(
                    "bob", "secret456", "MO", "Prof. Bob"
            );
            assertEquals("bob", da.getUsername());
            assertEquals("secret456", da.getPassword());
            assertEquals("MO", da.getRole());
            assertEquals("Prof. Bob", da.getDisplayName());
        }
    }

    // ─────────────────────────────────────────────────────────
    // VersionEntry 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("VersionEntry")
    class VersionEntryTests {

        @Test
        @DisplayName("默认构造器 + setter 可正常赋值")
        void defaultConstructor() {
            SystemConfig.VersionEntry ve = new SystemConfig.VersionEntry();
            ve.setVersion("2.1.0");
            ve.setDate("2026-04-01");
            ve.setTitle("New MO Portal Features");
            ve.setDescription("Added pending TA management.");

            assertEquals("2.1.0", ve.getVersion());
            assertEquals("2026-04-01", ve.getDate());
            assertEquals("New MO Portal Features", ve.getTitle());
            assertEquals("Added pending TA management.", ve.getDescription());
        }

        @Test
        @DisplayName("全参数构造器：所有字段正确赋值")
        void fullConstructor() {
            SystemConfig.VersionEntry ve = new SystemConfig.VersionEntry(
                    "1.0.0", "2026-01-15", "Initial Release", "Beta version"
            );
            assertEquals("1.0.0", ve.getVersion());
            assertEquals("2026-01-15", ve.getDate());
            assertEquals("Initial Release", ve.getTitle());
            assertEquals("Beta version", ve.getDescription());
        }
    }

    // ─────────────────────────────────────────────────────────
    // FeatureCoverage 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FeatureCoverage")
    class FeatureCoverageTests {

        @Test
        @DisplayName("全参数构造器")
        void fullConstructor() {
            SystemConfig.FeatureCoverage fc = new SystemConfig.FeatureCoverage("E", "AI Matching");
            assertEquals("E", fc.getIcon());
            assertEquals("AI Matching", fc.getText());
        }

        @Test
        @DisplayName("setter / getter")
        void setters() {
            SystemConfig.FeatureCoverage fc = new SystemConfig.FeatureCoverage();
            fc.setIcon("X");
            fc.setText("Role-based Access");
            assertEquals("X", fc.getIcon());
            assertEquals("Role-based Access", fc.getText());
        }
    }

    // ─────────────────────────────────────────────────────────
    // FileStatusConfig 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FileStatusConfig")
    class FileStatusConfigTests {

        @Test
        @DisplayName("全参数构造器")
        void fullConstructor() {
            SystemConfig.FileStatusConfig fsc = new SystemConfig.FileStatusConfig(
                    "syllabus_v1.pdf", "Syllabus v1", "Teaching Material"
            );
            assertEquals("syllabus_v1.pdf", fsc.getFilename());
            assertEquals("Syllabus v1", fsc.getDisplayName());
            assertEquals("Teaching Material", fsc.getCategory());
        }
    }

    // ─────────────────────────────────────────────────────────
    // WorkloadConfig 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WorkloadConfig")
    class WorkloadConfigTests {

        @Test
        @DisplayName("全参数构造器")
        void fullConstructor() {
            SystemConfig.WorkloadConfig wc = new SystemConfig.WorkloadConfig(20, 25, "h/week");
            assertEquals(20, wc.getCapacity());
            assertEquals(25, wc.getOverloadThreshold());
            assertEquals("h/week", wc.getOverloadUnit());
        }

        @Test
        @DisplayName("setter / getter")
        void setters() {
            SystemConfig.WorkloadConfig wc = new SystemConfig.WorkloadConfig();
            wc.setCapacity(15);
            wc.setOverloadThreshold(20);
            wc.setOverloadUnit("hrs/week");
            assertEquals(15, wc.getCapacity());
            assertEquals(20, wc.getOverloadThreshold());
            assertEquals("hrs/week", wc.getOverloadUnit());
        }
    }

    // ─────────────────────────────────────────────────────────
    // PositionDefaults 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PositionDefaults")
    class PositionDefaultsTests {

        @Test
        @DisplayName("全参数构造器")
        void fullConstructor() {
            SystemConfig.PositionDefaults pd = new SystemConfig.PositionDefaults(
                    10, 3, "2026-06-30", "Prof. Li"
            );
            assertEquals(10, pd.getDefaultHours());
            assertEquals(3, pd.getDefaultSlots());
            assertEquals("2026-06-30", pd.getDefaultDeadline());
            assertEquals("Prof. Li", pd.getDefaultPostedBy());
        }
    }

    // ─────────────────────────────────────────────────────────
    // DataTraceability 内部类测试
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DataTraceability")
    class DataTraceabilityTests {

        @Test
        @DisplayName("全参数构造器")
        void fullConstructor() {
            SystemConfig.DataTraceability dt = new SystemConfig.DataTraceability(
                    "positions.json", "applications.json", "applicants.json",
                    "workloads.json", "users.json", "system_logs.json", "cvs/"
            );
            assertEquals("positions.json", dt.getPositions());
            assertEquals("applications.json", dt.getApplications());
            assertEquals("applicants.json", dt.getApplicants());
            assertEquals("workloads.json", dt.getWorkloads());
            assertEquals("users.json", dt.getUsers());
            assertEquals("system_logs.json", dt.getLogs());
            assertEquals("cvs/", dt.getCvs());
        }
    }

    // ─────────────────────────────────────────────────────────
    // SystemConfig 顶层类测试
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SystemConfig：内部类可作为属性正常赋值和读取")
    void systemConfig_withInnerClasses() {
        SystemConfig cfg = new SystemConfig();

        cfg.setAppVersion("2.1.0");
        cfg.setBuildDate("2026-04-10");

        SystemConfig.DemoAccount da = new SystemConfig.DemoAccount("alice", "pass", "TA", "Alice");
        cfg.setDemoAccounts(List.of(da));

        SystemConfig.VersionEntry ve = new SystemConfig.VersionEntry("2.1.0", "2026-04-01", "Update", "Added features.");
        cfg.setVersionHistory(List.of(ve));

        SystemConfig.FeatureCoverage fc = new SystemConfig.FeatureCoverage("R", "AI Match");
        cfg.setFeatureCoverage(List.of(fc));

        SystemConfig.WorkloadConfig wc = new SystemConfig.WorkloadConfig(20, 25, "h/week");
        cfg.setWorkloadConfig(wc);

        SystemConfig.PositionDefaults pd = new SystemConfig.PositionDefaults(10, 3, "2026-06-30", "Prof.");
        cfg.setPositionDefaults(pd);

        cfg.setSkillSuggestions(Arrays.asList("Java", "Python", "Agile"));

        SystemConfig.DataTraceability dt = new SystemConfig.DataTraceability(
                "p.json", "a.json", "ap.json", "w.json", "u.json", "l.json", "cvs/"
        );
        cfg.setDataTraceability(dt);

        assertEquals("2.1.0", cfg.getAppVersion());
        assertEquals("2026-04-10", cfg.getBuildDate());
        assertEquals(1, cfg.getDemoAccounts().size());
        assertEquals("alice", cfg.getDemoAccounts().get(0).getUsername());
        assertEquals(1, cfg.getVersionHistory().size());
        assertEquals(1, cfg.getFeatureCoverage().size());
        assertEquals(20, cfg.getWorkloadConfig().getCapacity());
        assertEquals(3, cfg.getPositionDefaults().getDefaultSlots());
        assertEquals(3, cfg.getSkillSuggestions().size());
        assertEquals("a.json", cfg.getDataTraceability().getApplications());
    }
}
