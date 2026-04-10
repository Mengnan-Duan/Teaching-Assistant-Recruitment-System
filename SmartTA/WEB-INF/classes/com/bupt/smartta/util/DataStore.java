package com.bupt.smartta.util;

import com.bupt.smartta.model.*;
import java.io.IOException;
import java.util.*;

public class DataStore {
    private static DataStore instance;
    private final JsonFileStore store;

    private static final String POSITIONS    = "positions";
    private static final String APPLICANTS   = "applicants";
    private static final String APPLICATIONS  = "applications";
    private static final String LOGS         = "system_logs";
    private static final String WORKLOADS    = "workloads";
    private static final String USERS        = "users";
    private static final String SYSTEM_CONFIG = "system_config";

    private List<Position> positions;
    private List<TAPplicant> applicants;
    private List<Application> applications;
    private List<SystemLog> logs;
    private Map<String, Integer> workloadHours;
    private List<User> users;
    private SystemConfig systemConfig;

    private DataStore() {
        String dataDir = System.getProperty("catalina.base")
                     + "/webapps/SmartTA/data";
        this.store = new JsonFileStore(dataDir);
        loadAll();
    }

    public static synchronized DataStore getInstance() {
        if (instance == null) instance = new DataStore();
        return instance;
    }

    public void loadAll() {
        positions     = store.load(POSITIONS,    Position.class);
        applicants    = store.load(APPLICANTS,   TAPplicant.class);
        applications  = store.load(APPLICATIONS,  Application.class);
        logs          = store.load(LOGS,         SystemLog.class);
        workloadHours = store.loadObject(WORKLOADS, HashMap.class);
        users         = store.load(USERS,        User.class);
        systemConfig  = store.loadObject(SYSTEM_CONFIG, SystemConfig.class);
        if (systemConfig == null) {
            seedSystemConfig();
        } else {
            ensureFileStatusConfigCompleteness();
        }
        if (workloadHours == null) workloadHours = new HashMap<>();
        if (workloadHours.isEmpty()) seedWorkloads();
        if (users == null) users = new ArrayList<>();

        if (positions.isEmpty())    seedPositions();
        repairPositionSlotsIfInvalid();
        if (applicants.isEmpty())   seedApplicants();
        if (applications.isEmpty())  seedApplications();
        if (users.isEmpty())          seedUsers();
        if (logs.isEmpty()) {
            addLog(SystemLog.OP_READ, POSITIONS   + ".json", SystemLog.STATUS_OK);
            addLog(SystemLog.OP_READ, APPLICANTS  + ".json", SystemLog.STATUS_OK);
        }
        ensureWorkloadsForAllTaUsers();
    }

    /** 为每个已绑定 applicant 的 TA 用户补齐 workloads 条目，避免前端显示 0h。 */
    private void ensureWorkloadsForAllTaUsers() {
        boolean changed = false;
        for (User u : users) {
            if (!u.hasRole("TA")) continue;
            String aid = u.getApplicantId();
            if (aid == null || aid.isEmpty()) continue;
            if (!workloadHours.containsKey(aid)) {
                workloadHours.put(aid, 8);
                changed = true;
            }
        }
        if (changed) {
            try {
                store.saveObject(WORKLOADS, workloadHours);
            } catch (IOException e) {
                System.err.println("[DataStore] Failed to save workloads: " + e.getMessage());
            }
        }
    }

    // ---- Seed data ----

    /**
     * 自动同步 User.email 与 TAPplicant.email（优先 User.email）。
     * 每次登录时由 AuthServlet 调用，确保两边邮箱一致。
     */
    public void syncUserAndApplicantEmails(String username) {
        User user = getUserByUsername(username);
        if (user == null) return;
        String aid = user.getApplicantId();
        if (aid == null || aid.isEmpty()) return;
        TAPplicant ta = getApplicantById(aid);
        if (ta == null) return;
        String ue = user.getEmail() == null ? "" : user.getEmail().trim();
        String ae = ta.getEmail() == null ? "" : ta.getEmail().trim();
        if (!ue.isEmpty() && ae.isEmpty()) {
            ta.setEmail(ue);
            saveApplicant(ta);
        } else if (ue.isEmpty() && !ae.isEmpty()) {
            user.setEmail(ae);
            saveUser(user);
        }
    }

