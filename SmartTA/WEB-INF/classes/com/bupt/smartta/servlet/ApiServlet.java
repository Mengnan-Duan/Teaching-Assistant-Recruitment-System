package com.bupt.smartta.servlet;

import com.bupt.smartta.model.*;
import com.bupt.smartta.util.DataStore;
import com.bupt.smartta.util.LLMService;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

@MultipartConfig
public class ApiServlet extends HttpServlet {

    private final DataStore ds = DataStore.getInstance();
    private final LLMService llmService = LLMService.getInstance();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();
        // 支持 /api/positions 与 /api?action=positions（pathInfo 可能为空）
        String action = null;
        if (path != null && path.length() > 1) {
            action = path.substring(1);
        }
        if (action == null || action.isEmpty()) {
            action = req.getParameter("action");
        }
        if (action == null || action.isEmpty()) {
            sendError(resp, "Missing action parameter");
            return;
        }
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

            case "logs":
                sb.append("{\"logs\":[");
                List<SystemLog> logs = ds.getLogs();
                for (int i = 0; i < logs.size() && i < 50; i++) {
                    sb.append(logToJson(logs.get(i)));
                    if (i < Math.min(logs.size(), 50) - 1) sb.append(",");
                }
                sb.append("]}");
                break;

            case "workloads":
                sb.append("{\"workloadEntries\":[");
                Map<String, Integer> workloads = ds.getWorkloadHours();
                Set<String> seenApplicant = new HashSet<>();
                List<String> workloadApplicantOrder = new ArrayList<>();
                List<User> allUsers = ds.getUsers();
                boolean firstEntry = true;
                for (User u : allUsers) {
                    if (!u.hasRole("TA")) continue;
                    String appId = u.getApplicantId();
                    if (appId == null || appId.isEmpty()) continue;
                    String appKey = appId.toUpperCase();
                    if (seenApplicant.contains(appKey)) continue;
                    TAPplicant ta = ds.getApplicantById(appId);
                    if (ta == null) continue;
                    seenApplicant.add(appKey);
                    workloadApplicantOrder.add(appId);
                    int hours = 0;
                    if (workloads.containsKey(appId)) hours = workloads.get(appId);
                    else {
                        for (Map.Entry<String, Integer> e : workloads.entrySet()) {
                            if (e.getKey() != null && e.getKey().equalsIgnoreCase(appId)) {
                                hours = e.getValue();
                                break;
                            }
                        }
                    }
                    if (!firstEntry) sb.append(",");
                    firstEntry = false;
                    sb.append("{");
                    sb.append("\"applicantId\":\"").append(esc(appId)).append("\",");
                    sb.append("\"username\":\"").append(esc(u.getUsername())).append("\",");
                    sb.append("\"taName\":\"").append(esc(ta.getName())).append("\",");
                    sb.append("\"hours\":").append(hours);
                    sb.append("}");
                }
                sb.append("],\"workloads\":{");
                // 兼容旧前端：key 为 applicantId，避免多名 TA 重名时 JSON 字段互相覆盖
                boolean firstMap = true;
                for (String appId : workloadApplicantOrder) {
                    int hours = 0;
                    if (workloads.containsKey(appId)) hours = workloads.get(appId);
                    else {
                        for (Map.Entry<String, Integer> e : workloads.entrySet()) {
                            if (e.getKey() != null && e.getKey().equalsIgnoreCase(appId)) {
                                hours = e.getValue();
                                break;
                            }
                        }
                    }
                    if (!firstMap) sb.append(",");
                    firstMap = false;
                    sb.append("\"").append(esc(appId)).append("\":").append(hours);
                }
                sb.append("}}");
                break;

            case "config":
                sb.append(systemConfigToJson(ds.getSystemConfig()));
                break;

            case "users":
                if (!requireAdmin(req)) {
                    sendForbidden(resp);
                    return;
                }
                sb.append("{\"users\":[");
                List<User> allUsersList = ds.getUsers();
                boolean firstUser = true;
                for (int i = 0; i < allUsersList.size(); i++) {
                    User u = allUsersList.get(i);
                    if (u.hasRole("ADMIN")) continue;
                    if (!u.hasRole("MO") && !u.hasRole("TA")) continue;
                    String linkedApplicantId = u.getApplicantId();
                    TAPplicant prof = (linkedApplicantId != null && !linkedApplicantId.isEmpty())
                        ? ds.getApplicantById(linkedApplicantId) : null;
                    if (!firstUser) sb.append(",");
                    firstUser = false;
                    sb.append(userToJsonPublic(u, prof));
                }
                sb.append("]}");
                break;

