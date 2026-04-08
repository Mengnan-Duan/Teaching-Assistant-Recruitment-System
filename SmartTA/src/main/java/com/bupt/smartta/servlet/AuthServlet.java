package com.bupt.smartta.servlet;

import com.bupt.smartta.model.TAPplicant;
import com.bupt.smartta.model.User;
import com.bupt.smartta.model.SystemLog;
import com.bupt.smartta.util.DataStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class AuthServlet extends HttpServlet {

    private final DataStore ds = DataStore.getInstance();

    private static final String CSRF_PARAM = "_csrf";
    private static final String CSRF_SESSION_ATTR = "csrfToken";

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
            sendError(resp, "Invalid or missing CSRF token. Please refresh the page and try again.");
            return false;
        }
        return true;
    }

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();

        if (path == null) {
            sendError(resp, "Missing action");
            return;
        }
        String action = path.substring(1);

        switch (action) {
            case "login":    handleLogin(req, resp);    break;
            case "register": handleRegister(req, resp); break;
            case "logout":   handleLogout(req, resp);   break;
            case "switchRole": handleSwitchRole(req, resp); break;
            default: sendError(resp, "Unknown action: " + action);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo();

        if (path == null || path.equals("/") || path.equals("/session")) {
            handleGetSession(req, resp);
            return;
        }
        String action = path.substring(1);
        if (action.equals("roles")) {
            handleGetRoles(req, resp);
        } else {
            sendError(resp, "Unknown action: " + action);
        }
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String targetRole = req.getParameter("role");

        if (username == null || username.trim().isEmpty()) {
            sendError(resp, "Username is required");
            return;
        }
        if (password == null || password.isEmpty()) {
            sendError(resp, "Password is required");
            return;
        }

        User user = ds.getUserByUsername(username.trim());
        if (user == null) {
            sendError(resp, "Invalid username or password");
            return;
        }

        if (!user.checkPassword(password)) {
            sendError(resp, "Invalid username or password");
            return;
        }

        // 会话固定攻击防护：登录成功后创建新 session
        HttpSession oldSession = req.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(30 * 60);

        if (targetRole != null && !targetRole.trim().isEmpty()) {
            String tr = targetRole.trim().toUpperCase();
            if (!user.hasRole(tr)) {
                sendError(resp, "You do not have permission for role: " + tr);
                return;
            }
            session.setAttribute("currentRole", tr);
        } else {
            if (user.getRoles().isEmpty()) {
                sendError(resp, "This account has no roles assigned. Please contact administrator.");
                return;
            }
            String defaultRole = user.hasRole("TA") ? "TA"
                    : user.hasRole("MO") ? "MO"
                    : user.hasRole("ADMIN") ? "ADMIN"
                    : user.getRoles().iterator().next();
            session.setAttribute("currentRole", defaultRole);
        }

        session.setAttribute("username", user.getUsername());
        session.setAttribute("displayName", user.getDisplayName());
        session.setAttribute("roles", user.getRoles());
        ensureTaApplicantLinked(user.getUsername());
        ds.syncUserAndApplicantEmails(user.getUsername());
        user = ds.getUserByUsername(user.getUsername());
        String email = user.getEmail() != null ? user.getEmail() : "";
        session.setAttribute("email", email);

        String csrfToken = java.util.UUID.randomUUID().toString();
        session.setAttribute(CSRF_SESSION_ATTR, csrfToken);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"message\":\"Login successful\",");
        sb.append("\"username\":\"").append(esc(user.getUsername())).append("\",");
        sb.append("\"displayName\":\"").append(esc(user.getDisplayName())).append("\",");
        sb.append("\"email\":\"").append(esc(email)).append("\",");
        sb.append("\"applicantId\":\"").append(esc(user.getApplicantId() != null ? user.getApplicantId() : "")).append("\",");
        sb.append("\"currentRole\":\"").append(esc((String) session.getAttribute("currentRole"))).append("\",");
        sb.append("\"csrfToken\":\"").append(csrfToken).append("\",");
        sb.append("\"roles\":[");
        int i = 0;
        for (String r : user.getRoles()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(r)).append("\"");
            i++;
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String displayName = req.getParameter("displayName");
        String email = req.getParameter("email");
        String rolesStr = req.getParameter("roles");

        if (username == null || username.trim().isEmpty()
         || password == null || password.isEmpty()
         || displayName == null || displayName.trim().isEmpty()) {
            sendError(resp, "Username, password, and display name are required");
            return;
        }

        username = username.trim();
        displayName = displayName.trim();

        if (username.length() < 3) {
            sendError(resp, "Username must be at least 3 characters");
            return;
        }
        if (username.length() > 30) {
            sendError(resp, "Username must be no more than 30 characters");
            return;
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            sendError(resp, "Password must be at least 8 characters and contain both letters and numbers");
            return;
        }
        if (password.length() > 128) {
            sendError(resp, "Password is too long");
            return;
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            sendError(resp, "Username can only contain letters, numbers, and underscores");
            return;
        }

        if (ds.getUserByUsername(username) != null) {
            sendError(resp, "Username already exists");
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

        User user = new User(username, User.hashPassword(password), displayName, "");
        user.setEmail(email != null ? email.trim() : "");
        for (String r : roles) user.addRole(r);

        ds.saveUser(user);
        if (user.hasRole("TA")) ensureTaApplicantLinked(user.getUsername());
        ds.addLog(SystemLog.OP_WRITE, "users.json", SystemLog.STATUS_OK);

        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute("username", user.getUsername());
        session.setAttribute("displayName", user.getDisplayName());
        session.setAttribute("roles", user.getRoles());
        session.setAttribute("email", user.getEmail() != null ? user.getEmail() : "");
        String csrfToken = java.util.UUID.randomUUID().toString();
        session.setAttribute(CSRF_SESSION_ATTR, csrfToken);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"message\":\"Registration successful\",\"username\":\"")
          .append(esc(user.getUsername())).append("\",");
        sb.append("\"csrfToken\":\"").append(csrfToken).append("\",");
        sb.append("\"roles\":[");
        int i = 0;
        for (String r : user.getRoles()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(r)).append("\"");
            i++;
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        resp.getWriter().write("{\"success\":true,\"message\":\"Logged out successfully\"}");
    }

    private void handleSwitchRole(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            sendError(resp, "Not logged in");
            return;
        }
        if (!validateCsrf(req, resp)) return;

        String rawRole = req.getParameter("role");
        if (rawRole == null || rawRole.trim().isEmpty()) {
            sendError(resp, "Role is required");
            return;
        }
        String targetRole = rawRole.trim().toUpperCase();
        if (!targetRole.equals("TA") && !targetRole.equals("MO") && !targetRole.equals("ADMIN")) {
            sendError(resp, "Invalid role");
            return;
        }

        @SuppressWarnings("unchecked")
        Set<String> userRoles = (Set<String>) session.getAttribute("roles");
        if (userRoles == null || !userRoles.contains(targetRole)) {
            sendError(resp, "You do not have permission for role: " + targetRole + ". Please re-login.");
            return;
        }

        session.setAttribute("currentRole", targetRole);
        String csrfToken = (String) session.getAttribute(CSRF_SESSION_ATTR);
        resp.getWriter().write("{\"success\":true,\"currentRole\":\"" + esc(targetRole)
                + "\",\"csrfToken\":\"" + esc(csrfToken != null ? csrfToken : "") + "\"}");
    }

    private void handleGetSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            resp.getWriter().write("{\"authenticated\":false}");
            return;
        }

        String username = (String) session.getAttribute("username");
        User user = ds.getUserByUsername(username);
        String currentRole = (String) session.getAttribute("currentRole");
        String csrfToken = (String) session.getAttribute(CSRF_SESSION_ATTR);

        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttribute("roles");
        if (roles == null && user != null) roles = user.getRoles();

        if (user == null) {
            resp.getWriter().write("{\"authenticated\":false}");
            return;
        }

        ensureTaApplicantLinked(username);
        ds.syncUserAndApplicantEmails(username);
        user = ds.getUserByUsername(username);
        String email = user.getEmail() != null ? user.getEmail() : "";
        session.setAttribute("email", email);

        if (currentRole != null && roles != null && !roles.contains(currentRole)) {
            session.removeAttribute("currentRole");
            if (!roles.isEmpty()) {
                currentRole = roles.iterator().next();
                session.setAttribute("currentRole", currentRole);
            } else {
                session.invalidate();
                resp.getWriter().write("{\"authenticated\":false}");
                return;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"authenticated\":true,");
        sb.append("\"username\":\"").append(esc(user.getUsername())).append("\",");
        sb.append("\"displayName\":\"").append(esc(user.getDisplayName())).append("\",");
        sb.append("\"email\":\"").append(esc(email)).append("\",");
        sb.append("\"applicantId\":\"").append(esc(user.getApplicantId() != null ? user.getApplicantId() : "")).append("\",");
        sb.append("\"currentRole\":\"").append(esc(currentRole != null ? currentRole : "")).append("\",");
        sb.append("\"csrfToken\":\"").append(esc(csrfToken != null ? csrfToken : "")).append("\",");
        sb.append("\"roles\":[");
        if (roles != null) {
            int i = 0;
            for (String r : roles) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(r)).append("\"");
                i++;
            }
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    private void handleGetRoles(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            sendError(resp, "Not authenticated");
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttribute("roles");
        String currentRole = (String) session.getAttribute("currentRole");
        String csrfToken = (String) session.getAttribute(CSRF_SESSION_ATTR);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"currentRole\":\"").append(esc(currentRole != null ? currentRole : "")).append("\",");
        sb.append("\"csrfToken\":\"").append(esc(csrfToken != null ? csrfToken : "")).append("\",");
        sb.append("\"roles\":[");
        if (roles != null) {
            int i = 0;
            for (String r : roles) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(r)).append("\"");
                i++;
            }
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    private void ensureTaApplicantLinked(String username) {
        User user = ds.getUserByUsername(username);
        if (user == null || !user.hasRole("TA")) return;

        String aid = user.getApplicantId();
        TAPplicant linked = (aid != null && !aid.isEmpty()) ? ds.getApplicantById(aid) : null;

        if (linked != null) {
            return;
        }

        if (aid != null && !aid.isEmpty()) user.setApplicantId(null);

        String id = ds.allocateNextApplicantId();
        String nm = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                ? user.getDisplayName() : user.getUsername();
        String em = user.getEmail() != null ? user.getEmail().trim() : "";
        TAPplicant ta = new TAPplicant(id, nm, em, "", 0, new ArrayList<>(), 0);
        ds.saveApplicant(ta);
        user.setApplicantId(id);
        ds.saveUser(user);
    }

    private void syncUserApplicantContact(String username) {
        User user = ds.getUserByUsername(username);
        if (user == null || !user.hasRole("TA")) return;
        String aid = user.getApplicantId();
        if (aid == null || aid.isEmpty()) return;
        TAPplicant ta = ds.getApplicantById(aid);
        if (ta == null) return;

        String ue = user.getEmail() == null ? "" : user.getEmail().trim();
        String ae = ta.getEmail() == null ? "" : ta.getEmail().trim();

        if (!ue.isEmpty() && ae.isEmpty()) {
            ta.setEmail(ue);
            ds.saveApplicant(ta);
        } else if (ue.isEmpty() && !ae.isEmpty()) {
            user.setEmail(ae);
            ds.saveUser(user);
        }
    }

    private void sendError(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write("{\"success\":false,\"error\":\"" + esc(message) + "\"}");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
