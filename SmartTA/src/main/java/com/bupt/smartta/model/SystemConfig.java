package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.List;

/**
 * Holds system-wide configuration data used by the Smart-TA frontend and backend.
 *
 * <p>This model is loaded lazily from {@code system_config.json} via
 * {@link com.bupt.smartta.util.DataStore#getSystemConfig()}. It provides demo
 * account credentials, application version info, feature coverage checklists,
 * workload thresholds, position defaults, skill suggestion lists, and data
 * traceability metadata — all in one place rather than being hardcoded in JSP pages.</p>
 *
 * <p>Several inner static classes represent structured sub-configurations:</p>
 * <ul>
 *   <li>{@link DemoAccount} — pre-configured demo login credentials</li>
 *   <li>{@link VersionEntry} — version history entries</li>
 *   <li>{@link FeatureCoverage} — feature checklist items with icons</li>
 *   <li>{@link FileStatusConfig} — data file metadata for the Data Traceability panel</li>
 *   <li>{@link WorkloadConfig} — TA workload capacity settings</li>
 *   <li>{@link PositionDefaults} — default values for new position creation</li>
 *   <li>{@link DataTraceability} — paths/last-modified metadata for each data file</li>
 * </ul>
 *
 * @see com.bupt.smartta.util.DataStore
 */
public class SystemConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /** List of pre-configured demo accounts for evaluation. */
    private List<DemoAccount> demoAccounts;
    /** Current application version string (e.g., "3.0"). */
    private String appVersion;
    /** Build date of the application in ISO format. */
    private String buildDate;

    /** Version history entries describing past releases. */
    private List<VersionEntry> versionHistory;

    /** Feature coverage checklist items (icon + text). */
    private List<FeatureCoverage> featureCoverage;

    /** Data file status entries for the Data Traceability panel. */
    private List<FileStatusConfig> fileStatusConfig;

    /** TA workload configuration (capacity and overload threshold). */
    private WorkloadConfig workloadConfig;

    /** Default values used when creating a new position. */
    private PositionDefaults positionDefaults;

    /** Pre-defined skill suggestions shown in the TA profile editor. */
    private List<String> skillSuggestions;

    /** Data traceability metadata (paths and last-modified info for each JSON file). */
    private DataTraceability dataTraceability;

    // ---- Inner class: Demo Account ----
    /**
     * Represents a pre-configured demo account for evaluation purposes.
     */
    public static class DemoAccount implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Demo login username. */
        private String username;
        /** Demo plaintext password (for display in the About panel). */
        private String password;
        /** Role assigned to this demo account. */
        private String role;
        /** Display name of the account holder. */
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

    // ---- Inner class: Version Entry ----
    /**
     * Represents a single version history entry.
     */
    public static class VersionEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Version string (e.g., "3.0"). */
        private String version;
        /** Release date. */
        private String date;
        /** Brief title of this release. */
        private String title;
        /** Detailed description of changes. */
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

    // ---- Inner class: Feature Coverage Item ----
    /**
     * Represents a feature coverage checklist item with an icon and description.
     */
    public static class FeatureCoverage implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Icon — either a Unicode emoji or an SVG path. */
        private String icon;
        /** Feature description text. */
        private String text;

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

    // ---- Inner class: File Status Config ----
    /**
     * Represents the metadata for a single data file shown in the Data Traceability panel.
     */
    public static class FileStatusConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Actual filename on disk (without path). */
        private String filename;
        /** Human-readable display name for the UI. */
        private String displayName;
        /** Logical category (e.g., "Applicant Data", "System Data"). */
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

    // ---- Inner class: Workload Config ----
    /**
     * Configures the TA workload capacity and overload detection threshold.
     */
    public static class WorkloadConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Maximum safe weekly hours per TA. */
        private int capacity;
        /** Hours-per-week value above which a TA is considered overloaded. */
        private int overloadThreshold;
        /** Human-readable unit label for the threshold (e.g., "h/week"). */
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

    // ---- Inner class: Position Defaults ----
    /**
     * Default values applied when an MO creates a new position.
     */
    public static class PositionDefaults implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Default weekly hours. */
        private int defaultHours;
        /** Default total TA slots. */
        private int defaultSlots;
        /** Default application deadline date. */
        private String defaultDeadline;
        /** Default "posted by" value. */
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

    // ---- Inner class: Data Traceability ----
    /**
     * Holds file path and last-modified metadata for each persistent data file.
     */
    public static class DataTraceability implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Path to positions.json. */
        private String positions;
        /** Path to applications.json. */
        private String applications;
        /** Path to applicants.json. */
        private String applicants;
        /** Path to workloads.json. */
        private String workloads;
        /** Path to users.json. */
        private String users;
        /** Path to system_logs.json. */
        private String logs;
        /** Path to cv_uploads/ directory. */
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
