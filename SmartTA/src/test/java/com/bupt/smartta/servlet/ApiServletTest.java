package com.bupt.smartta.servlet;

import com.bupt.smartta.model.*;
import com.bupt.smartta.util.DataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ApiServlet 集成测试（手动测试框架，无 JUnit/Mockito 注解依赖）。
 *
 * 测试范围：
 * - GET /api?action=positions    —— 岗位列表
 * - GET /api?action=users       —— 用户列表
 * - GET /api?action=applicants  —— 申请者列表（含按岗位筛选）
 * - GET /api?action=taWorkloads —— TA 工作量
 * - GET /api?action=moTaMessages —— MO-TA 消息（角色权限校验）
 * - 状态流转模型验证             —— Application STATUS_SUBMITTED/REVIEW/ACCEPTED/REJECTED
 *
 * 策略：
 * - 通过反射操作 DataStore 私有集合进行数据准备和验证
 * - 通过 HttpServletResponseWrapper/HttpServletRequestWrapper 实现最小化 Mock
 * - 业务层模型测试直接操作 POJO，验证状态机规则
 */
public class ApiServletTest {

    // ============================================================
    // 测试报告辅助
    // ============================================================
    static final AtomicInteger passed = new AtomicInteger(0);
    static final AtomicInteger failed = new AtomicInteger(0);

    static void section(String name) { System.out.println("\n── " + name + " ──"); }

    static void test(String name, Runnable r) {
        try {
            r.run();
            System.out.println("  [PASS] " + name);
            passed.incrementAndGet();
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + name + " => " + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed.incrementAndGet();
        }
    }

    static void expectEq(Object a, Object b) {
        if (!Objects.equals(a, b)) throw new AssertionError("期望 <" + b + "> 实际 <" + a + ">");
    }

