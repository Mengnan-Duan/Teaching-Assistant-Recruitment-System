package com.bupt.smartta.servlet;

import com.bupt.smartta.model.*;
import com.bupt.smartta.util.DataStore;
import com.bupt.smartta.util.LLMService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Central REST API servlet handling all business operations for the Smart-TA platform.
 *
 * <p>This servlet serves as the primary API endpoint ({@code /api/*}) and dispatches
 * requests to appropriate handler methods based on the path info. It enforces role-based
 * access control (TA, MO, ADMIN), validates input parameters, manages application
 * status transitions, generates AI match scores, integrates with the Bailian (Qwen) LLM
 * for applicant analysis and workload rebalancing, and persists all data through the
 * {@link com.bupt.smartta.util.DataStore}.</p>
 *
 * <p>Key API endpoints handled:</p>
 * <ul>
 *   <li>{@code GET /api/config} — return system configuration</li>
 *   <li>{@code GET /api/positions} — list all positions</li>
 *   <li>{@code POST /api/positions} — create a new position (MO/ADMIN)</li>
 *   <li>{@code GET /api/applications} — list applications (filtered by role)</li>
 *   <li>{@code POST /api/applications} — submit a new application (TA)</li>
 *   <li>{@code PUT /api/applications/:id} — update application status (MO/ADMIN)</li>
 *   <li>{@code GET /api/applicants} — list all TA applicants</li>
 *   <li>{@code PUT /api/applicants/:id} — update TA applicant profile</li>
 *   <li>{@code GET /api/users} — list users (ADMIN only)</li>
 *   <li>{@code GET /api/workloads} — get TA workload data</li>
 *   <li>{@code PUT /api/workloads/:id} — update TA workload</li>
 *   <li>{@code POST /api/rebalance} — request AI-powered workload rebalancing advice</li>
 *   <li>{@code GET /api/logs} — retrieve system audit logs</li>
 *   <li>{@code GET /api/messages} / {@code POST /api/messages} — MO-TA messaging</li>
 * </ul>
 *
 * @see com.bupt.smartta.util.DataStore
 * @see com.bupt.smartta.util.LLMService
 */
public class ApiServlet extends HttpServlet {

    private final DataStore ds = DataStore.getInstance();
    private final LLMService llmService = LLMService.getInstance();

    /** Set of valid application status values. */
    private static final Set<String> VALID_STATUSES = Set.of(
        Application.STATUS_SUBMITTED,
        Application.STATUS_REVIEW,
        Application.STATUS_ACCEPTED,
        Application.STATUS_REJECTED
    );

    /**
     * Status transition rules: key = current status, value = set of allowed next statuses.
     * MOs may skip "Under Review" and accept directly from "Submitted".
     * Accepted TAs may be rolled back to "Rejected" to release their slot.
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS;
    static {
        Map<String, Set<String>> m = new HashMap<>();
        m.put(Application.STATUS_SUBMITTED, Set.of(Application.STATUS_REVIEW, Application.STATUS_ACCEPTED));
        m.put(Application.STATUS_REVIEW,    Set.of(Application.STATUS_ACCEPTED, Application.STATUS_REJECTED));
        m.put(Application.STATUS_ACCEPTED,  Set.of(Application.STATUS_REJECTED));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(m);
    }

    /** Minimum and maximum valid values for workload hours. */
    private static final int MIN_HOURS = 0;
    private static final int MAX_HOURS = 168; // 24h * 7

    /** Maximum allowed lengths for string input parameters (prevents DoS). */
    private static final int MAX_STRING_LENGTH = 512;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_CODE_LENGTH = 32;
    private static final int MAX_EMAIL_LENGTH = 128;

    /**
     * Validates that a string parameter does not exceed the maximum allowed length.
     * Used to prevent excessively long inputs from causing DoS or storage issues.
     */
    private boolean isValidLength(String s, int maxLen) {
        return s != null && s.length() <= maxLen;
    }

    // ============================================================
    // Utility: path safety check
    // ============================================================

    /**
     * Validates that the action parameter contains no path-traversal characters.
     * Prevents attacks like /api/../../../etc/passwd.
     */
    private boolean isValidAction(String action) {
        if (action == null || action.isEmpty()) return false;
        // Block path traversal, absolute paths, and backslashes
        if (action.contains("..") || action.startsWith("/") || action.startsWith("\\")
                || action.contains("./") || action.contains(".\\")) {
            return false;
        }
        // Only allow letters, digits, underscores, hyphens
        return Pattern.matches("^[a-zA-Z0-9_-]+$", action);
    }

    // ============================================================
    // Utility: role permission check
    // ============================================================

    /**
     * Checks if the current session user holds one of the specified roles.
     * @param roles Allowed roles (MO, ADMIN, etc.)
     */
    private boolean hasRole(HttpServletRequest req, String... roles) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;
        String currentRole = (String) session.getAttribute("currentRole");
        if (currentRole == null) return false;
        for (String r : roles) {
            if (r.equals(currentRole)) return true;
        }
        return false;
    }

    /**
     * Checks if the user has admin capability: current view role is ADMIN, or the account holds ADMIN in the database (supports multi-admin, decoupled from current view role).
     */
    private boolean hasAdminCapability(HttpServletRequest req) {
        if (hasRole(req, "ADMIN")) return true;
        HttpSession session = req.getSession(false);
        if (session == null) return false;
        String uname = (String) session.getAttribute("username");
        if (uname == null) return false;
        User u = ds.getUserByUsername(uname);
        return u != null && u.hasRole("ADMIN");
    }

    /**
     * Checks if the current session is authenticated.
     */
    private boolean isAuthenticated(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && session.getAttribute("username") != null;
    }

    private String sessionUsername(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (String) s.getAttribute("username");
    }

    /** Checks if MO manages the position corresponding to this application (by postedByUsername or Posted By display name) */
    private boolean moCanManageApplication(HttpServletRequest req, Application target) {
        if (hasAdminCapability(req)) return true;
        if (!hasRole(req, "MO")) return false;
        String uname = sessionUsername(req);
        if (uname == null) return false;
        User mo = ds.getUserByUsername(uname);
        if (mo == null) return false;
        Position pos = ds.getPositionByCode(target.getPositionCode());
        if (pos == null) return false;
        if (pos.getPostedByUsername() != null && !pos.getPostedByUsername().trim().isEmpty()) {
            return pos.getPostedByUsername().trim().equalsIgnoreCase(uname);
        }
        String moDisp = mo.getDisplayName() != null ? mo.getDisplayName().trim() : "";
        String pb = pos.getPostedBy() != null ? pos.getPostedBy().trim() : "";
        return !moDisp.isEmpty() && moDisp.equalsIgnoreCase(pb);
    }

    private String resolveMoUsernameForPosition(Position p) {
        if (p == null) return null;
        if (p.getPostedByUsername() != null && !p.getPostedByUsername().trim().isEmpty()) {
            return p.getPostedByUsername().trim();
        }
        User mo = ds.findMoUserByPostedByLabel(p.getPostedBy());
        return mo != null ? mo.getUsername() : null;
    }

    /** Checks if the TA has been accepted for any position published by the current MO */
    private boolean isTaOfMo(String applicantId, String moUsername) {
        if (applicantId == null || moUsername == null) return false;
        User mo = ds.getUserByUsername(moUsername);
        String moDisp = mo != null && mo.getDisplayName() != null ? mo.getDisplayName().trim() : "";
        for (Application a : ds.getApplications()) {
            if (a == null || !Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
            if (!applicantId.equalsIgnoreCase(a.getApplicantId())) continue;
            Position p = ds.getPositionByCode(a.getPositionCode());
            if (p == null) continue;
            if (p.getPostedByUsername() != null && !p.getPostedByUsername().trim().isEmpty()) {
                if (p.getPostedByUsername().trim().equalsIgnoreCase(moUsername)) return true;
            } else if (!moDisp.isEmpty() && p.getPostedBy() != null
                    && moDisp.equalsIgnoreCase(p.getPostedBy().trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean taHasAcceptedWithMo(String applicantId, String moUsername) {
        if (applicantId == null || moUsername == null) return false;
        for (Application a : ds.getApplications()) {
            if (a == null || !Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
            if (!applicantId.equalsIgnoreCase(a.getApplicantId())) continue;
            Position p = ds.getPositionByCode(a.getPositionCode());
            if (p == null) continue;
            String resolved = resolveMoUsernameForPosition(p);
            if (resolved != null && resolved.equalsIgnoreCase(moUsername)) return true;
        }
        return false;
    }

    /** Whether this applicant has any application record (any status) for any position published by the current MO */
    private boolean moHasApplicationWith(String applicantId, String moUsername) {
        if (applicantId == null || moUsername == null) return false;
        for (Application a : ds.getApplications()) {
            if (a == null) continue;
            if (!applicantId.equalsIgnoreCase(a.getApplicantId())) continue;
            Position p = ds.getPositionByCode(a.getPositionCode());
            if (p == null) continue;
            String resolved = resolveMoUsernameForPosition(p);
            if (resolved != null && resolved.equalsIgnoreCase(moUsername)) return true;
        }
        return false;
    }

    private int countUnreadMoTaThread(String moUsername, String taApplicantId, String recipientUsername) {
        int n = 0;
        for (MoTaMessage msg : ds.getMoTaMessagesSnapshot()) {
            if (msg == null) continue;
            if (!moUsername.equalsIgnoreCase(msg.getMoUsername())) continue;
            if (!taApplicantId.equalsIgnoreCase(msg.getTaApplicantId())) continue;
            if (recipientUsername.equalsIgnoreCase(msg.getToUsername()) && !msg.isReadByRecipient()) n++;
        }
        return n;
    }

    /**
     * Returns the currently logged-in user and ensures the account is linked to an applicant profile.
     * MO/TA messaging and profile pages rely on this linkage, so the check is centralized here.
     */
    private User requireLinkedApplicant(HttpServletRequest req, StringBuilder sb, String noProfileMessage) {
        User me = ds.getUserByUsername(sessionUsername(req));
        if (me == null || me.getApplicantId() == null || me.getApplicantId().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"").append(esc(noProfileMessage)).append("\"}");
            return null;
        }
        return me;
    }

    // ============================================================
    // Utility: response output
    // ============================================================

    private void sendError(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"success\":false,\"message\":\"" + esc(message) + "\"}");
    }

    private void sendSuccess(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(body);
    }

    // ============================================================
    // HTTP method dispatch
    // ============================================================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();
        String action = null;
        if (path != null && path.length() > 1) {
            action = path.substring(1);
        }
        if (action == null || action.isEmpty()) {
            action = req.getParameter("action");
        }
        if (action == null || action.isEmpty()) {
            sendError(resp, 400, "Missing action parameter");
            return;
        }
        // P0-1: Path traversal protection
        if (!isValidAction(action)) {
            sendError(resp, 400, "Invalid action parameter");
            return;
        }

        // Public endpoints (no login required)
        Set<String> publicActions = Set.of(
            "positions", "allPositions", "applicants",
            "config", "score", "llmanalysis"
        );

        // Authenticated endpoints (no special permission required)
        Set<String> authRequiredActions = Set.of(
            "applications", "users", "logs", "workloads",
            "workloadEntries", "myProfile", "myTas",
            "pendingApplicants", "myPositions", "moTaThreads",
            "taMoMessages", "messageUnread"
        );

        if (!publicActions.contains(action) && !authRequiredActions.contains(action)) {
            // Unknown endpoints go to subsequent checks (may be /api/debug or other admin endpoint)
        }

        // Authenticated endpoint check
        if (authRequiredActions.contains(action)) {
            if (!isAuthenticated(req)) {
                sendError(resp, 403, "Login required");
                return;
            }
        }

        StringBuilder sb = new StringBuilder();

        switch (action) {
            case "positions":
                sb.append("{\"positions\":[");
                List<Position> positions = ds.getPositions();
                // Fix 2: If current user is both TA and MO, do not show positions they published as MO
                String taUsername = sessionUsername(req);
                User taUser = taUsername != null ? ds.getUserByUsername(taUsername) : null;
                boolean isAlsoMo = taUser != null && taUser.hasRole("MO");
                // Fix 4: Filter out positions the current TA has already applied to (never shown regardless of status, avoids race condition with unrefreshed frontend)
                List<Application> myApps = new ArrayList<>();
                if (taUser != null && taUser.getApplicantId() != null) {
                    for (Application a : ds.getApplications()) {
                        if (a != null && taUser.getApplicantId().equalsIgnoreCase(a.getApplicantId())) {
                            myApps.add(a);
                        }
                    }
                }
                boolean firstPos = true;
                for (int i = 0; i < positions.size(); i++) {
                    Position pos = positions.get(i);
                    // If this position was published by the current user (via postedByUsername) and they are also an MO, filter it out
                    if (isAlsoMo && pos.getPostedByUsername() != null
                            && pos.getPostedByUsername().equalsIgnoreCase(taUsername)) {
                        continue;
                    }
                    // Filter out positions with existing applications (but if the application was rejected by MO and TA has viewed the details and is not permanently blocked, do not filter, allow re-application)
                    boolean hasApp = false;
                    for (Application a : myApps) {
                        if (a.getPositionCode().equalsIgnoreCase(pos.getCode())) {
                            // Filter out: applications not yet rejected by MO, applications still under review, or permanently blocked applications
                            if (!Application.STATUS_REJECTED.equals(a.getStatus())
                                    || !a.isRejectedByMo()
                                    || a.isPermanentlyBlocked()) {
                                hasApp = true;
                            }
                            break;
                        }
                    }
                    if (hasApp) continue;
                    if (!firstPos) sb.append(",");
                    sb.append(positionToJson(pos));
                    firstPos = false;
                }
                sb.append("]}");
                break;

            case "debug":
                {
                    // Debug endpoint - admin only
                    if (!hasAdminCapability(req)) {
                        sendError(resp, 403, "Administrator access required");
                        return;
                    }
                    String dataDir = ds.getDataDir();
                    java.io.File posFile = new java.io.File(dataDir + java.io.File.separator + "positions.json");
                    boolean posFileExists = posFile.exists();
                    String posFileContent = "";
                    if (posFileExists) {
                        try {
                            posFileContent = java.nio.file.Files.readString(posFile.toPath());
                            if (posFileContent.length() > 500) posFileContent = posFileContent.substring(0, 500) + "...";
                        } catch (Exception e) {
                            posFileContent = "ERROR: " + e.getMessage();
                        }
                    }
                    // Test direct Jackson deserialization
                    String directParse = "";
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper testMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        testMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                        testMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                        java.util.List<com.bupt.smartta.model.Position> testList =
                            testMapper.readValue(posFile,
                                testMapper.getTypeFactory().constructCollectionType(java.util.ArrayList.class, com.bupt.smartta.model.Position.class));
                        directParse = "SUCCESS, count=" + testList.size();
                    } catch (Exception e) {
                        directParse = "FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                    }
                    sb.append("{\"debug\":{");
                    sb.append("\"dataDir\":\"").append(esc(dataDir)).append("\",");
                    sb.append("\"positionsFileExists\":").append(posFileExists).append(",");
                    sb.append("\"directParse\":\"").append(esc(directParse)).append("\",");
                    sb.append("\"positionsCount\":").append(ds.getPositions().size()).append(",");
                    sb.append("\"applicantsCount\":").append(ds.getApplicants().size()).append(",");
                    sb.append("\"usersCount\":").append(ds.getUsers().size());
                    sb.append("}}");
                }
                break;

            case "allPositions":
                // Unfiltered version, used exclusively by the admin Recruitment Summary page
                sb.append("{\"positions\":[");
                List<Position> allPos = ds.getPositions();
                for (int i = 0; i < allPos.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(positionToJson(allPos.get(i)));
                }
                sb.append("]}");
                break;

            case "applications":
                {
                    String aid = req.getParameter("applicantId");
                    sb.append("{\"applications\":[");
                    List<Application> apps;
                    if (aid != null && !aid.isEmpty()) {
                        apps = ds.getApplicationsByApplicantId(aid);
                    } else {
                        apps = ds.getApplications();
                    }
                    for (int i = 0; i < apps.size(); i++) {
                        sb.append(applicationToJson(apps.get(i)));
                        if (i < apps.size() - 1) sb.append(",");
                    }
                    sb.append("]}");
                }
                break;

            case "applicants":
                sb.append("{\"applicants\":[");
                List<TAPplicant> applicants = ds.getApplicants();
                for (int i = 0; i < applicants.size(); i++) {
                    sb.append(applicantToJson(applicants.get(i)));
                    if (i < applicants.size() - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            // P0-8: users API — admin sees MO/TA directory; regular logged-in users see only their own entry (as fallback for TA page applicantId)
            // Fix 1: Admin does not show themselves in MO&TA Directory (admin should not appear in the directory list)
            case "users":
                if (!isAuthenticated(req)) {
                    sendError(resp, 403, "Login required");
                    return;
                }
                String currentSessionUsername = sessionUsername(req);
                if (hasAdminCapability(req)) {
                    sb.append("{\"users\":[");
                    List<User> allUsers = ds.getUsers();
                    boolean firstUser = true;
                    for (int i = 0; i < allUsers.size(); i++) {
                        User u = allUsers.get(i);
                        if (u == null) continue;
                        if (!u.hasRole("MO") && !u.hasRole("TA")) continue;
                        // Exclude the currently logged-in admin
                        if (u.getUsername() != null && u.getUsername().equalsIgnoreCase(currentSessionUsername)) continue;
                        if (!firstUser) sb.append(",");
                        sb.append(userToJsonForDirectory(u));
                        firstUser = false;
                    }
                    sb.append("]}");
                    break;
                }
                User selfUser = ds.getUserByUsername(sessionUsername(req));
                if (selfUser == null) {
                    sendError(resp, 404, "User not found");
                    return;
                }
                sb.append("{\"users\":[").append(userToJsonForDirectory(selfUser)).append("]}");
                break;

            case "logs":
                if (!isAuthenticated(req)) {
                    sendError(resp, 403, "Login required");
                    return;
                }
                sb.append("{\"logs\":[");
                List<SystemLog> logs = ds.getLogs();
                int logLimit = Math.min(logs.size(), 50);
                for (int i = 0; i < logLimit; i++) {
                    sb.append(logToJson(logs.get(i)));
                    if (i < logLimit - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            case "workloads":
                sb.append("{\"workloads\":{");
                Map<String, Integer> workloads = ds.getWorkloadHours();
                // Returns workload for the TA user account linked to the applicant
                List<User> allUsersForWorkload = ds.getUsers();
                boolean first = true;
                for (User u : allUsersForWorkload) {
                    if (!u.hasRole("TA")) continue;
                    String appId = u.getApplicantId();
                    if (appId == null || appId.isEmpty()) continue;
                    TAPplicant ta = ds.getApplicantById(appId);
                    if (ta == null) continue;
                    String displayName = ta.getName();
                    int hours = workloads.containsKey(appId) ? workloads.get(appId) : 0;
                    if (!first) sb.append(",");
                    sb.append("\"").append(esc(displayName)).append("\":").append(hours);
                    first = false;
                }
                sb.append("}}");
                break;

            // Workload details (returns entries array for direct frontend use)
            case "workloadEntries":
                sb.append("{\"workloadEntries\":[");
                Map<String, Integer> wloadMap = ds.getWorkloadHours();
                List<User> wloadUsers = ds.getUsers();
                boolean firstEntry = true;
                for (User u : wloadUsers) {
                    if (!u.hasRole("TA")) continue;
                    String appId = u.getApplicantId();
                    if (appId == null || appId.isEmpty()) continue;
                    TAPplicant ta = ds.getApplicantById(appId);
                    if (ta == null) continue;
                    if (!firstEntry) sb.append(",");
                    int hours = wloadMap.containsKey(appId) ? wloadMap.get(appId) : 0;
                    sb.append("{")
                      .append("\"applicantId\":\"").append(esc(appId)).append("\",")
                      .append("\"username\":\"").append(esc(u.getUsername())).append("\",")
                      .append("\"taName\":\"").append(esc(ta.getName())).append("\",")
                      .append("\"hours\":").append(hours)
                      .append("}");
                    firstEntry = false;
                }
                sb.append("]}");
                break;

            case "config":
                sb.append(systemConfigToJson(ds.getSystemConfig()));
                break;
            case "score":
                {
                    String applicantId = req.getParameter("applicantId");
                    String posCode = req.getParameter("positionCode");
                    TAPplicant ta = ds.getApplicantById(applicantId);
                    Position pos = ds.getPositionByCode(posCode);
                    if (ta == null || pos == null) {
                        sendError(resp, 404, "Applicant or position not found");
                        return;
                    }
                    int score = (int) ta.computeAIScore(pos.getRequiredSkills(), pos.getHoursPerWeek());
                    int matched = ta.getMatchedSkillCount(pos.getRequiredSkills());
                    int reqCount = pos.getRequiredSkills().size();
                    int skillPct = reqCount > 0 ? (matched * 100 / reqCount) : 0;
                    int gpaPct = (int) ((ta.getGpa() / 4.0) * 100);
                    int availPct = Math.min((ta.getHoursAvailable() * 100) / 20, 100);
                    sb.append("{\"score\":").append(score)
                      .append(",\"skillScore\":").append(skillPct)
                      .append(",\"gpaScore\":").append(gpaPct)
                      .append(",\"availScore\":").append(availPct)
                      .append(",\"matchedSkills\":").append(matched)
                      .append(",\"requiredSkills\":").append(reqCount)
                      .append("}");
                }
                break;

            case "llmanalysis":
                {
                    String aid = req.getParameter("applicantId");
                    String pCode = req.getParameter("positionCode");
                    TAPplicant ta2 = ds.getApplicantById(aid);
                    Position pos2 = ds.getPositionByCode(pCode);
                    if (ta2 == null || pos2 == null) {
                        sendError(resp, 404, "Applicant or position not found");
                        return;
                    }
                    int sc = (int) ta2.computeAIScore(pos2.getRequiredSkills(), pos2.getHoursPerWeek());
                    int mch = ta2.getMatchedSkillCount(pos2.getRequiredSkills());
                    int rc = pos2.getRequiredSkills().size();
                    int sp = rc > 0 ? (mch * 100 / rc) : 0;
                    int gp = (int) ((ta2.getGpa() / 4.0) * 100);
                    int avp = Math.min((ta2.getHoursAvailable() * 100) / 20, 100);

                    String skillsStr = String.join(", ", ta2.getSkills());
                    String reqSkillsStr = String.join(", ", pos2.getRequiredSkills());
                    String analysis = llmService.generateMatchAnalysis(
                            ta2.getName(), skillsStr,
                            ta2.getGpa(), ta2.getHoursAvailable(),
                            pos2.getName(), reqSkillsStr,
                            pos2.getHoursPerWeek(),
                            sp, gp, avp, sc);

                    sb.append("{\"analysis\":").append(esc(analysis)).append("}");
                }
                break;

            case "myTas":
                if (!isAuthenticated(req) || !hasRole(req, "MO")) {
                    sendError(resp, 403, "MO role required");
                    return;
                }
                handleGetMyTas(req, sb);
                break;

            case "pendingApplicants":
                if (!isAuthenticated(req) || !hasRole(req, "MO")) {
                    sendError(resp, 403, "MO role required");
                    return;
                }
                handleGetPendingApplicants(req, sb);
                break;

            case "myPositions":
                if (!isAuthenticated(req) || !hasRole(req, "TA")) {
                    sendError(resp, 403, "TA role required");
                    return;
                }
                handleGetMyPositions(req, sb);
                break;

            case "moTaMessages":
                if (!isAuthenticated(req) || !hasRole(req, "MO")) {
                    sendError(resp, 403, "MO role required");
                    return;
                }
                handleGetMoTaMessages(req, sb);
                break;

            case "taMoThreads":
                if (!isAuthenticated(req) || !hasRole(req, "TA")) {
                    sendError(resp, 403, "TA role required");
                    return;
                }
                handleGetTaMoThreads(req, sb);
                break;

            case "taMoMessages":
                if (!isAuthenticated(req) || !hasRole(req, "TA")) {
                    sendError(resp, 403, "TA role required");
                    return;
                }
                handleGetTaMoMessages(req, sb);
                break;

            case "messageUnread":
                if (!isAuthenticated(req) || (!hasRole(req, "MO") && !hasRole(req, "TA"))) {
                    sendError(resp, 403, "MO or TA role required");
                    return;
                }
                handleGetMessageUnread(req, sb);
                break;

            // MO-only: returns the list of courses managed by the current MO (filtered by postedByUsername)
            case "moPositions":
                if (!isAuthenticated(req) || !hasRole(req, "MO")) {
                    sendError(resp, 403, "MO role required");
                    return;
                }
                String moUser = sessionUsername(req);
                sb.append("{\"moPositions\":[");
                List<Position> allPositions = ds.getPositions();
                boolean firstMoPos = true;
                for (Position pos : allPositions) {
                    if (pos == null || pos.getPostedByUsername() == null) continue;
                    if (!pos.getPostedByUsername().equalsIgnoreCase(moUser)) continue;
                    if (!firstMoPos) sb.append(",");
                    sb.append(positionToJson(pos));
                    firstMoPos = false;
                }
                sb.append("]}");
                break;

            case "moProfile":
                if (!isAuthenticated(req) || !hasRole(req, "MO")) {
                    sendError(resp, 403, "MO role required");
                    return;
                }
                {
                    String mpUser = sessionUsername(req);
                    User mpU = ds.getUserByUsername(mpUser);
                    if (mpU == null) {
                        sendError(resp, 500, "User not found");
                        return;
                    }
                    sb.append("{\"success\":true,");
                    sb.append("\"username\":\"").append(esc(mpU.getUsername())).append("\",");
                    sb.append("\"displayName\":\"").append(esc(mpU.getDisplayName() != null ? mpU.getDisplayName() : "")).append("\",");
                    sb.append("\"email\":\"").append(esc(mpU.getEmail() != null ? mpU.getEmail() : "")).append("\"}");
                }
                break;

            default:
                sendError(resp, 400, "Unknown action: " + action);
                return;
        }

        resp.getWriter().write(sb.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();

        if (path == null || path.length() <= 1) {
            sendError(resp, 400, "Missing action");
            return;
        }
        String action = path.substring(1);

        // P0-1: Path traversal protection
        if (!isValidAction(action)) {
            sendError(resp, 400, "Invalid action parameter");
            return;
        }

        StringBuilder sb = new StringBuilder();

        switch (action) {
            case "apply":
                handleApply(req, sb);
                break;
            case "applicant":
                // Only TA (own profile save) or ADMIN can modify applicant data
                if (!hasRole(req, "TA", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: TA or ADMIN role required");
                    return;
                }
                handleCreateApplicant(req, sb);
                break;
            case "position":
                // P0-2: Only MO/ADMIN can publish/update positions
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handlePositionCreateOrUpdate(req, sb);
                break;
            case "positionDelete":
                // Delete position
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handleDeletePosition(req, sb);
                break;
            case "updateStatus":
                // P0-2: Only MO/ADMIN can review applications
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handleUpdateStatus(req, sb);
                break;
            case "workload":
                // P0-2: Only admin (including account-level ADMIN) can modify workload
                if (!hasAdminCapability(req)) {
                    sendError(resp, 403, "Insufficient permissions: administrator access required");
                    return;
                }
                handleUpdateWorkload(req, sb);
                break;
            case "rebalance":
                if (!hasAdminCapability(req)) {
                    sendError(resp, 403, "Insufficient permissions: administrator access required");
                    return;
                }
                handleRebalanceWorkload(sb);
                break;
            case "workloadSuggestion":
                // TA gets the current pending AI workload adjustment suggestion
                if (!isAuthenticated(req) || !hasRole(req, "TA")) {
                    sendError(resp, 403, "TA role required");
                    return;
                }
                handleGetWorkloadSuggestion(req, sb);
                break;
            case "workloadSuggestionResponse":
                // TA confirms or dismisses the AI suggestion
                if (!isAuthenticated(req) || !hasRole(req, "TA")) {
                    sendError(resp, 403, "TA role required");
                    return;
                }
                handleWorkloadSuggestionResponse(req, sb);
                break;
            // P0-7: Fix missing quota route registration
            case "quota":
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handleUpdateQuota(req, sb);
                break;
            case "user":
                // User management (used by admin page)
                if (!hasAdminCapability(req)) {
                    sendError(resp, 403, "Insufficient permissions: administrator access required");
                    return;
                }
                handleManageUser(req, sb);
                break;
            case "adminApplicant":
                // Applicant management (used by admin page)
                if (!hasAdminCapability(req)) {
                    sendError(resp, 403, "Insufficient permissions: administrator access required");
                    return;
                }
                handleAdminApplicant(req, sb);
                break;
            case "moApplicant":
                if (!hasRole(req, "MO")) {
                    sendError(resp, 403, "Insufficient permissions: MO role required");
                    return;
                }
                handleMoApplicant(req, sb);
                break;
            case "moProfile":
                if (!hasRole(req, "MO")) {
                    sendError(resp, 403, "Insufficient permissions: MO role required");
                    return;
                }
                handleMoProfile(req, sb);
                break;
            case "markApplicationViewed":
                if (!isAuthenticated(req)) {
                    sendError(resp, 403, "Login required");
                    return;
                }
                handleMarkApplicationViewed(req, sb);
                break;
            case "moTaMessage":
                if (!isAuthenticated(req) || (!hasRole(req, "MO") && !hasRole(req, "TA"))) {
                    sendError(resp, 403, "MO or TA role required");
                    return;
                }
                handlePostMoTaMessage(req, sb);
                break;
            case "markMoTaRead":
                if (!isAuthenticated(req) || (!hasRole(req, "MO") && !hasRole(req, "TA"))) {
                    sendError(resp, 403, "MO or TA role required");
                    return;
                }
                handlePostMarkMoTaRead(req, sb);
                break;
            case "cv":
                // CV upload (TA role)
                if (!hasRole(req, "TA", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: TA or ADMIN role required");
                    return;
                }
                handleCVUpload(req, sb);
                break;
            default:
                sendError(resp, 400, "Unknown POST action: " + action);
                return;
        }

        resp.getWriter().write(sb.toString());
    }

    // ============================================================
    // Business logic handler
    // ============================================================

    private void handleApply(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String positionCode = req.getParameter("positionCode");
        String applicantName = req.getParameter("applicantName");

        if (applicantId == null || applicantId.isEmpty() || positionCode == null || positionCode.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields: applicantId and positionCode\"}");
            return;
        }

        // Input length validation
        if (!isValidLength(applicantId, MAX_STRING_LENGTH) || !isValidLength(positionCode, MAX_CODE_LENGTH)) {
            sb.append("{\"success\":false,\"message\":\"Invalid input length\"}");
            return;
        }

        // Prevent path traversal attacks (forbid ".." in IDs)
        if (applicantId.contains("..") || positionCode.contains("..")) {
            sb.append("{\"success\":false,\"message\":\"Invalid characters in parameter\"}");
            return;
        }

        TAPplicant ta = ds.getApplicantById(applicantId);
        Position pos = ds.getPositionByCode(positionCode);
        if (ta == null || pos == null) {
            sb.append("{\"success\":false,\"message\":\"Applicant or position not found\"}");
            return;
        }

        if (!pos.isOpen()) {
            sb.append("{\"success\":false,\"message\":\"This position is no longer accepting applications\"}");
            return;
        }

        // Check if this TA already has an application record for this position (any status)
        Application existing = ds.getApplication(applicantId, positionCode);
        if (existing != null) {
            // After MO rejection, re-application allowed: keep old record (stays Rejected), create new application
            if (Application.STATUS_REJECTED.equals(existing.getStatus())) {
                // Permanently blocked (>=2 rejections): no more applications allowed
                if (existing.isPermanentlyBlocked()) {
                    sb.append("{\"success\":false,\"message\":\"You have been permanently blocked from this position after two rejections\"}");
                    return;
                }
                // Only one re-application chance: clear the old record's MO rejection flag
                // Old record kept (status stays Rejected), used to show history in My Applications
                existing.setRejectedByMo(false);
                existing.setMoRejectionCount(0);
                ds.updateApplication(existing);
            } else {
                // Non-Rejected status: duplicate application not allowed
                sb.append("{\"success\":false,\"message\":\"Already applied to this position\"}");
                return;
            }
        }

        // Get current application count (determines new application's applyCount)
        int currentApplyCount = 1;
        if (existing != null) {
            currentApplyCount = existing.getApplyCount() + 1;
        }

        int score = (int) ta.computeAIScore(pos.getRequiredSkills(), pos.getHoursPerWeek());
        int matched = ta.getMatchedSkillCount(pos.getRequiredSkills());
        int reqCount = pos.getRequiredSkills().size();
        int skillPct = reqCount > 0 ? (matched * 100 / reqCount) : 0;
        int gpaPct = (int) ((ta.getGpa() / 4.0) * 100);
        int availPct = Math.min((ta.getHoursAvailable() * 100) / 20, 100);

        Application app = new Application(applicantId,
            applicantName != null && !applicantName.isEmpty() ? applicantName : ta.getName(),
            positionCode, pos.getName(), score);
        app.setSkillScore(skillPct);
        app.setGpaScore(gpaPct);
        app.setAvailScore(availPct);
        // Multiple applications: use applyCount to distinguish multiple application records for the same position
        app.setApplyCount(currentApplyCount);

        String skillsStr = String.join(", ", ta.getSkills());
        String reqSkillsStr = String.join(", ", pos.getRequiredSkills());
        String llmExplanation = llmService.generateMatchAnalysis(
                app.getApplicantName(), skillsStr,
                ta.getGpa(), ta.getHoursAvailable(),
                pos.getName(), reqSkillsStr,
                pos.getHoursPerWeek(),
                skillPct, gpaPct, availPct, score);
        app.setLlmExplanation(llmExplanation);

        ds.addApplication(app);

        sb.append("{\"success\":true,\"message\":\"Application submitted successfully\",")
          .append("\"application\":").append(applicationToJson(app)).append("}");
    }

    private void handleCreateApplicant(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String name = req.getParameter("name");
        String email = req.getParameter("email");
        String year = req.getParameter("yearOfStudy");
        String gpaStr = req.getParameter("gpa");
        String skillsStr = req.getParameter("skills");
        String hoursStr = req.getParameter("hoursAvailable");

        if (name == null || name.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Name is required\"}");
            return;
        }

        // Input length validation
        if (!isValidLength(name, MAX_NAME_LENGTH)) {
            sb.append("{\"success\":false,\"message\":\"Name too long (max 128 characters)\"}");
            return;
        }
        if (email != null && !isValidLength(email, MAX_EMAIL_LENGTH)) {
            sb.append("{\"success\":false,\"message\":\"Email too long (max 128 characters)\"}");
            return;
        }
        if (skillsStr != null && skillsStr.length() > MAX_STRING_LENGTH) {
            sb.append("{\"success\":false,\"message\":\"Skills too long (max 512 characters)\"}");
            return;
        }

        // If applicantId not provided, try to get from the applicant linked to the current logged-in user
        if (applicantId == null || applicantId.isEmpty()) {
            String username = sessionUsername(req);
            if (username != null) {
                User u = ds.getUserByUsername(username);
                if (u != null && u.getApplicantId() != null && !u.getApplicantId().isEmpty()) {
                    applicantId = u.getApplicantId();
                }
            }
        }

        double gpa = 0.0;
        try { gpa = Double.parseDouble(gpaStr); } catch (Exception e) {}
        if (gpa < 0.0 || gpa > 4.0) {
            gpa = Math.max(0.0, Math.min(4.0, gpa));
        }
        int hours = 12;
        try { hours = Integer.parseInt(hoursStr); } catch (Exception e) {}
        if (hours < 0) hours = 0;
        if (hours > 20) hours = 20;

        List<String> skills = new ArrayList<>();
        if (skillsStr != null) {
            for (String s : skillsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) skills.add(trimmed);
            }
        }

        TAPplicant ta;
        // If applicantId is provided and exists in the database, update the existing record
        if (applicantId != null && !applicantId.isEmpty()) {
            ta = ds.getApplicantById(applicantId);
            if (ta != null) {
                // Update existing record
                ta.setName(name);
                ta.setEmail(email != null ? email : "");
                ta.setYearOfStudy(year != null ? year : "Year 2");
                ta.setGpa(gpa);
                ta.setSkills(skills);
                ta.setHoursAvailable(hours);
            } else {
                // applicantId exists but record not found, reject the operation (prevents wrong ID)
                sb.append("{\"success\":false,\"message\":\"Applicant ID not found: " + esc(applicantId) + "\"}");
                return;
            }
        } else {
            // No applicantId (and current user has no linked applicant), reject creating new record
            sb.append("{\"success\":false,\"message\":\"No applicant ID linked to your account. Please contact administrator.\"}");
            return;
        }
        ds.saveApplicant(ta);

        sb.append("{\"success\":true,\"message\":\"Profile saved\",")
          .append("\"applicant\":").append(applicantToJson(ta)).append("}");
    }

    private void handlePositionCreateOrUpdate(HttpServletRequest req, StringBuilder sb) {
        String code = req.getParameter("code");
        String name = req.getParameter("name");
        String skillsStr = req.getParameter("requiredSkills");
        String hoursStr = req.getParameter("hoursPerWeek");
        String slotsStr = req.getParameter("totalSlots");
        String deadline = req.getParameter("deadline");
        String postedBy = req.getParameter("postedBy");
        String desc = req.getParameter("description");

        if (code == null || code.isEmpty() || name == null || name.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Code and name are required\"}");
            return;
        }

        // Input length validation
        if (!isValidLength(code, MAX_CODE_LENGTH) || !isValidLength(name, MAX_NAME_LENGTH)) {
            sb.append("{\"success\":false,\"message\":\"Invalid input length\"}");
            return;
        }

        // Prevent path traversal and special characters
        if (code.contains("..") || name.contains("..")) {
            sb.append("{\"success\":false,\"message\":\"Invalid characters in parameter\"}");
            return;
        }

        Position existingPos = ds.getPositionByCode(code);
        boolean isUpdate = existingPos != null;

        // Permission check: for updates, MO can only update courses they published
        if (isUpdate) {
            if (!hasAdminCapability(req) && hasRole(req, "MO")) {
                String moUser = sessionUsername(req);
                if (existingPos.getPostedByUsername() == null || !existingPos.getPostedByUsername().equalsIgnoreCase(moUser)) {
                    sb.append("{\"success\":false,\"message\":\"You can only edit your own modules\"}");
                    return;
                }
            }
        } else {
            // Check code uniqueness when creating new
            if (ds.positionCodeExists(code)) {
                sb.append("{\"success\":false,\"message\":\"Position code already exists: " + esc(code) + "\"}");
                return;
            }
        }

        List<String> skills = new ArrayList<>();
        if (skillsStr != null) {
            for (String s : skillsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) skills.add(trimmed);
            }
        }

        int hours = 8;
        try { hours = Integer.parseInt(hoursStr); } catch (Exception e) {}
        if (hours < 1) hours = 1;
        if (hours > 40) hours = 40;

        int slots = 2;
        try { slots = Integer.parseInt(slotsStr); } catch (Exception e) {}
        if (slots < 1) slots = 1;
        if (slots > 10) slots = 10;

        Position pos;
        HttpSession session = req.getSession(false);
        String moUser = session != null ? sessionUsername(req) : null;

        if (isUpdate) {
            pos = existingPos;
            pos.setName(name);
            pos.setRequiredSkills(skills);
            pos.setHoursPerWeek(hours);
            pos.setTotalSlots(slots);
            pos.setDeadline(deadline != null ? deadline : pos.getDeadline());
            pos.setDescription(desc);
            // Do not change postedByUsername, preserve original MO ownership
            ds.savePositions();
        } else {
            pos = new Position(code, name, skills, hours, slots,
                deadline != null ? deadline : "2026-04-30",
                postedBy != null ? postedBy : "Admin");
            pos.setOpen(true);
            pos.setDescription(desc);

            if (session != null && hasRole(req, "MO")) {
                // Even for ADMIN+MO hybrid accounts, set postedByUsername to ensure correct MO ownership
                User moUserObj = ds.getUserByUsername(moUser);
                if (moUserObj != null) {
                    pos.setPostedByUsername(moUser);
                    String pb = postedBy != null ? postedBy.trim() : "";
                    if (pb.isEmpty()) {
                        String dn = moUserObj.getDisplayName();
                        pos.setPostedBy(dn != null && !dn.isEmpty() ? dn : moUser);
                    }
                }
            }
            ds.addPosition(pos);
        }

        String msg = isUpdate ? "Module updated successfully" : "Position posted successfully";
        sb.append("{\"success\":true,\"message\":\"" + esc(msg) + "\",")
          .append("\"position\":").append(positionToJson(pos)).append("}");
    }

    private void handleDeletePosition(HttpServletRequest req, StringBuilder sb) {
        String code = req.getParameter("positionCode");
        if (code == null || code.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"positionCode is required\"}");
            return;
        }

        Position pos = ds.getPositionByCode(code);
        if (pos == null) {
            sb.append("{\"success\":false,\"message\":\"Position not found: " + esc(code) + "\"}");
            return;
        }

        // Permission check: MO can only delete courses they published
        if (!hasAdminCapability(req) && hasRole(req, "MO")) {
            String moUser = sessionUsername(req);
            if (pos.getPostedByUsername() == null || !pos.getPostedByUsername().equalsIgnoreCase(moUser)) {
                sb.append("{\"success\":false,\"message\":\"You can only delete your own modules\"}");
                return;
            }
        }

        // Delete all applications for this course
        List<Application> toRemove = new ArrayList<>();
        for (Application a : ds.getApplications()) {
            if (a != null && code.equalsIgnoreCase(a.getPositionCode())) {
                toRemove.add(a);
            }
        }
        for (Application a : toRemove) {
            ds.getApplications().remove(a);
        }
        if (!toRemove.isEmpty()) ds.saveApplications();

        // Delete course
        ds.getPositions().remove(pos);
        ds.savePositions();
        ds.addLog(SystemLog.OP_WRITE, "positions.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Module deleted: " + esc(code) + "\"}");
    }

    private void handleMoProfile(HttpServletRequest req, StringBuilder sb) {
        String moUser = sessionUsername(req);
        if (moUser == null) {
            sb.append("{\"success\":false,\"message\":\"Not authenticated\"}");
            return;
        }
        User user = ds.getUserByUsername(moUser);
        if (user == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }

        String displayName = req.getParameter("displayName");
        String email = req.getParameter("email");

        // Input length validation
        if (displayName != null && !isValidLength(displayName, MAX_NAME_LENGTH)) {
            sb.append("{\"success\":false,\"message\":\"Display name too long (max 128 characters)\"}");
            return;
        }
        if (email != null && !isValidLength(email, MAX_EMAIL_LENGTH)) {
            sb.append("{\"success\":false,\"message\":\"Email too long (max 128 characters)\"}");
            return;
        }

        if (displayName != null) user.setDisplayName(displayName.trim());
        if (email != null) user.setEmail(email.trim());
        ds.saveUsers();
        ds.addLog(SystemLog.OP_WRITE, "users.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Profile updated\",\"username\":\"").append(esc(user.getUsername())).append("\",");
        sb.append("\"displayName\":\"").append(esc(user.getDisplayName() != null ? user.getDisplayName() : "")).append("\",");
        sb.append("\"email\":\"").append(esc(user.getEmail() != null ? user.getEmail() : "")).append("\"}");
    }

    private void handleUpdateStatus(HttpServletRequest req, StringBuilder sb) {
        String appId = req.getParameter("applicationId");
        String status = req.getParameter("status");

        if (appId == null || appId.isEmpty() || status == null || status.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields: applicationId and status\"}");
            return;
        }

        // P0-3: Status value whitelist validation
        if (!VALID_STATUSES.contains(status)) {
            sb.append("{\"success\":false,\"message\":\"Invalid status value. Must be one of: Submitted, Under Review, Accepted, Rejected\"}");
            return;
        }

        // Use efficient getApplicationById (newly added)
        Application target = ds.getApplicationById(appId);
        if (target == null) {
            sb.append("{\"success\":false,\"message\":\"Application not found\"}");
            return;
        }

        if (!moCanManageApplication(req, target)) {
            sb.append("{\"success\":false,\"message\":\"You can only review applications for positions you posted\"}");
            return;
        }

        String oldStatus = target.getStatus();

        // P0-3: State machine transition validation
        if (!oldStatus.equals(status)) {
            Set<String> allowed = ALLOWED_TRANSITIONS.get(oldStatus);
            if (allowed == null || !allowed.contains(status)) {
                sb.append("{\"success\":false,\"message\":\"Invalid status transition: " + esc(oldStatus)
                    + " -> " + esc(status) + ". Allowed: " + (allowed != null ? allowed : "none") + "\"}");
                return;
            }
        }

        // P0-3: Decide whether to update filledSlots and workload based on state transition
        // Critical: all data updates must happen before setStatus!
        // Only Accepted -> Rejected (retract) reduces filledSlots and workload
        if (Application.STATUS_ACCEPTED.equals(oldStatus)
                && Application.STATUS_REJECTED.equals(status)) {
            // Retract: decrement filledSlots, decrement workload
            Position pos = ds.getPositionByCode(target.getPositionCode());
            if (pos != null) {
                // Bug 1/2 fix: try decrement even if filledSlots is 0 (prevents double retract)
                if (pos.getFilledSlots() > 0) {
                    pos.setFilledSlots(pos.getFilledSlots() - 1);
                }
                ds.savePositions();
                // Bug 2 fix: ensure workload is correctly decremented, works even with no existing record
                int current = ds.getWorkloadHours(target.getApplicantId());
                int reduceHours = pos.getHoursPerWeek();
                int newWorkload = Math.max(0, current - reduceHours);
                ds.setWorkloadHours(target.getApplicantId(), newWorkload);
                System.out.println("[handleUpdateStatus] Retract: applicantId=" + target.getApplicantId()
                    + ", reduceHours=" + reduceHours + ", oldWorkload=" + current + ", newWorkload=" + newWorkload
                    + ", filledSlots=" + pos.getFilledSlots());
            }
            // Retract also sets rejectedByMo so TA can reapply
            target.setRejectedByMo(true);
        }

        // On MO rejection, set rejectedByMo flag and increment rejection count (only when transitioning from Submitted/Review to Rejected)
        if (Application.STATUS_REJECTED.equals(status)
                && (Application.STATUS_SUBMITTED.equals(oldStatus) || Application.STATUS_REVIEW.equals(oldStatus))) {
            target.setRejectedByMo(true);
            // New: record rejection count, permanently block at 2
            int newCount = target.getMoRejectionCount() + 1;
            target.setMoRejectionCount(newCount);
            System.out.println("[handleUpdateStatus] Reject: applicantId=" + target.getApplicantId()
                + ", position=" + target.getPositionCode()
                + ", moRejectionCount=" + newCount
                + ", permanentlyBlocked=" + target.isPermanentlyBlocked());
        }

        // On acceptance, increment filledSlots and add to workload, clear rejectedByMo flag
        if (Application.STATUS_ACCEPTED.equals(status)) {
            Position pos = ds.getPositionByCode(target.getPositionCode());
            if (pos != null) {
                // Bug 1 fix: increment filledSlots and save
                pos.setFilledSlots(pos.getFilledSlots() + 1);
                ds.savePositions();
                // Bug 1 fix: ensure workload is correctly incremented, works even with no existing record
                int current = ds.getWorkloadHours(target.getApplicantId());
                int addHours = pos.getHoursPerWeek();
                int newWorkload = current + addHours;
                ds.setWorkloadHours(target.getApplicantId(), newWorkload);
                System.out.println("[handleUpdateStatus] Accept: applicantId=" + target.getApplicantId()
                    + ", addHours=" + addHours + ", oldWorkload=" + current + ", newWorkload=" + newWorkload
                    + ", filledSlots=" + pos.getFilledSlots());
            }
            target.setRejectedByMo(false);
        }

        target.setStatus(status);
        ds.updateApplication(target);

        ds.addLog(SystemLog.OP_WRITE, "applications.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Status updated from ").append(esc(oldStatus))
          .append(" to ").append(esc(status)).append("\"}");
    }

    private void handleUpdateWorkload(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String hoursStr = req.getParameter("hours");

        if (applicantId == null || applicantId.isEmpty() || hoursStr == null || hoursStr.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields\"}");
            return;
        }

        int hours;
        try {
            hours = Integer.parseInt(hoursStr);
        } catch (NumberFormatException e) {
            sb.append("{\"success\":false,\"message\":\"Invalid hours value: must be an integer\"}");
            return;
        }

        // P0-6: Workload range validation (prevent negative or oversized values)
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            sb.append("{\"success\":false,\"message\":\"Hours must be between " + MIN_HOURS + " and " + MAX_HOURS + "\"}");
            return;
        }

        TAPplicant ta = ds.getApplicantById(applicantId);
        if (ta == null) {
            sb.append("{\"success\":false,\"message\":\"Applicant not found: " + esc(applicantId) + "\"}");
            return;
        }

        ds.setWorkloadHours(applicantId, hours);
        sb.append("{\"success\":true,\"message\":\"Workload updated to ").append(hours).append("h for ").append(esc(ta.getName())).append("\"}");
    }

    private void handleUpdateQuota(HttpServletRequest req, StringBuilder sb) {
        String code = req.getParameter("positionCode");
        String totalSlotsStr = req.getParameter("totalSlots");

        if (code == null || code.isEmpty() || totalSlotsStr == null || totalSlotsStr.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields\"}");
            return;
        }

        Position pos = ds.getPositionByCode(code);
        if (pos == null) {
            sb.append("{\"success\":false,\"message\":\"Position not found: " + esc(code) + "\"}");
            return;
        }

        // Permission check: MO can only modify quota for courses they manage
        if (!hasAdminCapability(req) && hasRole(req, "MO")) {
            String moUser = sessionUsername(req);
            if (pos.getPostedByUsername() == null || !pos.getPostedByUsername().equalsIgnoreCase(moUser)) {
                sb.append("{\"success\":false,\"message\":\"You can only modify quotas for your own modules\"}");
                return;
            }
        }

        int totalSlots;
        try {
            totalSlots = Integer.parseInt(totalSlotsStr);
        } catch (NumberFormatException e) {
            sb.append("{\"success\":false,\"message\":\"Invalid totalSlots value\"}");
            return;
        }

        // Quota cannot be less than the number of accepted slots
        if (totalSlots < pos.getFilledSlots()) {
            sb.append("{\"success\":false,\"message\":\"Total slots cannot be less than filled slots (" + pos.getFilledSlots() + ")\"}");
            return;
        }
        // Quota upper limit
        if (totalSlots > 10) {
            // P0-6: Return error for out-of-range instead of silently truncating
            sb.append("{\"success\":false,\"message\":\"Total slots cannot exceed 10\"}");
            return;
        }

        pos.setTotalSlots(totalSlots);
        ds.savePositions();
        ds.addLog(SystemLog.OP_WRITE, "positions.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Quota updated to ").append(totalSlots).append(" slots for ").append(esc(code)).append("\"}");
    }

    private void handleRebalanceWorkload(StringBuilder sb) {
        Map<String, Integer> workloads = ds.getWorkloadHours();
        List<User> allUsers = ds.getUsers();

        // Build WorkloadEntry with accepted positions for AI analysis
        List<LLMService.WorkloadEntry> entries = new ArrayList<>();
        for (User u : allUsers) {
            if (!u.hasRole("TA")) continue;
            String appId = u.getApplicantId();
            if (appId == null || appId.isEmpty()) continue;
            TAPplicant ta = ds.getApplicantById(appId);
            if (ta == null) continue;
            int hours = workloads.containsKey(appId) ? workloads.get(appId) : 0;

            // Gather accepted positions for this TA
            Map<String, Integer> posMap = new java.util.HashMap<>();
            for (Application a : ds.getApplications()) {
                if (a == null || !Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
                if (!appId.equalsIgnoreCase(a.getApplicantId())) continue;
                Position p = ds.getPositionByCode(a.getPositionCode());
                if (p != null) {
                    posMap.put(a.getPositionCode(), p.getHoursPerWeek());
                }
            }
            entries.add(new LLMService.WorkloadEntry(appId, ta.getName(), u.getUsername(), hours, posMap));
        }

        int capacity = 20;
        SystemConfig.WorkloadConfig wc = ds.getSystemConfig().getWorkloadConfig();
        if (wc != null) capacity = wc.getOverloadThreshold();

        LLMService.RebalanceAdvice advice = llmService.generateWorkloadRebalanceAdvice(entries, capacity);

        if (advice != null && "reduce".equals(advice.action)
                && advice.targetApplicantId != null && advice.targetPositionCode != null) {
            // Store the suggestion for the TA to confirm — do NOT auto-apply
            ds.saveWorkloadSuggestion(advice);
            sb.append("{\"success\":true,\"ai\":true,\"message\":\"")
              .append(esc(advice.summary))
              .append("\",\"reasoning\":\"")
              .append(esc(advice.reasoning))
              .append("\",\"targetApplicantId\":\"")
              .append(esc(advice.targetApplicantId))
              .append("\",\"targetDisplayName\":\"")
              .append(esc(advice.targetDisplayName != null ? advice.targetDisplayName : ""))
              .append("\",\"targetPositionCode\":\"")
              .append(esc(advice.targetPositionCode))
              .append("\",\"targetPositionName\":\"")
              .append(esc(advice.targetPositionName != null ? advice.targetPositionName : ""))
              .append("\",\"targetHoursDelta\":")
              .append(advice.targetHoursDelta != null ? advice.targetHoursDelta : "null")
              .append("}");
            return;
        }

        // No overloaded TAs or AI unavailable — nothing to suggest
        sb.append("{\"success\":true,\"ai\":false,\"message\":\"")
          .append(advice != null && "no_action".equals(advice.action)
                  ? "No overloaded TAs. Workloads are within safe range."
                  : "AI service unavailable and no overloaded TAs found.")
          .append("\"}");
    }

    /**
     * TA fetches their pending AI workload adjustment suggestion.
     * Reads the current pending suggestion from DataStore, returns it if it belongs to the current TA.
     */
    private void handleGetWorkloadSuggestion(HttpServletRequest req, StringBuilder sb) {
        User me = requireLinkedApplicant(req, sb, "No applicant profile linked.");
        if (me == null) return;
        String aid = me.getApplicantId();
        LLMService.RebalanceAdvice sugg = ds.getPendingWorkloadSuggestion();
        if (sugg == null || !aid.equalsIgnoreCase(sugg.targetApplicantId)) {
            sb.append("{\"hasSuggestion\":false,\"message\":\"No pending suggestion for you.\"}");
            return;
        }
        sb.append("{\"hasSuggestion\":true,\"suggestion\":{");
        sb.append("\"targetApplicantId\":\"").append(esc(sugg.targetApplicantId)).append("\",");
        sb.append("\"targetDisplayName\":\"").append(esc(sugg.targetDisplayName != null ? sugg.targetDisplayName : "")).append("\",");
        sb.append("\"targetPositionCode\":\"").append(esc(sugg.targetPositionCode != null ? sugg.targetPositionCode : "")).append("\",");
        sb.append("\"targetPositionName\":\"").append(esc(sugg.targetPositionName != null ? sugg.targetPositionName : "")).append("\",");
        sb.append("\"targetHoursDelta\":").append(sugg.targetHoursDelta != null ? sugg.targetHoursDelta : "null").append(",");
        sb.append("\"reasoning\":\"").append(esc(sugg.reasoning != null ? sugg.reasoning : "")).append("\",");
        sb.append("\"summary\":\"").append(esc(sugg.summary != null ? sugg.summary : "")).append("\"");
        sb.append("}}");
    }

    /**
     * TA confirms or dismisses the AI suggestion.
     * confirm=true: changes the specified application's status to Rejected and updates workload.
     * confirm=false: clears the pending suggestion only.
     */
    private void handleWorkloadSuggestionResponse(HttpServletRequest req, StringBuilder sb) {
        User me = requireLinkedApplicant(req, sb, "No applicant profile linked.");
        if (me == null) return;
        String aid = me.getApplicantId();
        String actionParam = req.getParameter("action"); // "confirm" or "dismiss"
        boolean confirm = "confirm".equalsIgnoreCase(actionParam);

        LLMService.RebalanceAdvice sugg = ds.getPendingWorkloadSuggestion();
        if (sugg == null || !aid.equalsIgnoreCase(sugg.targetApplicantId)) {
            sb.append("{\"success\":false,\"message\":\"No pending suggestion to respond to.\"}");
            return;
        }

        if (confirm) {
            // Find the accepted application for this position and withdraw it
            String posCode = sugg.targetPositionCode;
            Application toWithdraw = null;
            for (Application a : ds.getApplications()) {
                if (a == null || !Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
                if (!aid.equalsIgnoreCase(a.getApplicantId())) continue;
                if (posCode != null && posCode.equalsIgnoreCase(a.getPositionCode())) {
                    toWithdraw = a;
                    break;
                }
            }
            if (toWithdraw != null) {
                toWithdraw.setStatus(Application.STATUS_REJECTED);
                ds.updateApplication(toWithdraw);

                // Recalculate workload: subtract the position's hours
                Position p = ds.getPositionByCode(posCode);
                int deduct = p != null ? p.getHoursPerWeek() : 0;
                int currentHours = ds.getWorkloadHours(aid);
                int newHours = Math.max(0, currentHours - deduct);
                ds.setWorkloadHours(aid, newHours);
            }

            ds.clearWorkloadSuggestion();
            sb.append("{\"success\":true,\"message\":\"Position ").append(esc(posCode)).append(" has been removed. Workload updated.\"}");
        } else {
            // Dismiss: just clear the suggestion
            ds.clearWorkloadSuggestion();
            sb.append("{\"success\":true,\"message\":\"Suggestion dismissed.\"}");
        }
    }

    // ============================================================
    // MO's TAs, MO↔TA Messages
    // ============================================================

    private void handleGetMyTas(HttpServletRequest req, StringBuilder sb) {
        String moUser = sessionUsername(req);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> ordered = new ArrayList<>();
        for (Application a : ds.getApplications()) {
            if (a == null || !Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
            if (!moCanManageApplication(req, a)) continue;
            String aid = a.getApplicantId();
            if (aid == null) continue;
            if (seen.add(aid.toUpperCase())) ordered.add(aid);
        }
        sb.append("{\"myTas\":[");
        for (int i = 0; i < ordered.size(); i++) {
            String aid = ordered.get(i);
            TAPplicant tap = ds.getApplicantById(aid);
            User tu = ds.findUserByApplicantId(aid);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"applicantId\":\"").append(esc(aid)).append("\",");
            sb.append("\"taUsername\":\"").append(esc(tu != null ? tu.getUsername() : "")).append("\",");
            sb.append("\"taDisplayName\":\"").append(esc(tu != null ? tu.getDisplayName() : (tap != null ? tap.getName() : ""))).append("\",");
            sb.append("\"applicant\":").append(tap != null ? applicantToJson(tap) : "{}").append(",");
            List<Application> acc = ds.getApplications().stream()
                .filter(x -> x != null && Application.STATUS_ACCEPTED.equals(x.getStatus())
                    && aid.equalsIgnoreCase(x.getApplicantId()))
                .filter(x -> moCanManageApplication(req, x))
                .collect(java.util.stream.Collectors.toList());
            sb.append("\"acceptedApplications\":[");
            for (int j = 0; j < acc.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append(applicationToJson(acc.get(j)));
            }
            sb.append("]");
            sb.append("}");
        }
        sb.append("]}");
    }

    /**
     * MO gets all pending TA applicants (Submitted / Under Review) for positions they published.
     * Sorted by AI score descending, each applicantId appears only once.
     */
    private void handleGetPendingApplicants(HttpServletRequest req, StringBuilder sb) {
        String moUser = sessionUsername(req);
        LinkedHashMap<String, Integer> seenOrder = new LinkedHashMap<>();
        List<Application> pendingApps = new ArrayList<>();
        for (Application a : ds.getApplications()) {
            if (a == null) continue;
            String st = a.getStatus();
            if (!Application.STATUS_SUBMITTED.equals(st) && !Application.STATUS_REVIEW.equals(st)) continue;
            if (!moCanManageApplication(req, a)) continue;
            String aid = a.getApplicantId();
            if (aid == null) continue;
            if (!seenOrder.containsKey(aid.toUpperCase())) {
                seenOrder.put(aid.toUpperCase(), pendingApps.size());
                pendingApps.add(a);
            } else {
                int existingIdx = seenOrder.get(aid.toUpperCase());
                Application existing = pendingApps.get(existingIdx);
                if (a.getAiScore() > existing.getAiScore()) {
                    pendingApps.set(existingIdx, a);
                }
            }
        }
        pendingApps.sort((a, b) -> b.getAiScore() - a.getAiScore());
        sb.append("{\"pendingApplicants\":[");
        for (int i = 0; i < pendingApps.size(); i++) {
            if (i > 0) sb.append(",");
            Application a = pendingApps.get(i);
            TAPplicant tap = ds.getApplicantById(a.getApplicantId());
            User tu = ds.findUserByApplicantId(a.getApplicantId());
            sb.append("{");
            sb.append("\"application\":").append(applicationToJson(a)).append(",");
            sb.append("\"applicant\":").append(tap != null ? applicantToJson(tap) : "{}").append(",");
            sb.append("\"taUsername\":\"").append(esc(tu != null ? tu.getUsername() : "")).append("\",");
            sb.append("\"taDisplayName\":\"").append(esc(tu != null ? tu.getDisplayName() : (tap != null ? tap.getName() : ""))).append("\"");
            sb.append("}");
        }
        sb.append("]}");
    }

    /**
     * TA gets their list of accepted positions (used for My Positions).
     * Returns position details + MO username/display name.
     */
    private void handleGetMyPositions(HttpServletRequest req, StringBuilder sb) {
        User me = requireLinkedApplicant(req, sb, "No applicant profile linked");
        if (me == null) return;
        String aid = me.getApplicantId();
        List<Application> acceptedApps = new ArrayList<>();
        for (Application a : ds.getApplications()) {
            if (a == null || !Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
            if (!aid.equalsIgnoreCase(a.getApplicantId())) continue;
            acceptedApps.add(a);
        }
        sb.append("{\"myPositions\":[");
        for (int i = 0; i < acceptedApps.size(); i++) {
            if (i > 0) sb.append(",");
            Application a = acceptedApps.get(i);
            Position p = ds.getPositionByCode(a.getPositionCode());
            String moU = resolveMoUsernameForPosition(p);
            String moDisp = "";
            if (moU != null) {
                User moAcc = ds.getUserByUsername(moU);
                moDisp = (moAcc != null && moAcc.getDisplayName() != null) ? moAcc.getDisplayName() : moU;
            }
            sb.append("{");
            sb.append("\"application\":").append(applicationToJson(a)).append(",");
            sb.append("\"position\":").append(p != null ? positionToJson(p) : "{}").append(",");
            sb.append("\"moUsername\":\"").append(esc(moU != null ? moU : "")).append("\",");
            sb.append("\"moDisplayName\":\"").append(esc(moDisp)).append("\"");
            sb.append("}");
        }
        sb.append("]}");
    }

    /**
     * When TA views a rejected application detail, clears the rejectedByMo flag.
     * If rejected >= 2 times, permanently block (no more re-applications).
     */
    private void handleMarkApplicationViewed(HttpServletRequest req, StringBuilder sb) {
        String appId = req.getParameter("applicationId");
        if (appId == null || appId.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing applicationId\"}");
            return;
        }
        Application app = ds.getApplicationById(appId);
        if (app == null) {
            sb.append("{\"success\":false,\"message\":\"Application not found\"}");
            return;
        }
        // Security check: can only clear own application
        User me = ds.getUserByUsername(sessionUsername(req));
        if (me == null || me.getApplicantId() == null
                || !me.getApplicantId().equalsIgnoreCase(app.getApplicantId())) {
            sb.append("{\"success\":false,\"message\":\"Unauthorized\"}");
            return;
        }
        // Bug 3 fix: when TA views a rejected application detail, clear the rejectedByMo flag
        // But if rejected >= 2 times, permanently block, no more re-applications
        boolean wasRejectedByMo = Application.STATUS_REJECTED.equals(app.getStatus()) && app.isRejectedByMo();
        boolean permanentlyBlocked = app.isPermanentlyBlocked();

        if (wasRejectedByMo) {
            if (permanentlyBlocked) {
                // Rejected >= 2 times, permanently blocked: clear flag but don't set to false, keep blocked state
                System.out.println("[handleMarkApplicationViewed] Permanently blocked: appId=" + appId
                    + ", moRejectionCount=" + app.getMoRejectionCount());
            } else {
                // First-time rejection: clear flag so position becomes Available again
                app.setRejectedByMo(false);
                ds.updateApplication(app);
                System.out.println("[handleMarkApplicationViewed] Cleared rejectedByMo for appId=" + appId
                    + " (applicantId=" + app.getApplicantId() + ", position=" + app.getPositionCode() + ")");
            }
        }
        sb.append("{\"success\":true,\"message\":\"Application marked as viewed\",\"positionAvailable\":").append(wasRejectedByMo && !permanentlyBlocked).append(",\"permanentlyBlocked\":").append(permanentlyBlocked).append("}");
    }

    private void handleGetMoTaMessages(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String moUser = sessionUsername(req);
        if (applicantId == null || applicantId.isEmpty() || moUser == null) {
            sb.append("{\"success\":false,\"message\":\"Missing applicantId\"}");
            return;
        }
        // New logic: MO view messages no longer checks isTaOfMo (only Accepted), now checks:
        // this applicantId has any application record for any position published by the MO (Submitted/Under Review/Accepted all qualify)
        if (!moHasApplicationWith(applicantId.trim(), moUser)) {
            sb.append("{\"success\":false,\"message\":\"No application from this applicant under your positions\"}");
            return;
        }
        List<MoTaMessage> all = ds.getMoTaMessagesSnapshot().stream()
            .filter(m -> m != null && moUser.equalsIgnoreCase(m.getMoUsername())
                && applicantId.trim().equalsIgnoreCase(m.getTaApplicantId()))
            .sorted(Comparator.comparing(MoTaMessage::getSentAt, Comparator.nullsLast(String::compareTo)))
            .collect(java.util.stream.Collectors.toList());
        sb.append("{\"success\":true,\"messages\":[");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(moTaMessageToJson(all.get(i)));
        }
        sb.append("]}");
    }

    private void handleGetTaMoThreads(HttpServletRequest req, StringBuilder sb) {
        User me = requireLinkedApplicant(req, sb, "No applicant profile linked");
        if (me == null) return;
        String aid = me.getApplicantId();
        LinkedHashMap<String, String[]> moUserToInfo = new LinkedHashMap<>();
        // Collect all MOs for this TA's applications (including Accepted and Pending)
        for (Application a : ds.getApplications()) {
            if (a == null) continue;
            String appStatus = a.getStatus();
            if (appStatus == null) continue;
            boolean isAccepted = Application.STATUS_ACCEPTED.equals(appStatus);
            boolean isPending = Application.STATUS_SUBMITTED.equals(appStatus) || Application.STATUS_REVIEW.equals(appStatus);
            if (!isAccepted && !isPending) continue;
            if (!aid.equalsIgnoreCase(a.getApplicantId())) continue;
            Position p = ds.getPositionByCode(a.getPositionCode());
            String mu = resolveMoUsernameForPosition(p);
            if (mu == null) continue;
            if (moUserToInfo.containsKey(mu)) continue;
            User m = ds.getUserByUsername(mu);
            String displayName = m != null && m.getDisplayName() != null ? m.getDisplayName() : mu;
            moUserToInfo.put(mu, new String[]{displayName, isAccepted ? "accepted" : "pending"});
        }
        String myU = me.getUsername();
        sb.append("{\"success\":true,\"threads\":[");
        int i = 0;
        for (Map.Entry<String, String[]> e : moUserToInfo.entrySet()) {
            if (i++ > 0) sb.append(",");
            int unread = countUnreadMoTaThread(e.getKey(), aid, myU);
            String displayName = e.getValue()[0];
            String status = e.getValue()[1];
            sb.append("{");
            sb.append("\"moUsername\":\"").append(esc(e.getKey())).append("\",");
            sb.append("\"moDisplayName\":\"").append(esc(displayName)).append("\",");
            sb.append("\"unread\":").append(unread).append(",");
            sb.append("\"status\":\"").append(esc(status)).append("\"");
            sb.append("}");
        }
        sb.append("]}");
    }

    private void handleGetTaMoMessages(HttpServletRequest req, StringBuilder sb) {
        String moUsername = req.getParameter("moUsername");
        User me = requireLinkedApplicant(req, sb, "No applicant profile");
        if (me == null) return;
        if (moUsername == null || moUsername.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing moUsername\"}");
            return;
        }
        final String moUserKey = moUsername.trim();
        // New logic: no longer limited to Accepted, now any application record for any position the MO published is sufficient
        if (!moHasApplicationWith(me.getApplicantId(), moUserKey)) {
            sb.append("{\"success\":false,\"message\":\"Invalid thread\"}");
            return;
        }
        final String aid = me.getApplicantId();
        List<MoTaMessage> all = ds.getMoTaMessagesSnapshot().stream()
            .filter(m -> m != null && moUserKey.equalsIgnoreCase(m.getMoUsername())
                && aid.equalsIgnoreCase(m.getTaApplicantId()))
            .sorted(Comparator.comparing(MoTaMessage::getSentAt, Comparator.nullsLast(String::compareTo)))
            .collect(java.util.stream.Collectors.toList());
        sb.append("{\"success\":true,\"messages\":[");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(moTaMessageToJson(all.get(i)));
        }
        sb.append("]}");
    }

    private void handleGetMessageUnread(HttpServletRequest req, StringBuilder sb) {
        String u = sessionUsername(req);
        int n = ds.countUnreadMoTaForUser(u);
        sb.append("{\"success\":true,\"unread\":").append(n).append("}");
    }

    private void handlePostMoTaMessage(HttpServletRequest req, StringBuilder sb) {
        HttpSession session = req.getSession(false);
        String role = (String) session.getAttribute("currentRole");
        String body = req.getParameter("body");
        if (body == null || body.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Message body is required\"}");
            return;
        }
        body = body.trim();
        if (body.length() > 8000) {
            sb.append("{\"success\":false,\"message\":\"Message too long\"}");
            return;
        }
        String myUser = sessionUsername(req);
        User me = ds.getUserByUsername(myUser);
        if (me == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }

        if ("MO".equals(role)) {
            String applicantId = req.getParameter("applicantId");
            if (applicantId == null || applicantId.isEmpty()) {
                sb.append("{\"success\":false,\"message\":\"applicantId required\"}");
                return;
            }
            applicantId = applicantId.trim();
            // New logic: no longer requires isTaOfMo (only Accepted), now uses moHasApplicationWith (any application status qualifies)
            if (!moHasApplicationWith(applicantId, myUser)) {
                sb.append("{\"success\":false,\"message\":\"No application from this applicant under your positions\"}");
                return;
            }
            User taUser = ds.findUserByApplicantId(applicantId);
            String toU = taUser != null ? taUser.getUsername() : "";
            MoTaMessage m = MoTaMessage.create(myUser, applicantId, myUser,
                me.getDisplayName() != null ? me.getDisplayName() : myUser,
                "MO", toU, body);
            ds.addMoTaMessage(m);
            sb.append("{\"success\":true,\"message\":\"Sent\"}");
            return;
        }
        if ("TA".equals(role)) {
            String moUsername = req.getParameter("moUsername");
            if (moUsername == null || moUsername.trim().isEmpty()) {
                sb.append("{\"success\":false,\"message\":\"moUsername required\"}");
                return;
            }
            moUsername = moUsername.trim();
            String aid = me.getApplicantId();
            if (aid == null || aid.isEmpty()) {
                sb.append("{\"success\":false,\"message\":\"No applicant profile\"}");
                return;
            }
            if (!taHasAcceptedWithMo(aid, moUsername)) {
                sb.append("{\"success\":false,\"message\":\"You are not assigned to this module organiser\"}");
                return;
            }
            User moAcc = ds.getUserByUsername(moUsername);
            if (moAcc == null || !moAcc.hasRole("MO")) {
                sb.append("{\"success\":false,\"message\":\"Invalid MO account\"}");
                return;
            }
            MoTaMessage m = MoTaMessage.create(moUsername, aid, myUser,
                me.getDisplayName() != null ? me.getDisplayName() : myUser,
                "TA", moUsername, body);
            ds.addMoTaMessage(m);
            sb.append("{\"success\":true,\"message\":\"Sent\"}");
            return;
        }
        sb.append("{\"success\":false,\"message\":\"Invalid role\"}");
    }

    private void handlePostMarkMoTaRead(HttpServletRequest req, StringBuilder sb) {
        HttpSession session = req.getSession(false);
        String role = (String) session.getAttribute("currentRole");
        String myUser = sessionUsername(req);
        User me = ds.getUserByUsername(myUser);
        if (me == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }
        if ("MO".equals(role)) {
            String applicantId = req.getParameter("applicantId");
            if (applicantId == null || applicantId.isEmpty()) {
                sb.append("{\"success\":false,\"message\":\"applicantId required\"}");
                return;
            }
            applicantId = applicantId.trim();
            if (!moHasApplicationWith(applicantId, myUser)) {
                sb.append("{\"success\":false,\"message\":\"No application from this applicant under your positions\"}");
                return;
            }
            ds.markMoTaThreadRead(myUser, applicantId, myUser);
            sb.append("{\"success\":true}");
            return;
        }
        if ("TA".equals(role)) {
            String moUsername = req.getParameter("moUsername");
            if (moUsername == null || moUsername.trim().isEmpty() || me.getApplicantId() == null) {
                sb.append("{\"success\":false,\"message\":\"moUsername required\"}");
                return;
            }
            moUsername = moUsername.trim();
            if (!moHasApplicationWith(me.getApplicantId(), moUsername)) {
                sb.append("{\"success\":false,\"message\":\"Invalid thread\"}");
                return;
            }
            ds.markMoTaThreadRead(moUsername, me.getApplicantId(), myUser);
            sb.append("{\"success\":true}");
            return;
        }
        sb.append("{\"success\":false,\"message\":\"Invalid role\"}");
    }

    private void handleMoApplicant(HttpServletRequest req, StringBuilder sb) {
        String op = req.getParameter("op");
        if (!"update".equals(op)) {
            sb.append("{\"success\":false,\"message\":\"Only op=update supported\"}");
            return;
        }
        String applicantId = req.getParameter("applicantId");
        String moUser = sessionUsername(req);
        if (applicantId == null || applicantId.isEmpty() || moUser == null) {
            sb.append("{\"success\":false,\"message\":\"Missing applicantId\"}");
            return;
        }
        applicantId = applicantId.trim();
        if (!moHasApplicationWith(applicantId, moUser)) {
            sb.append("{\"success\":false,\"message\":\"No application from this applicant under your positions\"}");
            return;
        }
        handleUpdateApplicantProfile(req, sb);
    }

    // ============================================================
    // Admin user management
    // ============================================================

    private void handleManageUser(HttpServletRequest req, StringBuilder sb) {
        String op = req.getParameter("op");
        if (op == null || op.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing operation parameter (op)\"}");
            return;
        }

        switch (op) {
            case "create":
                handleCreateUser(req, sb);
                break;
            case "update":
                handleUpdateUser(req, sb);
                break;
            case "delete":
                handleDeleteUser(req, sb);
                break;
            default:
                sb.append("{\"success\":false,\"message\":\"Unknown operation: " + esc(op) + "\"}");
        }
    }

    private void handleCreateUser(HttpServletRequest req, StringBuilder sb) {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String displayName = req.getParameter("displayName");
        String email = req.getParameter("email");
        String rolesStr = req.getParameter("roles");
        String applicantId = req.getParameter("applicantId");

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Username and password are required\"}");
            return;
        }
        if (ds.getUserByUsername(username) != null) {
            sb.append("{\"success\":false,\"message\":\"Username already exists\"}");
            return;
        }

        Set<String> roles = new HashSet<>();
        if (rolesStr != null && !rolesStr.trim().isEmpty()) {
            for (String r : rolesStr.split(",")) {
                String role = r.trim().toUpperCase();
                if (role.equals("TA") || role.equals("MO") || role.equals("ADMIN")) {
                    roles.add(role);
                }
            }
        }
        if (roles.isEmpty()) roles.add("TA");

        User user = new User(username, User.hashPassword(password),
            displayName != null ? displayName : username, email != null ? email : "");
        for (String r : roles) user.addRole(r);
        if (applicantId != null && !applicantId.isEmpty()) user.setApplicantId(applicantId);

        ds.saveUser(user);
        if (user.hasRole("TA")) {
            ds.syncUserAndApplicantEmails(username);
        }

        sb.append("{\"success\":true,\"message\":\"User created: " + esc(username) + "\"}");
    }

    private void handleUpdateUser(HttpServletRequest req, StringBuilder sb) {
        String username = req.getParameter("username");
        String newPassword = req.getParameter("newPassword");
        String displayName = req.getParameter("displayName");
        String email = req.getParameter("email");
        String rolesStr = req.getParameter("roles");
        String applicantId = req.getParameter("applicantId");

        if (username == null || username.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Username is required\"}");
            return;
        }

        User user = ds.getUserByUsername(username);
        if (user == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }

        if (newPassword != null && !newPassword.isEmpty()) {
            if (newPassword.length() > 128) {
                sb.append("{\"success\":false,\"message\":\"Password too long (max 128)\"}");
                return;
            }
            user.setPasswordHash(User.hashPassword(newPassword));
        }
        if (displayName != null) user.setDisplayName(displayName);
        if (email != null) user.setEmail(email);
        if (applicantId != null) user.setApplicantId(applicantId.isEmpty() ? null : applicantId);

        if (rolesStr != null && !rolesStr.isEmpty()) {
            user.getRoles().clear();
            for (String r : rolesStr.split(",")) {
                String role = r.trim().toUpperCase();
                if (role.equals("TA") || role.equals("MO") || role.equals("ADMIN")) {
                    user.addRole(role);
                }
            }
        }

        ds.saveUser(user);
        ds.syncUserAndApplicantEmails(username);

        sb.append("{\"success\":true,\"message\":\"User updated: " + esc(username) + "\"}");
    }

    private void handleDeleteUser(HttpServletRequest req, StringBuilder sb) {
        String username = req.getParameter("username");
        if (username == null || username.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Username is required\"}");
            return;
        }
        User user = ds.getUserByUsername(username);
        if (user == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }
        // Prevent deleting the last ADMIN
        if (user.hasRole("ADMIN")) {
            long adminCount = ds.getUsers().stream()
                .filter(u -> u != null && u.hasRole("ADMIN"))
                .count();
            if (adminCount <= 1) {
                sb.append("{\"success\":false,\"message\":\"Cannot delete the last administrator account\"}");
                return;
            }
        }
        if (!ds.removeUserByUsername(username)) {
            sb.append("{\"success\":false,\"message\":\"Failed to delete user (could not save)\"}");
            return;
        }
        sb.append("{\"success\":true,\"message\":\"User deleted: " + esc(username) + "\"}");
    }

    // ============================================================
    // Admin applicant management
    // ============================================================

    private void handleAdminApplicant(HttpServletRequest req, StringBuilder sb) {
        String op = req.getParameter("op");
        if (op == null || op.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing operation parameter (op)\"}");
            return;
        }

        switch (op) {
            case "update":
                handleUpdateApplicantProfile(req, sb);
                break;
            case "delete":
                handleDeleteApplicant(req, sb);
                break;
            default:
                sb.append("{\"success\":false,\"message\":\"Unknown operation: " + esc(op) + "\"}");
        }
    }

    private void handleUpdateApplicantProfile(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        if (applicantId == null || applicantId.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Applicant ID is required\"}");
            return;
        }

        TAPplicant ta = ds.getApplicantById(applicantId);
        if (ta == null) {
            sb.append("{\"success\":false,\"message\":\"Applicant not found\"}");
            return;
        }

        String name = req.getParameter("name");
        String email = req.getParameter("email");
        String year = req.getParameter("yearOfStudy");
        String gpaStr = req.getParameter("gpa");
        String hoursStr = req.getParameter("hoursAvailable");
        String skillsStr = req.getParameter("skills");

        if (name != null && !name.isEmpty()) ta.setName(name);
        if (email != null) ta.setEmail(email);
        if (year != null) ta.setYearOfStudy(year);

        if (gpaStr != null && !gpaStr.isEmpty()) {
            try {
                double gpa = Double.parseDouble(gpaStr);
                if (gpa >= 0.0 && gpa <= 4.0) ta.setGpa(gpa);
            } catch (NumberFormatException ignored) {}
        }
        if (hoursStr != null && !hoursStr.isEmpty()) {
            try {
                int hours = Integer.parseInt(hoursStr);
                if (hours >= 0 && hours <= 20) ta.setHoursAvailable(hours);
            } catch (NumberFormatException ignored) {}
        }
        if (skillsStr != null) {
            List<String> skills = new ArrayList<>();
            for (String s : skillsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) skills.add(trimmed);
            }
            ta.setSkills(skills);
        }

        ds.saveApplicant(ta);
        sb.append("{\"success\":true,\"message\":\"Applicant profile updated\"}");
    }

    private void handleDeleteApplicant(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        if (applicantId == null || applicantId.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Applicant ID is required\"}");
            return;
        }
        // Check if any user is linked to this applicant ID
        boolean hasLinkedUser = ds.getUsers().stream()
            .anyMatch(u -> u != null && applicantId.equals(u.getApplicantId()));
        if (hasLinkedUser) {
            sb.append("{\"success\":false,\"message\":\"Cannot delete: a user account is linked to this applicant ID\"}");
            return;
        }
        // Delete all application records for this applicant
        List<Application> toRemove = new ArrayList<>();
        for (Application a : ds.getApplications()) {
            if (a != null && applicantId.equals(a.getApplicantId())) {
                toRemove.add(a);
            }
        }
        for (Application a : toRemove) {
            ds.getApplications().remove(a);
        }
        if (!toRemove.isEmpty()) ds.saveApplications();

        // Delete applicant record
        TAPplicant toDelete = ds.getApplicantById(applicantId);
        if (toDelete != null) {
            ds.getApplicants().remove(toDelete);
            ds.saveApplicants();
        }

        sb.append("{\"success\":true,\"message\":\"Applicant and " + toRemove.size() + " application(s) deleted\"}");
    }

    // ============================================================
    // CV upload handler
    // ============================================================

    private void handleCVUpload(HttpServletRequest req, StringBuilder sb) {
        // Get Content-Type to determine if there is a file upload
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            sb.append("{\"success\":false,\"message\":\"No file uploaded or incorrect Content-Type\"}");
            return;
        }

        // Note: requires @MultipartConfig annotation support.
        // If servlet container does not support multipart, use a separate UploadServlet instead.
        String applicantId = req.getParameter("applicantId");
        if (applicantId == null || applicantId.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Applicant ID is required for CV upload\"}");
            return;
        }

        TAPplicant ta = ds.getApplicantById(applicantId);
        if (ta == null) {
            sb.append("{\"success\":false,\"message\":\"Applicant not found\"}");
            return;
        }

        // Simple implementation: return success response (actual file upload requires @MultipartConfig)
        // File save logic will be implemented in a separate UploadServlet
        sb.append("{\"success\":true,\"message\":\"CV upload endpoint reached. Please configure @MultipartConfig for full file upload support.\"}");
    }

    // ============================================================
    // JSON serialization
    // ============================================================

    private String positionToJson(Position p) {
        if (p == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"code\":\"").append(esc(p.getCode())).append("\",");
        sb.append("\"name\":\"").append(esc(p.getName())).append("\",");
        sb.append("\"requiredSkills\":\"").append(esc(p.getRequiredSkillsStrComputed())).append("\",");
        sb.append("\"skillsList\":[");
        List<String> sk = p.getRequiredSkills();
        if (sk != null) {
            for (int i = 0; i < sk.size(); i++) {
                sb.append("\"").append(esc(sk.get(i))).append("\"");
                if (i < sk.size() - 1) sb.append(",");
            }
        }
        sb.append("],");
        sb.append("\"hoursPerWeek\":").append(p.getHoursPerWeek()).append(",");
        sb.append("\"totalSlots\":").append(p.getTotalSlots()).append(",");
        sb.append("\"filledSlots\":").append(p.getFilledSlots()).append(",");
        sb.append("\"remainingSlots\":").append(p.getRemainingSlots()).append(",");
        sb.append("\"deadline\":\"").append(esc(p.getDeadline())).append("\",");
        sb.append("\"status\":\"").append(esc(p.getStatus())).append("\",");
        sb.append("\"postedBy\":\"").append(esc(p.getPostedBy())).append("\",");
        sb.append("\"postedByUsername\":").append(p.getPostedByUsername() != null
            ? "\"" + esc(p.getPostedByUsername()) + "\"" : "null").append(",");
        sb.append("\"open\":").append(p.isOpen());
        sb.append("}");
        return sb.toString();
    }

    private String applicantToJson(TAPplicant a) {
        if (a == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(esc(a.getId())).append("\",");
        sb.append("\"name\":\"").append(esc(a.getName())).append("\",");
        sb.append("\"email\":\"").append(esc(a.getEmail())).append("\",");
        sb.append("\"yearOfStudy\":\"").append(esc(a.getYearOfStudy())).append("\",");
        sb.append("\"gpa\":").append(a.getGpa()).append(",");
        sb.append("\"skills\":[");
        List<String> sk = a.getSkills();
        if (sk != null) {
            for (int i = 0; i < sk.size(); i++) {
                sb.append("\"").append(esc(sk.get(i))).append("\"");
                if (i < sk.size() - 1) sb.append(",");
            }
        }
        sb.append("],");
        sb.append("\"hoursAvailable\":").append(a.getHoursAvailable()).append(",");
        sb.append("\"cvFileName\":").append(a.getCvFileName() != null && !a.getCvFileName().isEmpty()
            ? "\"" + esc(a.getCvFileName()) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }

    private String applicationToJson(Application a) {
        if (a == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(esc(a.getId())).append("\",");
        sb.append("\"applicantId\":\"").append(esc(a.getApplicantId())).append("\",");
        sb.append("\"applicantName\":\"").append(esc(a.getApplicantName())).append("\",");
        sb.append("\"positionCode\":\"").append(esc(a.getPositionCode())).append("\",");
        sb.append("\"positionName\":\"").append(esc(a.getPositionName())).append("\",");
        sb.append("\"appliedAt\":\"").append(esc(a.getAppliedAt())).append("\",");
        sb.append("\"status\":\"").append(esc(a.getStatus())).append("\",");
        sb.append("\"rejectedByMo\":").append(a.isRejectedByMo()).append(",");
        sb.append("\"moRejectionCount\":").append(a.getMoRejectionCount()).append(",");
        sb.append("\"applyCount\":").append(a.getApplyCount()).append(",");
        sb.append("\"aiScore\":").append(a.getAiScore()).append(",");
        sb.append("\"aiExplanation\":\"").append(esc(a.getAiExplanation())).append("\",");
        sb.append("\"llmExplanation\":").append(a.getLlmExplanation() != null ? "\"" + esc(a.getLlmExplanation()) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }

    private String userToJsonForDirectory(User u) {
        if (u == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\":\"").append(esc(u.getUsername())).append("\",");
        sb.append("\"displayName\":\"").append(esc(u.getDisplayName())).append("\",");
        sb.append("\"email\":\"").append(esc(u.getEmail() != null ? u.getEmail() : "")).append("\",");
        sb.append("\"applicantId\":\"").append(esc(u.getApplicantId() != null ? u.getApplicantId() : "")).append("\",");
        sb.append("\"roles\":[");
        if (u.getRoles() != null) {
            int i = 0;
            for (String r : u.getRoles()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(r)).append("\"");
                i++;
            }
        }
        sb.append("]");
        TAPplicant tap = null;
        if (u.getApplicantId() != null && !u.getApplicantId().isEmpty()) {
            tap = ds.getApplicantById(u.getApplicantId());
        }
        if (tap != null) {
            sb.append(",\"applicantProfile\":").append(applicantToJson(tap));
        } else {
            sb.append(",\"applicantProfile\":null");
        }
        sb.append("}");
        return sb.toString();
    }

    private String moTaMessageToJson(MoTaMessage m) {
        if (m == null) return "{}";
        return "{\"id\":\"" + esc(m.getId()) + "\","
            + "\"moUsername\":\"" + esc(m.getMoUsername()) + "\","
            + "\"taApplicantId\":\"" + esc(m.getTaApplicantId()) + "\","
            + "\"fromUsername\":\"" + esc(m.getFromUsername()) + "\","
            + "\"fromDisplayName\":\"" + esc(m.getFromDisplayName()) + "\","
            + "\"fromRole\":\"" + esc(m.getFromRole()) + "\","
            + "\"toUsername\":\"" + esc(m.getToUsername()) + "\","
            + "\"body\":\"" + esc(m.getBody()) + "\","
            + "\"sentAt\":\"" + esc(m.getSentAt()) + "\","
            + "\"readByRecipient\":" + m.isReadByRecipient() + "}";
    }

    private String systemConfigToJson(SystemConfig cfg) {
        if (cfg == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"appVersion\":\"").append(esc(cfg.getAppVersion())).append("\",");
        sb.append("\"buildDate\":\"").append(esc(cfg.getBuildDate())).append("\",");
        sb.append("\"demoAccounts\":[");
        if (cfg.getDemoAccounts() != null) {
            for (int i = 0; i < cfg.getDemoAccounts().size(); i++) {
                SystemConfig.DemoAccount a = cfg.getDemoAccounts().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                // P0-9: Do not return plaintext passwords, only return roles and username
                sb.append("\"username\":\"").append(esc(a.getUsername())).append("\",");
                sb.append("\"role\":\"").append(esc(a.getRole())).append("\",");
                sb.append("\"displayName\":\"").append(esc(a.getDisplayName())).append("\"}");
            }
        }
        sb.append("],");
        sb.append("\"versionHistory\":[");
        if (cfg.getVersionHistory() != null) {
            for (int i = 0; i < cfg.getVersionHistory().size(); i++) {
                SystemConfig.VersionEntry v = cfg.getVersionHistory().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"version\":\"").append(esc(v.getVersion())).append("\",");
                sb.append("\"date\":\"").append(esc(v.getDate())).append("\",");
                sb.append("\"title\":\"").append(esc(v.getTitle())).append("\",");
                sb.append("\"description\":\"").append(esc(v.getDescription())).append("\"}");
            }
        }
        sb.append("],");
        sb.append("\"featureCoverage\":[");
        if (cfg.getFeatureCoverage() != null) {
            for (int i = 0; i < cfg.getFeatureCoverage().size(); i++) {
                SystemConfig.FeatureCoverage fc = cfg.getFeatureCoverage().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"icon\":\"").append(esc(fc.getIcon())).append("\",");
                sb.append("\"text\":\"").append(esc(fc.getText())).append("\"}");
            }
        }
        sb.append("],");
        sb.append("\"fileStatusConfig\":[");
        if (cfg.getFileStatusConfig() != null) {
            for (int i = 0; i < cfg.getFileStatusConfig().size(); i++) {
                SystemConfig.FileStatusConfig fsc = cfg.getFileStatusConfig().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"filename\":\"").append(esc(fsc.getFilename())).append("\",");
                sb.append("\"displayName\":\"").append(esc(fsc.getDisplayName())).append("\",");
                sb.append("\"category\":\"").append(esc(fsc.getCategory())).append("\"}");
            }
        }
        sb.append("],");
        if (cfg.getWorkloadConfig() != null) {
            SystemConfig.WorkloadConfig wc = cfg.getWorkloadConfig();
            sb.append("\"workloadConfig\":{");
            sb.append("\"capacity\":").append(wc.getCapacity()).append(",");
            sb.append("\"overloadThreshold\":").append(wc.getOverloadThreshold()).append(",");
            sb.append("\"overloadUnit\":\"").append(esc(wc.getOverloadUnit())).append("\"},");
        } else { sb.append("\"workloadConfig\":{},"); }
        if (cfg.getPositionDefaults() != null) {
            SystemConfig.PositionDefaults pd = cfg.getPositionDefaults();
            sb.append("\"positionDefaults\":{");
            sb.append("\"defaultHours\":").append(pd.getDefaultHours()).append(",");
            sb.append("\"defaultSlots\":").append(pd.getDefaultSlots()).append(",");
            sb.append("\"defaultDeadline\":\"").append(esc(pd.getDefaultDeadline())).append("\",");
            sb.append("\"defaultPostedBy\":\"").append(esc(pd.getDefaultPostedBy())).append("\"},");
        } else { sb.append("\"positionDefaults\":{},"); }
        sb.append("\"skillSuggestions\":[");
        if (cfg.getSkillSuggestions() != null) {
            for (int i = 0; i < cfg.getSkillSuggestions().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(cfg.getSkillSuggestions().get(i))).append("\"");
            }
        }
        sb.append("],");
        if (cfg.getDataTraceability() != null) {
            SystemConfig.DataTraceability dt = cfg.getDataTraceability();
            sb.append("\"dataTraceability\":{");
            sb.append("\"positions\":\"").append(esc(dt.getPositions())).append("\",");
            sb.append("\"applications\":\"").append(esc(dt.getApplications())).append("\",");
            sb.append("\"applicants\":\"").append(esc(dt.getApplicants())).append("\",");
            sb.append("\"workloads\":\"").append(esc(dt.getWorkloads())).append("\",");
            sb.append("\"users\":\"").append(esc(dt.getUsers())).append("\",");
            sb.append("\"logs\":\"").append(esc(dt.getLogs())).append("\",");
            sb.append("\"cvs\":\"").append(esc(dt.getCvs())).append("\"}");
        } else { sb.append("\"dataTraceability\":{}"); }
        sb.append("}");
        return sb.toString();
    }

    private String logToJson(SystemLog l) {
        if (l == null) return "{}";
        return "{\"timestamp\":\"" + esc(l.getTimestamp()) + "\","
             + "\"operation\":\"" + esc(l.getOperation()) + "\","
             + "\"fileName\":\"" + esc(l.getFileName()) + "\","
             + "\"status\":\"" + esc(l.getStatus()) + "\","
             + "\"opIcon\":\"" + esc(l.getOpIcon()) + "\"}";
    }

    // 删除有问题的logToJsonExtended方法
    private String logToJsonExtended(SystemLog l) {
        if (l == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{"timestamp":"").append(esc(l.getTimestamp())).append("",");
        sb.append(""operation":"").append(esc(l.getOperation())).append("",");
        sb.append(""fileName":"").append(esc(l.getFileName())).append("",");
        sb.append(""status":"").append(esc(l.getStatus())).append("",");
        sb.append(""opIcon":"").append(esc(l.getOpIcon())).append(""");
        
        // 添加新字段
        if (l.getLevel() != null) {
            sb.append(","level":"").append(esc(l.getLevel())).append(""");
        }
        if (l.getUserId() != null) {
            sb.append(","userId":"").append(esc(l.getUserId())).append(""");
        }
        if (l.getIpAddress() != null) {
            sb.append(","ipAddress":"").append(esc(l.getIpAddress())).append(""");
        }
        if (l.getSessionId() != null) {
            sb.append(","sessionId":"").append(esc(l.getSessionId())).append(""");
        }
        if (l.getDetail() != null) {
            sb.append(","detail":"").append(esc(l.getDetail())).append(""");
        }
        
        sb.append("}");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
