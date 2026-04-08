package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.List;

/**
 * 系统配置模型，包含演示账户、版本历史、功能覆盖项等前端所需数据。
 * 统一由后端管理，前端通过 /api/config 端点获取，消除硬编码。
 */
public class SystemConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<DemoAccount> demoAccounts;
    private String appVersion;
    private String buildDate;
    private List<VersionEntry> versionHistory;
    private List<FeatureCoverage> featureCoverage;
    private List<FileStatusConfig> fileStatusConfig;
    private WorkloadConfig workloadConfig;
    private PositionDefaults positionDefaults;
    private List<String> skillSuggestions;
    private DataTraceability dataTraceability;

    public static class DemoAccount implements Serializable {
        private static final long serialVersionUID = 1L;
        private String username;
        private String password;
        private String role;
        private String displayName;

        public DemoAccount() {}
        public DemoAccount(String username, String password, String role, String displayName) {
            this.username = username; this.password = password;
            this.role = role; this.displayName = displayName;
        }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
        public String getRole() { return role; }
        public void setRole(String v) { this.role = v; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String v) { this.displayName = v; }
    }

    public static class VersionEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        private String version;
        private String date;
        private String title;
        private String description;
        public VersionEntry() {}
        public VersionEntry(String v, String d, String t, String desc) {
            this.version = v; this.date = d; this.title = t; this.description = desc;
        }
        public String getVersion() { return version; }
        public void setVersion(String v) { this.version = v; }
        public String getDate() { return date; }
        public void setDate(String v) { this.date = v; }
        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    public static class FeatureCoverage implements Serializable {
        private static final long serialVersionUID = 1L;
        private String icon;
        private String text;
        public FeatureCoverage() {}
        public FeatureCoverage(String icon, String text) { this.icon = icon; this.text = text; }
        public String getIcon() { return icon; }
        public void setIcon(String v) { this.icon = v; }
        public String getText() { return text; }
        public void setText(String v) { this.text = v; }
    }

    public static class FileStatusConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private String filename;
        private String displayName;
        private String category;
        public FileStatusConfig() {}
        public FileStatusConfig(String fn, String dn, String cat) {
            this.filename = fn; this.displayName = dn; this.category = cat;
        }
        public String getFilename() { return filename; }
        public void setFilename(String v) { this.filename = v; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String v) { this.displayName = v; }
        public String getCategory() { return category; }
        public void setCategory(String v) { this.category = v; }
    }

    public static class WorkloadConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private int capacity;
        private int overloadThreshold;
        private String overloadUnit;
        public WorkloadConfig() {}
        public WorkloadConfig(int cap, int thr, String unit) {
            this.capacity = cap; this.overloadThreshold = thr; this.overloadUnit = unit;
        }
        public int getCapacity() { return capacity; }
        public void setCapacity(int v) { this.capacity = v; }
        public int getOverloadThreshold() { return overloadThreshold; }
        public void setOverloadThreshold(int v) { this.overloadThreshold = v; }
        public String getOverloadUnit() { return overloadUnit; }
        public void setOverloadUnit(String v) { this.overloadUnit = v; }
    }

    public static class PositionDefaults implements Serializable {
        private static final long serialVersionUID = 1L;
        private int defaultHours;
        private int defaultSlots;
        private String defaultDeadline;
        private String defaultPostedBy;
        public PositionDefaults() {}
        public PositionDefaults(int h, int s, String dl, String pb) {
            this.defaultHours = h; this.defaultSlots = s;
            this.defaultDeadline = dl; this.defaultPostedBy = pb;
        }
        public int getDefaultHours() { return defaultHours; }
        public void setDefaultHours(int v) { this.defaultHours = v; }
        public int getDefaultSlots() { return defaultSlots; }
        public void setDefaultSlots(int v) { this.defaultSlots = v; }
        public String getDefaultDeadline() { return defaultDeadline; }
        public void setDefaultDeadline(String v) { this.defaultDeadline = v; }
        public String getDefaultPostedBy() { return defaultPostedBy; }
        public void setDefaultPostedBy(String v) { this.defaultPostedBy = v; }
    }

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
        public DataTraceability(String p, String a, String ap, String w, String u, String l, String c) {
            this.positions = p; this.applications = a; this.applicants = ap;
            this.workloads = w; this.users = u; this.logs = l; this.cvs = c;
        }
        public String getPositions() { return positions; }
        public void setPositions(String v) { this.positions = v; }
        public String getApplications() { return applications; }
        public void setApplications(String v) { this.applications = v; }
        public String getApplicants() { return applicants; }
        public void setApplicants(String v) { this.applicants = v; }
        public String getWorkloads() { return workloads; }
        public void setWorkloads(String v) { this.workloads = v; }
        public String getUsers() { return users; }
        public void setUsers(String v) { this.users = v; }
        public String getLogs() { return logs; }
        public void setLogs(String v) { this.logs = v; }
        public String getCvs() { return cvs; }
        public void setCvs(String v) { this.cvs = v; }
    }

    // Getter/Setter
    public List<DemoAccount> getDemoAccounts() { return demoAccounts; }
    public void setDemoAccounts(List<DemoAccount> v) { this.demoAccounts = v; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String v) { this.appVersion = v; }
    public String getBuildDate() { return buildDate; }
    public void setBuildDate(String v) { this.buildDate = v; }
    public List<VersionEntry> getVersionHistory() { return versionHistory; }
    public void setVersionHistory(List<VersionEntry> v) { this.versionHistory = v; }
    public List<FeatureCoverage> getFeatureCoverage() { return featureCoverage; }
    public void setFeatureCoverage(List<FeatureCoverage> v) { this.featureCoverage = v; }
    public List<FileStatusConfig> getFileStatusConfig() { return fileStatusConfig; }
    public void setFileStatusConfig(List<FileStatusConfig> v) { this.fileStatusConfig = v; }
    public WorkloadConfig getWorkloadConfig() { return workloadConfig; }
    public void setWorkloadConfig(WorkloadConfig v) { this.workloadConfig = v; }
    public PositionDefaults getPositionDefaults() { return positionDefaults; }
    public void setPositionDefaults(PositionDefaults v) { this.positionDefaults = v; }
    public List<String> getSkillSuggestions() { return skillSuggestions; }
    public void setSkillSuggestions(List<String> v) { this.skillSuggestions = v; }
    public DataTraceability getDataTraceability() { return dataTraceability; }
    public void setDataTraceability(DataTraceability v) { this.dataTraceability = v; }
}
