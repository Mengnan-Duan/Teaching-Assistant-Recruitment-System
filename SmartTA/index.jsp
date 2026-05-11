<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page session="true" %>
<%@ page import="java.util.Set" %>
<%
    // Logged-in user opening index with ?targetRole= — switch role then go to dashboard
    String loggedUser = (String) session.getAttribute("username");
    String targetRoleParam = request.getParameter("targetRole");
    if (loggedUser != null && targetRoleParam != null && !targetRoleParam.trim().isEmpty()) {
        String tr = targetRoleParam.trim().toUpperCase();
        if ("TA".equals(tr) || "MO".equals(tr) || "ADMIN".equals(tr)) {
            @SuppressWarnings("unchecked")
            Set<String> roleSet = (Set<String>) session.getAttribute("roles");
            if (roleSet != null && roleSet.contains(tr)) {
                session.setAttribute("currentRole", tr);
                if ("TA".equals(tr)) response.sendRedirect("ta.jsp");
                else if ("MO".equals(tr)) response.sendRedirect("mo.jsp");
                else response.sendRedirect("admin.jsp");
                return;
            }
        }
    }
    // If already logged in, redirect to appropriate dashboard
    String currentRole = (String) session.getAttribute("currentRole");
    if (currentRole != null) {
        if ("TA".equals(currentRole)) {
            response.sendRedirect("ta.jsp");
        } else if ("MO".equals(currentRole)) {
            response.sendRedirect("mo.jsp");
        } else if ("ADMIN".equals(currentRole)) {
            response.sendRedirect("admin.jsp");
        }
        return;
    }
%>
<!DOCTYPE html>
<html lang="en" class="smartta-shell">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Login · Smart-TA</title>
    <link rel="icon" href="<%= request.getContextPath() %>/favicon.ico" type="image/x-icon" />
    <link rel="icon" href="<%= request.getContextPath() %>/favicon.svg" type="image/svg+xml" />
    <link href="https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,300;9..40,500;9..40,700&family=Playfair+Display:wght@600;700&display=swap" rel="stylesheet" />
    <link rel="stylesheet" href="css/smartta-shell.css" />
    <style>
:root {
    --ink:#1a1a2e; --surface:#f8f7f4; --card:#ffffff;
    --accent:#e63946; --accent-soft:#fde8ea;
    --primary:#457b9d; --primary-dark:#1d3557; --primary-soft:#e8f0f6;
    --success:#2a9d8f; --success-soft:#e6f5f3;
    --warn:#e9c46a; --muted:#8d99ae; --border:#e0ddd8;
    --radius:14px; --radius-sm:8px;
    --shadow-md:0 4px 20px rgba(26,26,46,0.08);
    --shadow-lg:0 12px 40px rgba(26,26,46,0.14);
    --font-body:"DM Sans",sans-serif; --font-display:"Playfair Display",serif;
    --transition:0.2s ease;
}
* { margin:0; padding:0; box-sizing:border-box; }
body {
    font-family:var(--font-body);
    color:var(--ink); min-height:100vh; display:flex;
    align-items:center; justify-content:center;
}

.page-wrapper {
    display:grid; grid-template-columns:1fr 1fr;
    max-width:960px; width:95vw; min-height:560px;
    border-radius:var(--radius); overflow:hidden;
    box-shadow:var(--shadow-lg);
    background:var(--card);
}

