package com.bupt.smartta.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Global security response-header filter for all HTTP responses in the Smart-TA application.
 *
 * <p>This filter adds security-related HTTP headers to every response to mitigate common
 * web vulnerabilities:</p>
 * <ul>
 *   <li>{@code X-Content-Type-Options}: prevents MIME-type sniffing</li>
 *   <li>{@code X-Frame-Options}: prevents clickjacking (DENY)</li>
 *   <li>{@code X-XSS-Protection}: XSS filter for legacy browsers</li>
 *   <li>{@code Cache-Control / Pragma / Expires}: disables browser caching for sensitive pages</li>
 *   <li>{@code Referrer-Policy}: controls referrer information sent to other origins</li>
 *   <li>{@code Content-Security-Policy}: restricts resource loading to trusted origins</li>
 * </ul>
 *
 * <p>The CSP permits inline styles and scripts (required by JSP pages that use many
 * inline {@code <style>} and {@code <script>} blocks). See in-code comments for how
 * to tighten the policy once external JS/CSS files are introduced.</p>
 */
public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No configuration needed
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking (DENY prevents framing entirely)
        response.setHeader("X-Frame-Options", "DENY");

        // XSS protection (modern browsers enable this by default; opt-in for legacy browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Disable browser caching (sensitive data pages)
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // Referrer-Policy: only send referrer for same-origin requests
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Content Security Policy (CSP)
        // Inline styles and scripts are required because JSP pages use many inline blocks.
        // See in-code comments for how to tighten the policy later.
        String csp = "default-src 'self'; " +
                "style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; " +
                "font-src 'self' https://fonts.gstatic.com; " +
                "script-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none';";
        response.setHeader("Content-Security-Policy", csp);

        // Strict Transport Security (only effective when HTTPS is in use)
        // Uncomment in production:
        // response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
