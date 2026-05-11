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
 * Handles CV file upload and deletion for TA applicants.
 *
 * <p>This servlet processes POST requests to {@code /upload} for uploading CV files
 * and DELETE requests for removing previously uploaded CVs. Security measures include:
 * <ul>
 *   <li>Role enforcement: only TA and ADMIN may upload/delete</li>
 *   <li>File-type allowlist: only PDF, DOC, and DOCX are accepted</li>
 *   <li>File-size limit: 5 MB per file (configured in {@code web.xml})</li>
 *   <li>Safe filenames: files are renamed to a random UUID, preventing path traversal</li>
 *   <li>Extension validation: double extensions (e.g., {@code shell.pdf.jpg}) are rejected</li>
 *   <li>One CV per TA: uploading a new file automatically deletes the old one</li>
 * </ul>
 *
 * <p>Uploaded files are stored in {@code cv_uploads/} within the SmartTA webapp directory.</p>
 *
 * @see com.bupt.smartta.servlet.DownloadServlet
 */
@MultipartConfig(
    maxFileSize = 5 * 1024 * 1024,   // 5 MB
    maxRequestSize = 10 * 1024 * 1024,
    fileSizeThreshold = 1024 * 1024
)
public class UploadServlet extends HttpServlet {

    private static final DataStore ds = DataStore.getInstance();

    /** Allowed file extension allowlist (lowercase). */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>();
    static {
        ALLOWED_EXTENSIONS.add(".pdf");
        ALLOWED_EXTENSIONS.add(".doc");
        ALLOWED_EXTENSIONS.add(".docx");
    }

    /** Allowed MIME type allowlist. */
    private static final Set<String> ALLOWED_TYPES = new HashSet<>();
    static {
        ALLOWED_TYPES.add("application/pdf");
        ALLOWED_TYPES.add("application/msword");
        ALLOWED_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** Upload directory relative to the SmartTA webapp root. */
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

        // 删除旧CV文件（如果存在）- 每个TA只能有一个CV
        String oldFileName = ta.getCvFileName();
        if (oldFileName != null && !oldFileName.isEmpty()) {
            Path oldFilePath = uploadDir.resolve(oldFileName);
            try {
                Files.deleteIfExists(oldFilePath);
                ds.addLog(SystemLog.OP_WRITE, "DELETE:" + UPLOAD_DIR + "/" + oldFileName, SystemLog.STATUS_OK);
            } catch (IOException e) {
                System.err.println("[UploadServlet] Failed to delete old CV: " + oldFileName);
            }
        }

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
     * Deletes the CV file associated with a TA applicant.
     * Each TA may have only one CV; after deletion they may upload a new one.
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
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

        // 获取旧 CV 文件名
        String oldFileName = ta.getCvFileName();
        if (oldFileName == null || oldFileName.isEmpty()) {
            sendError(resp, 404, "No CV file to delete");
            return;
        }

        // 确定上传目录路径
        String catalinaBase = System.getProperty("catalina.base", "");
        Path uploadDir = Paths.get(catalinaBase, "webapps", "SmartTA", UPLOAD_DIR);
        Path oldFilePath = uploadDir.resolve(oldFileName);

        // 删除旧 CV 文件
        try {
            Files.deleteIfExists(oldFilePath);
        } catch (IOException e) {
            System.err.println("[UploadServlet] Failed to delete CV file: " + oldFileName);
        }

        // 清除申请者的 CV 文件名
        ta.setCvFileName(null);
        ds.saveApplicant(ta);
        ds.addLog(SystemLog.OP_WRITE, "DELETE:" + UPLOAD_DIR + "/" + oldFileName, SystemLog.STATUS_OK);

        String json = "{\"success\":true,\"message\":\"CV deleted successfully\"}";
        resp.getWriter().write(json);
    }

    /**
     * Extracts the original filename from a multipart request Part.
     * Compatible with different Servlet container implementations.
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
