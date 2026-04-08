<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true" session="true" %>
<%
    String adminCurrentRole = (String) session.getAttribute("currentRole");
    if (adminCurrentRole == null) {
        response.sendRedirect("index.jsp");
        return;
    }
    if (!"ADMIN".equals(adminCurrentRole)) {
        if ("TA".equals(adminCurrentRole)) {
            response.sendRedirect("ta.jsp");
        } else if ("MO".equals(adminCurrentRole)) {
            response.sendRedirect("mo.jsp");
        } else {
            response.sendRedirect("index.jsp");
        }
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Admin · Smart-TA</title>
<link href="https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,300;9..40,500;9..40,700&family=Playfair+Display:wght@600;700&display=swap" rel="stylesheet" />
<style>
:root {
    --ink:#1a1a2e; --surface:#f8f7f4; --card:#ffffff;
    --accent:#e63946; --accent-soft:#fde8ea;
    --primary:#457b9d; --primary-dark:#1d3557; --primary-soft:#e8f0f6;
    --success:#2a9d8f; --success-soft:#e6f5f3;
    --warn:#e9c46a; --warn-soft:#fdf6e3; --muted:#8d99ae; --border:#e0ddd8;
    --radius:14px; --radius-sm:8px;
    --shadow-sm:0 1px 3px rgba(26,26,46,0.06);
    --shadow-md:0 4px 20px rgba(26,26,46,0.08);
    --font-body:"DM Sans",sans-serif; --font-display:"Playfair Display",serif;
    --transition:0.2s ease;
}
* { margin:0; padding:0; box-sizing:border-box; }
body { font-family:var(--font-body); background:var(--surface); color:var(--ink); line-height:1.6; }
a { color:var(--primary); text-decoration:none; }

.topbar {
    position:sticky; top:0; z-index:100;
    display:flex; align-items:center; justify-content:space-between;
    padding:0 32px; height:64px;
    background:rgba(255,255,255,0.88); backdrop-filter:blur(16px);
    border-bottom:1px solid var(--border);
}
.topbar .logo { font-family:var(--font-display); font-size:1.25rem; font-weight:700; color:var(--primary-dark); letter-spacing:-0.02em; }
.topbar .logo span { color:var(--accent); }
.topbar nav { display:flex; gap:6px; }
.topbar nav a {
    font-size:0.85rem; font-weight:500; padding:8px 18px;
    border-radius:100px; border:1.5px solid transparent;
    cursor:pointer; transition:var(--transition);
    background:transparent; color:var(--muted);
    display:inline-block;
}
.topbar nav a:hover { color:var(--ink); background:rgba(0,0,0,0.03); }
.topbar nav a.active { background:var(--primary-dark); color:#fff; border-color:var(--primary-dark); }
.topbar .back-btn { font-size:0.82rem; color:var(--muted); display:flex; align-items:center; gap:6px; cursor:pointer; }
.topbar .back-btn:hover { color:var(--ink); }

.dashboard { display:grid; grid-template-columns:1fr 340px; gap:24px; padding:28px 32px; max-width:1200px; margin:0 auto; }
.hero-banner {
    grid-column:1/-1; border-radius:var(--radius); padding:28px 32px;
    background:linear-gradient(135deg,#1d3557,#264653);
    color:#fff; margin-bottom:4px;
}
.hero-banner h1 { font-family:var(--font-display); font-size:1.6rem; margin-bottom:4px; }
.hero-banner p { font-size:0.88rem; opacity:0.85; }

.card-section { background:var(--card); border-radius:var(--radius); padding:24px; box-shadow:var(--shadow-sm); margin-bottom:20px; }
.section-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; }
.section-header h2 { font-size:1.1rem; font-weight:700; }
.section-header-actions { display:flex; align-items:center; gap:10px; }
.pill-count { background:var(--primary-soft); color:var(--primary); padding:3px 12px; border-radius:100px; font-size:0.78rem; font-weight:600; }

.ai-inline-badge {
    background:var(--primary-soft); color:var(--primary); padding:4px 12px;
    border-radius:100px; font-size:0.78rem; font-weight:700; font-family:monospace;
}
.ai-badge {
    display:inline-block; background:rgba(255,255,255,0.15);
    padding:3px 10px; border-radius:100px; font-size:0.72rem; font-weight:700;
    margin-bottom:12px; letter-spacing:0.05em;
}

.status-chip { display:inline-block; padding:3px 10px; border-radius:100px; font-size:0.75rem; font-weight:600; }
.status-chip.status-open { background:var(--success-soft); color:var(--success); }
.status-chip.status-closed { background:var(--accent-soft); color:var(--accent); }

.ai-panel {
    background:linear-gradient(135deg,#1d3557,#264653);
    border-radius:var(--radius); padding:22px; color:#fff; margin-bottom:16px;
}
.ai-panel.green { background:linear-gradient(135deg,#264653,#2a9d8f); }
.ai-panel h3 { font-size:1rem; margin-bottom:10px; }
.ai-suggestion { background:rgba(255,255,255,0.1); border-radius:var(--radius-sm); padding:14px; font-size:0.82rem; line-height:1.6; }

.btn {
    display:inline-flex; align-items:center; gap:6px;
    padding:8px 20px; border-radius:var(--radius-sm);
    font-family:var(--font-body); font-size:0.85rem; font-weight:600;
    cursor:pointer; transition:var(--transition); border:1.5px solid transparent;
}
.btn-primary { background:var(--primary); color:#fff; border-color:var(--primary); }
.btn-primary:hover { background:var(--primary-dark); }
.btn-success { background:var(--success); color:#fff; border-color:var(--success); }
.btn-outline { background:transparent; color:#fff; border-color:rgba(255,255,255,0.4); }
.btn-outline:hover { background:rgba(255,255,255,0.1); }
.btn-sm { padding:5px 12px; font-size:0.78rem; }

table { width:100%; border-collapse:collapse; margin-top:12px; }
th { text-align:left; padding:10px 12px; font-size:0.78rem; font-weight:700; color:var(--muted); border-bottom:2px solid var(--border); }
td { padding:12px 12px; border-bottom:1px solid var(--border); font-size:0.85rem; vertical-align:middle; }
tr:hover td { background:#fafaf8; }
tr:last-child td { border-bottom:none; }

.sidebar-card { background:var(--card); border-radius:var(--radius); padding:20px; box-shadow:var(--shadow-sm); margin-bottom:16px; }
.sidebar-card h3 { font-size:0.92rem; font-weight:700; margin-bottom:12px; }

.workload-bar-container { margin-bottom:16px; }
.workload-head { display:flex; justify-content:space-between; align-items:flex-start; gap:12px; margin-bottom:6px; }
.workload-head-main { min-width:0; flex:1; }
.workload-head-main strong { font-size:0.88rem; font-weight:700; display:block; color:var(--ink); }
.workload-who { font-size:0.72rem; color:var(--muted); margin-top:3px; line-height:1.4; }
.workload-hours { font-size:0.82rem; color:var(--muted); white-space:nowrap; font-weight:600; }
.workload-bar { height:12px; background:#eee; border-radius:6px; overflow:hidden; }
.workload-fill { height:100%; border-radius:6px; transition:width 0.5s ease; }
.workload-fill.safe { background:var(--success); }
.workload-fill.warn { background:var(--warn); }
.workload-fill.danger { background:var(--accent); }

.log-terminal {
    background:#1a1a2e; border-radius:var(--radius-sm); padding:16px;
    font-family:"Courier New",monospace; font-size:0.78rem;
    max-height:400px; overflow-y:auto; color:#e0e0e0;
    line-height:1.8;
}
.log-entry { display:flex; gap:12px; align-items:baseline; }
.log-ts { color:#8d99ae; white-space:nowrap; }
.log-op { font-weight:700; width:16px; text-align:center; flex-shrink:0; }
.log-op.read { color:#64b5f6; }
.log-op.write { color:#81c784; }
.log-op.error { color:#e57373; }
.log-file { color:#e0e0e0; flex:1; }
.log-status { color:#8d99ae; flex-shrink:0; }
.log-status.ok { color:#81c784; }
.log-status.fail { color:#e57373; }

.version-item { display:flex; gap:14px; padding:14px 0; border-bottom:1px solid var(--border); }
.version-item:last-child { border-bottom:none; }
.version-badge {
    flex-shrink:0; background:var(--primary-soft); color:var(--primary);
    padding:4px 10px; border-radius:6px; font-size:0.75rem; font-weight:700;
}
.version-item .version-date { font-size:0.78rem; color:var(--muted); margin-bottom:4px; }
.version-item .version-desc { font-size:0.85rem; }

.badge { display:inline-block; padding:3px 10px; border-radius:100px; font-size:0.72rem; font-weight:600; }
.badge-blue { background:#e3f2fd; color:#1565c0; }
.badge-green { background:var(--success-soft); color:var(--success); }
.badge-red { background:var(--accent-soft); color:var(--accent); }
.badge-yellow { background:var(--warn-soft); color:#7d6000; }

.file-status-item { display:flex; justify-content:space-between; align-items:center; padding:10px 0; border-bottom:1px solid var(--border); }
.file-status-item:last-child { border-bottom:none; }
.file-status-item code { font-family:monospace; font-size:0.82rem; }
.file-status-item .status-ok { color:var(--success); font-weight:700; }
.file-status-item .status-error { color:var(--accent); font-weight:700; }

.dir-user-row {
    display:flex; align-items:flex-start; justify-content:space-between; gap:12px;
    padding:12px 0; border-bottom:1px solid var(--border); cursor:pointer;
}
.dir-user-row:last-child { border-bottom:none; }
.dir-user-row:hover { background:#fafaf8; margin:0 -8px; padding-left:8px; padding-right:8px; border-radius:8px; }
.dir-user-meta { flex:1; min-width:0; }
.dir-user-name { font-weight:700; font-size:0.9rem; }
.dir-user-sub { font-size:0.78rem; color:var(--muted); margin-top:2px; }
.dir-role-pill { display:inline-block; font-size:0.68rem; font-weight:700; padding:2px 8px; border-radius:100px; margin-right:4px; margin-top:4px; }
.dir-role-mo { background:#e3f2fd; color:#1565c0; }
.dir-role-ta { background:var(--success-soft); color:var(--success); }
.dir-role-admin { background:var(--accent-soft); color:var(--accent); }
.dir-actions { display:flex; flex-wrap:wrap; gap:6px; flex-shrink:0; }
.dir-actions button { font-size:0.72rem; padding:4px 10px; border-radius:6px; border:1.5px solid var(--border); background:#fff; cursor:pointer; font-family:var(--font-body); font-weight:600; }
.dir-actions button:hover { border-color:var(--primary); color:var(--primary); }
.dir-actions button.danger:hover { border-color:var(--accent); color:var(--accent); }

.modal-overlay { display:none; position:fixed; inset:0; background:rgba(26,26,46,0.45); z-index:500; align-items:center; justify-content:center; padding:20px; }
.modal-overlay.show { display:flex; }
.modal-box { background:#fff; border-radius:var(--radius); max-width:520px; width:100%; max-height:90vh; overflow:auto; box-shadow:var(--shadow-md); padding:22px; }
.modal-box h3 { font-size:1.05rem; margin-bottom:12px; }
.modal-box label { display:block; font-size:0.72rem; font-weight:700; color:var(--muted); margin:10px 0 4px; }
.modal-box input, .modal-box select, .modal-box textarea { width:100%; padding:8px 10px; border-radius:8px; border:1.5px solid var(--border); font-family:var(--font-body); font-size:0.85rem; }
.modal-box textarea { min-height:72px; resize:vertical; }
.modal-footer { display:flex; gap:8px; margin-top:18px; justify-content:flex-end; }

.version-tag { background:var(--success-soft); color:var(--success); padding:3px 10px; border-radius:100px; font-size:0.72rem; font-weight:700; }

#toastContainer { position:fixed; bottom:24px; right:24px; z-index:9999; display:flex; flex-direction:column; gap:8px; }
.toast { display:flex; align-items:center; gap:10px; padding:12px 18px; border-radius:var(--radius-sm); font-size:0.85rem; font-weight:500; box-shadow:0 4px 20px rgba(0,0,0,0.15); animation:slideIn 0.25s ease; max-width:360px; }
.toast-success { background:#fff; border-left:4px solid var(--success); color:var(--ink); }
.toast-error { background:#fff; border-left:4px solid var(--accent); color:var(--ink); }
.toast-warn { background:#fff; border-left:4px solid var(--warn); color:var(--ink); }
.toast-info { background:#fff; border-left:4px solid var(--primary); color:var(--ink); }
.toast-close { background:none; border:none; cursor:pointer; font-size:1.1rem; margin-left:auto; color:var(--muted); }
@keyframes slideIn { from { transform:translateX(100%); opacity:0; } to { transform:translateX(0); opacity:1; } }
</style>
</head>
<body>

<div class="topbar">
    <a href="#" class="back-btn" onclick="event.preventDefault();doAdminLogout();" title="Sign out">&#8592; Smart-TA</a>
    <div style="display:flex;align-items:center;gap:10px;">
        <div style="display:flex;align-items:center;gap:8px;font-size:0.82rem;color:var(--muted);">
            <span>&#128100;</span>
            <span id="adminUserName" style="font-weight:600;color:var(--ink)"></span>
            <div class="role-switcher" style="position:relative;display:inline-block;">
                <button type="button" onclick="toggleAdminRoleMenu()" style="background:var(--primary-soft);border:1.5px solid var(--primary);color:var(--primary);padding:4px 10px;border-radius:100px;font-size:0.75rem;font-weight:700;cursor:pointer;font-family:var(--font-body);">
                    <span id="adminCurrentRoleLabel">ADMIN</span> &#9660;
                </button>
                <div id="adminRoleMenu" style="display:none;position:absolute;top:calc(100% + 6px);right:0;background:#fff;border:1.5px solid var(--border);border-radius:var(--radius-sm);box-shadow:var(--shadow-md);min-width:220px;z-index:200;overflow:hidden;"></div>
            </div>
        </div>
        <span class="version-tag">v2.0</span>
    </div>
</div>

<div class="dashboard">
    <div class="hero-banner">
        <h1>Administrator Overview</h1>
        <p>Monitor TA workload, review recruitment progress, and audit system operations.</p>
    </div>

    <div>
        <div id="content-workload">
            <div class="card-section">
                <div class="section-header">
                    <h2>TA Workload Distribution</h2>
                    <div class="section-header-actions">
                        <span class="ai-inline-badge">Live</span>
                        <span class="pill-count" id="overloadCount">0 Overloaded</span>
                    </div>
                </div>
                <div id="workloadBars"></div>
            </div>
        </div>

        <div id="content-recruit">
            <div class="card-section">
                <div class="section-header">
                    <h2>Recruitment Summary by Module</h2>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>Module</th>
                            <th>Total Applicants</th>
                            <th>Accepted</th>
                            <th>Remaining Slots</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody id="recruitmentTableBody">
                        <tr><td colspan="5" style="text-align:center;color:var(--muted);padding:30px">Loading...</td></tr>
                    </tbody>
                </table>
            </div>
            <div class="card-section">
                <div class="section-header">
                    <h2>MO &amp; TA Directory</h2>
                    <div class="section-header-actions">
                        <span class="ai-inline-badge">MO / TA</span>
                        <button type="button" class="btn btn-primary btn-sm" onclick="openUserModal(null)">+ Add user</button>
                    </div>
                </div>
                <p style="font-size:0.78rem;color:var(--muted);margin-bottom:12px;">Only <strong>MO</strong> and <strong>TA</strong> accounts are listed (administrators excluded). Click a row to view the profile; you can maintain accounts and linked TA applicant records.</p>
                <div id="directoryUserList">
                    <div style="font-size:0.78rem;color:var(--muted);padding:12px 0;">Loading directory…</div>
                </div>
            </div>
        </div>

        <div id="content-logs">
            <div class="card-section">
                <div class="section-header">
                    <h2>System Activity Log</h2>
                    <div class="section-header-actions">
                        <span class="ai-inline-badge" id="logCount">v2.0</span>
                    </div>
                </div>
                <div class="log-terminal" id="logTerminal">
                    <div style="color:#8d99ae;text-align:center;padding:20px">Loading system logs...</div>
                </div>
            </div>
            <div class="card-section">
                <div class="section-header">
                    <h2>Version History</h2>
                </div>
                <div id="versionHistoryList">
                    <!-- Dynamically rendered from /api?action=config -->
                    <div style="font-size:0.78rem;color:var(--muted);padding:8px 0;">Loading...</div>
                </div>
            </div>
        </div>
    </div>

    <div>
        <div class="ai-panel green" id="aiBalancerPanel">
            <div class="ai-badge">AI WORKLOAD BALANCER</div>
            <h3>Rebalancing Recommendation</h3>
            <div id="aiSuggestion" class="ai-suggestion">Loading...</div>
            <div style="display:flex;gap:8px;margin-top:14px">
                <button class="btn btn-success" style="flex:1" onclick="applySuggestion()">Apply Suggestion</button>
                <button class="btn btn-outline" style="flex:1" onclick="dismissSuggestion()">Dismiss</button>
            </div>
        </div>
        <div class="sidebar-card">
            <h3>System Overview</h3>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:8px">
                <div style="text-align:center;padding:10px;background:var(--primary-soft);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700;color:var(--primary)" id="statTotalApps">0</div>
                    <div style="font-size:0.72rem;color:var(--muted)">Total Apps</div>
                </div>
                <div style="text-align:center;padding:10px;background:var(--success-soft);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700;color:var(--success)" id="statAccepted">0</div>
                    <div style="font-size:0.72rem;color:var(--muted)">Accepted</div>
                </div>
                <div style="text-align:center;padding:10px;background:#fff8e1;border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700;color:#f57f17" id="statPending">0</div>
                    <div style="font-size:0.72rem;color:var(--muted)">Pending</div>
                </div>
                <div style="text-align:center;padding:10px;background:var(--accent-soft);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700;color:var(--accent)" id="statOverload">0</div>
                    <div style="font-size:0.72rem;color:var(--muted)">Overloaded</div>
                </div>
            </div>
        </div>
        <div class="sidebar-card">
            <h3>Mid-Term Demo Coverage</h3>
            <div id="featureCoverageList" style="font-size:0.78rem;line-height:2;color:var(--muted)">
                <!-- Dynamically rendered from /api?action=config -->
            </div>
        </div>
    </div>
</div>

<div id="toastContainer"></div>

<div id="adminModalOverlay" class="modal-overlay" onclick="if(event.target===this)closeAdminModal()">
    <div class="modal-box" onclick="event.stopPropagation()">
        <h3 id="adminModalTitle">User</h3>
        <div id="adminModalBody"></div>
        <div class="modal-footer" id="adminModalFooter"></div>
    </div>
</div>

<script>
let adminSession = {};
let adminCsrfToken = null;

(async function init() {
    await adminCheckSession();
    if (!adminSession.username) return;
    await loadAll();
})();

async function adminCheckSession() {
    try {
        let res = await fetch("auth/session");
        let json = await res.json();
        if (!json.authenticated) { window.location.href = "index.jsp"; return; }
        adminSession = { username: json.username, displayName: json.displayName, currentRole: json.currentRole, roles: json.roles || [] };
        adminCsrfToken = json.csrfToken || sessionStorage.getItem("csrfToken") || "";
        sessionStorage.setItem("csrfToken", adminCsrfToken);
        renderAdminUserInfo();
        renderAdminRoleSwitcher();
    } catch(e) { window.location.href = "index.jsp"; }
}

function renderAdminUserInfo() {
    let el = document.getElementById("adminUserName");
    if (el) el.textContent = adminSession.displayName || "";
    el = document.getElementById("adminCurrentRoleLabel");
    if (el) el.textContent = adminSession.currentRole || "";
}

function renderAdminRoleSwitcher() {
    let menu = document.getElementById("adminRoleMenu");
    let roles = adminSession.roles || [];
    let current = adminSession.currentRole;
    let roleIcons = { TA: "&#127891;", MO: "&#127979;", ADMIN: "&#9881;" };
    let roleNames = { TA: "Teaching Assistant", MO: "Module Organiser", ADMIN: "Administrator" };
    let html = '<div style="padding:8px 12px;font-size:0.72rem;font-weight:700;color:var(--muted);text-transform:uppercase;letter-spacing:0.05em;">Switch Role</div>';
    roles.forEach(r => {
        let isActive = r === current;
        html += `<div onclick="adminSwitchRole('${r}')" style="padding:10px 14px;cursor:pointer;display:flex;align-items:center;gap:8px;font-size:0.85rem;font-weight:600;${isActive ? 'background:var(--primary-soft);color:var(--primary);' : 'color:var(--ink);'}" onmouseover="this.style.background='${isActive ? 'var(--primary-soft)' : 'var(--surface)'}'" onmouseout="this.style.background='${isActive ? 'var(--primary-soft)' : 'transparent'}'">
            <span style="font-size:1rem">${roleIcons[r] || '&#128100;'}</span>
            <div><div style="font-weight:700">${r}</div><div style="font-size:0.68rem;color:var(--muted)">${roleNames[r]||''}</div></div>
            ${isActive ? '<span style="margin-left:auto;color:var(--primary)">&#10003;</span>' : ''}
        </div>`;
    });
    if (menu) menu.innerHTML = html;
}

function toggleAdminRoleMenu() {
    let menu = document.getElementById("adminRoleMenu");
    menu.style.display = menu.style.display === "block" ? "none" : "block";
    event.stopPropagation();
}
document.addEventListener("click", () => { var m = document.getElementById("adminRoleMenu"); if(m) m.style.display="none"; });

async function adminSwitchRole(role) {
    if (role === adminSession.currentRole) return;
    let btn = document.querySelector("[onclick='toggleAdminRoleMenu()']");
    if (btn) btn.disabled = true;
    try {
        let csrf = adminCsrfToken || sessionStorage.getItem("csrfToken") || "";
        let res = await fetch("auth/switchRole", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: new URLSearchParams({ role: role, _csrf: csrf }),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (!json.success) {
            showToast(json.error || "Cannot switch role. Please try again.", "error");
            return;
        }
        let dest = role === "TA" ? "ta.jsp" : role === "MO" ? "mo.jsp" : role === "ADMIN" ? "admin.jsp" : "index.jsp";
        window.location.href = dest;
    } catch (e) {
        showToast("Could not switch role. Please try again.", "error");
    } finally {
        if (btn) btn.disabled = false;
    }
}

async function doAdminLogout() {
    try { await fetch("auth/logout", { method: "POST" }); } catch(e) {}
    sessionStorage.removeItem("csrfToken");
    window.location.href = "index.jsp";
}

let CAPACITY = 20;
let OVERLOAD_THRESHOLD = 20;

// System config (loaded dynamically from backend)
let systemConfig = null;

// Load system config from backend
async function loadSystemConfig() {
    try {
        let res = await fetch("api?action=config");
        if (!res.ok) {
            console.warn("[SmartTA] config request failed:", res.status);
            return;
        }
        let text = await res.text();
        try {
            systemConfig = JSON.parse(text);
        } catch (pe) {
            console.warn("[SmartTA] Invalid config JSON", pe);
            return;
        }
        if (systemConfig) {
            // Apply workload constants from config
            if (systemConfig.workloadConfig) {
                CAPACITY = systemConfig.workloadConfig.capacity || 20;
                OVERLOAD_THRESHOLD = systemConfig.workloadConfig.overloadThreshold || 20;
            }
            // Render dynamic sections
            renderFileStatus();
            renderVersionHistory();
            renderFeatureCoverage();
            // Update version badge in log count
            let vb = document.getElementById("logCount");
            if (vb && systemConfig.appVersion) {
                vb.textContent = "v" + systemConfig.appVersion;
            }
            loadWorkload();
        }
    } catch(e) {
        console.warn("[SmartTA] Failed to load system config:", e);
    }
}

// Render file status list from backend config
function renderFileStatus() {
    let el = document.getElementById("fileStatusList");
    if (!el || !systemConfig || !systemConfig.fileStatusConfig) return;
    el.innerHTML = systemConfig.fileStatusConfig.map(function(f) {
        return '<div class="file-status-item"><code>' + f.filename + '</code><span class="status-ok">&#10003; OK</span></div>';
    }).join("");
}

// Render version history from backend config
function renderVersionHistory() {
    let el = document.getElementById("versionHistoryList");
    if (!el || !systemConfig || !systemConfig.versionHistory) return;
    el.innerHTML = systemConfig.versionHistory.map(function(v) {
        return '<div class="version-item"><span class="version-badge">' + v.version + '</span>' +
            '<div><div class="version-date">' + v.date + ' \u2014 ' + v.title + '</div>' +
            '<div class="version-desc">' + v.description + '</div></div></div>';
    }).join("");
}

// Render feature coverage from backend config
function renderFeatureCoverage() {
    let el = document.getElementById("featureCoverageList");
    if (!el || !systemConfig || !systemConfig.featureCoverage) return;
    el.innerHTML = systemConfig.featureCoverage.map(function(f) {
        return '<div>' + f.icon + ' ' + f.text + '</div>';
    }).join("");
}

async function loadAll() {
    await Promise.all([loadWorkload(), loadRecruitment(), loadLogs(), loadSystemConfig(), loadDirectoryUsers()]);
}

let directoryUsers = [];

async function loadDirectoryUsers() {
    let el = document.getElementById("directoryUserList");
    try {
        let res = await fetch("api?action=users", { credentials: "same-origin" });
        if (!res.ok) {
            if (el) el.innerHTML = '<div style="color:var(--accent);font-size:0.85rem">Could not load directory (admin session required).</div>';
            return;
        }
        let json = await res.json();
        directoryUsers = json.users || [];
        renderDirectoryUserList();
    } catch (e) {
        if (el) el.innerHTML = '<div style="color:var(--accent);font-size:0.85rem">Failed to load directory.</div>';
    }
}

function renderDirectoryUserList() {
    let el = document.getElementById("directoryUserList");
    if (!el) return;
    if (!directoryUsers.length) {
        el.innerHTML = '<div style="color:var(--muted);font-size:0.85rem">No user accounts.</div>';
        return;
    }
    let html = "";
    directoryUsers.forEach(function(u) {
        let roles = (u.roles || []).filter(function(r) { return r !== "ADMIN"; });
        let pills = roles.map(function(r) {
            let c = r === "MO" ? "dir-role-mo" : "dir-role-ta";
            return '<span class="dir-role-pill ' + c + '">' + r + '</span>';
        }).join("");
        let sub = (u.email || "") + (u.applicantId ? " · Applicant " + u.applicantId : "");
        html += '<div class="dir-user-row" onclick="showUserProfileModal(\'' + escAttr(u.username) + '\')">' +
            '<div class="dir-user-meta"><div class="dir-user-name">' + escHtml(u.displayName || u.username) + '</div>' +
            '<div class="dir-user-sub">' + escHtml(sub) + '</div><div>' + pills + '</div></div>' +
            '<div class="dir-actions" onclick="event.stopPropagation()">' +
            '<button type="button" onclick="openUserModal(\'' + escAttr(u.username) + '\')">Edit</button>' +
            '<button type="button" class="danger" onclick="deleteUserConfirm(\'' + escAttr(u.username) + '\')">Delete</button>' +
            '</div></div>';
    });
    el.innerHTML = html;
}

function escHtml(s) {
    if (!s) return "";
    return String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");
}
function escAttr(s) {
    if (!s) return "";
    return String(s).replace(/\\/g,"\\\\").replace(/'/g,"\\'");
}

function closeAdminModal() {
    document.getElementById("adminModalOverlay").classList.remove("show");
}

function showUserProfileModal(username) {
    let u = directoryUsers.find(function(x) { return x.username === username; });
    if (!u) return;
    document.getElementById("adminModalTitle").textContent = "Profile · " + (u.displayName || u.username);
    let body = "<p style=\"font-size:0.85rem;margin-bottom:10px\"><strong>Username:</strong> " + escHtml(u.username) + "</p>";
    body += "<p style=\"font-size:0.85rem;margin-bottom:10px\"><strong>Email:</strong> " + escHtml(u.email || "") + "</p>";
    body += "<p style=\"font-size:0.85rem;margin-bottom:10px\"><strong>Roles:</strong> " + escHtml((u.roles || []).filter(function(r) { return r !== "ADMIN"; }).join(", ") || "—") + "</p>";
    body += "<p style=\"font-size:0.85rem;margin-bottom:10px\"><strong>Applicant ID:</strong> " + escHtml(u.applicantId || "(none)") + "</p>";
    if (u.applicantProfile) {
        let p = u.applicantProfile;
        body += "<hr style=\"border:none;border-top:1px solid var(--border);margin:14px 0\"/>";
        body += "<p style=\"font-weight:700;font-size:0.88rem;margin-bottom:8px\">TA applicant record</p>";
        body += "<p style=\"font-size:0.82rem;color:var(--muted)\">" + escHtml(p.yearOfStudy || "") + " · GPA " + p.gpa + " · " + (p.hoursAvailable != null ? p.hoursAvailable + "h avail" : "") + "</p>";
        body += "<p style=\"font-size:0.82rem;margin-top:6px\">Skills: " + escHtml((p.skills || []).join(", ")) + "</p>";
        if (p.createdAt) body += "<p style=\"font-size:0.75rem;color:var(--muted);margin-top:8px\">Record created: " + escHtml(p.createdAt) + "</p>";
        body += "<div style=\"margin-top:12px\">" +
            "<button type=\"button\" class=\"btn btn-primary btn-sm\" onclick=\"closeAdminModal();openApplicantEditModal('" + escAttr(p.id) + "')\">Edit applicant profile</button></div>";
    } else if ((u.roles || []).indexOf("TA") >= 0) {
        body += "<p style=\"font-size:0.78rem;color:var(--muted);margin-top:10px\">No linked applicant profile. Set Applicant ID when editing the user.</p>";
    }
    document.getElementById("adminModalBody").innerHTML = body;
    document.getElementById("adminModalFooter").innerHTML = '<button type="button" class="btn btn-outline" style="color:var(--ink);border-color:var(--border)" onclick="closeAdminModal()">Close</button>' +
        '<button type="button" class="btn btn-primary btn-sm" onclick="closeAdminModal();openUserModal(\'' + escAttr(u.username) + '\')">Edit account</button>';
    document.getElementById("adminModalOverlay").classList.add("show");
}

function openUserModal(username) {
    let u = username ? directoryUsers.find(function(x) { return x.username === username; }) : null;
    document.getElementById("adminModalTitle").textContent = u ? "Edit user · " + u.username : "Create user";
    let rolesVal = u ? (u.roles || []).join(",") : "TA";
    let form = "";
    if (!u) {
        form += "<label>Username</label><input id=\"admUserUsername\" type=\"text\" autocomplete=\"off\"/>";
        form += "<label>Password</label><input id=\"admUserPassword\" type=\"password\" autocomplete=\"new-password\"/>";
    } else {
        form += "<label>Username (read-only)</label><input id=\"admUserUsername\" type=\"text\" value=\"" + escHtml(u.username) + "\" readonly/>";
        form += "<label>New password (optional)</label><input id=\"admUserPassword\" type=\"password\" autocomplete=\"new-password\" placeholder=\"Leave blank to keep\"/>";
    }
    form += "<label>Display name</label><input id=\"admUserDisplay\" type=\"text\" value=\"" + (u ? escHtml(u.displayName || "") : "") + "\"/>";
    form += "<label>Email</label><input id=\"admUserEmail\" type=\"email\" value=\"" + (u ? escHtml(u.email || "") : "") + "\"/>";
    form += "<label>Roles (comma: TA, MO, ADMIN)</label><input id=\"admUserRoles\" type=\"text\" value=\"" + escHtml(rolesVal) + "\"/>";
    form += "<label>Linked applicant ID (TA profile, e.g. A001)</label><input id=\"admUserApplicantId\" type=\"text\" value=\"" + (u && u.applicantId ? escHtml(u.applicantId) : "") + "\" placeholder=\"Optional\"/>";
    document.getElementById("adminModalBody").innerHTML = form;
    let saveLabel = u ? "Save changes" : "Create user";
    document.getElementById("adminModalFooter").innerHTML =
        '<button type="button" class="btn btn-outline" style="color:var(--ink);border-color:var(--border)" onclick="closeAdminModal()">Cancel</button>' +
        '<button type="button" class="btn btn-primary btn-sm" onclick="submitUserForm(' + (u ? "true" : "false") + ')">' + saveLabel + '</button>';
    document.getElementById("adminModalOverlay").classList.add("show");
}

async function submitUserForm(isUpdate) {
    // 必须用 x-www-form-urlencoded：multipart FormData 在未配置 @MultipartConfig 时 getParameter("op") 为 null，会报 Unknown op
    let p = new URLSearchParams();
    p.append("_csrf", adminCsrfToken || sessionStorage.getItem("csrfToken") || "");
    p.append("op", isUpdate ? "update" : "create");
    p.append("username", document.getElementById("admUserUsername").value.trim());
    if (!isUpdate) {
        p.append("password", document.getElementById("admUserPassword").value);
    } else {
        let np = document.getElementById("admUserPassword").value;
        if (np) p.append("newPassword", np);
    }
    p.append("displayName", document.getElementById("admUserDisplay").value.trim());
    p.append("email", document.getElementById("admUserEmail").value.trim());
    p.append("roles", document.getElementById("admUserRoles").value.trim());
    p.append("applicantId", document.getElementById("admUserApplicantId").value.trim());
    try {
        let res = await fetch("api/user", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p.toString(),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (json.success) {
            showToast(json.message || "Saved", "success");
            closeAdminModal();
            await loadDirectoryUsers();
        } else {
            showToast(json.message || "Failed", "error");
        }
    } catch (e) {
        showToast("Request failed", "error");
    }
}

async function deleteUserConfirm(username) {
    if (!confirm("Delete user \"" + username + "\"? This cannot be undone.")) return;
    let p = new URLSearchParams();
    p.append("_csrf", adminCsrfToken || sessionStorage.getItem("csrfToken") || "");
    p.append("op", "delete");
    p.append("username", username);
    try {
        let res = await fetch("api/user", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p.toString(),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (json.success) {
            showToast(json.message || "Deleted", "success");
            await loadDirectoryUsers();
        } else {
            showToast(json.message || "Failed", "error");
        }
    } catch (e) {
        showToast("Request failed", "error");
    }
}

function openApplicantEditModal(applicantId) {
    let u = directoryUsers.find(function(x) { return x.applicantProfile && x.applicantProfile.id === applicantId; });
    let p = u ? u.applicantProfile : null;
    if (!p) {
        showToast("Applicant not found in current directory data. Refresh and try again.", "warn");
        return;
    }
    document.getElementById("adminModalTitle").textContent = "Edit applicant · " + p.id;
    let skillsStr = (p.skills || []).join(", ");
    let form = "<label>Name</label><input id=\"admApName\" type=\"text\" value=\"" + escHtml(p.name || "") + "\"/>";
    form += "<label>Email</label><input id=\"admApEmail\" type=\"email\" value=\"" + escHtml(p.email || "") + "\"/>";
    form += "<label>Year of study</label><input id=\"admApYear\" type=\"text\" value=\"" + escHtml(p.yearOfStudy || "") + "\"/>";
    form += "<label>GPA</label><input id=\"admApGpa\" type=\"text\" value=\"" + (p.gpa != null ? p.gpa : "") + "\"/>";
    form += "<label>Hours available / week</label><input id=\"admApHours\" type=\"number\" value=\"" + (p.hoursAvailable != null ? p.hoursAvailable : "") + "\"/>";
    form += "<label>Skills (comma-separated)</label><textarea id=\"admApSkills\">" + escHtml(skillsStr) + "</textarea>";
    form += "<p style=\"font-size:0.72rem;color:var(--muted);margin-top:10px\">Deleting removes this applicant and all their applications (only if no user account links this ID).</p>";
    document.getElementById("adminModalBody").innerHTML = form;
    document.getElementById("adminModalFooter").innerHTML =
        '<button type="button" class="btn btn-outline danger" style="border-color:var(--accent);color:var(--accent)" onclick="deleteApplicantConfirm(\'' + escAttr(p.id) + '\')">Delete applicant</button>' +
        '<button type="button" class="btn btn-primary btn-sm" onclick="submitApplicantForm(\'' + escAttr(p.id) + '\')">Save profile</button>';
    document.getElementById("adminModalOverlay").classList.add("show");
}

async function submitApplicantForm(applicantId) {
    let p = new URLSearchParams();
    p.append("_csrf", adminCsrfToken || sessionStorage.getItem("csrfToken") || "");
    p.append("op", "update");
    p.append("applicantId", applicantId);
    p.append("name", document.getElementById("admApName").value.trim());
    p.append("email", document.getElementById("admApEmail").value.trim());
    p.append("yearOfStudy", document.getElementById("admApYear").value.trim());
    p.append("gpa", document.getElementById("admApGpa").value.trim());
    p.append("hoursAvailable", document.getElementById("admApHours").value.trim());
    p.append("skills", document.getElementById("admApSkills").value.trim());
    try {
        let res = await fetch("api/adminApplicant", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p.toString(),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (json.success) {
            showToast(json.message || "Saved", "success");
            closeAdminModal();
            await Promise.all([loadDirectoryUsers(), loadRecruitment()]);
        } else {
            showToast(json.message || "Failed", "error");
        }
    } catch (e) {
        showToast("Request failed", "error");
    }
}

async function deleteApplicantConfirm(applicantId) {
    if (!confirm("Delete applicant " + applicantId + " and all their applications?")) return;
    let p = new URLSearchParams();
    p.append("_csrf", adminCsrfToken || sessionStorage.getItem("csrfToken") || "");
    p.append("op", "delete");
    p.append("applicantId", applicantId);
    try {
        let res = await fetch("api/adminApplicant", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p.toString(),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (json.success) {
            showToast(json.message || "Deleted", "success");
            closeAdminModal();
            await Promise.all([loadDirectoryUsers(), loadRecruitment()]);
        } else {
            showToast(json.message || "Failed", "error");
        }
    } catch (e) {
        showToast("Request failed", "error");
    }
}

async function loadWorkload() {
    try {
        let res = await fetch("api/workloads");
        let json = await res.json();
        let entries = json.workloadEntries;
        if (!entries || !entries.length) {
            let w = json.workloads || {};
            entries = Object.keys(w).map(function(k) {
                return { applicantId: k, username: "", taName: k, hours: w[k] };
            });
        }
        renderWorkload(entries);
        renderAiSuggestion(entries);
    } catch(e) { console.error(e); }
}

async function loadRecruitment() {
    try {
        let [posRes, appRes] = await Promise.all([fetch("api/positions"), fetch("api/applications")]);
        let posJson = await posRes.json();
        let appJson = await appRes.json();
        renderRecruitmentTable(posJson.positions || [], appJson.applications || []);
        renderSidebarStats(appJson.applications || []);
    } catch(e) { console.error(e); }
}

async function loadLogs() {
    try {
        let res = await fetch("api/logs");
        let json = await res.json();
        renderLogs(json.logs || []);
        document.getElementById("logCount").textContent = "v2.0 · " + (json.logs || []).length + " entries";
    } catch(e) { console.error(e); }
}

function renderWorkload(entries) {
    let container = document.getElementById("workloadBars");
    let overloadCount = entries.filter(function(e) { return (e.hours || 0) > OVERLOAD_THRESHOLD; }).length;
    document.getElementById("overloadCount").textContent = overloadCount + " Overloaded";
    if (!entries.length) {
        container.innerHTML = '<p style="color:var(--muted);text-align:center;padding:20px">No workload data (no TA accounts linked to an applicant ID).</p>';
        return;
    }
    container.innerHTML = entries.map(function(e) {
        let hours = e.hours != null ? e.hours : 0;
        let taName = (e.taName && String(e.taName).trim()) ? String(e.taName).trim() : "";
        let appId = e.applicantId || "";
        let uname = (e.username && String(e.username).trim()) ? String(e.username).trim() : "";
        let titleUser = uname ? ("@" + uname) : (taName || (appId ? appId : "Unknown"));
        let pct = Math.min((hours / CAPACITY) * 100, 100);
        let fillClass = hours > OVERLOAD_THRESHOLD ? "danger" : hours >= 16 ? "warn" : "safe";
        let overloadNote = hours > OVERLOAD_THRESHOLD ? " &#9888; OVERLOAD" : "";
        let whoParts = [];
        if (taName) whoParts.push(taName);
        whoParts.push("Applicant " + appId);
        let whoLine = whoParts.join(" · ");
        return `<div class="workload-bar-container">
            <div class="workload-head">
                <div class="workload-head-main">
                    <strong>TA workload · ${escHtml(titleUser)}</strong>
                    <div class="workload-who">${escHtml(whoLine)}</div>
                </div>
                <div class="workload-hours">${hours}h / ${CAPACITY}h${overloadNote}</div>
            </div>
            <div class="workload-bar">
                <div class="workload-fill ${fillClass}" style="width:${pct}%"></div>
            </div>
        </div>`;
    }).join("");
}

function renderAiSuggestion(entries) {
    let container = document.getElementById("aiSuggestion");
    let overloaded = entries.filter(function(e) { return (e.hours || 0) > OVERLOAD_THRESHOLD; });
    if (!overloaded.length) {
        container.innerHTML = "<strong>All TAs within safe workload range.</strong><br/>No rebalancing needed at this time.";
        document.getElementById("aiBalancerPanel").classList.remove("green");
        document.getElementById("aiBalancerPanel").style.background = "linear-gradient(135deg,#264653,#2a9d8f)";
    } else {
        let names = overloaded.map(function(e) {
            let u = (e.username && String(e.username).trim()) ? "@" + e.username : (e.taName || e.applicantId);
            return u + " (" + e.hours + "h)";
        }).join(", ");
        container.innerHTML = `<strong>Issue detected:</strong> ${names} exceed the ${CAPACITY}-hour limit.<br/><br/>
            <strong>Suggestion:</strong> Reassign one module from the overloaded TA to another with lower workload.<br/><br/>
            <strong>Impact:</strong> All TAs within safe range after reassignment.`;
    }
}

function renderRecruitmentTable(positions, apps) {
    let tbody = document.getElementById("recruitmentTableBody");
    if (!positions.length) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--muted);padding:30px">No position data.</td></tr>';
        return;
    }
    tbody.innerHTML = positions.map(pos => {
        let posApps = apps.filter(a => a.positionCode === pos.code);
        let accepted = posApps.filter(a => a.status === "Accepted").length;
        let remaining = pos.remainingSlots;
        let statusClass = pos.isOpen ? "status-open" : "status-closed";
        let statusText = pos.isOpen ? "Open" : "Closed";
        return `<tr>
            <td><strong>${pos.code}</strong> — ${pos.name}</td>
            <td>${posApps.length}</td>
            <td style="color:var(--success);font-weight:700">${accepted}</td>
            <td>${remaining} / ${pos.totalSlots}</td>
            <td><span class="status-chip ${statusClass}">${statusText}</span></td>
        </tr>`;
    }).join("");
}

function renderSidebarStats(apps) {
    document.getElementById("statTotalApps").textContent = apps.length;
    document.getElementById("statAccepted").textContent = apps.filter(a => a.status === "Accepted").length;
    document.getElementById("statPending").textContent = apps.filter(a => a.status === "Under Review").length;
}

function renderLogs(logs) {
    let container = document.getElementById("logTerminal");
    if (!logs.length) {
        container.innerHTML = '<div style="color:#8d99ae;text-align:center;padding:20px">No log entries yet.</div>';
        return;
    }
    container.innerHTML = logs.map(log => {
        let opClass = { READ:"read", WRITE:"write", ERROR:"error" }[log.operation] || "read";
        let statusClass = log.status === "OK" ? "ok" : "fail";
        return `<div class="log-entry">
            <span class="log-ts">${log.timestamp}</span>
            <span class="log-op ${opClass}">${log.opIcon || "?"}</span>
            <span class="log-file">${log.fileName || ""}</span>
            <span class="log-status ${statusClass}">${log.status}</span>
        </div>`;
    }).join("");
}

function applySuggestion() {
    if (!confirm("Are you sure you want to apply the AI workload rebalancing suggestion?")) return;
    let btn = document.querySelector(".ai-panel .btn-success");
    if (btn) btn.disabled = true;
    let p = new URLSearchParams();
    p.append("_csrf", adminCsrfToken || sessionStorage.getItem("csrfToken") || "");
    fetch("api/rebalance", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: p.toString(),
        credentials: "same-origin"
    })
        .then(r => r.json())
        .then(json => {
            if (json.success) showToast(json.message || "Workload rebalanced successfully!", "success");
            else showToast(json.message || "Rebalance failed", "error");
        })
        .catch(e => showToast("Error: " + e.message, "error"))
        .finally(() => {
            if (btn) btn.disabled = false;
            loadWorkload();
        });
}

function dismissSuggestion() {
    showToast("AI suggestion dismissed", "info");
}

function showToast(msg, type) {
    type = type || "success";
    var icons = { success:"&#9989;", error:"&#10060;", warn:"&#9888;&#65039;", info:"&#128712;" };
    var toast = document.createElement("div");
    toast.className = "toast toast-" + type;
    toast.innerHTML = '<span>' + icons[type] + '</span><span>' + msg + '</span><button class="toast-close" onclick="this.parentElement.remove()">&times;</button>';
    document.getElementById("toastContainer").appendChild(toast);
    setTimeout(() => { if(toast.parentElement) toast.remove(); }, 4000);
}
</script>
</body>
</html>
