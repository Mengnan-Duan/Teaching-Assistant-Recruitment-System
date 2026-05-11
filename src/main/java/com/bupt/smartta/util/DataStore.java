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

    public void loadAll() {
        positions    = store.load(POSITIONS,    new TypeReference<List<Position>>(){});
        applicants   = store.load(APPLICANTS,   new TypeReference<List<TAPplicant>>(){});
        applications = store.load(APPLICATIONS, new TypeReference<List<Application>>(){});
        logs         = store.load(LOGS,         new TypeReference<List<SystemLog>>(){});
        workloadHours = store.loadObject(WORKLOADS, HashMap.class);
        users        = store.load(USERS,        new TypeReference<List<User>>(){});

        if (workloadHours == null) workloadHours = new HashMap<>();
        initApplicantIdCounter();
        rebuildApplicationIndexes();

        if (workloadHours.isEmpty()) seedWorkloads();
        if (positions.isEmpty())    seedPositions();
        if (applicants.isEmpty())  seedApplicants();
        if (applications.isEmpty()) seedApplications();
        if (users.isEmpty())       seedUsers();
        if (logs.isEmpty()) {
            addLog(SystemLog.OP_READ, POSITIONS + ".json", SystemLog.STATUS_OK);
            addLog(SystemLog.OP_READ, APPLICANTS + ".json", SystemLog.STATUS_OK);
        }
    }

    /**
     * 从已有申请者列表中初始化原子计数器，
     * 确保新生成的 ID 永不与已有 ID 冲突。
     */
    private void initApplicantIdCounter() {
        int maxId = 0;
        for (TAPplicant a : applicants) {
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
        applicants.add(new TAPplicant("A001","Zhang Wei","zhangwei@bupt.edu.cn",
            "Year 3",3.7,Arrays.asList("Java","Agile","Git","SQL"),12));
        applicants.add(new TAPplicant("A002","Li Mei","limei@bupt.edu.cn",
            "Year 2",3.5,Arrays.asList("Java","Git","SQL","Python"),16));
        applicants.add(new TAPplicant("A003","Wang Hao","wanghao@bupt.edu.cn",
            "Year 3",3.3,Arrays.asList("Java","Python"),6));
        applicants.add(new TAPplicant("A004","Chen Yu","chenyu@bupt.edu.cn",
            "Year 4",3.8,Arrays.asList("Java","Agile","Git","SQL"),4));
        applicants.add(new TAPplicant("A005","Zhao Lin","zhaolin@bupt.edu.cn",
            "Year 2",3.2,Arrays.asList("Python","Git"),14));
        applicants.add(new TAPplicant("A006","Liu Na","liuna@bupt.edu.cn",
            "Year 3",3.6,Arrays.asList("Java","Python","SQL"),10));
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
            String appId = ta.getId();
            if (appId == null || appId.isEmpty()) continue;
            if (!workloadHours.containsKey(appId)) {
                workloadHours.put(appId, 8);
            }
        }
        saveWorkloadsQuietly();
    }

    private void seedUsers() {
        users = new ArrayList<>();
        users.add(new User("admin", User.hashPassword("admin123"), "Administrator", "admin@bupt.edu.cn"));
        users.get(0).addRole("ADMIN");

        User mo = new User("mosmith", User.hashPassword("mo123"), "Dr. J. Smith", "mosmith@bupt.edu.cn");
        mo.addRole("MO");
        users.add(mo);

        User ta1 = new User("zhangwei", User.hashPassword("ta123"), "Zhang Wei", "zhangwei@bupt.edu.cn");
        ta1.addRole("TA");
        ta1.setApplicantId("A001");
        users.add(ta1);

        User ta2 = new User("limei", User.hashPassword("ta123"), "Li Mei", "limei@bupt.edu.cn");
        ta2.addRole("TA");
        ta2.setApplicantId("A002");
        users.add(ta2);

        saveUsersQuietly();
    }

    /**
     * 重建申请索引缓存，在数据加载和变更后调用。
     */
    private void rebuildApplicationIndexes() {
        applicationIndexByApplicant = new HashMap<>();
        applicationIndexByPosition = new HashMap<>();
        for (Application a : applications) {
            applicationIndexByApplicant
                .computeIfAbsent(a.getApplicantId().toUpperCase(), k -> new ArrayList<>())
                .add(a);
            applicationIndexByPosition
                .computeIfAbsent(a.getPositionCode().toUpperCase(), k -> new ArrayList<>())
                .add(a);
        }
    }

    // ---- 职位操作 ----

    public List<Position> getPositions() { return new ArrayList<>(positions); }

    public Position getPositionByCode(String code) {
        return positions.stream()
                .filter(p -> p.getCode().equalsIgnoreCase(code))
                .findFirst().orElse(null);
    }

    /** 检查职位代码是否已存在（不区分大小写） */
    public boolean positionCodeExists(String code) {
        return positions.stream()
                .anyMatch(p -> p.getCode().equalsIgnoreCase(code));
    }

    public void addPosition(Position p) {
        synchronized (initLock) {
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
        return applicants.stream()
                .filter(a -> a.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    public void saveApplicant(TAPplicant a) {
        synchronized (initLock) {
            boolean found = false;
            for (int i = 0; i < applicants.size(); i++) {
                if (applicants.get(i).getId().equalsIgnoreCase(a.getId())) {
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
                    if (original != null) applicants.set(applicants.indexOf(a), original);
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

    // ---- 申请表操作 ----

    public List<Application> getApplications() { return new ArrayList<>(applications); }

    public List<Application> getApplicationsByApplicantId(String applicantId) {
        List<Application> cached = applicationIndexByApplicant.get(applicantId.toUpperCase());
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

    public List<Application> getApplicationsByPositionCode(String code) {
        List<Application> cached = applicationIndexByPosition.get(code.toUpperCase());
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

    public Application getApplication(String applicantId, String positionCode) {
        return applications.stream()
            .filter(a -> a.getApplicantId().equalsIgnoreCase(applicantId)
                      && a.getPositionCode().equalsIgnoreCase(positionCode))
            .findFirst().orElse(null);
    }

    public void addApplication(Application app) {
        synchronized (initLock) {
            applications.add(app);
            applicationIndexByApplicant
                .computeIfAbsent(app.getApplicantId().toUpperCase(), k -> new ArrayList<>())
                .add(app);
            applicationIndexByPosition
                .computeIfAbsent(app.getPositionCode().toUpperCase(), k -> new ArrayList<>())
                .add(app);
            if (!saveApplicationsQuietly()) {
                applications.remove(app);
                applicationIndexByApplicant.get(app.getApplicantId().toUpperCase()).remove(app);
                applicationIndexByPosition.get(app.getPositionCode().toUpperCase()).remove(app);
                throw new RuntimeException("Failed to save applications.json");
            }
            addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_OK);
        }
    }

    public void updateApplication(Application app) {
        synchronized (initLock) {
            for (int i = 0; i < applications.size(); i++) {
                if (applications.get(i).getId().equals(app.getId())) {
                    applications.set(i, app);
                    rebuildApplicationIndexes(); // 索引重建
                    if (!saveApplicationsQuietly()) {
                        rebuildApplicationIndexes(); // 回滚时重建
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
        return workloadHours.getOrDefault(applicantId, 0);
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
        return users.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    public void saveUser(User u) {
        synchronized (initLock) {
            boolean found = false;
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getUsername().equalsIgnoreCase(u.getUsername())) {
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
                    if (original != null) users.set(users.indexOf(u), original);
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
}
