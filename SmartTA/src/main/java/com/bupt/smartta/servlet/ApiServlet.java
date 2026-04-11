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
import java.util.*;
import java.util.regex.Pattern;

public class ApiServlet extends HttpServlet {

    private final DataStore ds = DataStore.getInstance();
    private final LLMService llmService = LLMService.getInstance();

    /** 合法的申请状态值 */
    private static final Set<String> VALID_STATUSES = Set.of(
        Application.STATUS_SUBMITTED,
        Application.STATUS_REVIEW,
        Application.STATUS_ACCEPTED,
        Application.STATUS_REJECTED
    );

    /** 状态流转规则：key = 当前状态，value = 允许的下一状态集合 */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS;
    static {
        Map<String, Set<String>> m = new HashMap<>();
        m.put(Application.STATUS_SUBMITTED, Set.of(Application.STATUS_REVIEW));
        m.put(Application.STATUS_REVIEW,    Set.of(Application.STATUS_ACCEPTED, Application.STATUS_REJECTED));
        // MO 可将已录用 TA 退回（释放席位），仅限其本人发布的职位
        m.put(Application.STATUS_ACCEPTED,  Set.of(Application.STATUS_REJECTED));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(m);
    }

    /** 工作量有效范围 */
    private static final int MIN_HOURS = 0;
    private static final int MAX_HOURS = 168; // 24h * 7

    // ============================================================
    // 工具方法：路径安全校验
    // ============================================================

    /**
     * 校验 action 参数不包含路径穿越字符。
     * 防止 /api/../../../etc/passwd 这类攻击。
     */
    private boolean isValidAction(String action) {
        if (action == null || action.isEmpty()) return false;
        // 禁止路径穿越、绝对路径、反斜杠
        if (action.contains("..") || action.startsWith("/") || action.startsWith("\\")
                || action.contains("./") || action.contains(".\\")) {
            return false;
        }
        // 只能是字母、数字、下划线、短横线
        return Pattern.matches("^[a-zA-Z0-9_-]+$", action);
    }

    // ============================================================
    // 工具方法：角色权限校验
    // ============================================================

    /**
     * 校验当前会话用户是否持有指定角色之一。
     * @param roles 允许的角色（MO、ADMIN 等）
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
     * 是否具备管理员权限：当前视图角色为 ADMIN，或账号在库中持有 ADMIN（支持多管理员、与「当前扮演角色」解耦）。
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
     * 校验当前会话是否已登录。
     */
    private boolean isAuthenticated(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && session.getAttribute("username") != null;
    }

    private String sessionUsername(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (String) s.getAttribute("username");
    }

    /** MO 是否管理该申请对应的职位（按 postedByUsername 或 Posted By 显示名） */
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

    /** TA 是否已被当前 MO 发布的职位录用 */
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

    /** MO 发布的所有职位（含历史）中，该申请者是否有任意状态的申请记录 */
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

    // ============================================================
    // 工具方法：响应输出
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
    // HTTP 方法分发
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
        // P0-1: 路径穿越防护
        if (!isValidAction(action)) {
            sendError(resp, 400, "Invalid action parameter");
            return;
        }

        StringBuilder sb = new StringBuilder();