            case "score":
                {
                    String aid = req.getParameter("applicantId");
                    String pCode = req.getParameter("positionCode");
                    TAPplicant ta2 = ds.getApplicantById(aid);
                    Position pos2 = ds.getPositionByCode(pCode);
                    if (ta2 == null || pos2 == null) {
                        sendError(resp, "Applicant or position not found");
                        return;
                    }
                    int sc = (int) ta2.computeAIScore(pos2.getRequiredSkills(), pos2.getHoursPerWeek());
                    int mch = ta2.getMatchedSkillCount(pos2.getRequiredSkills());
                    int rc = pos2.getRequiredSkills().size();
                    int sp = rc > 0 ? (mch * 100 / rc) : 0;
                    int gp = (int) ((ta2.getGpa() / 4.0) * 100);
                    int avp = Math.min((ta2.getHoursAvailable() * 100) / 20, 100);
                    sb.append("{\"score\":").append(sc)
                      .append(",\"skillScore\":").append(sp)
                      .append(",\"gpaScore\":").append(gp)
                      .append(",\"availScore\":").append(avp)
                      .append(",\"matchedSkills\":").append(mch)
                      .append(",\"requiredSkills\":").append(rc)
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
                        sendError(resp, "Applicant or position not found");
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

            default:
                sendError(resp, "Unknown action: " + action);
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

        if (path == null) { sendError(resp, "Missing action"); return; }
        String action = path.substring(1);
        StringBuilder sb = new StringBuilder();

        switch (action) {
            case "apply":
                handleApply(req, sb);
                break;
            case "applicant":
                handleCreateApplicant(req, sb);
                break;
            case "position":
                handleCreatePosition(req, sb);
                break;
            case "updateStatus":
                handleUpdateStatus(req, sb);
                break;
            case "workload":
                handleUpdateWorkload(req, sb);
                break;
            case "rebalance":
                handleRebalanceWorkload(sb);
                break;
            case "quota":
                handleUpdateQuota(req, sb);
                break;
            case "user":
                if (!requireAdmin(req)) {
                    sendForbidden(resp);
                    return;
                }
                handleUserCrud(req, sb);
                break;
            case "adminApplicant":
                if (!requireAdmin(req)) {
                    sendForbidden(resp);
                    return;
                }
                handleAdminApplicant(req, sb);
                break;
            default:
                sendError(resp, "Unknown POST action: " + action);
                return;
        }

        resp.getWriter().write(sb.toString());
    }