    static void expectContains(String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle))
            throw new AssertionError("未包含 <" + needle + ">: " + haystack);
    }

    // ============================================================
    // 反射工具
    // ============================================================
    @SuppressWarnings("unchecked")
    static <T> T getField(Object obj, String name) throws Exception {
        Class<?> c = obj == null ? DataStore.class : obj.getClass();
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return (T) (obj == null ? f.get(null) : f.get(obj));
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> getListField(Object obj, String name) throws Exception {
        return getField(obj, name);
    }

    static void callVoid(Object target, String name, Class<?>[] pTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, pTypes);
        m.setAccessible(true);
        m.invoke(target, args);
    }

    // ============================================================
    // 最小化 Mock：仅覆盖 ApiServlet 实际调用的方法
    // 通过继承 Wrapper 实现，其余方法由 Wrapper 默认实现
    // ============================================================

    /** 捕获响应输出，扩展 HttpServletResponseWrapper */
    static class CapturingResponseWrapper extends HttpServletResponseWrapper {
        final StringWriter sw = new StringWriter();
        int statusCode = 200;
        String contentType = "";

        public CapturingResponseWrapper(HttpServletResponse r) { super(r); }

        @Override public PrintWriter getWriter() { return new PrintWriter(sw); }
        @Override public void setStatus(int code) { this.statusCode = code; }
        @Override public void sendError(int code, String msg) { this.statusCode = code; }
        @Override public void sendError(int code) { this.statusCode = code; }
        @Override public void setContentType(String t) { this.contentType = t; }
        @Override public void setCharacterEncoding(String e) {}

        String body() { return sw.toString(); }
    }

    /** 注入参数和会话数据，扩展 HttpServletRequestWrapper */
    static class InjectedRequestWrapper extends HttpServletRequestWrapper {
        final Map<String, String> params = new HashMap<>();
        final Map<String, Object> sessionData = new HashMap<>();
        String method = "POST";

        public InjectedRequestWrapper(HttpServletRequest r) { super(r); }

        void p(String k, String v) { params.put(k, v); }
        void auth(String uid, String role) { sessionData.put("userId", uid); sessionData.put("role", role); }
        void clearAuth() { sessionData.clear(); }

        @Override public String getParameter(String n) { return params.getOrDefault(n, super.getParameter(n)); }
        @Override public String getMethod() { return method; }
        @Override public Object getAttribute(String n) {
            return sessionData.containsKey(n) ? sessionData.get(n) : super.getAttribute(n);
        }
        @Override public HttpSession getSession() { return new SimpleSession(); }
        @Override public HttpSession getSession(boolean create) { return new SimpleSession(); }

        class SimpleSession implements HttpSession {
            @Override public Object getAttribute(String n) { return sessionData.get(n); }
            @Override public void setAttribute(String n, Object v) { sessionData.put(n, v); }
            @Override public void removeAttribute(String n) { sessionData.remove(n); }
            @Override public java.util.Enumeration<String> getAttributeNames() {
                return Collections.enumeration(new HashSet<>(sessionData.keySet()));
            }
            @Override public long getCreationTime() { return System.currentTimeMillis(); }
            @Override public long getLastAccessedTime() { return System.currentTimeMillis(); }
            @Override public String getId() { return "test-session"; }
            @Override public int getMaxInactiveInterval() { return 1800; }
            @Override public void setMaxInactiveInterval(int i) {}
            @Override public boolean isNew() { return false; }
            @Override public void invalidate() {}
            @Override public jakarta.servlet.ServletContext getServletContext() { return null; }
        }
    }

    // ============================================================
    // 测试数据标识符
    // ============================================================
    static final String ADMIN_USER = "admin_test";
    static final String MO_USER    = "mo_test";
    static final String TA_USER    = "ta_test";
    static final String TA2_USER   = "ta2_test";
    static final String POS_CODE   = "CS-TEST-001";
    static final String APP_ID     = "app-test-001";
    static final String APP2_ID    = "app-test-002";

    public static void main(String[] args) throws Exception {
        System.out.println("================================================");
        System.out.println("  ApiServletTest 手动测试套件");
        System.out.println("================================================");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ApiServlet servlet = new ApiServlet();
        DataStore store = DataStore.getInstance();

        cleanTestData(store);
        setupTestData(store);

        testPositionsEndpoint(servlet);
        testUsersEndpoint(servlet);
        testApplicantsEndpoint(servlet);
        testTaWorkloadsEndpoint(servlet);
        testMoTaMessagesEndpoint(servlet);
        testApplicationStatusModel();

        System.out.println("\n================================================");
        System.out.println("  测试完成: " + passed + " 通过, " + failed + " 失败");
        System.out.println("================================================");
        if (failed.get() > 0) System.exit(1);
    }

    // ============================================================
    // 数据准备
    // ============================================================
    @SuppressWarnings("unchecked")
    static void cleanTestData(DataStore store) throws Exception {
        List<User> users = getListField(store, "users");
        users.removeIf(u -> u.getUsername().startsWith("admin_test")
                          || u.getUsername().startsWith("mo_test")
                          || u.getUsername().startsWith("ta_test")
                          || u.getUsername().startsWith("ta2_test"));
        List<Position> positions = getListField(store, "positions");
        positions.removeIf(p -> p.getCode().startsWith("CS-TEST"));
        List<TAPplicant> applicants = getListField(store, "applicants");
        applicants.removeIf(a -> a.getId().startsWith("app-test"));
        List<Application> applications = getListField(store, "applications");
        applications.removeIf(a -> a.getApplicantId().startsWith("app-test"));
        callVoid(store, "saveAll", new Class<?>[]{});
    }

    @SuppressWarnings("unchecked")
    static void setupTestData(DataStore store) throws Exception {
        User admin = new User(ADMIN_USER, "x", "Admin Test", "admin@test.com");
        admin.addRole("ADMIN");
        User mo = new User(MO_USER, "x", "MO Test", "mo@test.com");
        mo.addRole("MO");
        User ta = new User(TA_USER, "x", "TA Test", "ta@test.com");
        ta.addRole("TA");
        User ta2 = new User(TA2_USER, "x", "TA2 Test", "ta2@test.com");
        ta2.addRole("TA");
        getListField(store, "users").addAll(Arrays.asList(admin, mo, ta, ta2));

        Position pos = new Position(POS_CODE, "Test Course",
            Arrays.asList("Java", "Python", "SQL"),
            10, 2, "2025-12-31", MO_USER);
        pos.setDescription("Test position for unit testing");
        getListField(store, "positions").add(pos);

        TAPplicant app = new TAPplicant(APP_ID, "TA Test User", "ta@test.com",
            "Year 2", 3.5, Arrays.asList("Java", "Python", "SQL", "ML"), 15);
        TAPplicant app2 = new TAPplicant(APP2_ID, "TA2 Test User", "ta2@test.com",
            "Year 3", 3.8, Arrays.asList("Python", "Data Science"), 12);
        getListField(store, "applicants").addAll(Arrays.asList(app, app2));

        Application ap = new Application(APP_ID, "TA Test User",
            POS_CODE, "Test Course", 85);
        getListField(store, "applications").add(ap);

        callVoid(store, "saveAll", new Class<?>[]{});
    }

    // ============================================================
    // 测试用例
    // ============================================================

    static HttpServletRequest makeReq(String action, String uid, String role) {
        return makeReq(action, uid, role, null, null);
    }

    static HttpServletRequest makeReq(String action, String uid, String role,
                                      String extraKey, String extraVal) {
        HttpServletRequest base = new MinimalRequest();
        InjectedRequestWrapper w = new InjectedRequestWrapper(base);
        w.p("action", action);
        if (extraKey != null) w.p(extraKey, extraVal);
        if (uid != null) w.auth(uid, role);
        return w;
    }

    static CapturingResponseWrapper makeResp() {
        return new CapturingResponseWrapper(new MinimalResponse());
    }

    static void testPositionsEndpoint(ApiServlet servlet) throws Exception {
        section("GET /api?action=positions");
        InjectedRequestWrapper req = (InjectedRequestWrapper) makeReq("positions", ADMIN_USER, "ADMIN");
        CapturingResponseWrapper resp = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req, resp);
        String body = resp.body();
        test("HTTP 状态码 200", () -> expectEq(resp.statusCode, 200));
        test("包含岗位 CS-TEST-001", () -> expectContains(body, POS_CODE));
        test("包含 positions 字段", () -> expectContains(body, "\"positions\""));
    }

    static void testUsersEndpoint(ApiServlet servlet) throws Exception {
        section("GET /api?action=users");
        InjectedRequestWrapper req = (InjectedRequestWrapper) makeReq("users", ADMIN_USER, "ADMIN");
        CapturingResponseWrapper resp = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req, resp);
        String body = resp.body();
        test("HTTP 状态码 200", () -> expectEq(resp.statusCode, 200));
        test("包含 mo_test 用户", () -> expectContains(body, MO_USER));
        test("包含 TA 用户", () -> expectContains(body, TA_USER));
    }

    static void testApplicantsEndpoint(ApiServlet servlet) throws Exception {
        section("GET /api?action=applicants");
        // ADMIN 全量
        InjectedRequestWrapper req1 = (InjectedRequestWrapper) makeReq("applicants", ADMIN_USER, "ADMIN");
        CapturingResponseWrapper resp1 = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req1, resp1);
        test("[ADMIN] HTTP 200", () -> expectEq(resp1.statusCode, 200));
        test("[ADMIN] 包含 applicants", () -> expectContains(resp1.body(), "applicants"));

        // MO 角色
        InjectedRequestWrapper req2 = (InjectedRequestWrapper) makeReq("applicants", MO_USER, "MO");
        CapturingResponseWrapper resp2 = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req2, resp2);
        test("[MO] HTTP 200", () -> expectEq(resp2.statusCode, 200));

        // 按岗位筛选
        InjectedRequestWrapper req3 = (InjectedRequestWrapper) makeReq("applicants", ADMIN_USER, "ADMIN", "positionCode", POS_CODE);
        CapturingResponseWrapper resp3 = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req3, resp3);
        test("[按岗位] HTTP 200", () -> expectEq(resp3.statusCode, 200));
    }

    static void testTaWorkloadsEndpoint(ApiServlet servlet) throws Exception {
        section("GET /api?action=taWorkloads");
        InjectedRequestWrapper req = (InjectedRequestWrapper) makeReq("taWorkloads", ADMIN_USER, "ADMIN");
        CapturingResponseWrapper resp = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req, resp);
        test("HTTP 200", () -> expectEq(resp.statusCode, 200));
        test("包含 workloads", () -> expectContains(resp.body(), "workloads"));
    }

    static void testMoTaMessagesEndpoint(ApiServlet servlet) throws Exception {
        section("GET /api?action=moTaMessages");
        // MO
        InjectedRequestWrapper req1 = (InjectedRequestWrapper) makeReq("moTaMessages", MO_USER, "MO");
        CapturingResponseWrapper resp1 = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req1, resp1);
        test("[MO] HTTP 200", () -> expectEq(resp1.statusCode, 200));

        // TA
        InjectedRequestWrapper req2 = (InjectedRequestWrapper) makeReq("moTaMessages", TA_USER, "TA");
        CapturingResponseWrapper resp2 = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req2, resp2);
        test("[TA] HTTP 200", () -> expectEq(resp2.statusCode, 200));

        // 未登录 -> 403
        InjectedRequestWrapper req3 = (InjectedRequestWrapper) makeReq("moTaMessages", null, null);
        req3.clearAuth();
        CapturingResponseWrapper resp3 = makeResp();
        callVoid(servlet, "doGet", new Class[]{HttpServletRequest.class, HttpServletResponse.class}, req3, resp3);
        test("[未登录] HTTP 403", () -> expectEq(resp3.statusCode, 403));
    }

    static void testApplicationStatusModel() throws Exception {
        section("Application 状态机模型");
        Application ap = new Application(APP_ID, "Test TA", POS_CODE, "Test Position", 90);
        test("默认状态 Submitted", () -> expectEq(ap.getStatus(), Application.STATUS_SUBMITTED));
        test("ID = applicantId_positionCode", () -> expectEq(ap.getId(), APP_ID + "_" + POS_CODE));
        test("aiScore = 90", () -> expectEq(ap.getAiScore(), 90));

        ap.setStatus(Application.STATUS_REVIEW);
        test("Submitted -> Under Review", () -> expectEq(ap.getStatus(), Application.STATUS_REVIEW));

        ap.setStatus(Application.STATUS_ACCEPTED);
        test("Under Review -> Accepted", () -> expectEq(ap.getStatus(), Application.STATUS_ACCEPTED));

        Application ap2 = new Application(APP2_ID, "TA2", POS_CODE, "Test Position", 50);
        ap2.setStatus(Application.STATUS_REJECTED);
        test("Submitted -> Rejected", () -> expectEq(ap2.getStatus(), Application.STATUS_REJECTED));

        test("STATUS_SUBMITTED = 'Submitted'", () -> expectEq(Application.STATUS_SUBMITTED, "Submitted"));
        test("STATUS_REVIEW = 'Under Review'", () -> expectEq(Application.STATUS_REVIEW, "Under Review"));
        test("STATUS_ACCEPTED = 'Accepted'", () -> expectEq(Application.STATUS_ACCEPTED, "Accepted"));
        test("STATUS_REJECTED = 'Rejected'", () -> expectEq(Application.STATUS_REJECTED, "Rejected"));
    }

    // ============================================================
    // 最基础 Mock：继承 Wrapper，自动获得所有默认实现
    // 只覆盖最基础方法，其余委托给 Wrapper
    // ============================================================

    /** 最低限度 HttpServletRequest，直接继承 Wrapper，无需实现任何方法 */
    static class MinimalRequest extends HttpServletRequestWrapper {
        public MinimalRequest() { super(null); } // 传入 null，由子类覆盖 getAttribute/getParameter
        @Override public String getParameter(String n) { return null; }
        @Override public String getMethod() { return "POST"; }
        @Override public Object getAttribute(String n) { return null; }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public int getContentLength() { return 0; }
        @Override public long getContentLengthLong() { return 0L; }
        @Override public String getContentType() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public String getScheme() { return "http"; }
        @Override public String getServerName() { return "localhost"; }
        @Override public int getServerPort() { return 8080; }
        @Override public java.io.BufferedReader getReader() { return null; }
        @Override public jakarta.servlet.ServletInputStream getInputStream() { return null; }
        @Override public String getRemoteAddr() { return "127.0.0.1"; }
        @Override public String getRemoteHost() { return "localhost"; }
        @Override public int getRemotePort() { return 0; }
        @Override public String getLocalAddr() { return "127.0.0.1"; }
        @Override public String getLocalName() { return "localhost"; }
        @Override public int getLocalPort() { return 0; }
        @Override public String getContextPath() { return "/SmartTA"; }
        @Override public String getRequestURI() { return "/SmartTA/api"; }
        @Override public StringBuffer getRequestURL() { return new StringBuffer("/SmartTA/api"); }
        @Override public String getServletPath() { return "/api"; }
        @Override public HttpSession getSession() { return null; }
        @Override public HttpSession getSession(boolean create) { return null; }
        @Override public String getAuthType() { return null; }
        @Override public Cookie[] getCookies() { return null; }
        @Override public long getDateHeader(String n) { return -1L; }
        @Override public String getHeader(String n) { return null; }
        @Override public java.util.Enumeration<String> getHeaders(String n) { return Collections.emptyEnumeration(); }
        @Override public java.util.Enumeration<String> getHeaderNames() { return Collections.emptyEnumeration(); }
        @Override public int getIntHeader(String n) { return -1; }
        @Override public String getPathInfo() { return null; }
        @Override public String getPathTranslated() { return null; }
        @Override public String getQueryString() { return null; }
        @Override public String getRemoteUser() { return null; }
        @Override public boolean isUserInRole(String r) { return false; }
        @Override public java.security.Principal getUserPrincipal() { return null; }
        @Override public String getRequestedSessionId() { return null; }
        @Override public boolean isRequestedSessionIdFromCookie() { return false; }
        @Override public boolean isRequestedSessionIdFromURL() { return false; }
        @Override public boolean isRequestedSessionIdValid() { return false; }
        @Override public boolean authenticate(HttpServletResponse r) { return false; }
        @Override public void login(String u, String p) {}
        @Override public void logout() {}
        @Override public java.util.Collection<Part> getParts() { return Collections.emptyList(); }
        @Override public Part getPart(String n) { return null; }
        @Override public <T extends HttpUpgradeHandler> T upgrade(Class<T> c) { return null; }
        @Override public jakarta.servlet.http.HttpServletMapping getHttpServletMapping() { return null; }
    }

    /** 最低限度 HttpServletResponse，直接继承 Wrapper */
    static class MinimalResponse extends HttpServletResponseWrapper {
        int sc = 200; String ct = "";
        public MinimalResponse() { super(null); }
        @Override public void setStatus(int c) { this.sc = c; }
        @Override public int getStatus() { return sc; }
        @Override public void setContentType(String t) { this.ct = t; }
        @Override public String getContentType() { return ct; }
        @Override public PrintWriter getWriter() { return new PrintWriter(new StringWriter()); }
        @Override public jakarta.servlet.ServletOutputStream getOutputStream() { return null; }
        @Override public void sendError(int c, String m) { this.sc = c; }
        @Override public void sendError(int c) { this.sc = c; }
        @Override public void sendRedirect(String l) {}
        @Override public void setDateHeader(String n, long d) {}
        @Override public void addDateHeader(String n, long d) {}
        @Override public void setHeader(String n, String v) {}
        @Override public void addHeader(String n, String v) {}
        @Override public void setIntHeader(String n, int v) {}
        @Override public void addIntHeader(String n, int v) {}
        @Override public String getHeader(String n) { return null; }
        @Override public java.util.Collection<String> getHeaders(String n) { return Collections.emptyList(); }
        @Override public java.util.Collection<String> getHeaderNames() { return Collections.emptyList(); }
        @Override public boolean containsHeader(String n) { return false; }
        @Override public String encodeRedirectURL(String u) { return u; }
        @Override public String encodeURL(String u) { return u; }
        @Override public void setContentLength(int l) {}
        @Override public void setContentLengthLong(long l) {}
        @Override public void setBufferSize(int s) {}
        @Override public int getBufferSize() { return 0; }
        @Override public void flushBuffer() {}
        @Override public void reset() {}
        @Override public void resetBuffer() {}
        @Override public boolean isCommitted() { return false; }
        @Override public void setLocale(java.util.Locale l) {}
        @Override public java.util.Locale getLocale() { return java.util.Locale.getDefault(); }
    }
}
