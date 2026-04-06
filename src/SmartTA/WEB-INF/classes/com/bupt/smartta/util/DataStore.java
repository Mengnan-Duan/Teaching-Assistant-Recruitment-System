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

    private List<Position> positions;
    private List<TAPplicant> applicants;
    private List<Application> applications;
    private List<SystemLog> logs;
    private Map<String, Integer> workloadHours;
    private List<User> users;

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
        if (workloadHours == null) workloadHours = new HashMap<>();
        if (users == null) users = new ArrayList<>();

        if (positions.isEmpty())    seedPositions();
        if (applicants.isEmpty())   seedApplicants();
        if (applications.isEmpty())  seedApplications();
        if (users.isEmpty())          seedUsers();
        if (logs.isEmpty()) {
            addLog(SystemLog.OP_READ, POSITIONS   + ".json", SystemLog.STATUS_OK);
            addLog(SystemLog.OP_READ, APPLICANTS  + ".json", SystemLog.STATUS_OK);
        }
    }

    // ---- Seed data ----
    private void seedUsers() {
        users = new ArrayList<>();
        // Admin account: admin / admin123
        User admin = new User("admin", User.hashPassword("admin123"), "System Admin", "admin@bupt.edu.cn");
        admin.addRole("ADMIN");
        admin.addRole("MO");
        admin.addRole("TA");
        users.add(admin);

        // MO account: mosmith / mo123
        User mo = new User("mosmith", User.hashPassword("mo123"), "Dr. J. Smith", "jsmith@bupt.edu.cn");
        mo.addRole("MO");
        mo.addRole("TA");
        users.add(mo);

        // TA account: zhangwei / ta123  (linked to A001)
        User ta = new User("zhangwei", User.hashPassword("ta123"), "Zhang Wei", "zhangwei@bupt.edu.cn");
        ta.addRole("TA");
        ta.setApplicantId("A001");
        users.add(ta);

        // TA account: limei / ta123  (linked to A002)
        User ta2 = new User("limei", User.hashPassword("ta123"), "Li Mei", "limei@bupt.edu.cn");
        ta2.addRole("TA");
        ta2.setApplicantId("A002");
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

    public JsonFileStore getStore() { return store; }
}
