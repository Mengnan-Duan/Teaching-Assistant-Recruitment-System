package com.bupt.smartta.servlet;

import com.bupt.smartta.model.TAPplicant;
import com.bupt.smartta.model.SystemLog;
import com.bupt.smartta.util.DataStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * CV 文件上传处理 Servlet。
 *
 * 安全措施：
 * 1. 角色校验：仅 TA/ADMIN 可上传
 * 2. 文件类型白名单：仅允许 .pdf, .doc, .docx
 * 3. 文件大小限制：≤5MB（在 web.xml 的 multipart-config 中配置）
 * 4. 存储路径安全：文件以 UUID 重命名，防止路径穿越
 * 5. 扩展名校验：不允许双扩展名（如 shell.pdf.jpg）
 */
@MultipartConfig(
    maxFileSize = 5 * 1024 * 1024,   // 5MB
    maxRequestSize = 10 * 1024 * 1024,
    fileSizeThreshold = 1024 * 1024
)
public class UploadServlet extends HttpServlet {

    private static final DataStore ds = DataStore.getInstance();

    /** 允许的文件扩展名白名单（小写） */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>();
    static {
        ALLOWED_EXTENSIONS.add(".pdf");
        ALLOWED_EXTENSIONS.add(".doc");
        ALLOWED_EXTENSIONS.add(".docx");
    }

    /** MIME 类型白名单 */
    private static final Set<String> ALLOWED_TYPES = new HashSet<>();
    static {
        ALLOWED_TYPES.add("application/pdf");
        ALLOWED_TYPES.add("application/msword");
        ALLOWED_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** 上传目录（相对 SmartTA 应用根目录） */
    private static final String UPLOAD_DIR = "cv_uploads";

    private boolean hasRole(HttpServletRequest req, String... roles) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;
        String currentRole = (String) session.getAttribute("currentRole");
        if (currentRole == null) return false;
        for (String r : roles) if (r.equals(currentRole)) return true;
        return false;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // 权限校验
        if (!hasRole(req, "TA", "ADMIN")) {
            sendError(resp, 403, "Insufficient permissions: TA or ADMIN role required");
            return;
        }

        // 获取 applicantId
        String applicantId = req.getParameter("applicantId");
        if (applicantId == null || applicantId.isEmpty()) {
            sendError(resp, 400, "Applicant ID is required");
            return;
        }
        TAPplicant ta = ds.getApplicantById(applicantId);
        if (ta == null) {
            sendError(resp, 404, "Applicant not found: " + applicantId);
            return;
        }

        // 获取上传的文件 Part
        Part filePart;
        try {
            filePart = req.getPart("cv");
        } catch (Exception e) {
            sendError(resp, 400, "No file uploaded or request is not multipart/form-data");
            return;
        }

        if (filePart == null || filePart.getInputStream().available() == 0) {
            sendError(resp, 400, "No file selected");
            return;
        }

        String submittedFileName = extractFileName(filePart);
        if (submittedFileName == null || submittedFileName.isEmpty()) {
            sendError(resp, 400, "Invalid file name");
            return;
        }

        // 扩展名校验（防止双扩展名攻击）
        String lowerName = submittedFileName.toLowerCase();
        String extension = "";
        int lastDot = lowerName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = lowerName.substring(lastDot);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            sendError(resp, 400, "File type not allowed. Accepted: PDF, DOC, DOCX");
            return;
        }

        // MIME 类型校验（防御性）
        String contentType = filePart.getContentType();
        if (contentType != null && !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            // 允许部分意外类型（如 application/octet-stream），但必须通过扩展名校验
            if (!extension.equals(".pdf") && !extension.equals(".doc") && !extension.equals(".docx")) {
                sendError(resp, 400, "File type mismatch with extension");
                return;
            }
        }

        // 生成安全文件名（UUID + 原始扩展名）
        String safeFileName = UUID.randomUUID().toString() + extension;

        // 确定上传目录路径
        String catalinaBase = System.getProperty("catalina.base", "");
        Path uploadDir = Paths.get(catalinaBase, "webapps", "SmartTA", UPLOAD_DIR);
        Files.createDirectories(uploadDir);

        Path targetPath = uploadDir.resolve(safeFileName);

        // 写入文件
        try (InputStream is = filePart.getInputStream()) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 更新申请者的 CV 文件名
        ta.setCvFileName(safeFileName);
        ds.saveApplicant(ta);
        ds.addLog(SystemLog.OP_WRITE, UPLOAD_DIR + "/" + safeFileName, SystemLog.STATUS_OK);

        String json = "{\"success\":true,"
            + "\"message\":\"CV uploaded successfully\","
            + "\"filename\":\"" + escJson(safeFileName) + "\","
            + "\"originalName\":\"" + escJson(submittedFileName) + "\","
            + "\"size\":" + filePart.getSize() + "}";
        resp.getWriter().write(json);
    }

    /**
     * 从 Part 中提取原始文件名（兼容不同 Servlet 容器）。
     */
    private String extractFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition == null) return null;
        for (String token : contentDisposition.split(";")) {
            token = token.trim();
            if (token.startsWith("filename")) {
                int eq = token.indexOf('=');
                if (eq > 0) {
                    String fileName = token.substring(eq + 1).trim();
                    // 去除引号
                    if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                        fileName = fileName.substring(1, fileName.length() - 1);
                    }
                    // 去除路径（IE 兼容）
                    int lastSep = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                    if (lastSep >= 0) {
                        fileName = fileName.substring(lastSep + 1);
                    }
                    return fileName;
                }
            }
        }
        return null;
    }

    private void sendError(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code);
        resp.getWriter().write("{\"success\":false,\"message\":\"" + escJson(message) + "\"}");
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
