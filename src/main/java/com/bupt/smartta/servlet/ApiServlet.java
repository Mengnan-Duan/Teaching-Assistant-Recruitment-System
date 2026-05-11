package com.bupt.smartta.servlet;

import com.bupt.smartta.model.*;
import com.bupt.smartta.util.DataStore;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class ApiServlet extends HttpServlet {

    private static final String CSRF_PARAM = "_csrf";
    private static final String CSRF_SESSION_ATTR = "csrfToken";

    private final DataStore ds = DataStore.getInstance();

    // ==================== 权限校验 ====================

    /**
     * 验证 CSRF Token。
     */
    private boolean validateCsrf(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sess = req.getSession(false);
        if (sess == null) return true;
        String sessionToken = (String) sess.getAttribute(CSRF_SESSION_ATTR);
        if (sessionToken == null || sessionToken.isEmpty()) return true;
        String requestToken = req.getParameter(CSRF_PARAM);
        if (requestToken == null || !requestToken.equals(sessionToken)) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token. Please refresh the page and try again.");
            return false;
        }
        return true;
    }

    /**
     * 校验请求是否已认证。未登录返回 false 并发送错误响应。
     */
    private boolean requireAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated. Please login first.");
            return false;
        }
        return true;
    }

    /**
     * 校验当前会话用户是否具有指定角色之一。
     * 如果角色不匹配，发送 403 错误响应。
     */
    private boolean requireRole(HttpServletRequest req, HttpServletResponse resp, String... allowedRoles)
            throws IOException {
        HttpSession session = req.getSession(false);
        String currentRole = (session != null) ? (String) session.getAttribute("currentRole") : null;

        if (currentRole == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Session expired. Please re-login.");
            return false;
        }

        for (String role : allowedRoles) {
            if (role.equals(currentRole)) return true;
        }

        sendError(resp, HttpServletResponse.SC_FORBIDDEN,
                "Insufficient permissions. Required role: " + String.join(" or ", allowedRoles)
                + ", current role: " + currentRole);
        return false;
    }

    // ==================== GET 处理器 ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();

        if (path == null || path.equals("/")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing action parameter");
            return;
        }

        String action = path.substring(1);
        StringBuilder sb = new StringBuilder();

        switch (action) {
            case "positions":
                sb.append("{\"positions\":[");
                List<Position> positions = ds.getPositions();
                for (int i = 0; i < positions.size(); i++) {
                    sb.append(positionToJson(positions.get(i)));
                    if (i < positions.size() - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            case "applications":
                String aid = req.getParameter("applicantId");
                sb.append("{\"applications\":[");
                List<Application> apps;
                if (aid != null && !aid.isEmpty()) {
                    apps = ds.getApplicationsByApplicantId(aid);
                } else {
                    if (!requireRole(req, resp, "MO", "ADMIN")) return;
                    apps = ds.getApplications();
                }
                for (int i = 0; i < apps.size(); i++) {
                    sb.append(applicationToJson(apps.get(i)));
                    if (i < apps.size() - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            case "applicants":
                if (!requireRole(req, resp, "MO", "ADMIN")) return;
                sb.append("{\"applicants\":[");
                List<TAPplicant> applicants = ds.getApplicants();
                for (int i = 0; i < applicants.size(); i++) {
                    sb.append(applicantToJson(applicants.get(i)));
                    if (i < applicants.size() - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            case "logs":
                if (!requireRole(req, resp, "ADMIN")) return;
                sb.append("{\"logs\":[");
                List<SystemLog> logs = ds.getLogs();
                for (int i = 0; i < logs.size() && i < 50; i++) {
                    sb.append(logToJson(logs.get(i)));
                    if (i < Math.min(logs.size(), 50) - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            case "workloads":
                if (!requireRole(req, resp, "ADMIN")) return;
                sb.append("{\"workloads\":{");
                Map<String, Integer> workloads = ds.getWorkloadHours();
                List<User> allUsers = ds.getUsers();
                boolean first = true;
                for (User u : allUsers) {
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

            case "score":
                if (!requireAuth(req, resp)) return;
                String applicantId = req.getParameter("applicantId");
                String posCode = req.getParameter("positionCode");
                TAPplicant ta = ds.getApplicantById(applicantId);
                Position pos = ds.getPositionByCode(posCode);
                if (ta == null || pos == null) {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Applicant or position not found");
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
                break;

            // Perf-01: 统一评分计算端点，供前端直接调用，消除前后端重复逻辑
            case "calculateScore":
                if (!requireAuth(req, resp)) return;
                String aid = req.getParameter("applicantId");
                String pcode = req.getParameter("positionCode");
                TAPplicant tap = ds.getApplicantById(aid);
                Position ppos = ds.getPositionByCode(pcode);
                if (tap == null || ppos == null) {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Applicant or position not found");
                    return;
                }
                int calcScore = (int) tap.computeAIScore(ppos.getRequiredSkills(), ppos.getHoursPerWeek());
                int calcMatched = tap.getMatchedSkillCount(ppos.getRequiredSkills());
                int calcReq = ppos.getRequiredSkills().size();
                int calcSkillPct = calcReq > 0 ? (calcMatched * 100 / calcReq) : 0;
                int calcGpaPct = (int) ((tap.getGpa() / 4.0) * 100);
                int calcAvailPct = Math.min((tap.getHoursAvailable() * 100) / 20, 100);
                sb.append("{\"score\":").append(calcScore)
                  .append(",\"skillScore\":").append(calcSkillPct)
                  .append(",\"gpaScore\":").append(calcGpaPct)
                  .append(",\"availScore\":").append(calcAvailPct)
                  .append(",\"matchedSkills\":").append(calcMatched)
                  .append(",\"requiredSkills\":").append(calcReq)
                  .append("}");
                break;

            default:
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
                return;
        }

        resp.getWriter().write(sb.toString());
    }

    // ==================== POST 处理器 ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // CSRF 防护：所有写操作必须携带有效 token
        if (!validateCsrf(req, resp)) return;

        String path = req.getPathInfo();

        if (path == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing action");
            return;
        }
        String action = path.substring(1);
        StringBuilder sb = new StringBuilder();

        switch (action) {
            case "apply":
                handleApply(req, sb, resp);
                break;
            case "applicant":
                handleCreateApplicant(req, sb, resp);
                break;
            case "position":
                handleCreatePosition(req, sb, resp);
                break;
            case "updateStatus":
                handleUpdateStatus(req, sb, resp);
                break;
            case "workload":
                handleUpdateWorkload(req, sb, resp);
                break;
            case "rebalance":
                handleRebalanceWorkload(sb, resp);
                break;
            case "quota":
                handleUpdateQuota(req, sb, resp);
                break;
            default:
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown POST action: " + action);
                return;
        }

        resp.getWriter().write(sb.toString());
    }

    // ==================== 申请处理（TA / ADMIN）====================

    private void handleApply(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp)
            throws IOException {
        if (!requireRole(req, resp, "TA", "ADMIN")) return;

        String applicantId = req.getParameter("applicantId");
        String positionCode = req.getParameter("positionCode");
        String applicantName = req.getParameter("applicantName");

        if (applicantId == null || applicantId.trim().isEmpty()
         || positionCode == null || positionCode.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields: applicantId and positionCode\"}");
            return;
        }

        TAPplicant ta = ds.getApplicantById(applicantId.trim());
        Position pos = ds.getPositionByCode(positionCode.trim());
        if (ta == null) {
            sb.append("{\"success\":false,\"message\":\"Applicant profile not found. Please complete your profile first.\"}");
            return;
        }
        if (pos == null) {
            sb.append("{\"success\":false,\"message\":\"Position not found: ").append(esc(positionCode.trim())).append("\"}");
            return;
        }

        // P2 修复：检查截止日期
        try {
            LocalDate deadline = LocalDate.parse(pos.getDeadline());
            if (LocalDate.now().isAfter(deadline)) {
                sb.append("{\"success\":false,\"message\":\"Application deadline has passed (deadline: ").append(pos.getDeadline()).append(")\"}");
                return;
            }
        } catch (Exception ignored) {}

        // P2 修复：检查职位是否仍开放
        if (!pos.isOpen()) {
            sb.append("{\"success\":false,\"message\":\"This position is no longer accepting applications (filled or closed)\"}");
            return;
        }

        // 检查是否已申请（精确匹配申请人+职位）
        if (ds.getApplication(applicantId.trim(), positionCode.trim()) != null) {
            sb.append("{\"success\":false,\"message\":\"You have already applied to this position\"}");
            return;
        }

        int score = (int) ta.computeAIScore(pos.getRequiredSkills(), pos.getHoursPerWeek());
        String name = (applicantName != null && !applicantName.trim().isEmpty())
                      ? applicantName.trim() : ta.getName();
        Application app = new Application(applicantId.trim(), name,
            positionCode.trim(), pos.getName(), score);

        try {
            ds.addApplication(app);
        } catch (RuntimeException e) {
            sb.append("{\"success\":false,\"message\":\"Failed to submit application. Please try again.\"}");
            return;
        }

        sb.append("{\"success\":true,\"message\":\"Application submitted successfully. Your application is now under review.\",")
          .append("\"application\":").append(applicationToJson(app)).append("}");
    }

    // ==================== 创建/更新申请者资料 ====================

    private void handleCreateApplicant(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp)
            throws IOException {
        if (!requireRole(req, resp, "TA", "ADMIN")) return;

        String name = req.getParameter("name");
        String email = req.getParameter("email");
        String year = req.getParameter("yearOfStudy");
        String gpaStr = req.getParameter("gpa");
        String skillsStr = req.getParameter("skills");
        String hoursStr = req.getParameter("hoursAvailable");

        if (name == null || name.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Name is required\"}");
            return;
        }

        String id = ds.allocateNextApplicantId();
        double gpa = 0.0;
        if (gpaStr != null && !gpaStr.trim().isEmpty()) {
            try { gpa = Double.parseDouble(gpaStr.trim()); } catch (Exception e) {}
        }
        int hours = 12;
        if (hoursStr != null && !hoursStr.trim().isEmpty()) {
            try { hours = Integer.parseInt(hoursStr.trim()); } catch (Exception e) {}
        }

        List<String> skills = new ArrayList<>();
        if (skillsStr != null && !skillsStr.trim().isEmpty()) {
            for (String s : skillsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) skills.add(trimmed);
            }
        }

        TAPplicant ta = new TAPplicant(id, name.trim(),
            email != null ? email.trim() : "",
            year != null ? year.trim() : "Year 2", gpa, skills, hours);

        try {
            ds.saveApplicant(ta);
        } catch (RuntimeException e) {
            sb.append("{\"success\":false,\"message\":\"Failed to save profile. Please try again.\"}");
            return;
        }

        sb.append("{\"success\":true,\"message\":\"Profile saved successfully\",")
          .append("\"applicant\":").append(applicantToJson(ta)).append("}");
    }

    // ==================== 发布职位（MO / ADMIN）====================

    private void handleCreatePosition(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp)
            throws IOException {
        if (!requireRole(req, resp, "MO", "ADMIN")) return;

        String code = req.getParameter("code");
        String name = req.getParameter("name");
        String skillsStr = req.getParameter("requiredSkills");
        String hoursStr = req.getParameter("hoursPerWeek");
        String slotsStr = req.getParameter("totalSlots");
        String deadline = req.getParameter("deadline");
        String postedBy = req.getParameter("postedBy");
        String desc = req.getParameter("description");

        if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Code and name are required\"}");
            return;
        }

        // P1 修复：检查职位代码是否已存在（不区分大小写）
        if (ds.positionCodeExists(code.trim())) {
            sb.append("{\"success\":false,\"message\":\"A position with code '")
              .append(esc(code.trim())).append("' already exists. Please use a different code.\"}");
            return;
        }

        // P2 修复：验证截止日期格式和合理性
        String effectiveDeadline = (deadline != null && !deadline.trim().isEmpty())
                                  ? deadline.trim() : "2026-04-30";
        try {
            LocalDate dl = LocalDate.parse(effectiveDeadline);
            if (dl.isBefore(LocalDate.now())) {
                sb.append("{\"success\":false,\"message\":\"Deadline cannot be in the past\"}");
                return;
            }
        } catch (Exception e) {
            sb.append("{\"success\":false,\"message\":\"Invalid deadline format. Use yyyy-MM-dd.\"}");
            return;
        }

        List<String> skills = new ArrayList<>();
        if (skillsStr != null && !skillsStr.trim().isEmpty()) {
            for (String s : skillsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) skills.add(trimmed);
            }
        }

        int hours = 8;
        if (hoursStr != null && !hoursStr.trim().isEmpty()) {
            try { hours = Integer.parseInt(hoursStr.trim()); } catch (Exception e) {}
        }
        int slots = 2;
        if (slotsStr != null && !slotsStr.trim().isEmpty()) {
            try { slots = Integer.parseInt(slotsStr.trim()); } catch (Exception e) {}
        }
        if (slots < 1) slots = 1;
        if (slots > 20) slots = 20;

        HttpSession session = req.getSession(false);
        String poster = (postedBy != null && !postedBy.trim().isEmpty())
                        ? postedBy.trim()
                        : (session != null ? (String) session.getAttribute("displayName") : "Admin");

        Position pos = new Position(code.trim().toUpperCase(), name.trim(), skills,
            hours, slots, effectiveDeadline, poster);
        pos.setDescription(desc);

        try {
            ds.addPosition(pos);
        } catch (RuntimeException e) {
            sb.append("{\"success\":false,\"message\":\"Failed to publish position. Please try again.\"}");
            return;
        }

        sb.append("{\"success\":true,\"message\":\"Position published successfully\",")
          .append("\"position\":").append(positionToJson(pos)).append("}");
    }

    // ==================== 更新申请状态（MO / ADMIN）====================

    private void handleUpdateStatus(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp)
            throws IOException {
        if (!requireRole(req, resp, "MO", "ADMIN")) return;

        String appId = req.getParameter("applicationId");
        String status = req.getParameter("status");

        if (appId == null || appId.trim().isEmpty() || status == null || status.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing fields: applicationId and status are required\"}");
            return;
        }

        String trimmedId = appId.trim();
        Application target = null;
        for (Application a : ds.getApplications()) {
            if (a.getId().equals(trimmedId)) { target = a; break; }
        }

        if (target == null) {
            sb.append("{\"success\":false,\"message\":\"Application not found: ").append(esc(trimmedId)).append("\"}");
            return;
        }

        // 验证状态值的合法性
        if (!isValidApplicationStatus(status.trim())) {
            sb.append("{\"success\":false,\"message\":\"Invalid status value. Must be one of: Submitted, Under Review, Accepted, Rejected\"}");
            return;
        }

        String oldStatus = target.getStatus();
        String newStatus = status.trim();

        // 防止重复操作
        if (oldStatus.equals(newStatus)) {
            sb.append("{\"success\":true,\"message\":\"Application is already in status: ").append(esc(oldStatus)).append("\"}");
            return;
        }

        // 如果接受，检查是否还有空位
        if (Application.STATUS_ACCEPTED.equals(newStatus)) {
            Position pos = ds.getPositionByCode(target.getPositionCode());
            if (pos != null) {
                if (pos.getRemainingSlots() <= 0) {
                    sb.append("{\"success\":false,\"message\":\"No remaining slots for this position. Adjust the quota first.\"}");
                    return;
                }
            }
        }

        target.setStatus(newStatus);

        try {
            ds.updateApplication(target);

            // 如果是接受，更新职位的已填充名额
            if (Application.STATUS_ACCEPTED.equals(newStatus)) {
                Position pos = ds.getPositionByCode(target.getPositionCode());
                if (pos != null) {
                    pos.setFilledSlots(pos.getFilledSlots() + 1);
                    if (!ds.savePositions()) {
                        sb.append("{\"success\":false,\"message\":\"Failed to update position slot count\"}");
                        return;
                    }
                }
            }
        } catch (RuntimeException e) {
            sb.append("{\"success\":false,\"message\":\"Failed to update status. Please try again.\"}");
            return;
        }

        String statusMsg = Application.STATUS_ACCEPTED.equals(newStatus) ? "accepted"
                         : Application.STATUS_REJECTED.equals(newStatus) ? "rejected"
                         : "updated to " + newStatus;

        sb.append("{\"success\":true,\"message\":\"Application ")
          .append(statusMsg).append(" successfully (was: ").append(oldStatus).append(")\"}");
    }

    private boolean isValidApplicationStatus(String status) {
        return Application.STATUS_SUBMITTED.equals(status)
            || Application.STATUS_REVIEW.equals(status)
            || Application.STATUS_ACCEPTED.equals(status)
            || Application.STATUS_REJECTED.equals(status);
    }

    // ==================== 更新工作量（ADMIN）====================

    private void handleUpdateWorkload(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp)
            throws IOException {
        if (!requireRole(req, resp, "ADMIN")) return;

        String applicantId = req.getParameter("applicantId");
        String hoursStr = req.getParameter("hours");

        if (applicantId == null || applicantId.trim().isEmpty()
         || hoursStr == null || hoursStr.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing fields: applicantId and hours are required\"}");
            return;
        }

        int hours;
        try {
            hours = Integer.parseInt(hoursStr.trim());
        } catch (NumberFormatException e) {
            sb.append("{\"success\":false,\"message\":\"Invalid hours value. Please enter a number.\"}");
            return;
        }

        if (hours < 0 || hours > 40) {
            sb.append("{\"success\":false,\"message\":\"Hours must be between 0 and 40\"}");
            return;
        }

        try {
            ds.setWorkloadHours(applicantId.trim(), hours);
        } catch (RuntimeException e) {
            sb.append("{\"success\":false,\"message\":\"Failed to update workload. Please try again.\"}");
            return;
        }

        sb.append("{\"success\":true,\"message\":\"Workload updated to ").append(hours).append("h/week\"}");
    }

    // ==================== 调整配额（MO / ADMIN）====================

    private void handleUpdateQuota(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp)
            throws IOException {
        if (!requireRole(req, resp, "MO", "ADMIN")) return;

        String code = req.getParameter("positionCode");
        String totalSlotsStr = req.getParameter("totalSlots");

        if (code == null || code.trim().isEmpty()
         || totalSlotsStr == null || totalSlotsStr.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Missing fields: positionCode and totalSlots are required\"}");
            return;
        }

        Position pos = ds.getPositionByCode(code.trim());
        if (pos == null) {
            sb.append("{\"success\":false,\"message\":\"Position not found: ").append(esc(code.trim())).append("\"}");
            return;
        }

        int totalSlots;
        try {
            totalSlots = Integer.parseInt(totalSlotsStr.trim());
        } catch (NumberFormatException e) {
            sb.append("{\"success\":false,\"message\":\"Invalid totalSlots value. Please enter a number.\"}");
            return;
        }

        if (totalSlots < pos.getFilledSlots()) {
            totalSlots = pos.getFilledSlots();
        }
        if (totalSlots > 20) totalSlots = 20;
        if (totalSlots < 1) totalSlots = 1;

        pos.setTotalSlots(totalSlots);
        if (!ds.savePositions()) {
            sb.append("{\"success\":false,\"message\":\"Failed to update quota. Please try again.\"}");
            return;
        }
        ds.addLog(SystemLog.OP_WRITE, POSITIONS + ".json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Quota updated: ")
          .append(pos.getCode()).append(" now has ")
          .append(totalSlots).append(" slots (")
          .append(pos.getFilledSlots()).append(" filled)\"}");
    }

    // ==================== 工作量再平衡（ADMIN）====================

    private void handleRebalanceWorkload(HttpServletRequest req, StringBuilder sb, HttpServletResponse resp) throws IOException {
        if (!requireRole(req, resp, "ADMIN")) return;

        Map<String, Integer> workloads = ds.getWorkloadHours();
        List<User> allUsers = ds.getUsers();
        String mostOverloadedId = null;
        int maxHours = -1;

        for (User u : allUsers) {
            if (!u.hasRole("TA")) continue;
            String appId = u.getApplicantId();
            if (appId == null || appId.isEmpty()) continue;
            TAPplicant ta = ds.getApplicantById(appId);
            if (ta == null) continue;
            int hours = workloads.getOrDefault(appId, 0);
            if (hours > maxHours) {
                maxHours = hours;
                mostOverloadedId = appId;
            }
        }

        if (mostOverloadedId != null && maxHours > 20) {
            int newHours = Math.max(0, maxHours - 4);
            try {
                ds.setWorkloadHours(mostOverloadedId, newHours);
            } catch (RuntimeException e) {
                sb.append("{\"success\":false,\"message\":\"Failed to rebalance workload. Please try again.\"}");
                return;
            }
            TAPplicant ta = ds.getApplicantById(mostOverloadedId);
            String name = ta != null ? ta.getName() : mostOverloadedId;
            sb.append("{\"success\":true,\"message\":\"Workload reduced for ")
              .append(esc(name)).append(" from ").append(maxHours)
              .append("h to ").append(newHours).append("h/week\"}");
        } else {
            sb.append("{\"success\":true,\"message\":\"No overloaded TAs detected. All workloads are within the safe range (≤20h/week).\"}");
        }
    }

    // ==================== 错误与 JSON 工具 ====================

    private void sendError(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code);
        resp.getWriter().write("{\"success\":false,\"error\":\"" + esc(message) + "\"}");
    }

    // ---- JSON builders ----

    private String positionToJson(Position p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"code\":\"").append(esc(p.getCode())).append("\",");
        sb.append("\"name\":\"").append(esc(p.getName())).append("\",");
        sb.append("\"requiredSkills\":\"").append(esc(p.getRequiredSkillsStr())).append("\",");
        sb.append("\"skillsList\":[");
        List<String> sk = p.getRequiredSkills();
        for (int i = 0; i < sk.size(); i++) {
            sb.append("\"").append(esc(sk.get(i))).append("\"");
            if (i < sk.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"hoursPerWeek\":").append(p.getHoursPerWeek()).append(",");
        sb.append("\"totalSlots\":").append(p.getTotalSlots()).append(",");
        sb.append("\"filledSlots\":").append(p.getFilledSlots()).append(",");
        sb.append("\"remainingSlots\":").append(p.getRemainingSlots()).append(",");
        sb.append("\"deadline\":\"").append(esc(p.getDeadline())).append("\",");
        sb.append("\"status\":\"").append(esc(p.getStatus())).append("\",");
        sb.append("\"postedBy\":\"").append(esc(p.getPostedBy())).append("\",");
        sb.append("\"isOpen\":").append(p.isOpen());
        sb.append("}");
        return sb.toString();
    }

    private String applicantToJson(TAPplicant a) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(esc(a.getId())).append("\",");
        sb.append("\"name\":\"").append(esc(a.getName())).append("\",");
        sb.append("\"email\":\"").append(esc(a.getEmail())).append("\",");
        sb.append("\"yearOfStudy\":\"").append(esc(a.getYearOfStudy())).append("\",");
        sb.append("\"gpa\":").append(a.getGpa()).append(",");
        sb.append("\"skills\":[");
        List<String> sk = a.getSkills();
        for (int i = 0; i < sk.size(); i++) {
            sb.append("\"").append(esc(sk.get(i))).append("\"");
            if (i < sk.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"hoursAvailable\":").append(a.getHoursAvailable());
        sb.append("}");
        return sb.toString();
    }

    private String applicationToJson(Application a) {
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
        sb.append("\"aiExplanation\":\"").append(esc(a.getAiExplanation())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String logToJson(SystemLog l) {
        return "{\"timestamp\":\"" + esc(l.getTimestamp()) + "\","
             + "\"operation\":\"" + esc(l.getOperation()) + "\","
             + "\"fileName\":\"" + esc(l.getFileName()) + "\","
             + "\"status\":\"" + esc(l.getStatus()) + "\","
             + "\"detail\":\"" + esc(l.getDetail() != null ? l.getDetail() : "") + "\","
             + "\"opIcon\":\"" + esc(l.getOpIcon()) + "\"}";
    }

    /**
     * 全面 HTML 实体转义，防止 XSS。
     */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