    private boolean requireAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return false;
        return "ADMIN".equals(s.getAttribute("currentRole"));
    }

    private void sendForbidden(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.getWriter().write("{\"error\":\"Admin only\"}");
    }

    private void handleUserCrud(HttpServletRequest req, StringBuilder sb) {
        String op = req.getParameter("op");
        if (op != null) op = op.trim();
        if (op == null || op.isEmpty()) op = "";
        switch (op) {
            case "create":
                handleUserCreate(req, sb);
                break;
            case "update":
                handleUserUpdate(req, sb);
                break;
            case "delete":
                handleUserDelete(req, sb);
                break;
            default:
                sb.append("{\"success\":false,\"message\":\"Unknown op\"}");
        }
    }

    private void handleUserCreate(HttpServletRequest req, StringBuilder sb) {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String displayName = req.getParameter("displayName");
        String email = req.getParameter("email");
        String rolesStr = req.getParameter("roles");
        String applicantId = req.getParameter("applicantId");

        if (username == null || username.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Username required\"}");
            return;
        }
        if (password == null || password.length() < 6) {
            sb.append("{\"success\":false,\"message\":\"Password at least 6 characters\"}");
            return;
        }
        if (ds.getUserByUsername(username.trim()) != null) {
            sb.append("{\"success\":false,\"message\":\"Username already exists\"}");
            return;
        }

        User u = new User(username.trim(), User.hashPassword(password),
            displayName != null && !displayName.isEmpty() ? displayName : username.trim(),
            email != null ? email : "");
        if (rolesStr != null) {
            for (String r : rolesStr.split(",")) {
                String t = r.trim().toUpperCase();
                if (!t.isEmpty() && (t.equals("TA") || t.equals("MO") || t.equals("ADMIN"))) u.addRole(t);
            }
        }
        if (u.getRoles().isEmpty()) u.addRole("TA");
        if (applicantId != null && !applicantId.trim().isEmpty()) {
            u.setApplicantId(applicantId.trim());
        }
        ds.saveUser(u);
        ds.addLog(SystemLog.OP_WRITE, "users.json", SystemLog.STATUS_OK);
        sb.append("{\"success\":true,\"message\":\"User created\"}");
    }

    private void handleUserUpdate(HttpServletRequest req, StringBuilder sb) {
        String username = req.getParameter("username");
        if (username == null || username.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Username required\"}");
            return;
        }
        User u = ds.getUserByUsername(username.trim());
        if (u == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }
        String displayName = req.getParameter("displayName");
        String email = req.getParameter("email");
        String rolesStr = req.getParameter("roles");
        String applicantId = req.getParameter("applicantId");
        String newPassword = req.getParameter("newPassword");

        if (displayName != null) u.setDisplayName(displayName.trim());
        if (email != null) u.setEmail(email.trim());
        if (rolesStr != null) {
            u.getRoles().clear();
            for (String r : rolesStr.split(",")) {
                String t = r.trim().toUpperCase();
                if (!t.isEmpty() && (t.equals("TA") || t.equals("MO") || t.equals("ADMIN"))) u.addRole(t);
            }
            if (u.getRoles().isEmpty()) u.addRole("TA");
        }
        if (applicantId != null) {
            u.setApplicantId(applicantId.trim());
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            if (newPassword.length() < 6) {
                sb.append("{\"success\":false,\"message\":\"Password at least 6 characters\"}");
                return;
            }
            u.setPasswordHash(User.hashPassword(newPassword));
        }
        ds.saveUser(u);
        ds.addLog(SystemLog.OP_WRITE, "users.json", SystemLog.STATUS_OK);
        sb.append("{\"success\":true,\"message\":\"User updated\"}");
    }

    private void handleUserDelete(HttpServletRequest req, StringBuilder sb) {
        HttpSession s = req.getSession(false);
        String self = s != null ? (String) s.getAttribute("username") : null;
        String username = req.getParameter("username");
        if (username == null || username.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Username required\"}");
            return;
        }
        if (self != null && self.equalsIgnoreCase(username.trim())) {
            sb.append("{\"success\":false,\"message\":\"Cannot delete your own account\"}");
            return;
        }
        User target = ds.getUserByUsername(username.trim());
        if (target == null) {
            sb.append("{\"success\":false,\"message\":\"User not found\"}");
            return;
        }
        if (target.hasRole("ADMIN") && ds.countAdminUsers() <= 1) {
            sb.append("{\"success\":false,\"message\":\"Cannot delete the last administrator\"}");
            return;
        }
        if (ds.deleteUser(username.trim())) {
            sb.append("{\"success\":true,\"message\":\"User deleted\"}");
        } else {
            sb.append("{\"success\":false,\"message\":\"Delete failed\"}");
        }
    }

    private void handleAdminApplicant(HttpServletRequest req, StringBuilder sb) {
        String op = req.getParameter("op");
        if (op != null) op = op.trim();
        if (op == null) op = "";
        if ("delete".equals(op)) {
            String id = req.getParameter("applicantId");
            if (id == null || id.trim().isEmpty()) {
                sb.append("{\"success\":false,\"message\":\"applicantId required\"}");
                return;
            }
            if (ds.deleteApplicantCascade(id.trim())) {
                sb.append("{\"success\":true,\"message\":\"Applicant removed\"}");
            } else {
                sb.append("{\"success\":false,\"message\":\"Applicant in use by a user account or not found\"}");
            }
            return;
        }
        if ("update".equals(op)) {
            String id = req.getParameter("applicantId");
            TAPplicant a = id != null ? ds.getApplicantById(id.trim()) : null;
            if (a == null) {
                sb.append("{\"success\":false,\"message\":\"Applicant not found\"}");
                return;
            }
            String name = req.getParameter("name");
            String email = req.getParameter("email");
            String year = req.getParameter("yearOfStudy");
            String gpaStr = req.getParameter("gpa");
            String hoursStr = req.getParameter("hoursAvailable");
            String skillsStr = req.getParameter("skills");
            if (name != null) a.setName(name.trim());
            if (email != null) a.setEmail(email.trim());
            if (year != null) a.setYearOfStudy(year.trim());
            if (gpaStr != null) {
                try { a.setGpa(Double.parseDouble(gpaStr)); } catch (Exception ignored) { }
            }
            if (hoursStr != null) {
                try { a.setHoursAvailable(Integer.parseInt(hoursStr)); } catch (Exception ignored) { }
            }
            if (skillsStr != null) {
                List<String> skills = new ArrayList<>();
                for (String x : skillsStr.split(",")) {
                    String t = x.trim();
                    if (!t.isEmpty()) skills.add(t);
                }
                a.setSkills(skills);
            }
            ds.saveApplicant(a);
            sb.append("{\"success\":true,\"message\":\"Applicant profile saved\"}");
            return;
        }
        sb.append("{\"success\":false,\"message\":\"Unknown op\"}");
    }

    private void handleApply(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String positionCode = req.getParameter("positionCode");
        String applicantName = req.getParameter("applicantName");

        if (applicantId == null || positionCode == null) {
            sb.append("{\"success\":false,\"message\":\"Missing required fields\"}");
            return;
        }

        TAPplicant ta = ds.getApplicantById(applicantId);
        Position pos = ds.getPositionByCode(positionCode);
        if (ta == null || pos == null) {
            sb.append("{\"success\":false,\"message\":\"Applicant or position not found\"}");
            return;
        }

        if (ds.getApplication(applicantId, positionCode) != null) {
            sb.append("{\"success\":false,\"message\":\"Already applied to this position\"}");
            return;
        }

        int score = (int) ta.computeAIScore(pos.getRequiredSkills(), pos.getHoursPerWeek());
        int matched = ta.getMatchedSkillCount(pos.getRequiredSkills());
        int reqCount = pos.getRequiredSkills().size();
        int skillPct = reqCount > 0 ? (matched * 100 / reqCount) : 0;
        int gpaPct = (int) ((ta.getGpa() / 4.0) * 100);
        int availPct = Math.min((ta.getHoursAvailable() * 100) / 20, 100);
        Application app = new Application(applicantId, applicantName != null ? applicantName : ta.getName(),
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
        ds.addLog(SystemLog.OP_WRITE, "applications.json", SystemLog.STATUS_OK);

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

        String id = "A" + String.format("%03d", ds.getApplicants().size() + 1);
        double gpa = 0.0;
        try { gpa = Double.parseDouble(gpaStr); } catch (Exception e) {}
        int hours = 12;
        try { hours = Integer.parseInt(hoursStr); } catch (Exception e) {}

        List<String> skills = new ArrayList<>();
        if (skillsStr != null) {
            for (String s : skillsStr.split(",")) {
                skills.add(s.trim());
            }
        }

        TAPplicant ta = new TAPplicant(id, name, email != null ? email : "",
            year != null ? year : "Year 2", gpa, skills, hours);
        ds.saveApplicant(ta);

        sb.append("{\"success\":true,\"message\":\"Profile saved\",")
          .append("\"applicant\":").append(applicantToJson(ta)).append("}");
    }

    private void handleCreatePosition(HttpServletRequest req, StringBuilder sb) {
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

        List<String> skills = new ArrayList<>();
        if (skillsStr != null) {
            for (String s : skillsStr.split(",")) skills.add(s.trim());
        }

        int hours = 8;
        try { hours = Integer.parseInt(hoursStr); } catch (Exception e) {}
        int slots = 2;
        try { slots = Integer.parseInt(slotsStr); } catch (Exception e) {}

        Position pos = new Position(code, name, skills, hours, slots,
            deadline != null ? deadline : "2026-04-30",
            postedBy != null ? postedBy : "Admin");
        pos.setDescription(desc);

        ds.addPosition(pos);

        sb.append("{\"success\":true,\"message\":\"Position posted successfully\",")
          .append("\"position\":").append(positionToJson(pos)).append("}");
    }

    private void handleUpdateStatus(HttpServletRequest req, StringBuilder sb) {
        String appId = req.getParameter("applicationId");
        String status = req.getParameter("status");

        if (appId == null || status == null) {
            sb.append("{\"success\":false,\"message\":\"Missing fields\"}");
            return;
        }

        List<Application> all = ds.getApplications();
        Application target = null;
        for (Application a : all) {
            if (a.getId().equals(appId)) { target = a; break; }
        }

        if (target == null) {
            sb.append("{\"success\":false,\"message\":\"Application not found\"}");
            return;
        }

        String oldStatus = target.getStatus();
        target.setStatus(status);
        ds.updateApplication(target);

        if (Application.STATUS_ACCEPTED.equals(status)) {
            Position pos = ds.getPositionByCode(target.getPositionCode());
            if (pos != null) {
                pos.setFilledSlots(pos.getFilledSlots() + 1);
                ds.savePositions();
            }
        }

        ds.addLog(SystemLog.OP_WRITE, "applications.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Status updated from ").append(oldStatus)
          .append(" to ").append(status).append("\"}");
    }

    private void handleUpdateWorkload(HttpServletRequest req, StringBuilder sb) {
        String applicantId = req.getParameter("applicantId");
        String hoursStr = req.getParameter("hours");

        if (applicantId == null || hoursStr == null) {
            sb.append("{\"success\":false,\"message\":\"Missing fields\"}");
            return;
        }

        int hours = 0;
        try { hours = Integer.parseInt(hoursStr); } catch (Exception e) {
            sb.append("{\"success\":false,\"message\":\"Invalid hours value\"}");
            return;
        }

        ds.setWorkloadHours(applicantId, hours);
        sb.append("{\"success\":true,\"message\":\"Workload updated to ").append(hours).append("h\"}");
    }

    private void handleUpdateQuota(HttpServletRequest req, StringBuilder sb) {
        String code = req.getParameter("positionCode");
        String totalSlotsStr = req.getParameter("totalSlots");

        if (code == null || totalSlotsStr == null) {
            sb.append("{\"success\":false,\"message\":\"Missing fields\"}");
            return;
        }

        Position pos = ds.getPositionByCode(code);
        if (pos == null) {
            sb.append("{\"success\":false,\"message\":\"Position not found\"}");
            return;
        }

        int totalSlots = 2;
        try { totalSlots = Integer.parseInt(totalSlotsStr); } catch (Exception e) {}

        if (totalSlots < pos.getFilledSlots()) {
            totalSlots = pos.getFilledSlots();
        }
        if (totalSlots > 10) totalSlots = 10;

        pos.setTotalSlots(totalSlots);
        ds.savePositions();
        ds.addLog(SystemLog.OP_WRITE, "positions.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Quota updated to ").append(totalSlots).append(" slots for ").append(code).append("\"}");
    }

    private void handleRebalanceWorkload(StringBuilder sb) {
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
            int hours = workloads.containsKey(appId) ? workloads.get(appId) : 0;
            if (hours > maxHours) {
                maxHours = hours;
                mostOverloadedId = appId;
            }
        }
        if (mostOverloadedId != null && maxHours > 20) {
            int newHours = Math.max(0, maxHours - 4);
            ds.setWorkloadHours(mostOverloadedId, newHours);
            sb.append("{\"success\":true,\"message\":\"Workload reduced for overloaded TA. New hours: ").append(newHours).append("\"}");
        } else {
            sb.append("{\"success\":true,\"message\":\"No overloaded TAs. Workloads are within safe range.\"}");
        }
    }

    private void sendError(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
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
        sb.append("\"hoursAvailable\":").append(a.getHoursAvailable()).append(",");
        sb.append("\"cvFileName\":\"").append(esc(a.getCvFileName())).append("\",");
        sb.append("\"createdAt\":\"").append(esc(a.getCreatedAt())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String userToJsonPublic(User u, TAPplicant profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\":\"").append(esc(u.getUsername())).append("\",");
        sb.append("\"displayName\":\"").append(esc(u.getDisplayName())).append("\",");
        sb.append("\"email\":\"").append(esc(u.getEmail())).append("\",");
        sb.append("\"createdAt\":\"").append(esc(u.getCreatedAt())).append("\",");
        sb.append("\"applicantId\":\"").append(esc(u.getApplicantId() != null ? u.getApplicantId() : "")).append("\",");
        sb.append("\"roles\":[");
        int ri = 0;
        for (String r : u.getRoles()) {
            if (ri++ > 0) sb.append(",");
            sb.append("\"").append(esc(r)).append("\"");
        }
        sb.append("],");
        sb.append("\"applicantProfile\":");
        if (profile != null) sb.append(applicantToJson(profile));
        else sb.append("null");
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
        sb.append("\"aiExplanation\":\"").append(esc(a.getAiExplanation())).append("\",");
        sb.append("\"llmExplanation\":").append(a.getLlmExplanation() != null ? "\"" + esc(a.getLlmExplanation()) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }


    // ---- System Config JSON Serializer ----

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
                sb.append("\"username\":\"").append(esc(a.getUsername())).append("\",");
                sb.append("\"password\":\"").append(esc(a.getPassword())).append("\",");
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
        return "{\"timestamp\":\"" + esc(l.getTimestamp()) + "\","
             + "\"operation\":\"" + esc(l.getOperation()) + "\","
             + "\"fileName\":\"" + esc(l.getFileName()) + "\","
             + "\"status\":\"" + esc(l.getStatus()) + "\","
             + "\"opIcon\":\"" + esc(l.getOpIcon()) + "\"}";
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