/* LEFT SIDE - branding */
.brand-side {
    background:linear-gradient(160deg, #1d3557 0%, #457b9d 55%, #2a9d8f 100%);
    color:#fff; padding:48px 40px;
    display:flex; flex-direction:column; justify-content:center;
    position:relative; overflow:hidden;
}
.brand-side::before {
    content:""; position:absolute; top:-80px; right:-80px;
    width:280px; height:280px; border-radius:50%;
    background:rgba(255,255,255,0.05);
}
.brand-side::after {
    content:""; position:absolute; bottom:-60px; left:-40px;
    width:200px; height:200px; border-radius:50%;
    background:rgba(255,255,255,0.04);
}
.brand-logo {
    font-family:var(--font-display); font-size:2.2rem; font-weight:700;
    letter-spacing:-0.02em; margin-bottom:6px;
}
.brand-logo span { color:var(--accent); }
.brand-tagline {
    font-size:0.9rem; opacity:0.75; margin-bottom:40px; line-height:1.5;
}
.brand-features { display:flex; flex-direction:column; gap:14px; }
.brand-feature {
    display:flex; align-items:center; gap:12px;
    font-size:0.85rem; opacity:0.9;
}
.brand-feature-icon {
    width:32px; height:32px; border-radius:8px;
    background:rgba(255,255,255,0.12);
    display:flex; align-items:center; justify-content:center;
    font-size:1rem; flex-shrink:0;
}
.brand-footer {
    margin-top:auto; padding-top:40px;
    font-size:0.72rem; opacity:0.5; line-height:1.6;
}

/* RIGHT SIDE - form */
.form-side { padding:48px 44px; display:flex; flex-direction:column; justify-content:center; }

.form-toggle { display:flex; gap:0; margin-bottom:36px; border:1.5px solid var(--border); border-radius:var(--radius-sm); overflow:hidden; }
.form-toggle-btn {
    flex:1; padding:10px; text-align:center; font-size:0.85rem; font-weight:600;
    cursor:pointer; transition:var(--transition); background:transparent;
    color:var(--muted); border:none; font-family:var(--font-body);
}
.form-toggle-btn.active { background:var(--primary); color:#fff; }
.form-toggle-btn:not(.active):hover { background:rgba(69,123,157,0.05); color:var(--ink); }

.form-box { display:flex; flex-direction:column; gap:20px; }

.field-group { display:flex; flex-direction:column; gap:6px; }
.field-group label { font-size:0.82rem; font-weight:600; color:var(--ink); }
.field-input {
    display:flex; align-items:center; border:1.5px solid var(--border);
    border-radius:var(--radius-sm); overflow:hidden; transition:var(--transition);
}
.field-input:focus-within { border-color:var(--primary); }
.field-input input, .field-input select {
    flex:1; min-width:0; border:none; outline:none; padding:11px 14px;
    font-family:var(--font-body); font-size:0.88rem; color:var(--ink);
    background:transparent;
}
/* Hide Chromium/Edge built-in password reveal so only our toggle shows */
input[type="password"]::-ms-reveal { display: none; }
input[type="password"]::-webkit-credentials-auto-fill-button {
    visibility: hidden; display: none; pointer-events: none;
}
.field-input .input-icon {
    padding:0 12px; color:var(--muted); font-size:1rem;
    display:flex; align-items:center;
}
/* Custom show-password (avoids flaky browser built-in reveal) */
.pw-toggle-btn {
    flex-shrink:0; width:34px; height:34px; margin:0 10px 0 4px;
    border:none; border-radius:50%; cursor:pointer;
    background:linear-gradient(145deg, #ffb347, #ff8c42);
    color:#fff; display:flex; align-items:center; justify-content:center;
    box-shadow:0 1px 4px rgba(255,140,66,0.45);
    transition:transform 0.15s ease, filter 0.15s ease;
}
.pw-toggle-btn:hover { filter:brightness(1.08); }
.pw-toggle-btn:active { transform:scale(0.94); }
.pw-toggle-btn:focus { outline:2px solid var(--primary); outline-offset:2px; }
.pw-toggle-btn svg { display:block; }
.pw-toggle-btn .pw-eye-shut { display:none; }
.pw-toggle-btn.is-revealed .pw-eye-open { display:none; }
.pw-toggle-btn.is-revealed .pw-eye-shut { display:block; }

.role-selector { display:grid; grid-template-columns:1fr 1fr 1fr; gap:10px; }
.role-option {
    border:2px solid var(--border); border-radius:var(--radius-sm);
    padding:14px 10px; text-align:center; cursor:pointer;
    transition:var(--transition); background:#fff;
    position:relative;
}
.role-option:hover { border-color:var(--primary); transform:translateY(-2px); box-shadow:0 4px 12px rgba(69,123,157,0.15); }
.role-option.selected { border-color:var(--primary); background:var(--primary-soft); box-shadow:0 4px 12px rgba(69,123,157,0.2); }
.role-option.selected::after {
    content:""; position:absolute; top:6px; right:6px;
    width:18px; height:18px; border-radius:50%;
    background:var(--primary); color:#fff;
    background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='white' stroke-width='3'%3E%3Cpath d='M20 6L9 17l-5-5'/%3E%3C/svg%3E");
    background-size:12px; background-position:center; background-repeat:no-repeat;
}
.role-option input[type="radio"] { position:absolute; opacity:0; width:0; height:0; }
.role-option input[type="checkbox"] { position:absolute; opacity:0; width:0; height:0; }
.role-option-icon { font-size:1.4rem; margin-bottom:4px; }
.role-option-name { font-size:0.78rem; font-weight:700; color:var(--ink); }
.role-option-desc { font-size:0.65rem; color:var(--muted); margin-top:2px; }

.submit-btn {
    width:100%; padding:13px; border:none; border-radius:var(--radius-sm);
    background:var(--primary); color:#fff; font-family:var(--font-body);
    font-size:0.92rem; font-weight:700; cursor:pointer;
    transition:var(--transition); margin-top:4px;
    display:flex; align-items:center; justify-content:center; gap:8px;
}
.submit-btn:hover { background:var(--primary-dark); }
.submit-btn:disabled { background:var(--muted); cursor:not-allowed; }
.submit-btn .spinner {
    width:16px; height:16px; border:2px solid rgba(255,255,255,0.3);
    border-top-color:#fff; border-radius:50%;
    animation:spin 0.7s linear infinite; display:none;
}
.submit-btn.loading .spinner { display:block; }
.submit-btn.loading .btn-text { display:none; }
@keyframes spin { to { transform:rotate(360deg); } }

.form-footer { text-align:center; margin-top:20px; font-size:0.82rem; color:var(--muted); }
.form-footer a { color:var(--primary); text-decoration:none; font-weight:600; cursor:pointer; }
.form-footer a:hover { text-decoration:underline; }

.error-box {
    display:none; padding:12px 16px; border-radius:var(--radius-sm);
    background:var(--accent-soft); border-left:4px solid var(--accent);
    font-size:0.82rem; color:var(--accent); font-weight:500;
}
.error-box.show { display:flex; align-items:center; gap:8px; }

.success-box {
    display:none; padding:12px 16px; border-radius:var(--radius-sm);
    background:var(--success-soft); border-left:4px solid var(--success);
    font-size:0.82rem; color:#1d6f65; font-weight:500;
}
.success-box.show { display:flex; align-items:center; gap:8px; }
.success-box .success-icon {
    flex-shrink:0; width:22px; height:22px; border-radius:50%;
    background:var(--success); color:#fff;
    display:flex; align-items:center; justify-content:center;
    font-size:0.75rem; font-weight:800;
}

/* Demo badge */
.demo-badge {
    position:absolute; top:16px; right:16px;
    background:rgba(255,255,255,0.15); color:#fff;
    padding:3px 10px; border-radius:100px;
    font-size:0.7rem; font-weight:700; letter-spacing:0.05em;
}

/* Responsive */
@media (max-width: 700px) {
    .page-wrapper { grid-template-columns:1fr; }
    .brand-side { display:none; }
    .form-side { padding:36px 28px; }
}
</style>
</head>
<body class="smartta-shell">

<div class="page-wrapper">
    <!-- LEFT BRAND SIDE -->
    <div class="brand-side">
        <div class="demo-badge" id="versionBadge">v2.0</div>
        <div class="brand-logo">Smart<span>TA</span></div>
        <p class="brand-tagline">AI-Powered TA Recruitment System<br/>BUPT International School · EBU6304 Group 37</p>

        <div class="brand-features">
            <div class="brand-feature">
                <div class="brand-feature-icon">&#127891;</div>
                <span>Role-based dashboards for TAs, Module Organisers & Admins</span>
            </div>
            <div class="brand-feature">
                <div class="brand-feature-icon">&#128200;</div>
                <span>AI-powered applicant ranking &amp; skill matching</span>
            </div>
            <div class="brand-feature">
                <div class="brand-feature-icon">&#128273;</div>
                <span>Secure login with SHA-256 password hashing</span>
            </div>
            <div class="brand-feature">
                <div class="brand-feature-icon">&#128203;</div>
                <span>Multi-role accounts — one user, many permissions</span>
            </div>
        </div>

        <div class="brand-footer">
            <span id="brandVersion">Smart-TA v2.0 &middot; Mid-Term Demo</span><br/>
            Data stored in: /webapps/SmartTA/data/*.json
        </div>
    </div>

    <!-- RIGHT FORM SIDE -->
    <div class="form-side">
        <div class="form-toggle">
            <button class="form-toggle-btn active" id="btn-login" onclick="showLogin()">Sign In</button>
            <button class="form-toggle-btn" id="btn-register" onclick="showRegister()">Register</button>
        </div>

        <!-- ERROR / SUCCESS (same position, mutually exclusive) -->
        <div class="error-box" id="errorBox">
            <span>&#9888;&#65039;</span>
            <span id="errorMsg"></span>
        </div>
        <div class="success-box" id="successBox" aria-live="polite">
            <span class="success-icon" aria-hidden="true">&#10003;</span>
            <span id="successMsg"></span>
        </div>

        <!-- LOGIN FORM -->
        <div class="form-box" id="loginForm">
            <div class="field-group">
                <label>Username</label>
                <div class="field-input">
                    <span class="input-icon">&#128100;</span>
                    <input type="text" id="loginUsername" placeholder="Enter your username" autocomplete="username" />
                </div>
            </div>
            <div class="field-group">
                <label>Password</label>
                <div class="field-input">
                    <span class="input-icon">&#128273;</span>
                    <input type="password" id="loginPassword" placeholder="Enter your password" autocomplete="current-password" />
                    <button type="button" class="pw-toggle-btn" id="loginPwToggle" aria-pressed="false" aria-label="Show password" title="Show password">
                        <svg class="pw-eye-open" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        <svg class="pw-eye-shut" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                    </button>
                </div>
            </div>
            <div class="field-group">
                <label>Select Role to Enter</label>
                <div class="role-selector" id="loginRoleSelector">
                    <label class="role-option" id="role-ta">
                        <input type="radio" name="loginRole" value="TA" />
                        <div class="role-option-icon">&#127891;</div>
                        <div class="role-option-name">TA</div>
                        <div class="role-option-desc">Teaching Asst.</div>
                    </label>
                    <label class="role-option" id="role-mo">
                        <input type="radio" name="loginRole" value="MO" />
                        <div class="role-option-icon">&#127979;</div>
                        <div class="role-option-name">MO</div>
                        <div class="role-option-desc">Module Org.</div>
                    </label>
                    <label class="role-option" id="role-admin">
                        <input type="radio" name="loginRole" value="ADMIN" />
                        <div class="role-option-icon">&#9881;</div>
                        <div class="role-option-name">Admin</div>
                        <div class="role-option-desc">Administrator</div>
                    </label>
                </div>
            </div>
            <button class="submit-btn" id="loginBtn" onclick="doLogin()">
                <span class="btn-text">Sign In</span>
                <div class="spinner"></div>
            </button>
        </div>

        <!-- REGISTER FORM -->
        <div class="form-box" id="registerForm" style="display:none">
            <div class="field-group">
                <label>Full Name</label>
                <div class="field-input">
                    <span class="input-icon">&#128100;</span>
                    <input type="text" id="regDisplayName" placeholder="e.g. Wang Hao" />
                </div>
            </div>
            <div class="field-group">
                <label>Username</label>
                <div class="field-input">
                    <span class="input-icon">&#128196;</span>
                    <input type="text" id="regUsername" placeholder="At least 3 characters" autocomplete="username" />
                </div>
            </div>
            <div class="field-group">
                <label>Email</label>
                <div class="field-input">
                    <span class="input-icon">&#9993;</span>
                    <input type="email" id="regEmail" placeholder="yourname@bupt.edu.cn" />
                </div>
            </div>
            <div class="field-group">
                <label>Password</label>
                <div class="field-input">
                    <span class="input-icon">&#128273;</span>
                    <input type="password" id="regPassword" placeholder="8+ chars, letters &amp; numbers" autocomplete="new-password" />
                    <button type="button" class="pw-toggle-btn" id="regPwToggle" aria-pressed="false" aria-label="Show password" title="Show password">
                        <svg class="pw-eye-open" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        <svg class="pw-eye-shut" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                    </button>
                </div>
            </div>
            <div class="field-group">
                <label>Your Roles (select one or more)</label>
                <div class="role-selector" id="regRoleSelector">
                    <label class="role-option" id="reg-role-ta">
                        <input type="checkbox" name="regRole" value="TA" />
                        <div class="role-option-icon">&#127891;</div>
                        <div class="role-option-name">TA</div>
                        <div class="role-option-desc">Teaching Asst.</div>
                    </label>
                    <label class="role-option" id="reg-role-mo">
                        <input type="checkbox" name="regRole" value="MO" />
                        <div class="role-option-icon">&#127979;</div>
                        <div class="role-option-name">MO</div>
                        <div class="role-option-desc">Module Org.</div>
                    </label>
                    <label class="role-option" id="reg-role-admin">
                        <input type="checkbox" name="regRole" value="ADMIN" />
                        <div class="role-option-icon">&#9881;</div>
                        <div class="role-option-name">Admin</div>
                        <div class="role-option-desc">Administrator</div>
                    </label>
                </div>
            </div>
            <button class="submit-btn" id="registerBtn" onclick="doRegister()">
                <span class="btn-text">Create Account</span>
                <div class="spinner"></div>
            </button>
        </div>

        <div class="form-footer">
            <span id="footerText">Don't have an account? <a onclick="showRegister()">Register here</a></span>
        </div>
    </div>
</div>

<script>
var APP_CTX = "<%= request.getContextPath() %>";
(async function() {
    try {
        let res = await fetch(APP_CTX + "/api?action=config");
        if (res.ok) {
            let cfg = await res.json();
            if (cfg.appVersion) {
                let vb = document.getElementById("versionBadge");
                if (vb) vb.textContent = "v" + cfg.appVersion;
                let bv = document.getElementById("brandVersion");
                if (bv) bv.textContent = "Smart-TA v" + cfg.appVersion;
            }
        }
    } catch(e) {
        console.warn("[SmartTA] Failed to load system config:", e);
    }
})();

// ---- Form toggle ----
/** @param opts {{keepSuccess?:boolean}} Keep the green success bar when switching to login page after successful registration */
function showLogin(opts) {
    var keepSuccess = opts && opts.keepSuccess;
    document.getElementById("loginForm").style.display = "flex";
    document.getElementById("registerForm").style.display = "none";
    document.getElementById("btn-login").className = "form-toggle-btn active";
    document.getElementById("btn-register").className = "form-toggle-btn";
    document.getElementById("footerText").innerHTML = 'Don\'t have an account? <a onclick="showRegister()\">Register here</a>';
    hideError();
    if (!keepSuccess) hideSuccess();
}
function showRegister() {
    document.getElementById("loginForm").style.display = "none";
    document.getElementById("registerForm").style.display = "flex";
    document.getElementById("btn-login").className = "form-toggle-btn";
    document.getElementById("btn-register").className = "form-toggle-btn active";
    document.getElementById("footerText").innerHTML = 'Already have an account? <a onclick="showLogin()\">Sign in</a>';
    hideError();
    hideSuccess();
}

// Auto-select role from query param
(function() {
    let params = new URLSearchParams(window.location.search);
    let targetRole = params.get("targetRole");
    if (targetRole) {
        showLogin();
        let roleMap = { "TA": "role-ta", "MO": "role-mo", "ADMIN": "role-admin" };
        let roleEl = document.getElementById(roleMap[targetRole]);
        if (roleEl) {
            document.querySelectorAll("#loginRoleSelector .role-option").forEach(e => e.classList.remove("selected"));
            roleEl.classList.add("selected");
            roleEl.querySelector("input").checked = true;
        }
    }
})();

// ---- Role selector toggle (whole card clickable) ----
document.querySelectorAll("#loginRoleSelector .role-option").forEach(el => {
    el.addEventListener("click", (e) => {
        e.preventDefault();
        document.querySelectorAll("#loginRoleSelector .role-option").forEach(e => e.classList.remove("selected"));
        el.classList.add("selected");
        el.querySelector("input").checked = true;
    });
});
document.querySelectorAll("#regRoleSelector .role-option").forEach(el => {
    el.addEventListener("click", (e) => {
        e.preventDefault();
        el.classList.toggle("selected");
        el.querySelector("input").checked = el.classList.contains("selected");
    });
});

// ---- Error / Success banners ----
function showError(msg) {
    hideSuccess();
    document.getElementById("errorMsg").textContent = msg;
    document.getElementById("errorBox").classList.add("show");
}
function hideError() {
    document.getElementById("errorBox").classList.remove("show");
}
function showSuccess(msg) {
    hideError();
    document.getElementById("successMsg").textContent = msg;
    document.getElementById("successBox").classList.add("show");
}
function hideSuccess() {
    document.getElementById("successBox").classList.remove("show");
}

// ---- Login ----
async function doLogin() {
    hideError();
    hideSuccess();
    let username = document.getElementById("loginUsername").value.trim();
    let password = document.getElementById("loginPassword").value;
    let roleEl = document.querySelector("#loginRoleSelector .role-option.selected");
    let role = roleEl ? roleEl.querySelector("input").value : null;

    if (!username) { showError("Please enter your username"); return; }
    if (!password) { showError("Please enter your password"); return; }
    if (!role) { showError("Please select a role to enter"); return; }

    let btn = document.getElementById("loginBtn");
    btn.classList.add("loading");
    btn.disabled = true;

    try {
        let params = new URLSearchParams();
        params.append("username", username);
        params.append("password", password);
        params.append("role", role);
        let res = await fetch(APP_CTX + "/auth/login", { method: "POST", body: params, headers: { "Content-Type": "application/x-www-form-urlencoded" } });
        let text = await res.text();
        let json;
        try {
            json = JSON.parse(text);
        } catch(e) {
            showError("Server error: " + text.substring(0, 200));
            return;
        }

        if (json.success) {
            // Save session data for subsequent pages
            sessionStorage.setItem("csrfToken", json.csrfToken || "");
            // Redirect to the appropriate dashboard based on role
            let redirectUrl = "ta.jsp";
            if (role === "MO") redirectUrl = "mo.jsp";
            else if (role === "ADMIN") redirectUrl = "admin.jsp";
            window.location.href = redirectUrl;
        } else {
            showError(json.error || json.message || "Login failed");
        }
    } catch(e) {
        showError("Connection error: " + e.message);
    } finally {
        btn.classList.remove("loading");
        btn.disabled = false;
    }
}

// ---- Register ----
async function doRegister() {
    hideError();
    hideSuccess();
    let displayName = document.getElementById("regDisplayName").value.trim();
    let username = document.getElementById("regUsername").value.trim();
    let email = document.getElementById("regEmail").value.trim();
    let password = document.getElementById("regPassword").value;
    let roleEls = document.querySelectorAll("#regRoleSelector .role-option.selected");
    let roles = Array.from(roleEls).map(el => el.querySelector("input").value);

    if (!displayName) { showError("Please enter your full name"); return; }
    if (!username) { showError("Please enter a username"); return; }
    if (username.length < 3) { showError("Username must be at least 3 characters"); return; }
    if (!password) { showError("Please enter a password"); return; }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(password)) {
        showError("Password: at least 8 characters, with both letters and numbers");
        return;
    }
    if (!email) { showError("Please enter your email"); return; }
    if (roles.length === 0) { showError("Please select at least one role"); return; }

    let btn = document.getElementById("registerBtn");
    btn.classList.add("loading");
    btn.disabled = true;

    try {
        let params = new URLSearchParams();
        params.append("displayName", displayName);
        params.append("username", username);
        params.append("email", email);
        params.append("password", password);
        params.append("roles", roles.join(","));
        let res = await fetch(APP_CTX + "/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: params,
            credentials: "same-origin"
        });
        let text = await res.text();
        let json;
        try {
            json = JSON.parse(text);
        } catch(e) {
            showError("Server error: " + text.substring(0, 200));
            return;
        }

        if (json.success) {
            sessionStorage.setItem("csrfToken", json.csrfToken || "");
            showSuccess("Account created successfully. You can sign in below with your username.");
            document.getElementById("loginUsername").value = username;
            document.getElementById("loginPassword").value = "";
            showLogin({ keepSuccess: true });
            document.getElementById("loginPassword").focus();
        } else {
            showError(json.error || json.message || "Registration failed");
        }
    } catch(e) {
        showError("Connection error: " + e.message);
    } finally {
        btn.classList.remove("loading");
        btn.disabled = false;
    }
}

// Enter key on login
document.getElementById("loginPassword").addEventListener("keydown", e => {
    if (e.key === "Enter") doLogin();
});
document.getElementById("loginUsername").addEventListener("keydown", e => {
    if (e.key === "Enter") doLogin();
});

function wirePasswordToggle(inputId, btnId) {
    let input = document.getElementById(inputId);
    let btn = document.getElementById(btnId);
    if (!input || !btn) return;
    btn.addEventListener("click", function(e) {
        e.preventDefault();
        let showPlain = input.type === "password";
        input.type = showPlain ? "text" : "password";
        btn.classList.toggle("is-revealed", showPlain);
        btn.setAttribute("aria-pressed", showPlain ? "true" : "false");
        let label = showPlain ? "Hide password" : "Show password";
        btn.setAttribute("aria-label", label);
        btn.title = label;
    });
}
wirePasswordToggle("loginPassword", "loginPwToggle");
wirePasswordToggle("regPassword", "regPwToggle");

// ---- Toast ----
var toastContainer = document.createElement("div");
toastContainer.id = "toastContainer";
toastContainer.style.cssText = "position:fixed;bottom:24px;right:24px;z-index:9999;display:flex;flex-direction:column;gap:8px;";
document.body.appendChild(toastContainer);

function showToast(msg, type) {
    type = type || "success";
    var icons = { success:"&#9989;", error:"&#10060;", warn:"&#9888;&#65039;", info:"&#128712;" };
    var toast = document.createElement("div");
    toast.style.cssText = "display:flex;align-items:center;gap:10px;padding:12px 18px;border-radius:8px;font-size:0.85rem;font-weight:500;box-shadow:0 4px 20px rgba(0,0,0,0.15);animation:slideIn 0.25s ease;max-width:360px;background:#fff;";
    toast.style.borderLeft = "4px solid " + ({"success":"#2a9d8f","error":"#e63946","warn":"#e9c46a","info":"#457b9d"})[type];
    toast.innerHTML = '<span>' + icons[type] + '</span><span>' + msg + '</span><button onclick="this.parentElement.remove()" style="background:none;border:none;cursor:pointer;font-size:1.1rem;margin-left:auto;color:#8d99ae;">&times;</button>';
    toastContainer.appendChild(toast);
    setTimeout(function() { if(toast.parentElement) toast.remove(); }, 4000);
}
</script>

</body>
</html>
