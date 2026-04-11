package com.bupt.smartta.servlet;

import com.bupt.smartta.model.TAPplicant;
import com.bupt.smartta.model.User;
import com.bupt.smartta.model.Application;
import com.bupt.smartta.model.Position;
import com.bupt.smartta.util.DataStore;
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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Admin / MO 下载 TA 已上传的 CV（存储于 cv_uploads，文件名为 UUID）。
 *
 * <p>权限规则：
 * <ul>
 *   <li>ADMIN（任意）：始终可下载任意 TA 的 CV</li>
 *   <li>MO（仅本人发布职位下申请者）：可下载已提交/待审核 TA 的 CV，
 *       下载时自动将申请状态从 Submitted 推进到 Under Review</li>
 * </ul>
 */
public class DownloadServlet extends HttpServlet {

    private static final DataStore DS = DataStore.getInstance();
    private static final Pattern SAFE_CV_NAME = Pattern.compile("^[a-fA-F0-9\\-]{20,}\\.(pdf|doc|docx)$");
    private static final String UPLOAD_DIR = "cv_uploads";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            writeFriendlyPage(req, resp, 200, "Sign in required",
                "Please sign in to download CV files.");
            return;
        }

        String applicantId = req.getParameter("applicantId");
        if (applicantId == null || applicantId.isEmpty()) {
            writeFriendlyPage(req, resp, 200, "Missing information",
                "No applicant ID was provided. Please use the link from the admin directory or MO portal.");
            return;
        }
        applicantId = applicantId.trim();

        String currentRole = (String) session.getAttribute("currentRole");
        String username = (String) session.getAttribute("username");

        // ADMIN 可下载任意 TA 的 CV
        if (isAdminDownloadAllowed(session)) {
            serveCv(req, resp, applicantId);
            return;
        }

        // MO 可下载其发布职位下申请者的 CV，并在下载时自动推进状态
        if ("MO".equals(currentRole)) {
            if (moMayDownloadCv(username, applicantId)) {
                advanceStatusToUnderReview(username, applicantId);
                serveCv(req, resp, applicantId);
                return;
            }
            writeFriendlyPage(req, resp, 200, "Access denied",
                "You can only download CV files for applicants who have applied to your posted positions.");
            return;
        }

        writeFriendlyPage(req, resp, 200, "Access denied",
            "Only administrators or module organisers can download CV files from this page.");
    }

    /**
     * 检查 MO 是否有权下载该申请者的 CV。
     * 即：该申请者在 MO 发布的任意职位下存在申请记录。
     */
    private boolean moMayDownloadCv(String moUsername, String applicantId) {
        if (moUsername == null || applicantId == null) return false;
        User mo = DS.getUserByUsername(moUsername);
        for (Application a : DS.getApplications()) {
            if (a == null) continue;
            if (!applicantId.equalsIgnoreCase(a.getApplicantId())) continue;
            Position p = DS.getPositionByCode(a.getPositionCode());
            if (p == null) continue;
            if (p.getPostedByUsername() != null && !p.getPostedByUsername().trim().isEmpty()) {
                if (p.getPostedByUsername().trim().equalsIgnoreCase(moUsername)) return true;
            } else if (mo != null && mo.getDisplayName() != null && p.getPostedBy() != null
                    && mo.getDisplayName().trim().equalsIgnoreCase(p.getPostedBy().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 若申请状态为 Submitted，则自动推进为 Under Review（MO 开始审阅简历的标志）。
     * 仅推进首个匹配的 Submitted 申请记录。
     */
    private void advanceStatusToUnderReview(String moUsername, String applicantId) {
        for (Application a : DS.getApplications()) {
            if (a == null) continue;
            if (!applicantId.equalsIgnoreCase(a.getApplicantId())) continue;
            if (!Application.STATUS_SUBMITTED.equals(a.getStatus())) continue;
            Position p = DS.getPositionByCode(a.getPositionCode());
            if (p == null) continue;
            boolean isMine = false;
            if (p.getPostedByUsername() != null && !p.getPostedByUsername().trim().isEmpty()) {
                isMine = p.getPostedByUsername().trim().equalsIgnoreCase(moUsername);
            } else {
                User mo = DS.getUserByUsername(moUsername);
                if (mo != null && mo.getDisplayName() != null && p.getPostedBy() != null
                        && mo.getDisplayName().trim().equalsIgnoreCase(p.getPostedBy().trim())) {
                    isMine = true;
                }
            }
            if (!isMine) continue;
            a.setStatus(Application.STATUS_REVIEW);
            DS.updateApplication(a);
            return;
        }
    }

    private void serveCv(HttpServletRequest req, HttpServletResponse resp, String applicantId) throws IOException {
        TAPplicant ta = DS.getApplicantById(applicantId);
        if (ta == null) {
            writeFriendlyPage(req, resp, 200, "Applicant not found",
                "No applicant record exists for this ID.");
            return;
        }
        String fn = ta.getCvFileName();
        if (fn == null || fn.isEmpty()) {
            writeFriendlyPage(req, resp, 200, "No CV uploaded yet",
                "This TA applicant has not uploaded a CV yet. Files appear here after they upload from the TA portal (stored under cv_uploads/).");
            return;
        }
        if (!SAFE_CV_NAME.matcher(fn).matches()) {
            writeFriendlyPage(req, resp, 200, "Cannot download CV",
                "The stored file name is invalid. Please contact an administrator.");
            return;
        }
        Path file = resolveCvFile(req, fn);
        if (file == null || !Files.isRegularFile(file)) {
            writeFriendlyPage(req, resp, 200, "CV file not on server",
                "The database lists a CV file, but it was not found on the server under cv_uploads/. "
                    + "The TA may need to upload again, or the file was removed.");
            return;
        }
        String lower = fn.toLowerCase();
        String mime = "application/octet-stream";
        if (lower.endsWith(".pdf")) {
            mime = "application/pdf";
        } else if (lower.endsWith(".doc")) {
            mime = "application/msword";
        } else if (lower.endsWith(".docx")) {
            mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        String ext = lower.substring(lower.lastIndexOf('.'));
        String attachName = "CV_" + applicantId + ext;
        resp.setContentType(mime);
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + attachName + "\"");
        resp.setContentLengthLong(Files.size(file));
        try (InputStream in = Files.newInputStream(file); OutputStream out = resp.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /**
     * 与 UploadServlet 一致：优先 catalina.base/webapps/SmartTA/cv_uploads，其次应用内真实路径。
     */
    private Path resolveCvFile(HttpServletRequest req, String fn) {
        String catalinaBase = System.getProperty("catalina.base", "");
        if (!catalinaBase.isEmpty()) {
            Path p = Paths.get(catalinaBase, "webapps", "SmartTA", UPLOAD_DIR, fn);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        try {
            URL url = getServletContext().getResource("/" + UPLOAD_DIR + "/" + fn);
            if (url != null && "file".equals(url.getProtocol())) {
                Path p2 = Paths.get(url.toURI());
                if (Files.isRegularFile(p2)) {
                    return p2;
                }
            }
            String real = getServletContext().getRealPath("/" + UPLOAD_DIR + "/" + fn);
            if (real != null) {
                Path p3 = Paths.get(real);
                if (Files.isRegularFile(p3)) {
                    return p3;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private void writeFriendlyPage(HttpServletRequest req, HttpServletResponse resp, int httpStatus,
            String title, String paragraph) throws IOException {
        resp.setStatus(httpStatus);
        resp.setContentType("text/html;charset=UTF-8");
        String ctx = req.getContextPath();
        if (ctx == null) {
            ctx = "";
        }
        String moUrl = escHtml(ctx + "/mo.jsp");
        String adminUrl = escHtml(ctx + "/admin.jsp");
        String indexUrl = escHtml(ctx + "/index.jsp");
        String backOnclick = "var r=document.referrer;if(r&amp;&amp;r.indexOf(location.origin)===0){location.href=r;return false;}if(history.length&gt;1){history.back();return false;}return true;";
        String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"/>"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>"
            + "<title>" + escHtml(title) + " · Smart-TA</title>"
            + "<style>body{font-family:system-ui,-apple-system,\"Segoe UI\",sans-serif;max-width:520px;margin:48px auto;padding:0 24px;color:#1a1a2e;line-height:1.65;}"
            + ".card{border:1px solid #e0ddd8;border-radius:12px;padding:28px;background:#fafaf8;}"
            + "h1{font-size:1.15rem;margin:0 0 12px;font-weight:700;}"
            + "p{margin:0 0 14px;}"
            + "a{color:#457b9d;font-weight:600;text-decoration:none;} a:hover{text-decoration:underline;}"
            + ".nav a{display:inline-block;padding:4px 2px;margin:2px 0;}"
            + ".hint{color:#8d99ae;font-size:0.82rem;margin-top:8px;}</style></head><body>"
            + "<div class=\"card\"><h1>" + escHtml(title) + "</h1>"
            + "<p>" + escHtml(paragraph) + "</p>"
            + "<p class=\"hint\">TA 在 TA 门户上传的简历保存在应用目录下的 <code>cv_uploads/</code>。</p>"
            + "<p class=\"nav\"><a href=\"" + moUrl + "\" onclick=\"" + backOnclick + "\">← 返回上一页</a>"
            + " <span aria-hidden=\"true\">·</span> <a href=\"" + moUrl + "\">MO Portal</a>"
            + " <span aria-hidden=\"true\">·</span> <a href=\"" + adminUrl + "\">Admin</a>"
            + " <span aria-hidden=\"true\">·</span> <a href=\"" + indexUrl + "\">登录页</a></p>"
            + "</div></body></html>";
        resp.getWriter().write(html);
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** 当前为 ADMIN 视图或账号在库中持有 ADMIN 均可下载。 */
    private static boolean isAdminDownloadAllowed(HttpSession session) {
        if (session == null) return false;
        if ("ADMIN".equals(session.getAttribute("currentRole"))) return true;
        String uname = (String) session.getAttribute("username");
        if (uname == null) return false;
        User u = DS.getUserByUsername(uname);
        return u != null && u.hasRole("ADMIN");
    }
}