        switch (action) {
            case "positions":
                sb.append("{\"positions\":[");
                List<Position> positions = ds.getPositions();
                // 修复2: 如果当前用户同时是TA和MO，不显示自己作为MO发布的职位
                String taUsername = sessionUsername(req);
                User taUser = taUsername != null ? ds.getUserByUsername(taUsername) : null;
                boolean isAlsoMo = taUser != null && taUser.hasRole("MO");
                // 修复4: 过滤掉当前TA已有申请的职位（无论状态如何均不再出现，避免前端未刷新的竞态）
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
                    // 如果该职位是自己发布的（通过postedByUsername判断），且自己也是MO，则过滤掉
                    if (isAlsoMo && pos.getPostedByUsername() != null
                            && pos.getPostedByUsername().equalsIgnoreCase(taUsername)) {
                        continue;
                    }
                    // 过滤掉已有申请的职位
                    boolean hasApp = false;
                    for (Application a : myApps) {
                        if (a.getPositionCode().equalsIgnoreCase(pos.getCode())) {
                            hasApp = true;
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

            // P0-8: users API — 管理员见 MO/TA 目录；普通登录用户仅见自身一条（供 TA 页兜底 applicantId）
            // 修复1: Admin在MO&TA Directory中不显示自己（管理员本人不应出现在目录列表中）
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
                        // 排除当前登录的Admin本人
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
                // 返回与 TA 用户账户关联的申请者工作量
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

            // 工作量详情（返回 entries 数组供前端直接使用）
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

            // MO专属：返回当前MO负责的课程列表（按postedByUsername过滤）
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

        // P0-1: 路径穿越防护
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
                handleCreateApplicant(req, sb);
                break;
            case "position":
                // P0-2: 只有 MO/ADMIN 可以发布/更新职位
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handlePositionCreateOrUpdate(req, sb);
                break;
            case "positionDelete":
                // 删除职位
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handleDeletePosition(req, sb);
                break;
            case "updateStatus":
                // P0-2: 只有 MO/ADMIN 可以审核申请
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handleUpdateStatus(req, sb);
                break;
            case "workload":
                // P0-2: 仅管理员（含账号级 ADMIN）可修改工作量
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
            // P0-7: 修复缺失的 quota 路由注册
            case "quota":
                if (!hasRole(req, "MO", "ADMIN")) {
                    sendError(resp, 403, "Insufficient permissions: MO or ADMIN role required");
                    return;
                }
                handleUpdateQuota(req, sb);
                break;
            case "user":
                // 用户管理（admin 页面使用）
                if (!hasAdminCapability(req)) {
                    sendError(resp, 403, "Insufficient permissions: administrator access required");
                    return;
                }
                handleManageUser(req, sb);
                break;
            case "adminApplicant":
                // 申请者管理（admin 页面使用）
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
                // CV 上传（TA 角色）
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
    // 业务处理器
    // ============================================================

    private void handleApply(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String positionCode = req.getParameter("positionCode");
        String applicantName = req.getParameter("applicantName");

        if (applicantId == null || applicantId.isEmpty() || positionCode == null || positionCode.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields: applicantId and positionCode\"}");
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

        Application existing = ds.getApplication(applicantId, positionCode);
        if (existing != null) {
            // 修复4: 被MO拒绝后可重新申请
            if (Application.STATUS_REJECTED.equals(existing.getStatus())) {
                // 删除旧记录，重新申请
                ds.getApplications().remove(existing);
            } else {
                sb.append("{\"success\":false,\"message\":\"Already applied to this position\"}");
                return;
            }
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

        String id = ds.allocateNextApplicantId();
        double gpa = 0.0;
        try { gpa = Double.parseDouble(gpaStr); } catch (Exception e) {}
        // GPA 范围校验
        if (gpa < 0.0 || gpa > 4.0) {
            gpa = Math.max(0.0, Math.min(4.0, gpa));
        }
        int hours = 12;
        try { hours = Integer.parseInt(hoursStr); } catch (Exception e) {}
        // 工作时间范围校验
        if (hours < 0) hours = 0;
        if (hours > 20) hours = 20;

        List<String> skills = new ArrayList<>();
        if (skillsStr != null) {
            for (String s : skillsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) skills.add(trimmed);
            }
        }

        TAPplicant ta = new TAPplicant(id, name, email != null ? email : "",
            year != null ? year : "Year 2", gpa, skills, hours);
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

        Position existingPos = ds.getPositionByCode(code);
        boolean isUpdate = existingPos != null;

        // 权限检查：如果是更新，MO只能更新自己发布的课程
        if (isUpdate) {
            if (!hasAdminCapability(req) && hasRole(req, "MO")) {
                String moUser = sessionUsername(req);
                if (existingPos.getPostedByUsername() == null || !existingPos.getPostedByUsername().equalsIgnoreCase(moUser)) {
                    sb.append("{\"success\":false,\"message\":\"You can only edit your own modules\"}");
                    return;
                }
            }
        } else {
            // 新建时检查代码唯一性
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
            // 不改变 postedByUsername，保持原有MO归属
            ds.savePositions();
        } else {
            pos = new Position(code, name, skills, hours, slots,
                deadline != null ? deadline : "2026-04-30",
                postedBy != null ? postedBy : "Admin");
            pos.setDescription(desc);

            if (session != null && hasRole(req, "MO") && !hasAdminCapability(req)) {
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

        // 权限检查：MO只能删除自己发布的课程
        if (!hasAdminCapability(req) && hasRole(req, "MO")) {
            String moUser = sessionUsername(req);
            if (pos.getPostedByUsername() == null || !pos.getPostedByUsername().equalsIgnoreCase(moUser)) {
                sb.append("{\"success\":false,\"message\":\"You can only delete your own modules\"}");
                return;
            }
        }

        // 删除该课程的所有申请
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

        // 删除课程
        ds.getPositions().remove(pos);
        ds.savePositions();
        ds.addLog(SystemLog.OP_WRITE, "positions.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Module deleted: " + esc(code) + "\"}");
    }

    private void handleUpdateStatus(HttpServletRequest req, StringBuilder sb) {
        String appId = req.getParameter("applicationId");
        String status = req.getParameter("status");

        if (appId == null || appId.isEmpty() || status == null || status.isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields: applicationId and status\"}");
            return;
        }

        // P0-3: 状态值白名单校验
        if (!VALID_STATUSES.contains(status)) {
            sb.append("{\"success\":false,\"message\":\"Invalid status value. Must be one of: Submitted, Under Review, Accepted, Rejected\"}");
            return;
        }

        // 使用高效的 getApplicationById（新增）
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

        // P0-3: 状态机流转合法性校验
        if (!oldStatus.equals(status)) {
            Set<String> allowed = ALLOWED_TRANSITIONS.get(oldStatus);
            if (allowed == null || !allowed.contains(status)) {
                sb.append("{\"success\":false,\"message\":\"Invalid status transition: " + esc(oldStatus)
                    + " -> " + esc(status) + ". Allowed: " + (allowed != null ? allowed : "none") + "\"}");
                return;
            }
        }

        // P0-3: 拒绝时减少 filledSlots
        if (Application.STATUS_ACCEPTED.equals(oldStatus)
                && Application.STATUS_REJECTED.equals(status)) {
            Position pos = ds.getPositionByCode(target.getPositionCode());
            if (pos != null && pos.getFilledSlots() > 0) {
                pos.setFilledSlots(pos.getFilledSlots() - 1);
                ds.savePositions();
            }
        }

        target.setStatus(status);
        ds.updateApplication(target);

        // 接受时增加 filledSlots
        if (Application.STATUS_ACCEPTED.equals(status)) {
            Position pos = ds.getPositionByCode(target.getPositionCode());
            if (pos != null) {
                pos.setFilledSlots(pos.getFilledSlots() + 1);
                ds.savePositions();
            }
        }

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

        // P0-6: 工作量范围校验（防止负数、超大值）
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

        // 权限检查：MO只能修改自己负责的课程的配额
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

        // 配额不能少于已接受的席位数
        if (totalSlots < pos.getFilledSlots()) {
            sb.append("{\"success\":false,\"message\":\"Total slots cannot be less than filled slots (" + pos.getFilledSlots() + ")\"}");
            return;
        }
        // 配额上限
        if (totalSlots > 10) {
            // P0-6: 超出范围返回错误而非静默截断
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
        List<LLMService.WorkloadEntry> entries = new ArrayList<>();
        for (User u : allUsers) {
            if (!u.hasRole("TA")) continue;
            String appId = u.getApplicantId();
            if (appId == null || appId.isEmpty()) continue;
            TAPplicant ta = ds.getApplicantById(appId);
            if (ta == null) continue;
            int hours = workloads.containsKey(appId) ? workloads.get(appId) : 0;
            entries.add(new LLMService.WorkloadEntry(appId, ta.getName(), u.getUsername(), hours));
        }

        // P1: 尝试调用 DeepSeek AI 再平衡建议
        int capacity = 20;
        SystemConfig.WorkloadConfig wc = ds.getSystemConfig().getWorkloadConfig();
        if (wc != null) capacity = wc.getOverloadThreshold();

        LLMService.RebalanceAdvice advice = llmService.generateWorkloadRebalanceAdvice(entries, capacity);

        if (advice != null && "reduce".equals(advice.action)
                && advice.targetApplicantId != null && advice.targetHours != null) {
            // DeepSeek 返回了有效建议
            ds.setWorkloadHours(advice.targetApplicantId, advice.targetHours);
            ds.addLog(SystemLog.OP_WRITE, "workloads.json", SystemLog.STATUS_OK);
            sb.append("{\"success\":true,\"ai\":true,\"message\":\"")
              .append(esc(advice.summary))
              .append("\",\"reasoning\":\"")
              .append(esc(advice.reasoning))
              .append("\",\"targetApplicantId\":\"")
              .append(esc(advice.targetApplicantId))
              .append("\",\"targetHours\":")
              .append(advice.targetHours)
              .append(",\"targetDisplayName\":\"")
              .append(esc(advice.targetDisplayName != null ? advice.targetDisplayName : ""))
              .append("\"}");
            return;
        }

        // P0: DeepSeek unavailable — fallback to fixed rule (reduce by 4h if > capacity)
        int adjusted = 0;
        for (User u : allUsers) {
            if (!u.hasRole("TA")) continue;
            String appId = u.getApplicantId();
            if (appId == null || appId.isEmpty()) continue;
            TAPplicant ta = ds.getApplicantById(appId);
            if (ta == null) continue;
            int hours = workloads.containsKey(appId) ? workloads.get(appId) : 0;
            if (hours > capacity) {
                int newHours = Math.max(0, hours - 4);
                ds.setWorkloadHours(appId, newHours);
                adjusted++;
            }
        }

        if (adjusted > 0) {
            sb.append("{\"success\":true,\"ai\":false,\"message\":\"Workload rebalanced for ")
              .append(adjusted).append(" overloaded TA(s). All TAs now within safe range.\"}");
        } else {
            sb.append("{\"success\":true,\"ai\":false,\"message\":\"No overloaded TAs. Workloads are within safe range.\"}");
        }
    }

    // ============================================================
    // MO 手下 TA、MO↔TA 留言
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
     * MO 获取自己发布的职位下所有待审核的 TA 申请者（Submitted / Under Review）。
     * 按 AI 分数降序排列，每个 applicantId 只出现一次。
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
     * TA 获取自己已接受（Accepted）的岗位列表（用于 My Positions）。
     * 返回岗位详情 + 对应 MO 的用户名/显示名。
     */
    private void handleGetMyPositions(HttpServletRequest req, StringBuilder sb) {
        User me = ds.getUserByUsername(sessionUsername(req));
        if (me == null || me.getApplicantId() == null || me.getApplicantId().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"No applicant profile linked\"}");
            return;
        }
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

    private void handleGetMoTaMessages(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String moUser = sessionUsername(req);
        if (applicantId == null || applicantId.isEmpty() || moUser == null) {
            sb.append("{\"success\":false,\"message\":\"Missing applicantId\"}");
            return;
        }
        // 新逻辑：MO 查看消息不再检查 isTaOfMo（仅 Accepted），改为检查：
        // 该 applicantId 在 MO 发布的任意职位下有申请记录（Submitted/Under Review/Accepted 均可）
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
        User me = ds.getUserByUsername(sessionUsername(req));
        if (me == null || me.getApplicantId() == null || me.getApplicantId().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"No applicant profile linked\"}");
            return;
        }
        String aid = me.getApplicantId();
        LinkedHashMap<String, String> moUserToName = new LinkedHashMap<>();
        // 收集该 TA 所有已 Accepted 的申请对应的 MO
        for (Application a : ds.getApplications()) {
            if (a == null) continue;
            if (!Application.STATUS_ACCEPTED.equals(a.getStatus())) continue;
            if (!aid.equalsIgnoreCase(a.getApplicantId())) continue;
            Position p = ds.getPositionByCode(a.getPositionCode());
            String mu = resolveMoUsernameForPosition(p);
            if (mu == null) continue;
            if (moUserToName.containsKey(mu)) continue;
            User m = ds.getUserByUsername(mu);
            moUserToName.put(mu, m != null && m.getDisplayName() != null ? m.getDisplayName() : mu);
        }
        String myU = me.getUsername();
        sb.append("{\"success\":true,\"threads\":[");
        int i = 0;
        for (Map.Entry<String, String> e : moUserToName.entrySet()) {
            if (i++ > 0) sb.append(",");
            int unread = countUnreadMoTaThread(e.getKey(), aid, myU);
            sb.append("{");
            sb.append("\"moUsername\":\"").append(esc(e.getKey())).append("\",");
            sb.append("\"moDisplayName\":\"").append(esc(e.getValue())).append("\",");
            sb.append("\"unread\":").append(unread);
            sb.append("}");
        }
        sb.append("]}");
    }

    private void handleGetTaMoMessages(HttpServletRequest req, StringBuilder sb) {
        String moUsername = req.getParameter("moUsername");
        User me = ds.getUserByUsername(sessionUsername(req));
        if (me == null || me.getApplicantId() == null || me.getApplicantId().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"No applicant profile\"}");
            return;
        }
        if (moUsername == null || moUsername.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing moUsername\"}");
            return;
        }
        final String moUserKey = moUsername.trim();
        // 新逻辑：不再仅限 Accepted，改为该 MO 发布任意职位下该 TA 有申请记录即可
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
            // 新逻辑：不再要求 isTaOfMo（仅 Accepted），改为 moHasApplicationWith（任意申请状态均可）
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
    // Admin 用户管理
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
        // 防止删除最后一个 ADMIN
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
    // Admin 申请者管理
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
        // 检查是否有用户关联此申请者 ID
        boolean hasLinkedUser = ds.getUsers().stream()
            .anyMatch(u -> u != null && applicantId.equals(u.getApplicantId()));
        if (hasLinkedUser) {
            sb.append("{\"success\":false,\"message\":\"Cannot delete: a user account is linked to this applicant ID\"}");
            return;
        }
        // 删除该申请者的所有申请记录
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

        // 删除申请者记录
        TAPplicant toDelete = ds.getApplicantById(applicantId);
        if (toDelete != null) {
            ds.getApplicants().remove(toDelete);
            ds.saveApplicants();
        }

        sb.append("{\"success\":true,\"message\":\"Applicant and " + toRemove.size() + " application(s) deleted\"}");
    }

    // ============================================================
    // CV 上传处理
    // ============================================================

    private void handleCVUpload(HttpServletRequest req, StringBuilder sb) {
        // 获取 Content-Type 判断是否有文件上传
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            sb.append("{\"success\":false,\"message\":\"No file uploaded or incorrect Content-Type\"}");
            return;
        }

        // 注意：需要 @MultipartConfig 注解支持。
        // 如果 servlet 容器不支持 multipart，请改用独立的 UploadServlet。
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

        // 简单实现：返回成功响应（实际文件上传需要配置 @MultipartConfig）
        // 文件保存逻辑将在独立 UploadServlet 中实现
        sb.append("{\"success\":true,\"message\":\"CV upload endpoint reached. Please configure @MultipartConfig for full file upload support.\"}");
    }

    // ============================================================
    // JSON 序列化
    // ============================================================

    private String positionToJson(Position p) {
        if (p == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"code\":\"").append(esc(p.getCode())).append("\",");
        sb.append("\"name\":\"").append(esc(p.getName())).append("\",");
        sb.append("\"requiredSkills\":\"").append(esc(p.getRequiredSkillsStr())).append("\",");
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
        sb.append("\"isOpen\":").append(p.isOpen());
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
                // P0-9: 不返回明文密码，只返回角色和用户名
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

// 修复后的 logToJsonExtended 方法
private String logToJsonExtended(SystemLog l) {
    if (l == null) return "{}";
    StringBuilder sb = new StringBuilder();
    sb.append("{\"timestamp\":\"").append(esc(l.getTimestamp())).append("\",");
    sb.append("\"operation\":\"").append(esc(l.getOperation())).append("\",");
    sb.append("\"fileName\":\"").append(esc(l.getFileName())).append("\",");
    sb.append("\"status\":\"").append(esc(l.getStatus())).append("\",");
    sb.append("\"opIcon\":\"").append(esc(l.getOpIcon())).append("\"");

    // 添加新字段
    if (l.getLevel() != null) {
        sb.append(",\"level\":\"").append(esc(l.getLevel())).append("\"");
    }
    if (l.getUserId() != null) {
        sb.append(",\"userId\":\"").append(esc(l.getUserId())).append("\"");
    }
    if (l.getIpAddress() != null) {
        sb.append(",\"ipAddress\":\"").append(esc(l.getIpAddress())).append("\"");
    }
    if (l.getSessionId() != null) {
        sb.append(",\"sessionId\":\"").append(esc(l.getSessionId())).append("\"");
    }
    if (l.getDetail() != null) {
        sb.append(",\"detail\":\"").append(esc(l.getDetail())).append("\"");
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
