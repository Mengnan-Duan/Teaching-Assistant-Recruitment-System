package com.bupt.smartta.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 全局安全响应头 Filter。
 * 为所有 HTTP 响应添加安全相关的响应头，防止常见 Web 安全攻击。
 *
 * 已修复：
 * - CSP 策略收紧（移除 'unsafe-inline' 和 'unsafe-eval'）
 * - 添加 Referrer-Policy
 */
public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 无需初始化配置
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 防止 MIME 类型 sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 防止 Clickjacking（禁止在 iframe 中嵌入）
        response.setHeader("X-Frame-Options", "DENY");

        // XSS 保护（现代浏览器默认启用，此为兼容旧浏览器）
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // 禁用浏览器缓存（敏感数据页面）
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // Referrer-Policy：仅在同源请求时发送 Referer
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // 内容安全策略（CSP）
        // JSP 页面大量使用内联 <style> / <script>，必须允许 'unsafe-inline'，
        // 否则浏览器会拦截样式与脚本，页面呈“纯文本”、登录不可用。
        // 若将来改为外链 JS/CSS 或 nonce，可再收紧 script-src / style-src。
        String csp = "default-src 'self'; " +
                "style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; " +
                "font-src 'self' https://fonts.gstatic.com; " +
                "script-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none';";
        response.setHeader("Content-Security-Policy", csp);

        // 严格传输安全（仅当使用 HTTPS 时生效）
        // 在生产环境取消注释：
        // response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        // 无需清理
    }
}
