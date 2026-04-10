package com.bupt.smartta.servlet;

import com.bupt.smartta.model.*;
import com.bupt.smartta.util.DataStore;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

public class ApiServlet extends HttpServlet {

    private final DataStore ds = DataStore.getInstance();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();

        if (path == null || path.equals("/")) {
            sendError(resp, "Missing action parameter");
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
                    apps = ds.getApplications();
                }
                for (int i = 0; i < apps.size(); i++) {
                    sb.append(applicationToJson(apps.get(i)));
                    if (i < apps.size() - 1) sb.append(",");
                }
                sb.append("]}");
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

            case "users":
                sb.append("{\"users\":[");
                List<User> users = ds.getUsers();
                for (int i = 0; i < users.size(); i++) {
                    User u = users.get(i);
                    sb.append("{");
                    sb.append("\"username\":\"").append(esc(u.getUsername())).append("\",");
                    sb.append("\"displayName\":\"").append(esc(u.getDisplayName())).append("\",");
                    sb.append("\"email\":\"").append(esc(u.getEmail())).append("\",");
                    sb.append("\"applicantId\":\"").append(esc(u.getApplicantId() != null ? u.getApplicantId() : "")).append("\",");
                    sb.append("\"roles\":[");
                    String[] roles = {"TA","MO","ADMIN"};
                    int ri = 0;
                    for (String r : u.getRoles()) {
                        if (ri > 0) sb.append(",");
                        sb.append("\"").append(esc(r)).append("\"");
                        ri++;
                    }
                    sb.append("]");
                    sb.append("}");
                    if (i < users.size() - 1) sb.append(",");
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
                sb.append("{\"workloads\":{");
                Map<String, Integer> workloads = ds.getWorkloadHours();
                // Only return TAs who have a real user account (exist in users.json with TA role)
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
                String applicantId = req.getParameter("applicantId");
                String posCode = req.getParameter("positionCode");
                TAPplicant ta = ds.getApplicantById(applicantId);
                Position pos = ds.getPositionByCode(posCode);
                if (ta == null || pos == null) {
                    sendError(resp, "Applicant or position not found");
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
            case "quota":
                handleUpdateQuota(req, sb);
                break;
            case "rebalance":
                handleRebalanceWorkload(sb);
                break;
            default:
                sendError(resp, "Unknown POST action: " + action);
                return;
        }

        resp.getWriter().write(sb.toString());
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
        Application app = new Application(applicantId, applicantName != null ? applicantName : ta.getName(),
            positionCode, pos.getName(), score);
        ds.addApplication(app);
        ds.addLog(SystemLog.OP_WRITE, "applications.json", SystemLog.STATUS_OK);

        sb.append("{\"success\":true,\"message\":\"Application submitted successfully\",")
          .append("\"application\":").append(applicationToJson(app)).append("}");
    }

    private void handleCreateApplicant(HttpServletRequest req, StringBuilder sb) {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            sb.append("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }
        User user = ds.getUserByUsername((String) session.getAttribute("username"));
        if (user == null || !user.hasRole("TA")) {
            sb.append("{\"success\":false,\"message\":\"TA profile only\"}");
            return;
        }

        String name = req.getParameter("name");
        String email = req.getParameter("email");
        String year = req.getParameter("yearOfStudy");
        String gpaStr = req.getParameter("gpa");
        String skillsStr = req.getParameter("skills");
        String hoursStr = req.getParameter("hoursAvailable");
        String paramApplicantId = req.getParameter("applicantId");

        if (name == null || name.trim().isEmpty()) {
            sb.append("{\"success\":false,\"message\":\"Name is required\"}");
            return;
        }

        double gpa = 0.0;
        if (gpaStr != null && !gpaStr.trim().isEmpty()) {
            try { gpa = Double.parseDouble(gpaStr.trim()); } catch (Exception ignored) { }
        }
        int hours = 0;
        if (hoursStr != null && !hoursStr.trim().isEmpty()) {
            try { hours = Integer.parseInt(hoursStr.trim()); } catch (Exception ignored) { }
        }

        List<String> skills = new ArrayList<>();
        if (skillsStr != null && !skillsStr.isEmpty()) {
            for (String s : skillsStr.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) skills.add(t);
            }
        }

        String yearVal = year != null ? year.trim() : "";

        String canonicalId = user.getApplicantId();
        if (canonicalId != null && !canonicalId.isEmpty()) {
            if (paramApplicantId != null && !paramApplicantId.isEmpty()
                    && !paramApplicantId.equals(canonicalId)) {
                sb.append("{\"success\":false,\"message\":\"Invalid applicant id\"}");
                return;
            }
            TAPplicant existing = ds.getApplicantById(canonicalId);
            if (existing != null) {
                String emailVal = email != null ? email.trim() : "";
                existing.setName(name.trim());
                existing.setEmail(emailVal);
                existing.setYearOfStudy(yearVal);
                existing.setGpa(gpa);
                existing.setSkills(skills);
                existing.setHoursAvailable(hours);
                ds.saveApplicant(existing);
                user.setDisplayName(name.trim());
                user.setEmail(emailVal);
                ds.saveUser(user);
                sb.append("{\"success\":true,\"message\":\"Profile saved\",")
                  .append("\"applicant\":").append(applicantToJson(existing)).append("}");
                return;
            }
        }

        String id = ds.allocateNextApplicantId();
        String emailVal = email != null ? email.trim() : "";
        TAPplicant ta = new TAPplicant(id, name.trim(), emailVal,
            yearVal, gpa, skills, hours);
        ds.saveApplicant(ta);
        user.setApplicantId(id);
        user.setDisplayName(name.trim());
        user.setEmail(emailVal);
        ds.saveUser(user);

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