    /**
     * 初始化系统配置（演示账户、版本历史、功能覆盖项等）。
     * 仅在 system_config.json 不存在时调用。
     */
    private void seedSystemConfig() {
        systemConfig = new SystemConfig();
        systemConfig.setAppVersion("2.0");
        systemConfig.setBuildDate("2026-04-08");

        // 演示账户
        List<SystemConfig.DemoAccount> demos = new ArrayList<>();
        demos.add(new SystemConfig.DemoAccount("admin", "admin123", "ADMIN", "Administrator"));
        demos.add(new SystemConfig.DemoAccount("mosmith", "mo123", "MO", "Dr. J. Smith"));
        demos.add(new SystemConfig.DemoAccount("zhangwei", "ta123", "TA", "Zhang Wei"));
        demos.add(new SystemConfig.DemoAccount("limei", "ta123", "TA", "Li Mei"));
        systemConfig.setDemoAccounts(demos);

        // 版本历史
        List<SystemConfig.VersionEntry> versions = new ArrayList<>();
        versions.add(new SystemConfig.VersionEntry("2.0", "2026-04-05", "Mid-Term Assessment",
            "JSON file persistence, REST API, AI scoring engine, system log viewer, Workload Monitor, MoSCoW traceability matrix"));
        versions.add(new SystemConfig.VersionEntry("1.1", "2026-03-29", "Working Software v1",
            "Role-based dashboards, static data, mock file-save feedback, form validation"));
        versions.add(new SystemConfig.VersionEntry("1.0", "2026-03-22", "First Assessment",
            "Product backlog, low-fidelity HTML prototype, brief report, stakeholder interviews, user stories"));
        systemConfig.setVersionHistory(versions);

        // 功能覆盖清单
        List<SystemConfig.FeatureCoverage> coverage = new ArrayList<>();
        coverage.add(new SystemConfig.FeatureCoverage("&#10003;", "TA: profile, CV, apply, track"));
        coverage.add(new SystemConfig.FeatureCoverage("&#10003;", "MO: post job, rank, accept/reject"));
        coverage.add(new SystemConfig.FeatureCoverage("&#10003;", "Admin: workload, logs, file status"));
        coverage.add(new SystemConfig.FeatureCoverage("&#10003;", "AI: skill matching, gap analysis"));
        coverage.add(new SystemConfig.FeatureCoverage("&#10003;", "Persistence: JSON file I/O"));
        coverage.add(new SystemConfig.FeatureCoverage("&#10003;", "Traceability matrix"));
        systemConfig.setFeatureCoverage(coverage);

        // 文件状态配置
        List<SystemConfig.FileStatusConfig> fileCfgs = new ArrayList<>();
        fileCfgs.add(new SystemConfig.FileStatusConfig("positions.json", "Positions", "Academic Data"));
        fileCfgs.add(new SystemConfig.FileStatusConfig("applicants.json", "Applicants", "Academic Data"));
        fileCfgs.add(new SystemConfig.FileStatusConfig("applications.json", "Applications", "Academic Data"));
        fileCfgs.add(new SystemConfig.FileStatusConfig("workloads.json", "Workloads", "Academic Data"));
        fileCfgs.add(new SystemConfig.FileStatusConfig("system_logs.json", "System Logs", "System Data"));
        fileCfgs.add(new SystemConfig.FileStatusConfig("users.json", "Users", "System Data"));
        fileCfgs.add(new SystemConfig.FileStatusConfig("system_config.json", "System Config", "System Data"));
        systemConfig.setFileStatusConfig(fileCfgs);

        // 工作负载配置
        systemConfig.setWorkloadConfig(new SystemConfig.WorkloadConfig(20, 20, "h/week"));

        // 职位发布默认值
        systemConfig.setPositionDefaults(new SystemConfig.PositionDefaults(8, 2, "2026-04-30", "Dr. J. Smith"));

        // 技能建议列表
        List<String> skills = new ArrayList<>();
        skills.addAll(Arrays.asList("Java","Python","JavaScript","Git","Agile","SQL",
            "React","Node.js","Machine Learning","Data Analysis","Docker","Linux","TCP/IP","CSS","HTML"));
        systemConfig.setSkillSuggestions(skills);

        // 数据追溯信息
        systemConfig.setDataTraceability(new SystemConfig.DataTraceability(
            "positions.json", "applications.json", "applicants.json",
            "workloads.json", "users.json", "system_logs.json", "cv_uploads/"));

        try {
            store.saveObject(SYSTEM_CONFIG, systemConfig);
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to seed system_config: " + e.getMessage());
        }
    }

