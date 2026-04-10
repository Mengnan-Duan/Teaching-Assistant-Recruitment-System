package com.bupt.smartta.util;

import com.bupt.smartta.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DataStore {
    private static DataStore instance;
    private static final Object initLock = new Object();
    private final JsonFileStore store;

    private static final String POSITIONS    = "positions";
    private static final String APPLICANTS   = "applicants";
    private static final String APPLICATIONS = "applications";
    private static final String LOGS        = "system_logs";
    private static final String WORKLOADS   = "workloads";
    private static final String USERS       = "users";

    private List<Position> positions;
    private List<TAPplicant> applicants;
    private List<Application> applications;
    private List<SystemLog> logs;
    private Map<String, Integer> workloadHours;
    private List<User> users;

    /** 申请者 ID 原子计数器，确保并发注册时 ID 不冲突 */
    private AtomicInteger applicantIdCounter;

    /** 申请索引缓存，按申请人ID和职位代码双重索引，加速查询 */
    private Map<String, List<Application>> applicationIndexByApplicant;
    private Map<String, List<Application>> applicationIndexByPosition;

    /** 标记是否已初始化（防止重复 seed） */
    private volatile boolean initialized = false;

    private DataStore() {
        String dataDir = System.getProperty("catalina.base")
                     + "/webapps/SmartTA/data";
        this.store = new JsonFileStore(dataDir);
        loadAll();
    }

    public static DataStore getInstance() {
        if (instance == null) {
            synchronized (initLock) {
                if (instance == null) {
                    instance = new DataStore();
                }
            }
        }
        return instance;
    }

    public synchronized void loadAll() {
        positions    = store.load(POSITIONS,    new TypeReference<List<Position>>(){});
        applicants   = store.load(APPLICANTS,   new TypeReference<List<TAPplicant>>(){});
        applications = store.load(APPLICATIONS, new TypeReference<List<Application>>(){});
        logs         = store.load(LOGS,         new TypeReference<List<SystemLog>>(){});
        workloadHours = store.loadObject(WORKLOADS, HashMap.class);
        users        = store.load(USERS,        new TypeReference<List<User>>(){});

        if (workloadHours == null) workloadHours = new HashMap<>();
        if (positions    == null) positions    = new ArrayList<>();
        if (applicants   == null) applicants   = new ArrayList<>();
        if (applications == null) applications = new ArrayList<>();
        if (logs         == null) logs         = new ArrayList<>();
        if (users        == null) users        = new ArrayList<>();

        initApplicantIdCounter();
        rebuildApplicationIndexes();

        // 只在首次初始化时执行 seed（initialized 标记控制）
        if (!initialized) {
            if (workloadHours.isEmpty()) seedWorkloads();
            if (positions.isEmpty())    seedPositions();
            if (applicants.isEmpty())  seedApplicants();
            if (applications.isEmpty()) seedApplications();
            if (users.isEmpty())       seedUsers();
            if (logs.isEmpty()) {
                addLog(SystemLog.OP_READ, POSITIONS + ".json", SystemLog.STATUS_OK);
                addLog(SystemLog.OP_READ, APPLICANTS + ".json", SystemLog.STATUS_OK);
            }
            initialized = true;
        }
    }

    /**
     * 从已有申请者列表中初始化原子计数器，
     * 确保新生成的 ID 永不与已有 ID 冲突。
     */
    private void initApplicantIdCounter() {
        int maxId = 0;
        for (TAPplicant a : applicants) {
            if (a == null || a.getId() == null) continue;
            try {
                int id = Integer.parseInt(a.getId().substring(1));
                if (id > maxId) maxId = id;
            } catch (Exception ignored) {}
        }
        applicantIdCounter = new AtomicInteger(maxId + 1);
    }

    /**
     * 分配下一个唯一的申请者 ID，使用原子计数器保证线程安全。
     */
    public String allocateNextApplicantId() {
        return "A" + String.format("%03d", applicantIdCounter.getAndIncrement());
    }

    // ---- Seed 数据 ----

    private void seedPositions() {
        positions = new ArrayList<>();
        positions.add(new Position("EBU6304","Software Engineering",
            Arrays.asList("Java","Agile","Git"), 8, 2, "2026-04-15", "Dr. J. Smith"));
        positions.add(new Position("EBU5476","Database Systems",
            Arrays.asList("SQL","Python","Linux"), 6, 3, "2026-04-20", "Dr. A. Lee"));
        positions.add(new Position("EBU4010","Computer Networks",
            Arrays.asList("Python","TCP/IP","Linux"), 4, 2, "2026-04-18", "Prof. W. Chen"));
        savePositions();
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
    }

    private void seedWorkloads() {
        for (TAPplicant ta : applicants) {
            if (ta == null) continue;
            String appId = ta.getId();
            if (appId == null || appId.isEmpty()) continue;
            if (!workloadHours.containsKey(appId)) {
                workloadHours.put(appId, 8);
            }
        }
        saveWorkloadsQuietly();
    }

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
            saveApplicantQuietly(ta);
        } else if (ue.isEmpty() && !ae.isEmpty()) {
            user.setEmail(ae);
            saveUserQuietly(user);
        }
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

        User ta1 = new User("zhangwei", User.hashPassword("ta123"), "Zhang Wei", "zhangwei@bupt.edu.cn");
        ta1.addRole("TA"); ta1.setApplicantId("A001");
        users.add(ta1);

        User ta2 = new User("limei", User.hashPassword("ta123"), "Li Mei", "limei@bupt.edu.cn");
        ta2.addRole("TA"); ta2.setApplicantId("A002");
        users.add(ta2);

        saveUsersQuietly();
    }

    /**
     * 重建申请索引缓存，在数据加载和变更后调用。
     */
    private void rebuildApplicationIndexes() {
        applicationIndexByApplicant = new HashMap<>();
        applicationIndexByPosition = new HashMap<>();
        if (applications == null) return;
        for (Application a : applications) {
            if (a == null) continue;
            String aid = a.getApplicantId();
            String pcode = a.getPositionCode();
            if (aid != null) {
                applicationIndexByApplicant
                    .computeIfAbsent(aid.toUpperCase(), k -> new ArrayList<>())
                    .add(a);
            }
            if (pcode != null) {
                applicationIndexByPosition
                    .computeIfAbsent(pcode.toUpperCase(), k -> new ArrayList<>())
                    .add(a);
            }
        }
    }

    // ---- 职位操作 ----

    public List<Position> getPositions() { return new ArrayList<>(positions); }

    public Position getPositionByCode(String code) {
        if (code == null) return null;
        return positions.stream()
                .filter(p -> p != null && p.getCode() != null && p.getCode().equalsIgnoreCase(code))
                .findFirst().orElse(null);
    }

    /** 检查职位代码是否已存在（不区分大小写） */
    public boolean positionCodeExists(String code) {
        if (code == null) return false;
        return positions.stream()
                .filter(p -> p != null && p.getCode() != null)
                .anyMatch(p -> p.getCode().equalsIgnoreCase(code));
    }

    public void addPosition(Position p) {
        if (p == null) throw new IllegalArgumentException("Position cannot be null");
        synchronized (initLock) {
            // 幂等性保护：检查 code 是否已存在
            if (positionCodeExists(p.getCode())) {
                throw new RuntimeException("Position code already exists: " + p.getCode());
            }
            positions.add(p);
            if (!savePositionsQuietly()) {
                positions.remove(p);
                throw new RuntimeException("Failed to save positions.json");
            }
            addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_OK);
        }
    }

    /** 静默保存（用于 seed 阶段，不记录日志避免递归） */
    private boolean savePositionsQuietly() {
        try {
            store.save(POSITIONS, positions);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save positions: " + e.getMessage());
            return false;
        }
    }

    public boolean savePositions() {
        try {
            store.save(POSITIONS, positions);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save positions: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    // ---- 申请者操作 ----

    public List<TAPplicant> getApplicants() { return new ArrayList<>(applicants); }

    public TAPplicant getApplicantById(String id) {
        if (id == null) return null;
        return applicants.stream()
                .filter(a -> a != null && a.getId() != null && a.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    public void saveApplicant(TAPplicant a) {
        if (a == null) throw new IllegalArgumentException("Applicant cannot be null");
        synchronized (initLock) {
            boolean found = false;
            for (int i = 0; i < applicants.size(); i++) {
                if (applicants.get(i) != null && applicants.get(i).getId() != null
                        && applicants.get(i).getId().equalsIgnoreCase(a.getId())) {
                    applicants.set(i, a);
                    found = true;
                    break;
                }
            }
            if (!found) {
                applicants.add(a);
            }
            if (!saveApplicantsQuietly()) {
                if (!found) applicants.remove(a);
                else {
                    TAPplicant original = getApplicantById(a.getId());
                    if (original != null) {
                        int idx = applicants.indexOf(a);
                        if (idx >= 0) applicants.set(idx, original);
                    }
                }
                throw new RuntimeException("Failed to save applicants.json");
            }
            addLog(SystemLog.OP_WRITE, APPLICANTS + ".json", SystemLog.STATUS_OK);
        }
    }

    private boolean saveApplicantsQuietly() {
        try {
            store.save(APPLICANTS, applicants);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applicants: " + e.getMessage());
            return false;
        }
    }

    public boolean saveApplicants() {
        try {
            store.save(APPLICANTS, applicants);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applicants: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, APPLICANTS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    private boolean saveApplicantQuietly(TAPplicant a) {
        if (a == null) return false;
        try {
            // 重新保存整个 applicants 列表
            store.save(APPLICANTS, applicants);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applicant quietly: " + e.getMessage());
            return false;
        }
    }

    private boolean saveUserQuietly(User u) {
        if (u == null) return false;
        try {
            store.save(USERS, users);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save user quietly: " + e.getMessage());
            return false;
        }
    }

    // ---- 申请表操作 ----

    public List<Application> getApplications() { return new ArrayList<>(applications); }

    public List<Application> getApplicationsByApplicantId(String applicantId) {
        if (applicantId == null) return new ArrayList<>();
        List<Application> cached = applicationIndexByApplicant.get(applicantId.toUpperCase());
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

    public List<Application> getApplicationsByPositionCode(String code) {
        if (code == null) return new ArrayList<>();
        List<Application> cached = applicationIndexByPosition.get(code.toUpperCase());
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

    public Application getApplication(String applicantId, String positionCode) {
        if (applicantId == null || positionCode == null) return null;
        return applications.stream()
            .filter(a -> a != null
                      && a.getApplicantId() != null && a.getApplicantId().equalsIgnoreCase(applicantId)
                      && a.getPositionCode() != null && a.getPositionCode().equalsIgnoreCase(positionCode))
            .findFirst().orElse(null);
    }

    /** 通过申请记录 ID 查询（新增，提高效率） */
    public Application getApplicationById(String id) {
        if (id == null) return null;
        return applications.stream()
            .filter(a -> a != null && a.getId() != null && a.getId().equals(id))
            .findFirst().orElse(null);
    }

    public void addApplication(Application app) {
        if (app == null) throw new IllegalArgumentException("Application cannot be null");
        synchronized (initLock) {
            String aidKey = app.getApplicantId() != null ? app.getApplicantId().toUpperCase() : "";
            String pcodeKey = app.getPositionCode() != null ? app.getPositionCode().toUpperCase() : "";
            applications.add(app);
            applicationIndexByApplicant.computeIfAbsent(aidKey, k -> new ArrayList<>()).add(app);
            applicationIndexByPosition.computeIfAbsent(pcodeKey, k -> new ArrayList<>()).add(app);
            if (!saveApplicationsQuietly()) {
                applications.remove(app);
                List<Application> aidList = applicationIndexByApplicant.get(aidKey);
                if (aidList != null) aidList.remove(app);
                List<Application> pcodeList = applicationIndexByPosition.get(pcodeKey);
                if (pcodeList != null) pcodeList.remove(app);
                throw new RuntimeException("Failed to save applications.json");
            }
            addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_OK);
        }
    }

    /** 增量更新申请索引（替代全量重建，提升性能） */
    private void updateApplicationIndexes(Application app, String oldApplicantId, String oldPositionCode) {
        // 从旧索引中移除
        if (oldApplicantId != null) {
            List<Application> oldAidList = applicationIndexByApplicant.get(oldApplicantId.toUpperCase());
            if (oldAidList != null) oldAidList.remove(app);
        }
        if (oldPositionCode != null) {
            List<Application> oldPcodeList = applicationIndexByPosition.get(oldPositionCode.toUpperCase());
            if (oldPcodeList != null) oldPcodeList.remove(app);
        }
        // 添加到新索引
        String aidKey = app.getApplicantId() != null ? app.getApplicantId().toUpperCase() : "";
        String pcodeKey = app.getPositionCode() != null ? app.getPositionCode().toUpperCase() : "";
        applicationIndexByApplicant.computeIfAbsent(aidKey, k -> new ArrayList<>()).add(app);
        applicationIndexByPosition.computeIfAbsent(pcodeKey, k -> new ArrayList<>()).add(app);
    }

    public void updateApplication(Application app) {
        if (app == null) throw new IllegalArgumentException("Application cannot be null");
        synchronized (initLock) {
            String oldApplicantId = null;
            String oldPositionCode = null;
            for (int i = 0; i < applications.size(); i++) {
                if (applications.get(i) != null && applications.get(i).getId().equals(app.getId())) {
                    oldApplicantId = applications.get(i).getApplicantId();
                    oldPositionCode = applications.get(i).getPositionCode();
                    applications.set(i, app);
                    updateApplicationIndexes(app, oldApplicantId, oldPositionCode);
                    if (!saveApplicationsQuietly()) {
                        // 回滚索引
                        updateApplicationIndexes(applications.get(i), app.getApplicantId(), app.getPositionCode());
                        applications.set(i, applications.get(i)); // 恢复原值
                        throw new RuntimeException("Failed to save applications.json");
                    }
                    addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_OK);
                    return;
                }
            }
        }
    }

    private boolean saveApplicationsQuietly() {
        try {
            store.save(APPLICATIONS, applications);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applications: " + e.getMessage());
            return false;
        }
    }

    public boolean saveApplications() {
        try {
            store.save(APPLICATIONS, applications);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applications: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    // ---- 日志操作 ----

    public List<SystemLog> getLogs() { return new ArrayList<>(logs); }

    public void addLog(String op, String file, String status) {
        addLog(new SystemLog(op, file, status));
    }

    public void addLog(String op, String file, String status, String detail) {
        addLog(new SystemLog(op, file, status, detail));
    }

    public void addLog(SystemLog log) {
        if (log == null) return;
        logs.add(0, log);
        if (logs.size() > 200) logs = new ArrayList<>(logs.subList(0, 200));
        try {
            store.save(LOGS, logs);
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save logs: " + e.getMessage());
        }
    }

    // ---- 工作量操作 ----

    public Map<String, Integer> getWorkloadHours() { return new HashMap<>(workloadHours); }

    public void setWorkloadHours(String applicantId, int hours) {
        if (applicantId == null) return;
        synchronized (initLock) {
            workloadHours.put(applicantId, hours);
            try {
                store.saveObject(WORKLOADS, workloadHours);
            } catch (IOException e) {
                System.err.println("[DataStore] Failed to save workloads: " + e.getMessage());
                addLog(SystemLog.OP_WRITE, WORKLOADS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
                throw new RuntimeException("Failed to save workloads.json");
            }
            addLog(SystemLog.OP_WRITE, WORKLOADS + ".json", SystemLog.STATUS_OK);
        }
    }

    public int getWorkloadHours(String applicantId) {
        if (applicantId == null) return 0;
        Integer val = workloadHours.get(applicantId);
        return val != null ? val : 0;
    }

    private boolean saveWorkloadsQuietly() {
        try {
            store.saveObject(WORKLOADS, workloadHours);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save workloads: " + e.getMessage());
            return false;
        }
    }

    // ---- 用户操作 ----

    public List<User> getUsers() { return new ArrayList<>(users); }

    public User getUserByUsername(String username) {
        if (username == null) return null;
        return users.stream()
                .filter(u -> u != null && u.getUsername() != null && u.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    public void saveUser(User u) {
        if (u == null) throw new IllegalArgumentException("User cannot be null");
        synchronized (initLock) {
            boolean found = false;
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i) != null && users.get(i).getUsername() != null
                        && users.get(i).getUsername().equalsIgnoreCase(u.getUsername())) {
                    users.set(i, u);
                    found = true;
                    break;
                }
            }
            if (!found) {
                users.add(u);
            }
            if (!saveUsersQuietly()) {
                if (!found) users.remove(u);
                else {
                    User original = getUserByUsername(u.getUsername());
                    if (original != null) {
                        int idx = users.indexOf(u);
                        if (idx >= 0) users.set(idx, original);
                    }
                }
                throw new RuntimeException("Failed to save users.json");
            }
            addLog(SystemLog.OP_WRITE, USERS + ".json", SystemLog.STATUS_OK);
        }
    }

    private boolean saveUsersQuietly() {
        try {
            store.save(USERS, users);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save users: " + e.getMessage());
            return false;
        }
    }

    public boolean saveUsers() {
        try {
            store.save(USERS, users);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save users: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, USERS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    public JsonFileStore getStore() { return store; }

    // ============================================================
    // SystemConfig 懒加载（只读，不需要频繁刷新）
    // ============================================================

    private static final String SYSTEM_CONFIG = "system_config";
    private volatile SystemConfig systemConfig = null;

    /**
     * 获取系统配置（懒加载，配置变更时调用 reloadSystemConfig()）。
     */
    public SystemConfig getSystemConfig() {
        if (systemConfig == null) {
            synchronized (initLock) {
                if (systemConfig == null) {
                    reloadSystemConfig();
                }
            }
        }
        return systemConfig;
    }

    /**
     * 重新加载系统配置（当配置被修改时调用）。
     */
    public void reloadSystemConfig() {
        try {
            systemConfig = store.loadObject(SYSTEM_CONFIG, SystemConfig.class);
            if (systemConfig == null) {
                // 配置不存在时创建默认配置
                systemConfig = createDefaultSystemConfig();
            }
        } catch (Exception e) {
            System.err.println("[DataStore] Failed to load system_config: " + e.getMessage());
            systemConfig = createDefaultSystemConfig();
        }
    }

    /**
     * 创建默认系统配置（仅在配置文件损坏或不存在时使用）。
     */
    private SystemConfig createDefaultSystemConfig() {
        SystemConfig cfg = new SystemConfig();
        cfg.setAppVersion("2.0");
        cfg.setBuildDate(java.time.LocalDate.now().toString());

        SystemConfig.WorkloadConfig wc = new SystemConfig.WorkloadConfig(20, 20, "h/week");
        cfg.setWorkloadConfig(wc);

        SystemConfig.PositionDefaults pd = new SystemConfig.PositionDefaults(8, 2, "2026-04-30", "Admin");
        cfg.setPositionDefaults(pd);

        cfg.setSkillSuggestions(Arrays.asList(
            "Java", "Python", "JavaScript", "Git", "Agile", "SQL",
            "React", "Node.js", "Machine Learning", "Docker"
        ));
        return cfg;
    }
}
