package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 系统配置模型，包含演示账户、版本历史、功能覆盖项等前端所需数据。
 * 统一由后端管理，前端通过 /api/config 端点获取，消除硬编码。
 */
public class SystemConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    // ---- 演示账户 ----
    private List<DemoAccount> demoAccounts;
    private String appVersion;
    private String buildDate;

    // ---- 版本历史 ----
    private List<VersionEntry> versionHistory;

    // ---- 功能覆盖清单 ----
    private List<FeatureCoverage> featureCoverage;

    // ---- 文件状态配置 ----
    private List<FileStatusConfig> fileStatusConfig;

    // ---- 工作负载配置 ----
    private WorkloadConfig workloadConfig;

    // ---- 职位发布默认值 ----
    private PositionDefaults positionDefaults;

    // ---- 技能建议列表 ----
    private List<String> skillSuggestions;

    // ---- 数据追溯信息 ----
    private DataTraceability dataTraceability;

    // ---- 内部类：演示账户 ----
    public static class DemoAccount implements Serializable {
        private static final long serialVersionUID = 1L;
        private String username;
        private String password;
        private String role;
        private String displayName;

        public DemoAccount() {}
        public DemoAccount(String username, String password, String role, String displayName) {
            this.username = username;
            this.password = password;
            this.role = role;
            this.displayName = displayName;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    // ---- 内部类：版本条目 ----
    public static class VersionEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        private String version;
        private String date;
        private String title;
        private String description;

        public VersionEntry() {}
        public VersionEntry(String version, String date, String title, String description) {
            this.version = version;
            this.date = date;
            this.title = title;
            this.description = description;
        }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // ---- 内部类：功能覆盖项 ----
    public static class FeatureCoverage implements Serializable {
        private static final long serialVersionUID = 1L;
        private String icon;    // Unicode emoji 或 SVG 图标
        private String text;    // 功能描述文本

        public FeatureCoverage() {}
        public FeatureCoverage(String icon, String text) {
            this.icon = icon;
            this.text = text;
        }

        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    // ---- 内部类：文件状态配置 ----
    public static class FileStatusConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private String filename;
        private String displayName;
        private String category;

        public FileStatusConfig() {}
        public FileStatusConfig(String filename, String displayName, String category) {
            this.filename = filename;
            this.displayName = displayName;
            this.category = category;
        }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    // ---- 内部类：工作负载配置 ----
    public static class WorkloadConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private int capacity;
        private int overloadThreshold;
        private String overloadUnit;

        public WorkloadConfig() {}
        public WorkloadConfig(int capacity, int overloadThreshold, String overloadUnit) {
            this.capacity = capacity;
            this.overloadThreshold = overloadThreshold;
            this.overloadUnit = overloadUnit;
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getOverloadThreshold() { return overloadThreshold; }
        public void setOverloadThreshold(int overloadThreshold) { this.overloadThreshold = overloadThreshold; }
        public String getOverloadUnit() { return overloadUnit; }
        public void setOverloadUnit(String overloadUnit) { this.overloadUnit = overloadUnit; }
    }

    // ---- 内部类：职位发布默认值 ----
    public static class PositionDefaults implements Serializable {
        private static final long serialVersionUID = 1L;
        private int defaultHours;
        private int defaultSlots;
        private String defaultDeadline;
        private String defaultPostedBy;

        public PositionDefaults() {}
        public PositionDefaults(int defaultHours, int defaultSlots, String defaultDeadline, String defaultPostedBy) {
            this.defaultHours = defaultHours;
            this.defaultSlots = defaultSlots;
            this.defaultDeadline = defaultDeadline;
            this.defaultPostedBy = defaultPostedBy;
        }

        public int getDefaultHours() { return defaultHours; }
        public void setDefaultHours(int defaultHours) { this.defaultHours = defaultHours; }
        public int getDefaultSlots() { return defaultSlots; }
        public void setDefaultSlots(int defaultSlots) { this.defaultSlots = defaultSlots; }
        public String getDefaultDeadline() { return defaultDeadline; }
        public void setDefaultDeadline(String defaultDeadline) { this.defaultDeadline = defaultDeadline; }
        public String getDefaultPostedBy() { return defaultPostedBy; }
        public void setDefaultPostedBy(String defaultPostedBy) { this.defaultPostedBy = defaultPostedBy; }
    }

    // ---- 内部类：数据追溯信息 ----
    public static class DataTraceability implements Serializable {
        private static final long serialVersionUID = 1L;
        private String positions;
        private String applications;
        private String applicants;
        private String workloads;
        private String users;
        private String logs;
        private String cvs;

        public DataTraceability() {}
        public DataTraceability(String positions, String applications, String applicants,
                              String workloads, String users, String logs, String cvs) {
            this.positions = positions;
            this.applications = applications;
            this.applicants = applicants;
            this.workloads = workloads;
            this.users = users;
            this.logs = logs;
            this.cvs = cvs;
        }

        public String getPositions() { return positions; }
        public void setPositions(String positions) { this.positions = positions; }
        public String getApplications() { return applications; }
        public void setApplications(String applications) { this.applications = applications; }
        public String getApplicants() { return applicants; }
        public void setApplicants(String applicants) { this.applicants = applicants; }
        public String getWorkloads() { return workloads; }
        public void setWorkloads(String workloads) { this.workloads = workloads; }
        public String getUsers() { return users; }
        public void setUsers(String users) { this.users = users; }
        public String getLogs() { return logs; }
        public void setLogs(String logs) { this.logs = logs; }
        public String getCvs() { return cvs; }
        public void setCvs(String cvs) { this.cvs = cvs; }
    }

    // ---- Getter / Setter ----
    public List<DemoAccount> getDemoAccounts() { return demoAccounts; }
    public void setDemoAccounts(List<DemoAccount> demoAccounts) { this.demoAccounts = demoAccounts; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getBuildDate() { return buildDate; }
    public void setBuildDate(String buildDate) { this.buildDate = buildDate; }
    public List<VersionEntry> getVersionHistory() { return versionHistory; }
    public void setVersionHistory(List<VersionEntry> versionHistory) { this.versionHistory = versionHistory; }
    public List<FeatureCoverage> getFeatureCoverage() { return featureCoverage; }
    public void setFeatureCoverage(List<FeatureCoverage> featureCoverage) { this.featureCoverage = featureCoverage; }
    public List<FileStatusConfig> getFileStatusConfig() { return fileStatusConfig; }
    public void setFileStatusConfig(List<FileStatusConfig> fileStatusConfig) { this.fileStatusConfig = fileStatusConfig; }
    public WorkloadConfig getWorkloadConfig() { return workloadConfig; }
    public void setWorkloadConfig(WorkloadConfig workloadConfig) { this.workloadConfig = workloadConfig; }
    public PositionDefaults getPositionDefaults() { return positionDefaults; }
    public void setPositionDefaults(PositionDefaults positionDefaults) { this.positionDefaults = positionDefaults; }
    public List<String> getSkillSuggestions() { return skillSuggestions; }
    public void setSkillSuggestions(List<String> skillSuggestions) { this.skillSuggestions = skillSuggestions; }
    public DataTraceability getDataTraceability() { return dataTraceability; }
    public void setDataTraceability(DataTraceability dataTraceability) { this.dataTraceability = dataTraceability; }
}