    /** 修复 totalSlots 为 0 等异常数据，避免招聘表出现 0/0、状态 Closed。 */
    private void repairPositionSlotsIfInvalid() {
        if (positions == null || positions.isEmpty()) return;
        boolean changed = false;
        for (Position p : positions) {
            if (p.getCode() == null) continue;
            if (p.getTotalSlots() <= 0) {
                int def = 2;
                if (p.getCode().equalsIgnoreCase("EBU5476")) def = 3;
                int f = Math.max(0, p.getFilledSlots());
                p.setTotalSlots(Math.max(def, f));
                changed = true;
            }
            if (p.getFilledSlots() > p.getTotalSlots()) {
                p.setFilledSlots(p.getTotalSlots());
                changed = true;
            }
            if (p.getStatus() == null || p.getStatus().trim().isEmpty()) {
                p.setStatus("Open");
                changed = true;
            }
        }
        if (changed) {
            savePositions();
            addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_OK);
        }
    }

    /** 若旧版 JSON 缺少 system_config 条目则补全并写回磁盘。 */
    private void ensureFileStatusConfigCompleteness() {
        if (systemConfig == null || systemConfig.getFileStatusConfig() == null) return;
        List<SystemConfig.FileStatusConfig> list = systemConfig.getFileStatusConfig();
        for (SystemConfig.FileStatusConfig fc : list) {
            if (fc != null && "system_config.json".equals(fc.getFilename())) return;
        }
        list.add(new SystemConfig.FileStatusConfig("system_config.json", "System Config", "System Data"));
        saveSystemConfig();
    }

    private void seedUsers() {
        users = new ArrayList<>();
        User admin = new User("admin", User.hashPassword("admin123"), "System Admin", "admin@bupt.edu.cn");
        admin.addRole("ADMIN"); admin.addRole("MO"); admin.addRole("TA");
        admin.setApplicantId("A008");
        users.add(admin);

        User mo = new User("mosmith", User.hashPassword("mo123"), "Dr. J. Smith", "jsmith@bupt.edu.cn");
        mo.addRole("MO"); mo.addRole("TA");
        users.add(mo);

        User ta = new User("zhangwei", User.hashPassword("ta123"), "Zhang Wei", "zhangwei@bupt.edu.cn");
        ta.addRole("TA"); ta.setApplicantId("A001");
        users.add(ta);

        User ta2 = new User("limei", User.hashPassword("ta123"), "Li Mei", "limei@bupt.edu.cn");
        ta2.addRole("TA"); ta2.setApplicantId("A002");
        users.add(ta2);

        saveUsers();
    }

    private void seedPositions() {
        positions = new ArrayList<>();
        positions.add(new Position("EBU6304","Software Engineering",
            Arrays.asList("Java","Agile","Git"), 8, 2, "2026-04-15", "Dr. J. Smith"));
        positions.add(new Position("EBU5476","Database Systems",
            Arrays.asList("SQL","Python","Linux"), 6, 3, "2026-04-20", "Dr. A. Lee"));
        positions.add(new Position("EBU4010","Computer Networks",
            Arrays.asList("Python","TCP/IP","Linux"), 4, 2, "2026-04-18", "Prof. W. Chen"));
        savePositions();
        addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_OK);
    }

    private void seedApplicants() {
        applicants = new ArrayList<>();
        applicants.add(new TAPplicant("A001","Zhang Wei","zhangwei@bupt.edu.cn","Year 3",3.7,Arrays.asList("Java","Agile","Git","SQL"),12));
        applicants.add(new TAPplicant("A002","Li Mei","limei@bupt.edu.cn","Year 2",3.5,Arrays.asList("Java","Git","SQL","Python"),16));
        applicants.add(new TAPplicant("A003","Wang Hao","wanghao@bupt.edu.cn","Year 3",3.3,Arrays.asList("Java","Python"),6));
        applicants.add(new TAPplicant("A004","Chen Yu","chenyu@bupt.edu.cn","Year 4",3.8,Arrays.asList("Java","Agile","Git","SQL"),4));
        applicants.add(new TAPplicant("A005","Zhao Lin","zhaolin@bupt.edu.cn","Year 2",3.2,Arrays.asList("Python","Git"),14));
        applicants.add(new TAPplicant("A006","Liu Na","liuna@bupt.edu.cn","Year 3",3.6,Arrays.asList("Java","Python","SQL"),10));
        applicants.add(new TAPplicant("A007","Zhang Yunhe","yunhezhang@bupt.edu.cn","Year 2",3.4,Arrays.asList("Java","Git","SQL"),10));
        applicants.add(new TAPplicant("A008","System Administrator","admin@bupt.edu.cn","Staff",0.0,Arrays.asList("System Administration","Java","Security"),0));
        saveApplicants();
        addLog(SystemLog.OP_WRITE, APPLICANTS + ".json", SystemLog.STATUS_OK);
    }

    private void seedApplications() {
        applications = new ArrayList<>();
        if (!applicants.isEmpty() && !positions.isEmpty()) {
            TAPplicant ta = applicants.get(0);
            Position pos = positions.get(0);
            int score = (int) ta.computeAIScore(pos.getRequiredSkills(), pos.getHoursPerWeek());
            Application app = new Application(ta.getId(), ta.getName(),
                pos.getCode(), pos.getName(), score);
            app.setStatus(Application.STATUS_REVIEW);
            applications.add(app);

            if (applicants.size() > 1) {
                TAPplicant ta2 = applicants.get(1);
                int score2 = (int) ta2.computeAIScore(pos.getRequiredSkills(), pos.getHoursPerWeek());
                Application app2 = new Application(ta2.getId(), ta2.getName(),
                    pos.getCode(), pos.getName(), score2);
                applications.add(app2);
            }
        }
        saveApplications();
        addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_OK);
    }

    // ---- CRUD for positions ----
    public List<Position> getPositions() { return new ArrayList<>(positions); }
    public Position getPositionByCode(String code) {
        return positions.stream().filter(p -> p.getCode().equals(code)).findFirst().orElse(null);
    }
    public void addPosition(Position p) {
        positions.add(p);
        savePositions();
        addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_OK);
    }
    public void savePositions() {
        try { store.save(POSITIONS, positions); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---- CRUD for applicants ----
    public List<TAPplicant> getApplicants() { return new ArrayList<>(applicants); }
    public TAPplicant getApplicantById(String id) {
        if (id == null || id.isEmpty()) return null;
        return applicants.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
    }

    /** Next id A001, A002, … based on max existing numeric suffix (not list size). */
    public synchronized String allocateNextApplicantId() {
        int max = 0;
        for (TAPplicant a : applicants) {
            String id = a.getId();
            if (id == null || id.length() < 2 || !id.startsWith("A")) continue;
            try {
                int n = Integer.parseInt(id.substring(1));
                if (n > max) max = n;
            } catch (NumberFormatException ignored) { }
        }
        return "A" + String.format("%03d", max + 1);
    }
    public void saveApplicant(TAPplicant a) {
        for (int i = 0; i < applicants.size(); i++) {
            if (applicants.get(i).getId().equals(a.getId())) {
                applicants.set(i, a);
                saveApplicants();
                addLog(SystemLog.OP_WRITE, APPLICANTS + ".json", SystemLog.STATUS_OK);
                return;
            }
        }
        applicants.add(a);
        saveApplicants();
        addLog(SystemLog.OP_WRITE, APPLICANTS + ".json", SystemLog.STATUS_OK);
    }
    public void saveApplicants() {
        try { store.save(APPLICANTS, applicants); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---- CRUD for applications ----
    public List<Application> getApplications() { return new ArrayList<>(applications); }
    public List<Application> getApplicationsByApplicantId(String applicantId) {
        List<Application> result = new ArrayList<>();
        for (Application a : applications) {
            if (a.getApplicantId().equals(applicantId)) result.add(a);
        }
        return result;
    }
    public List<Application> getApplicationsByPositionCode(String code) {
        List<Application> result = new ArrayList<>();
        for (Application a : applications) {
            if (a.getPositionCode().equals(code)) result.add(a);
        }
        return result;
    }
    public Application getApplication(String applicantId, String positionCode) {
        return applications.stream()
            .filter(a -> a.getApplicantId().equals(applicantId) && a.getPositionCode().equals(positionCode))
            .findFirst().orElse(null);
    }
    public void addApplication(Application app) {
        applications.add(app);
        saveApplications();
        addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_OK);
    }
    public void updateApplication(Application app) {
        for (int i = 0; i < applications.size(); i++) {
            if (applications.get(i).getId().equals(app.getId())) {
                applications.set(i, app);
                saveApplications();
                addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_OK);
                return;
            }
        }
    }
    public void saveApplications() {
        try { store.save(APPLICATIONS, applications); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---- Logs ----
    public List<SystemLog> getLogs() { return new ArrayList<>(logs); }
    public void addLog(String op, String file, String status) {
        logs.add(0, new SystemLog(op, file, status));
        if (logs.size() > 200) logs = logs.subList(0, 200);
        try { store.save(LOGS, logs); } catch (IOException e) { e.printStackTrace(); }
    }
    public void addLog(SystemLog log) {
        logs.add(0, log);
        if (logs.size() > 200) logs = logs.subList(0, 200);
        try { store.save(LOGS, logs); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---- Workload ----
    public Map<String, Integer> getWorkloadHours() { return new HashMap<>(workloadHours); }
    public void setWorkloadHours(String applicantId, int hours) {
        workloadHours.put(applicantId, hours);
        try { store.saveObject(WORKLOADS, workloadHours); } catch (IOException e) { e.printStackTrace(); }
        addLog(SystemLog.OP_WRITE, WORKLOADS + ".json", SystemLog.STATUS_OK);
    }
    public int getWorkloadHours(String applicantId) {
        return workloadHours.getOrDefault(applicantId, 0);
    }

    // ---- CRUD for users ----
    public List<User> getUsers() { return new ArrayList<>(users); }
    public User getUserByUsername(String username) {
        if (username == null) return null;
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) return u;
        }
        return null;
    }
    public void saveUser(User user) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equalsIgnoreCase(user.getUsername())) {
                users.set(i, user);
                saveUsers();
                return;
            }
        }
        users.add(user);
        saveUsers();
    }
    private void saveUsers() {
        try { store.save(USERS, users); } catch (IOException e) { e.printStackTrace(); }
    }

    /** 从用户表中删除账号（不删除关联的 TAPplicant 记录）。 */
    public boolean deleteUser(String username) {
        if (username == null || username.isEmpty()) return false;
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equalsIgnoreCase(username)) {
                users.remove(i);
                saveUsers();
                addLog(SystemLog.OP_WRITE, USERS + ".json", SystemLog.STATUS_OK);
                return true;
            }
        }
        return false;
    }

    /**
     * 删除申请者档案及其全部申请；若有用户仍绑定该 applicantId 则失败。
     */
    public boolean deleteApplicantCascade(String applicantId) {
        if (applicantId == null || applicantId.isEmpty()) return false;
        for (User u : users) {
            String aid = u.getApplicantId();
            if (aid != null && aid.equalsIgnoreCase(applicantId)) return false;
        }
        applications.removeIf(a -> a.getApplicantId().equalsIgnoreCase(applicantId));
        saveApplications();
        applicants.removeIf(a -> a.getId().equalsIgnoreCase(applicantId));
        saveApplicants();
        workloadHours.remove(applicantId);
        try {
            store.saveObject(WORKLOADS, workloadHours);
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save workloads after applicant delete: " + e.getMessage());
        }
        addLog(SystemLog.OP_WRITE, APPLICANTS + ".json", SystemLog.STATUS_OK);
        return true;
    }

    /** 统计仍拥有 ADMIN 角色的用户数量。 */
    public int countAdminUsers() {
        int n = 0;
        for (User u : users) {
            if (u.hasRole("ADMIN")) n++;
        }
        return n;
    }

    private void seedWorkloads() {
        // Only initialize workload for TAs who have a real user account
        for (User u : users) {
            if (!u.hasRole("TA")) continue;
            String appId = u.getApplicantId();
            if (appId == null || appId.isEmpty()) continue;
            if (!workloadHours.containsKey(appId)) {
                // Set a reasonable default: 8 hours for new TAs
                workloadHours.put(appId, 8);
            }
        }
        try { store.saveObject(WORKLOADS, workloadHours); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---- System Config ----

    /** 获取系统配置（演示账户、版本历史、功能覆盖项等）。 */
    public SystemConfig getSystemConfig() { return systemConfig; }

    /** 保存系统配置。 */
    public boolean saveSystemConfig() {
        try {
            store.saveObject(SYSTEM_CONFIG, systemConfig);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save system_config: " + e.getMessage());
            return false;
        }
    }

    public JsonFileStore getStore() { return store; }
}
