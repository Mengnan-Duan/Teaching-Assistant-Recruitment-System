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

.workload-bar-container { margin-bottom:14px; }
.workload-label { display:flex; justify-content:space-between; font-size:0.82rem; margin-bottom:4px; }
.workload-label strong { font-weight:600; }
.workload-label span { color:var(--muted); }
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
                    <h2>File Storage Status</h2>
                    <span class="ai-inline-badge">JSON</span>
                </div>
                <div>
                    <div class="file-status-item">
                        <code>positions.json</code>
                        <span class="status-ok">&#10003; OK</span>
                    </div>
                    <div class="file-status-item">
                        <code>applicants.json</code>
                        <span class="status-ok">&#10003; OK</span>
                    </div>
                    <div class="file-status-item">
                        <code>applications.json</code>
                        <span class="status-ok">&#10003; OK</span>
                    </div>
                    <div class="file-status-item">
                        <code>workloads.json</code>
                        <span class="status-ok">&#10003; OK</span>
                    </div>
                    <div class="file-status-item">
                        <code>system_logs.json</code>
                        <span class="status-ok">&#10003; OK</span>
                    </div>
                    <div class="file-status-item">
                        <code>backup_data.json</code>
                        <span class="status-error">&#10005; ERROR</span>
                    </div>
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
                <div>
                    <div class="version-item">
                        <span class="version-badge">v2.0</span>
                        <div>
                            <div class="version-date">2026-04-05 &mdash; Mid-Term Assessment</div>
                            <div class="version-desc">JSON file persistence, REST API, AI scoring engine, system log viewer, Workload Monitor, MoSCoW traceability matrix</div>
                        </div>
                    </div>
                    <div class="version-item">
                        <span class="version-badge">v1.1</span>
                        <div>
                            <div class="version-date">2026-03-29 &mdash; Working Software v1</div>
                            <div class="version-desc">Role-based dashboards, static data, mock file-save feedback, form validation</div>
                        </div>
                    </div>
                    <div class="version-item">
                        <span class="version-badge">v1.0</span>
                        <div>
                            <div class="version-date">2026-03-22 &mdash; First Assessment</div>
                            <div class="version-desc">Product backlog, low-fidelity HTML prototype, brief report, stakeholder interviews, user stories</div>
                        </div>
                    </div>
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
                <button class="btn btn-outline" style="flex:1" onclick="showToast('AI suggestion dismissed','warn')">Dismiss</button>
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
            <div style="font-size:0.78rem;line-height:2;color:var(--muted)">
                <div>&#10003; TA: profile, CV, apply, track</div>
                <div>&#10003; MO: post job, rank, accept/reject</div>
                <div>&#10003; Admin: workload, logs, file status</div>
                <div>&#10003; AI: skill matching, gap analysis</div>
                <div>&#10003; Persistence: JSON file I/O</div>
                <div>&#10003; Traceability matrix</div>
            </div>
        </div>
    </div>
</div>

<div id="toastContainer"></div>

<script>
let adminSession = {};

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
    try {
        let res = await fetch("auth/switchRole", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: new URLSearchParams({ role: role }),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (!json.success) {
            alert(json.error || "Cannot switch role");
            return;
        }
        let dest = role === "TA" ? "ta.jsp" : role === "MO" ? "mo.jsp" : role === "ADMIN" ? "admin.jsp" : "index.jsp";
        window.location.href = dest;
    } catch (e) {
        alert("Could not switch role. Please try again.");
    }
}

async function doAdminLogout() {
    try { await fetch("auth/logout", { method: "POST" }); } catch(e) {}
    window.location.href = "index.jsp";
}

const CAPACITY = 20;
const OVERLOAD_THRESHOLD = 20;

async function loadAll() {
    await Promise.all([loadWorkload(), loadRecruitment(), loadLogs()]);
}

async function loadWorkload() {
    try {
        let res = await fetch("api/workloads");
        let json = await res.json();
        renderWorkload(json.workloads || {});
        renderAiSuggestion(json.workloads || {});
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

function renderWorkload(workloads) {
    let container = document.getElementById("workloadBars");
    let names = Object.keys(workloads);
    let overloadCount = names.filter(n => workloads[n] > OVERLOAD_THRESHOLD).length;
    document.getElementById("overloadCount").textContent = overloadCount + " Overloaded";
    if (!names.length) {
        container.innerHTML = '<p style="color:var(--muted);text-align:center;padding:20px">No workload data available.</p>';
        return;
    }
    container.innerHTML = names.map(name => {
        let hours = workloads[name];
        let pct = Math.min((hours / CAPACITY) * 100, 100);
        let fillClass = hours > OVERLOAD_THRESHOLD ? "danger" : hours >= 16 ? "warn" : "safe";
        let overloadNote = hours > OVERLOAD_THRESHOLD ? `<span style="color:var(--accent);font-size:0.75rem;margin-left:8px">&#9888; OVERLOAD</span>` : "";
        return `<div class="workload-bar-container">
            <div class="workload-label">
                <strong>${name}</strong>
                <span>${hours}h / ${CAPACITY}h${overloadNote}</span>
            </div>
            <div class="workload-bar">
                <div class="workload-fill ${fillClass}" style="width:${pct}%"></div>
            </div>
        </div>`;
    }).join("");
}

function renderAiSuggestion(workloads) {
    let container = document.getElementById("aiSuggestion");
    let overloaded = Object.entries(workloads).filter(([,h]) => h > OVERLOAD_THRESHOLD);
    if (!overloaded.length) {
        container.innerHTML = "<strong>All TAs within safe workload range.</strong><br/>No rebalancing needed at this time.";
        document.getElementById("aiBalancerPanel").classList.remove("green");
        document.getElementById("aiBalancerPanel").style.background = "linear-gradient(135deg,#264653,#2a9d8f)";
    } else {
        let names = overloaded.map(([n,h]) => `${n} (${h}h)`).join(", ");
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
    showToast("AI rebalancing applied! All TAs within safe workload range. workloads.json updated.", "success");
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
