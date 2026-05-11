package com.bupt.smartta.util;

import com.bupt.smartta.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton data-access layer for the Smart-TA platform.
 *
 * <p>{@code DataStore} manages all persistent data — positions, applicants, applications,
 * users, workloads, audit logs, and MO-TA messages — backed by JSON files in the
 * {@code data/} directory. It provides thread-safe CRUD operations, automatic
 * application state transitions, application-index caching for fast lookups, and
 * lazy-loaded system configuration.</p>
 *
 * <p>Data files are stored under {@code ${catalina.base}/webapps/SmartTA/data/} when
 * running on Tomcat, or under {@code data/} relative to the webapp root in IDE
 * development mode.</p>
 *
 * <p>This class is a singleton: obtain the single instance via {@link #getInstance()}
 * and never construct it directly.</p>
 *
 * @see com.bupt.smartta.model.Position
 * @see com.bupt.smartta.model.TAPplicant
 * @see com.bupt.smartta.model.Application
 * @see com.bupt.smartta.model.User
 */
public class DataStore {
    private static DataStore instance;
    private static final Object initLock = new Object();

    /** Jackson ObjectMapper shared across all read/write operations. */
    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        // Ignore properties present in JSON but absent from Java classes (e.g., "permanentlyBlocked")
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static final String POSITIONS      = "positions";
    private static final String APPLICANTS     = "applicants";
    private static final String APPLICATIONS   = "applications";
    private static final String LOGS           = "system_logs";
    private static final String WORKLOADS      = "workloads";
    private static final String USERS          = "users";
    private static final String MOTA_MESSAGES  = "mota_messages";
    private static final String SYSTEM_CONFIG   = "system_config";
    private static final String WORKLOAD_SUGGESTION = "workload_suggestion";

    /** Absolute path to the data directory. */
    private final String dataDir;

    private List<Position> positions;
    private List<TAPplicant> applicants;
    private List<Application> applications;
    private List<SystemLog> logs;
    private Map<String, Integer> workloadHours;
    private List<User> users;
    private List<MoTaMessage> motaMessages;
    private LLMService.RebalanceAdvice workloadSuggestion;

    /** Atomic counter for generating sequential applicant IDs (e.g., A001, A002, ...). */
    private AtomicInteger applicantIdCounter;

    /** In-memory application index: applicantId -> list of their applications. */
    private Map<String, List<Application>> applicationIndexByApplicant;
    /** In-memory application index: positionCode -> list of applications for that position. */
    private Map<String, List<Application>> applicationIndexByPosition;

    /** Flag indicating whether initial seed data has been loaded. */
    private volatile boolean initialized = false;

    /**
     * Private constructor — initialises the data directory path and loads all JSON files.
     *
     * <p>Search order for data directory:</p>
     * <ol>
     *   <li>{@code ${catalina.base}/webapps/SmartTA/data} (production Tomcat)</li>
     *   <li>Derived from classpath (IDE development mode)</li>
     * </ol>
     *
     * @throws SecurityException if the resolved path is deemed unsafe (path traversal attempt)
     */
    private DataStore() {
        // Production: catalina.base/webapps/SmartTA/data
        // Fallback: derive from classpath (IDE development mode)
        String catalinaBase = System.getProperty("catalina.base", "");
        if (!catalinaBase.isEmpty()) {
            this.dataDir = catalinaBase + File.separator + "webapps" + File.separator + "SmartTA" + File.separator + "data";
        } else {
            String classPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            File classDir = new File(classPath).getParentFile();
            File webInfDir = classDir != null ? classDir.getParentFile() : null;
            File webappDir = webInfDir != null ? webInfDir.getParentFile() : null;
            this.dataDir = webappDir != null ? webappDir.getPath() + File.separator + "data" : "data";
        }

        // Path safety check: reject absolute paths, drive letters, or path-traversal sequences
        if (!isPathSafe(this.dataDir)) {
            throw new SecurityException("DataStore path contains potentially unsafe characters: " + this.dataDir);
        }

        System.out.println("[DataStore] ========== Initialization Start ==========");
        System.out.println("[DataStore] dataDir = " + dataDir);
        System.out.println("[DataStore] dataDir exists = " + new File(dataDir).exists());

        loadAll();

        System.out.println("[DataStore] positions loaded: " + positions.size());
        System.out.println("[DataStore] applicants loaded: " + applicants.size());
        System.out.println("[DataStore] users loaded: " + users.size());
        System.out.println("[DataStore] ========== Initialization Complete ==========");
    }

    /**
     * Validates that the given path does not contain path-traversal sequences or
     * other suspicious patterns that could be exploited in an attack.
     *
     * @param path the path to validate
     * @return {@code true} if the path is considered safe, {@code false} otherwise
     */
    private static boolean isPathSafe(String path) {
        if (path == null || path.isEmpty()) return false;
        String normalized = path.replace("\\", "/");
        if (normalized.contains("..")) return false;  // parent-directory reference
        if (normalized.startsWith("/")) return false; // absolute path
        if (path.matches("^[A-Za-z]:[/\\\\].*")) return false; // drive letter
        return true;
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

    // ============================================================
    // Load all data: read JSON files directly
    // ============================================================
    private synchronized void loadAll() {
        // Simple direct load
        positions     = loadList(POSITIONS, Position.class);
        applicants    = loadList(APPLICANTS, TAPplicant.class);
        applications  = loadList(APPLICATIONS, Application.class);
        logs          = loadList(LOGS, SystemLog.class);
        workloadHours = loadObject(WORKLOADS, HashMap.class);
        users         = loadList(USERS, User.class);
        motaMessages  = loadList(MOTA_MESSAGES, MoTaMessage.class);
        workloadSuggestion = loadObject(WORKLOAD_SUGGESTION, LLMService.RebalanceAdvice.class);

        // Null safety
        if (workloadHours == null) workloadHours = new HashMap<>();
        if (positions    == null) positions    = new ArrayList<>();
        if (applicants   == null) applicants   = new ArrayList<>();
        if (applications == null) applications = new ArrayList<>();
        if (logs         == null) logs         = new ArrayList<>();
        if (users        == null) users        = new ArrayList<>();
        if (motaMessages == null) motaMessages = new ArrayList<>();

        initApplicantIdCounter();
        rebuildApplicationIndexes();

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

    // ============================================================
    // Simple load: read file directly, no locking
    // ============================================================
    private <T> List<T> loadList(String name, Class<T> clazz) {
        Path path = Paths.get(dataDir, name + ".json");
        if (!Files.exists(path)) {
            System.out.println("[DataStore] File not found: " + path);
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(path);
            List<T> result = MAPPER.readValue(json,
                MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, clazz));
            System.out.println("[DataStore] Loaded " + name + ".json, count: " + result.size());
            return result;
        } catch (Exception e) {
            System.err.println("[DataStore] Failed to load " + name + ".json: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private <T> T loadObject(String name, Class<T> clazz) {
        Path path = Paths.get(dataDir, name + ".json");
        if (!Files.exists(path)) {
            System.out.println("[DataStore] File not found: " + path);
            return null;
        }
        try {
            String json = Files.readString(path);
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            System.err.println("[DataStore] Failed to load " + name + ".json (object): " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // Simple save: write file directly
    // ============================================================
    private <T> void saveList(String name, List<T> list) throws IOException {
        Path path = Paths.get(dataDir, name + ".json");
        String json = MAPPER.writeValueAsString(list);
        Files.writeString(path, json);
        System.out.println("[DataStore] Saved " + name + ".json, count: " + list.size());
    }

    private <T> void saveObject(String name, Object obj) throws IOException {
        Path path = Paths.get(dataDir, name + ".json");
        String json = MAPPER.writeValueAsString(obj);
        Files.writeString(path, json);
        System.out.println("[DataStore] Saved " + name + ".json (object)");
    }

    // ============================================================
    // Applicant ID counter
    // ============================================================
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

    public String allocateNextApplicantId() {
        return "A" + String.format("%03d", applicantIdCounter.getAndIncrement());
    }

    // ---- Seed Data ----
    /**
     * Seeds initial workload entries for all existing applicants.
     * Called only when the workloads file is empty during initialisation.
     */
    private void seedPositions() { /* Data cleared — no auto-seeding */ }
    private void seedApplicants() { /* Data cleared — no auto-seeding */ }
    private void seedApplications() { /* Data cleared — no auto-seeding */ }
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

    private void seedUsers() { /* Data cleared — no auto-seeding */ }

    // ---- Application Index ----
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

    // ---- Position Operations ----
    public List<Position> getPositions() { return new ArrayList<>(positions); }

    public Position getPositionByCode(String code) {
        if (code == null) return null;
        return positions.stream()
                .filter(p -> p != null && p.getCode() != null && p.getCode().equalsIgnoreCase(code))
                .findFirst().orElse(null);
    }

    public boolean positionCodeExists(String code) {
        if (code == null) return false;
        return positions.stream()
                .filter(p -> p != null && p.getCode() != null)
                .anyMatch(p -> p.getCode().equalsIgnoreCase(code));
    }

    public void addPosition(Position p) {
        if (p == null) throw new IllegalArgumentException("Position cannot be null");
        synchronized (initLock) {
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

    private boolean savePositionsQuietly() {
        try {
            saveList(POSITIONS, positions);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save positions: " + e.getMessage());
            return false;
        }
    }

    public boolean savePositions() {
        try {
            saveList(POSITIONS, positions);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save positions: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    // ---- Applicant Operations ----
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
            saveList(APPLICANTS, applicants);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applicants: " + e.getMessage());
            return false;
        }
    }

    public boolean saveApplicants() {
        try {
            saveList(APPLICANTS, applicants);
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
            saveList(APPLICANTS, applicants);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applicant quietly: " + e.getMessage());
            return false;
        }
    }

    private boolean saveUserQuietly(User u) {
        if (u == null) return false;
        try {
            saveList(USERS, users);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save user quietly: " + e.getMessage());
            return false;
        }
    }

    // ---- Application Operations ----
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

    private void updateApplicationIndexes(Application app, String oldApplicantId, String oldPositionCode) {
        if (oldApplicantId != null) {
            List<Application> oldAidList = applicationIndexByApplicant.get(oldApplicantId.toUpperCase());
            if (oldAidList != null) oldAidList.remove(app);
        }
        if (oldPositionCode != null) {
            List<Application> oldPcodeList = applicationIndexByPosition.get(oldPositionCode.toUpperCase());
            if (oldPcodeList != null) oldPcodeList.remove(app);
        }
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
                        updateApplicationIndexes(applications.get(i), app.getApplicantId(), app.getPositionCode());
                        applications.set(i, applications.get(i));
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
            saveList(APPLICATIONS, applications);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applications: " + e.getMessage());
            return false;
        }
    }

    public boolean saveApplications() {
        try {
            saveList(APPLICATIONS, applications);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save applications: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, APPLICATIONS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    // ---- Log Operations ----
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
            saveList(LOGS, logs);
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save logs: " + e.getMessage());
        }
    }

    // ---- Workload Operations ----
    public Map<String, Integer> getWorkloadHours() { return new HashMap<>(workloadHours); }

    public void setWorkloadHours(String applicantId, int hours) {
        if (applicantId == null) return;
        synchronized (initLock) {
            workloadHours.put(applicantId, hours);
            try {
                saveObject(WORKLOADS, workloadHours);
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
            saveObject(WORKLOADS, workloadHours);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save workloads: " + e.getMessage());
            return false;
        }
    }

    // ---- Pending Workload Suggestion Operations ----
    public LLMService.RebalanceAdvice getPendingWorkloadSuggestion() {
        return workloadSuggestion;
    }

    public void saveWorkloadSuggestion(LLMService.RebalanceAdvice suggestion) {
        synchronized (initLock) {
            this.workloadSuggestion = suggestion;
            try {
                saveObject(WORKLOAD_SUGGESTION, suggestion);
            } catch (IOException e) {
                System.err.println("[DataStore] Failed to save workload_suggestion: " + e.getMessage());
                throw new RuntimeException("Failed to save workload_suggestion.json");
            }
            addLog(SystemLog.OP_WRITE, WORKLOAD_SUGGESTION + ".json", SystemLog.STATUS_OK);
        }
    }

    public void clearWorkloadSuggestion() {
        synchronized (initLock) {
            this.workloadSuggestion = null;
            try {
                saveObject(WORKLOAD_SUGGESTION, null);
            } catch (IOException e) {
                System.err.println("[DataStore] Failed to clear workload_suggestion: " + e.getMessage());
            }
            addLog(SystemLog.OP_WRITE, WORKLOAD_SUGGESTION + ".json", SystemLog.STATUS_OK);
        }
    }

    // ---- User Operations ----
    public List<User> getUsers() { return new ArrayList<>(users); }

    public boolean removeUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        synchronized (initLock) {
            int idx = -1;
            User found = null;
            for (int i = 0; i < users.size(); i++) {
                User u = users.get(i);
                if (u != null && u.getUsername() != null
                        && u.getUsername().equalsIgnoreCase(username.trim())) {
                    idx = i;
                    found = u;
                    break;
                }
            }
            if (idx < 0 || found == null) return false;
            users.remove(idx);
            if (!saveUsersQuietly()) {
                users.add(idx, found);
                return false;
            }
            addLog(SystemLog.OP_WRITE, USERS + ".json", SystemLog.STATUS_OK);
            return true;
        }
    }

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
            saveList(USERS, users);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save users: " + e.getMessage());
            return false;
        }
    }

    public boolean saveUsers() {
        try {
            saveList(USERS, users);
            return true;
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save users: " + e.getMessage());
            addLog(SystemLog.OP_WRITE, USERS + ".json", SystemLog.STATUS_FAIL, e.getMessage());
            return false;
        }
    }

    public String getDataDir() { return dataDir; }

    // ---- MO/TA Messaging ----
    public synchronized void addMoTaMessage(MoTaMessage m) {
        if (m == null) return;
        motaMessages.add(m);
        saveMoTaMessagesQuietly();
        addLog(SystemLog.OP_WRITE, MOTA_MESSAGES + ".json", SystemLog.STATUS_OK);
    }

    public List<MoTaMessage> getMoTaMessagesSnapshot() {
        return new ArrayList<>(motaMessages);
    }

    public synchronized void markMoTaThreadRead(String moUsername, String taApplicantId, String readerUsername) {
        if (moUsername == null || taApplicantId == null || readerUsername == null) return;
        boolean changed = false;
        for (MoTaMessage msg : motaMessages) {
            if (msg == null) continue;
            if (!moUsername.equalsIgnoreCase(msg.getMoUsername())) continue;
            if (!taApplicantId.equalsIgnoreCase(msg.getTaApplicantId())) continue;
            if (readerUsername.equalsIgnoreCase(msg.getToUsername()) && !msg.isReadByRecipient()) {
                msg.setReadByRecipient(true);
                changed = true;
            }
        }
        if (changed) saveMoTaMessagesQuietly();
    }

    public int countUnreadMoTaForUser(String username) {
        if (username == null) return 0;
        int n = 0;
        for (MoTaMessage msg : motaMessages) {
            if (msg != null && username.equalsIgnoreCase(msg.getToUsername()) && !msg.isReadByRecipient()) {
                n++;
            }
        }
        return n;
    }

    private void saveMoTaMessagesQuietly() {
        try {
            saveList(MOTA_MESSAGES, motaMessages);
        } catch (IOException e) {
            System.err.println("[DataStore] Failed to save mota_messages: " + e.getMessage());
        }
    }

    public User findUserByApplicantId(String applicantId) {
        if (applicantId == null) return null;
        for (User u : users) {
            if (u != null && applicantId.equalsIgnoreCase(u.getApplicantId())) return u;
        }
        return null;
    }

    public User findMoUserByPostedByLabel(String postedBy) {
        if (postedBy == null || postedBy.isEmpty()) return null;
        String t = postedBy.trim();
        for (User u : users) {
            if (u == null || !u.hasRole("MO")) continue;
            String dn = u.getDisplayName();
            if (dn != null && dn.trim().equalsIgnoreCase(t)) return u;
        }
        return null;
    }

    // ---- SystemConfig Lazy Loading ----
    private volatile SystemConfig systemConfig = null;

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

    public void reloadSystemConfig() {
        try {
            systemConfig = loadObject(SYSTEM_CONFIG, SystemConfig.class);
            if (systemConfig == null) {
                systemConfig = createDefaultSystemConfig();
            }
        } catch (Exception e) {
            System.err.println("[DataStore] Failed to load system_config: " + e.getMessage());
            systemConfig = createDefaultSystemConfig();
        }
    }

    private SystemConfig createDefaultSystemConfig() {
        SystemConfig cfg = new SystemConfig();
        cfg.setAppVersion("3.0");
        cfg.setBuildDate(LocalDate.now().toString());
        SystemConfig.WorkloadConfig wc = new SystemConfig.WorkloadConfig(20, 20, "h/week");
        cfg.setWorkloadConfig(wc);
        SystemConfig.PositionDefaults pd = new SystemConfig.PositionDefaults(8, 2, "2026-04-30", "Admin");
        cfg.setPositionDefaults(pd);
        cfg.setSkillSuggestions(Arrays.asList(
            "Java", "Python", "JavaScript", "Git", "Agile", "SQL",
            "React", "Node.js", "Machine Learning", "Docker"
        ));
        SystemConfig.DataTraceability dt = new SystemConfig.DataTraceability(
            "positions.json", "applications.json", "applicants.json",
            "workloads.json", "users.json", "system_logs.json",
            "mota_messages.json", "workload_suggestion.json", "cv_uploads/"
        );
        cfg.setDataTraceability(dt);
        return cfg;
    }
}
