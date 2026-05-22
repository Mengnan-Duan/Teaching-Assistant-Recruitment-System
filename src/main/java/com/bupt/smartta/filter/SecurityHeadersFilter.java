package com.bupt.smartta.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 全局安全响应头 Filter。
 * 为所有 HTTP 响应添加安全相关的响应头，防止常见 Web 安全攻击。
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

        // XSS 保护（启用浏览器 XSS 过滤器）
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // 禁用浏览器缓存（敏感数据页面）
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // 内容安全策略（CSP），限制外部资源加载
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                "font-src 'self' https://fonts.gstatic.com; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "img-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none';");

        // 严格传输安全（仅当使用 HTTPS 时生效）
        // response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        // 无需清理
    }
}
