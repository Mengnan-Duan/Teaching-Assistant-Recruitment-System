<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true" session="true" %>
<%
    String username = (String) session.getAttribute("username");
    String displayName = (String) session.getAttribute("displayName");
    String currentRole = (String) session.getAttribute("currentRole");
    if (username == null) {
        response.sendRedirect("index.jsp");
        return;
    }
    if (!"TA".equals(currentRole) && !"ADMIN".equals(currentRole)) {
        // TA page only accessible for TA or ADMIN roles
        if ("MO".equals(currentRole)) {
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
    <title>TA Dashboard · Smart-TA</title>
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
.topbar .back-btn { font-size:0.82rem; color:var(--muted); display:flex; align-items:center; gap:6px; }
.topbar .back-btn:hover { color:var(--ink); }

.dashboard { display:grid; grid-template-columns:1fr 340px; gap:24px; padding:28px 32px; max-width:1200px; margin:0 auto; }
.hero-banner {
    grid-column:1/-1; border-radius:var(--radius); padding:28px 32px;
    background:linear-gradient(135deg,#1d3557 0%,#457b9d 100%);
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

.skill-tag {
    display:inline-block; background:var(--primary-soft); color:var(--primary);
    padding:3px 10px; border-radius:100px; font-size:0.75rem; font-weight:500;
    margin:2px;
}
.skill-tag.highlight { background:var(--success-soft); color:var(--success); }
.skill-tag.missing { background:var(--accent-soft); color:var(--accent); }

.match-bar { height:8px; background:#eee; border-radius:4px; overflow:hidden; display:inline-block; width:80px; vertical-align:middle; margin-right:6px; }
.fill { height:100%; border-radius:4px; }
.fill-high { background:var(--success); }
.fill-mid { background:var(--warn); }
.fill-low { background:var(--accent); }

.pos-card {
    border:1.5px solid var(--border); border-radius:var(--radius-sm);
    padding:18px 20px; margin-bottom:14px; transition:var(--transition);
}
.pos-card:hover { border-color:var(--primary); box-shadow:var(--shadow-sm); }
.pos-card-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:10px; }
.pos-card-header strong { font-size:1rem; }
.pos-card-meta { display:flex; gap:16px; font-size:0.8rem; color:var(--muted); margin-bottom:10px; flex-wrap:wrap; }
.pos-card-actions { display:flex; gap:8px; margin-top:10px; }

.app-card {
    border:1.5px solid var(--border); border-radius:var(--radius-sm);
    padding:18px 20px; margin-bottom:14px; cursor:pointer; transition:var(--transition);
}
.app-card:hover { border-color:var(--primary); }
.app-timeline { display:flex; align-items:center; gap:0; margin-top:12px; }
.timeline-step { display:flex; flex-direction:column; align-items:center; gap:4px; min-width:64px; }
.timeline-step .step-dot {
    width:28px; height:28px; border-radius:50%;
    display:flex; align-items:center; justify-content:center;
    font-size:0.75rem; font-weight:700;
    background:#eee; color:var(--muted);
}
.timeline-step.done .step-dot { background:var(--success); color:#fff; }
.timeline-step.active .step-dot { background:var(--primary); color:#fff; box-shadow:0 0 0 3px var(--primary-soft); }
.timeline-step.rejected .step-dot { background:var(--accent); color:#fff; }
.timeline-step .step-label { font-size:0.65rem; color:var(--muted); text-align:center; white-space:nowrap; }
.timeline-connector { flex:1; height:2px; background:#eee; min-width:16px; }
.timeline-connector.done { background:var(--success); }

.status-chip {
    display:inline-block; padding:3px 10px; border-radius:100px;
    font-size:0.75rem; font-weight:600;
}
.status-chip.status-submitted { background:#e3f2fd; color:#1565c0; }
.status-chip.status-review { background:#fff8e1; color:#f57f17; }
.status-chip.status-accepted { background:var(--success-soft); color:var(--success); }
.status-chip.status-rejected { background:var(--accent-soft); color:var(--accent); }

.ai-panel {
    background:linear-gradient(135deg,#1d3557,#457b9d);
    border-radius:var(--radius); padding:22px; color:#fff; margin-bottom:16px;
}
.ai-badge {
    display:inline-block; background:rgba(255,255,255,0.15);
    padding:3px 10px; border-radius:100px; font-size:0.72rem; font-weight:700;
    margin-bottom:12px; letter-spacing:0.05em;
}
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
.btn-primary:disabled { background:var(--muted); border-color:var(--muted); cursor:not-allowed; }
.btn-outline { background:transparent; color:var(--primary); border-color:var(--primary); }
.btn-outline:hover { background:var(--primary-soft); }
.btn-sm { padding:5px 12px; font-size:0.78rem; }
.btn-success { background:var(--success); color:#fff; border-color:var(--success); }
.btn-danger { background:var(--accent); color:#fff; border-color:var(--accent); }

.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
.form-group { display:flex; flex-direction:column; gap:6px; }
.form-group.full { grid-column:1/-1; }
label { font-size:0.82rem; font-weight:600; color:var(--ink); }
input, select, textarea {
    font-family:var(--font-body); font-size:0.88rem; padding:10px 14px;
    border:1.5px solid var(--border); border-radius:var(--radius-sm);
    background:#fff; color:var(--ink); outline:none;
    transition:var(--transition);
}
input:focus, select:focus, textarea:focus { border-color:var(--primary); }
textarea { resize:vertical; min-height:80px; }
.file-save-bar {
    display:none; align-items:center; gap:10px;
    margin-top:14px; padding:12px 16px;
    background:var(--primary-soft); border-radius:var(--radius-sm);
    font-size:0.82rem; color:var(--primary);
}
.file-save-bar.active { display:flex; }
.file-save-spinner {
    width:16px; height:16px; border:2px solid var(--primary);
    border-top-color:transparent; border-radius:50%;
    animation:spin 0.8s linear infinite;
}
@keyframes spin { to { transform:rotate(360deg); } }

#toastContainer { position:fixed; bottom:24px; right:24px; z-index:9999; display:flex; flex-direction:column; gap:8px; }
.toast {
    display:flex; align-items:center; gap:10px;
    padding:12px 18px; border-radius:var(--radius-sm);
    font-size:0.85rem; font-weight:500;
    box-shadow:0 4px 20px rgba(0,0,0,0.15);
    animation:slideIn 0.25s ease;
    max-width:360px;
}
.toast-success { background:#fff; border-left:4px solid var(--success); color:var(--ink); }
.toast-error { background:#fff; border-left:4px solid var(--accent); color:var(--ink); }
.toast-warn { background:#fff; border-left:4px solid var(--warn); color:var(--ink); }
.toast-info { background:#fff; border-left:4px solid var(--primary); color:var(--ink); }
.toast-close { background:none; border:none; cursor:pointer; font-size:1.1rem; margin-left:auto; color:var(--muted); }
@keyframes slideIn { from { transform:translateX(100%); opacity:0; } to { transform:translateX(0); opacity:1; } }

.modal-overlay {
    display:none; position:fixed; inset:0; background:rgba(26,26,46,0.5);
    z-index:1000; align-items:center; justify-content:center;
    backdrop-filter:blur(4px);
}
.modal-overlay.show { display:flex; }
.modal {
    background:var(--card); border-radius:var(--radius); padding:28px;
    width:90%; max-width:540px; box-shadow:0 20px 60px rgba(0,0,0,0.2);
    animation:modalIn 0.2s ease;
}
.modal h2 { font-family:var(--font-display); font-size:1.3rem; margin-bottom:16px; }
.modal-actions { display:flex; gap:10px; justify-content:flex-end; margin-top:20px; }
@keyframes modalIn { from { transform:scale(0.95); opacity:0; } to { transform:scale(1); opacity:1; } }

.filter-bar { display:flex; align-items:center; gap:10px; margin-bottom:16px; font-size:0.82rem; flex-wrap:wrap; }
.filter-bar select, .filter-bar input {
    padding:6px 10px; font-size:0.82rem; border-radius:6px;
}

.sidebar-card { background:var(--card); border-radius:var(--radius); padding:20px; box-shadow:var(--shadow-sm); margin-bottom:16px; }
.sidebar-card h3 { font-size:0.92rem; font-weight:700; margin-bottom:12px; }

.breadcrumb { font-size:0.82rem; color:var(--muted); margin-bottom:16px; }
.breadcrumb .sep { margin:0 6px; }
.breadcrumb .current { color:var(--ink); font-weight:600; }
.version-tag {
    background:var(--success-soft); color:var(--success);
    padding:3px 10px; border-radius:100px; font-size:0.72rem; font-weight:700;
}
</style>
</head>
<body>

<div class="topbar">
    <a href="#" class="back-btn" onclick="event.preventDefault();doLogout();" title="Sign out">&#8592; Smart-TA</a>
    <div style="display:flex;align-items:center;gap:10px;">
        <div class="user-info" id="userInfo" style="display:flex;align-items:center;gap:8px;font-size:0.82rem;color:var(--muted);">
            <span>&#128100;</span>
            <span id="userDisplayName" style="font-weight:600;color:var(--ink)"><%= displayName != null ? displayName : "User" %></span>
            <div class="role-switcher" id="roleSwitcher" style="position:relative;">
                <button type="button" onclick="toggleRoleMenu()" id="roleSwitchBtn" style="background:var(--primary-soft);border:1.5px solid var(--primary);color:var(--primary);padding:4px 10px;border-radius:100px;font-size:0.75rem;font-weight:700;cursor:pointer;font-family:var(--font-body);">
                    <span id="currentRoleLabel">TA</span> &#9660;
                </button>
                <div id="roleMenu" style="display:none;position:absolute;top:calc(100% + 6px);right:0;background:#fff;border:1.5px solid var(--border);border-radius:var(--radius-sm);box-shadow:var(--shadow-md);min-width:220px;z-index:200;overflow:hidden;">
                </div>
            </div>
        </div>
        <span class="version-tag">v2.0</span>
    </div>
</div>

<div class="dashboard">
    <div class="hero-banner">
        <h1>Teaching Assistant Dashboard</h1>
        <p>Welcome, <strong id="displayNameHero"><%= displayName != null ? displayName : "User" %></strong> &middot; <span id="heroRoleBadge" style="background:rgba(255,255,255,0.15);padding:2px 10px;border-radius:100px;font-size:0.78rem;font-weight:700;">TA</span></p>
    </div>

    <div>
        <div style="display:flex;gap:6px;margin-bottom:20px;">
            <button class="btn btn-primary" id="tab-pos" onclick="switchTab('positions')">Available Positions</button>
            <button class="btn btn-outline" id="tab-app" onclick="switchTab('applications')">My Applications</button>
            <button class="btn btn-outline" id="tab-profile" onclick="switchTab('profile')">My Profile</button>
        </div>

        <div id="content-positions">
            <div class="card-section">
                <div class="section-header">
                    <h2>Available Positions</h2>
                    <div class="section-header-actions">
                        <span class="ai-inline-badge">AI Matched</span>
                        <span class="pill-count" id="posCount">0 Open</span>
                    </div>
                </div>
                <div class="filter-bar">
                    <label>Sort by:</label>
                    <select id="sortSelect" onchange="renderPositions()">
                        <option value="score">AI Match Score</option>
                        <option value="deadline">Deadline</option>
                        <option value="hours">Hours/Week</option>
                    </select>
                </div>
                <div id="positionsList"></div>
            </div>
        </div>

        <div id="content-applications" style="display:none">
            <div class="card-section">
                <div class="section-header">
                    <h2>My Applications</h2>
                </div>
                <div id="applicationsList"></div>
            </div>
        </div>

        <div id="content-profile" style="display:none">
            <div class="card-section">
                <div class="section-header">
                    <h2>My Profile</h2>
                    <button class="btn btn-primary btn-sm" onclick="openProfileModal()">Edit Profile</button>
                </div>
                <div id="profileDisplay"></div>
                <hr style="border:none;border-top:1px solid var(--border);margin:20px 0;" />
                <div class="section-header">
                    <h2>My Skills</h2>
                </div>
                <div id="skillsDisplay"></div>
                <button class="btn btn-outline" style="margin-top:12px" onclick="addSkillPrompt()">+ Add Skill</button>
                <hr style="border:none;border-top:1px solid var(--border);margin:20px 0;" />
                <button class="btn btn-primary" onclick="document.getElementById('cvInput').click()">Upload CV</button>
                <input type="file" id="cvInput" accept=".pdf,.doc,.docx" style="display:none" onchange="handleCVUpload(this)" />
                <p style="font-size:0.78rem;color:var(--muted);margin-top:8px">Accepted formats: PDF, DOC, DOCX</p>
            </div>
        </div>
    </div>

    <div>
        <div class="ai-panel">
            <div class="ai-badge">AI INSIGHT</div>
            <h3>Skills Gap Analysis</h3>
            <p style="font-size:0.82rem;opacity:0.85;margin-bottom:10px">Based on currently open positions:</p>
            <div id="skillsGap" class="ai-suggestion">Loading...</div>
        </div>
        <div class="sidebar-card">
            <h3>Application Statistics</h3>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:8px">
                <div style="text-align:center;padding:12px;background:var(--primary-soft);border-radius:8px">
                    <div style="font-size:1.5rem;font-weight:700;color:var(--primary)" id="statApplied">0</div>
                    <div style="font-size:0.75rem;color:var(--muted)">Applied</div>
                </div>
                <div style="text-align:center;padding:12px;background:var(--success-soft);border-radius:8px">
                    <div style="font-size:1.5rem;font-weight:700;color:var(--success)" id="statAccepted">0</div>
                    <div style="font-size:0.75rem;color:var(--muted)">Accepted</div>
                </div>
            </div>
        </div>
        <div class="sidebar-card">
            <h3>Data Traceability</h3>
            <div style="font-size:0.78rem;color:var(--muted);line-height:2">
                <div>&#9679; Positions: <code>positions.json</code></div>
                <div>&#9679; Applications: <code>applications.json</code></div>
                <div>&#9679; Profile: <code>applicants.json</code></div>
                <div>&#9679; CVs: <code>cv_uploads/</code></div>
            </div>
        </div>
    </div>
</div>

<div class="modal-overlay" id="modalOverlay" onclick="if(event.target===this)closeModal()">
    <div class="modal">
        <h2 id="modalTitle">Modal</h2>
        <div id="modalBody"></div>
    </div>
</div>
<div id="toastContainer"></div>

<script>
let state = {
    positions: [], applications: [], applicant: null,
    session: { username: null, displayName: null, email: null, currentRole: null, roles: [] },
    applicantId: null
};

function syntheticApplicant() {
    return {
        id: state.applicantId || "",
        name: state.session.displayName || state.session.username || "",
        email: (state.session.email && state.session.email.trim()) ? state.session.email.trim() : "",
        yearOfStudy: "",
        gpa: 0,
        hoursAvailable: 0,
        skills: []
    };
}

function fmtDash(t) {
    if (t === null || t === undefined || String(t).trim() === "") return "—";
    return t;
}
function fmtNumDash(n) {
    if (n === null || n === undefined || n === "" || Number(n) === 0) return "—";
    return n;
}

function profileEmailMerged(a) {
    let ae = (a && a.email) ? String(a.email).trim() : "";
    let se = (state.session && state.session.email) ? String(state.session.email).trim() : "";
    return ae || se;
}

(async function init() {
    await checkSession();
    if (!state.session.username) return;
    await Promise.all([loadPositions(), loadApplications(), loadApplicant()]);
    renderSkillsGap();
    renderStats();
})();

async function checkSession() {
    try {
        let res = await fetch("auth/session");
        let json = await res.json();
        if (!json.authenticated) {
            window.location.href = "index.jsp";
            return;
        }
        state.session = {
            username: json.username,
            displayName: json.displayName,
            email: json.email || "",
            currentRole: json.currentRole,
            roles: json.roles || []
        };
        state.applicantId = json.applicantId || null;
        if (!state.applicantId) await loadApplicantIdFromUsersApi();
        renderRoleSwitcher();
        renderUserInfo();
    } catch(e) {
        window.location.href = "index.jsp";
    }
}

async function loadApplicantIdFromUsersApi() {
    try {
        let usersRes = await fetch("api/users");
        if (usersRes.ok) {
            let usersJson = await usersRes.json();
            let user = (usersJson.users || []).find(u => u.username === state.session.username);
            if (user && user.applicantId) state.applicantId = user.applicantId;
        }
    } catch (e) { /* ignore */ }
}

function renderUserInfo() {
    let el = document.getElementById("userDisplayName");
    if (el && state.session.displayName) el.textContent = state.session.displayName;
    el = document.getElementById("displayNameHero");
    if (el && state.session.displayName) el.textContent = state.session.displayName;
}

function renderRoleSwitcher() {
    let menu = document.getElementById("roleMenu");
    let roleLabel = document.getElementById("currentRoleLabel");
    let roles = state.session.roles || [];
    let current = state.session.currentRole;

    if (roleLabel) roleLabel.textContent = current || "";

    let roleIcons = { TA: "&#127891;", MO: "&#127979;", ADMIN: "&#9881;" };
    let roleNames = { TA: "Teaching Assistant", MO: "Module Organiser", ADMIN: "Administrator" };

    let html = '<div style="padding:8px 12px;font-size:0.72rem;font-weight:700;color:var(--muted);text-transform:uppercase;letter-spacing:0.05em;">Switch Role</div>';
    roles.forEach(function(r) {
        let isActive = r === current;
        let rowStyle = isActive ? "background:var(--primary-soft);color:var(--primary);" : "color:var(--ink);";
        let hoverIn = isActive ? "var(--primary-soft)" : "var(--surface)";
        let hoverOut = isActive ? "var(--primary-soft)" : "transparent";
        html += "<div onclick=\"switchRole('" + r + "')\" style=\"padding:10px 14px;cursor:pointer;display:flex;align-items:center;gap:8px;font-size:0.85rem;font-weight:600;" + rowStyle + "\" onmouseover=\"this.style.background='" + hoverIn + "'\" onmouseout=\"this.style.background='" + hoverOut + "'\">";
        html += "<span style=\"font-size:1rem\">" + (roleIcons[r] || "&#128100;") + "</span>";
        html += "<div><div style=\"font-weight:700\">" + r + "</div><div style=\"font-size:0.68rem;color:var(--muted)\">" + (roleNames[r] || "") + "</div></div>";
        if (isActive) html += "<span style=\"margin-left:auto;color:var(--primary)\">&#10003;</span>";
        html += "</div>";
    });
    if (menu) menu.innerHTML = html;
}

function toggleRoleMenu() {
    let menu = document.getElementById("roleMenu");
    menu.style.display = menu.style.display === "block" ? "none" : "block";
    event.stopPropagation();
}

// Close menu on outside click
document.addEventListener("click", () => {
    let menu = document.getElementById("roleMenu");
    if (menu) menu.style.display = "none";
});

async function switchRole(role) {
    if (role === state.session.currentRole) return;
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

async function doLogout() {
    try {
        await fetch("auth/logout", { method: "POST" });
    } catch(e) {}
    window.location.href = "index.jsp";
}

async function loadPositions() {
    try {
        let res = await fetch("api/positions");
        let json = await res.json();
        state.positions = json.positions || [];
        renderPositions();
    } catch(e) { console.error(e); }
}

async function loadApplications() {
    let appId = state.applicantId;
    if (!appId) return;
    try {
        let res = await fetch("api/applications?applicantId=" + appId);
        let json = await res.json();
        state.applications = json.applications || [];
        renderApplications();
        renderStats();
    } catch(e) { console.error(e); }
}

async function loadApplicant() {
    if (!state.applicantId) {
        state.applicant = syntheticApplicant();
        renderProfile();
        renderSkills();
        return;
    }
    try {
        let res = await fetch("api/applicants");
        let json = await res.json();
        let list = json.applicants || [];
        state.applicant = list.find(a => a.id === state.applicantId) || syntheticApplicant();
        renderProfile();
        renderSkills();
    } catch (e) {
        console.error(e);
        state.applicant = syntheticApplicant();
        renderProfile();
        renderSkills();
    }
}

function renderPositions() {
    let container = document.getElementById("positionsList");
    let sort = document.getElementById("sortSelect").value;
    let apps = state.applications;
    let positions = state.positions.filter(p => p.isOpen);

    if (sort === "score") positions.sort((a,b) => getMatchScore(b, state.applicant) - getMatchScore(a, state.applicant));
    else if (sort === "deadline") positions.sort((a,b) => a.deadline.localeCompare(b.deadline));
    else if (sort === "hours") positions.sort((a,b) => b.hoursPerWeek - a.hoursPerWeek);

    document.getElementById("posCount").textContent = positions.length + " Open";

    if (!positions.length) {
        container.innerHTML = '<p style="color:var(--muted);text-align:center;padding:20px">No open positions at the moment.</p>';
        return;
    }

    container.innerHTML = positions.map(p => {
        let myScore = getMatchScore(p, state.applicant);
        let fillClass = myScore >= 75 ? "fill-high" : myScore >= 55 ? "fill-mid" : "fill-low";
        let skillTags = (p.skillsList||[]).map(s => {
            let sk = (state.applicant && state.applicant.skills) ? state.applicant.skills : [];
            let has = sk.includes(s);
            return `<span class="skill-tag${has?' highlight':''}">${s}${!has?' (missing)':''}</span>`;
        }).join("");
        let alreadyApplied = apps.some(a => a.positionCode === p.code);
        let matchDetail = getScoreDetail(p, state.applicant);
        return `<div class="pos-card">
            <div class="pos-card-header">
                <div><strong>${p.code}</strong> — ${p.name}</div>
                <div class="match-bar" style="width:100px"><div class="fill ${fillClass}" style="width:${myScore}%"></div></div>
                <strong style="color:${myScore>=75?'var(--success)':myScore>=55?'#b8860b':'var(--accent)'}">${myScore}%</strong>
            </div>
            <div style="font-size:0.78rem;color:var(--muted);margin-bottom:10px">Posted by ${p.postedBy}</div>
            <div>${skillTags}</div>
            <div class="pos-card-meta">
                <span>&#128337; ${p.hoursPerWeek}h/week</span>
                <span>&#128203; ${p.remainingSlots} / ${p.totalSlots} slots</span>
                <span>&#128197; Deadline: ${p.deadline}</span>
            </div>
            <div style="font-size:0.75rem;color:var(--muted);margin-bottom:8px">${matchDetail}</div>
            <div class="pos-card-actions">
                <button class="btn btn-primary btn-sm" onclick="applyPosition('${p.code}','${p.name}')" ${alreadyApplied?'disabled':''}>
                    ${alreadyApplied?'Already Applied':'Apply Now'}
                </button>
            </div>
        </div>`;
    }).join("");
}

function renderApplications() {
    let container = document.getElementById("applicationsList");
    let apps = state.applications;
    if (!apps.length) {
        container.innerHTML = '<p style="color:var(--muted);text-align:center;padding:20px">No applications yet. Browse positions and apply!</p>';
        return;
    }
    container.innerHTML = apps.map(a => {
        let fillClass = a.aiScore >= 75 ? "fill-high" : a.aiScore >= 55 ? "fill-mid" : "fill-low";
        let statusClass = { "Submitted":"status-submitted","Under Review":"status-review","Accepted":"status-accepted","Rejected":"status-rejected" }[a.status] || "status-submitted";
        let timeline = buildTimeline(a.status);
        return `<div class="app-card" onclick="openAppDetail('${a.id}','${a.positionCode}','${a.status}')">
            <div style="display:flex;justify-content:space-between;align-items:start;">
                <div>
                    <strong>${a.positionCode}</strong> — ${a.positionName}
                    <span class="status-chip ${statusClass}" style="margin-left:8px">${a.status}</span>
                    <div style="font-size:0.78rem;color:var(--muted);margin-top:4px">Applied: ${a.appliedAt}</div>
                </div>
                <div style="text-align:right">
                    <div class="match-bar" style="width:80px"><div class="fill ${fillClass}" style="width:${a.aiScore}%"></div></div>
                    <strong style="font-size:1.1rem;color:${a.aiScore>=75?'var(--success)':'var(--muted)'}">${a.aiScore}</strong>
                </div>
            </div>
            <div class="app-timeline">${timeline}</div>
            <div style="font-size:0.75rem;color:var(--muted);margin-top:8px">Click to view AI score breakdown</div>
        </div>`;
    }).join("");
}

function renderProfile() {
    let a = state.applicant || syntheticApplicant();
    let hrs = fmtNumDash(a.hoursAvailable);
    let hrsHtml = hrs === "—" ? "—" : hrs + "h / week";
    document.getElementById("profileDisplay").innerHTML = `
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px">
            <div><label>Name</label><div style="font-weight:600">${fmtDash(a.name)}</div></div>
            <div><label>Email</label><div>${fmtDash(profileEmailMerged(a))}</div></div>
            <div><label>Year of Study</label><div>${fmtDash(a.yearOfStudy)}</div></div>
            <div><label>GPA</label><div><strong>${fmtNumDash(a.gpa)}</strong></div></div>
            <div><label>Hours Available</label><div>${hrsHtml}</div></div>
            <div><label>Profile ID</label><div style="font-family:monospace;color:var(--muted)">${fmtDash(a.id)}</div></div>
        </div>`;
}

function renderSkills() {
    let a = state.applicant || syntheticApplicant();
    let skills = a.skills || [];
    document.getElementById("skillsDisplay").innerHTML = skills.length
        ? skills.map(s => `<span class="skill-tag" style="cursor:pointer" onclick="removeSkill('${s}')">${s} &times;</span>`).join("")
        : '<p style="color:var(--muted);font-size:0.82rem">No skills added yet.</p>';
}

function renderSkillsGap() {
    let openPositions = state.positions.filter(p => p.isOpen);
    let mySkills = (state.applicant && state.applicant.skills) ? state.applicant.skills : [];
    let allRequired = {};
    openPositions.forEach(p => {
        (p.skillsList||[]).forEach(s => {
            if (!allRequired[s]) allRequired[s] = 0;
            allRequired[s]++;
        });
    });
    let missing = Object.keys(allRequired).filter(s => !mySkills.includes(s));
    let container = document.getElementById("skillsGap");
    if (!missing.length) {
        container.innerHTML = "<strong>Great job!</strong> Your skills cover all current demand.";
    } else {
        container.innerHTML = "<strong>In-demand skills you're missing:</strong><br/>" +
            missing.map(s => `<span class="skill-tag missing" style="margin:3px">${s}</span>`).join("") +
            "<br/><br/><em>Adding these skills will improve your AI match score.</em>";
    }
}

function renderStats() {
    let apps = state.applications;
    document.getElementById("statApplied").textContent = apps.length;
    document.getElementById("statAccepted").textContent = apps.filter(a => a.status === "Accepted").length;
}

function getMatchScore(pos, applicant) {
    if (!applicant) return 0;
    let req = pos.skillsList || [];
    let sk = applicant.skills || [];
    let matched = req.filter(s => sk.includes(s)).length;
    let skillScore = req.length > 0 ? (matched / req.length) * 100 : 0;
    let gpaScore = (applicant.gpa / 4.0) * 100;
    let availScore = Math.min((applicant.hoursAvailable / 20) * 100, 100);
    return Math.round(0.4 * skillScore + 0.3 * gpaScore + 0.3 * availScore);
}

function getScoreDetail(pos, applicant) {
    if (!applicant) return "";
    let req = pos.skillsList || [];
    let sk = applicant.skills || [];
    let matched = req.filter(s => sk.includes(s)).length;
    let skillPct = req.length > 0 ? Math.round((matched / req.length) * 100) : 0;
    let gpaPct = Math.round((applicant.gpa / 4.0) * 100);
    let availPct = Math.round(Math.min((applicant.hoursAvailable / 20) * 100, 100));
    return `Formula: 0.4 Skill(${skillPct}%) + 0.3 GPA(${gpaPct}%) + 0.3 Avail(${availPct}%)`;
}

function buildTimeline(status) {
    let steps = ["Submitted","Under Review","Interview","Decision"];
    let current = steps.indexOf(status);
    if (status === "Accepted") current = steps.length - 1;
    if (status === "Rejected") current = 2;
    let html = steps.map((step, i) => {
        let cls = "";
        if (status === "Rejected" && i === 2) cls = "rejected";
        else if (i < current) cls = "done";
        else if (i === current) cls = "active";
        let dot = i < current ? "&#10003;" : (status === "Rejected" && i === 2 ? "&times;" : (i+1));
        return `<div class="timeline-step ${cls}"><div class="step-dot">${dot}</div><div class="step-label">${step}</div></div>${i < steps.length-1 ? '<div class="timeline-connector' + (i < current ? ' done' : '') + '"></div>' : ''}`;
    }).join("");
    return html;
}

async function applyPosition(code, name) {
    if (!state.applicantId) { showToast("Please set up your profile first", "error"); return; }
    try {
        let p = new URLSearchParams();
        p.append("applicantId", state.applicantId);
        p.append("positionCode", code);
        p.append("applicantName", state.applicant ? state.applicant.name : state.session.displayName || "Unknown");
        let res = await fetch("api/apply", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p,
            credentials: "same-origin"
        });
        let json = await res.json();
        if (json.success) {
            showToast("Application submitted for " + code + "! Saved to applications.json", "success");
            await loadApplications();
            await loadPositions();
        } else {
            showToast(json.message || "Failed to apply", "error");
        }
    } catch(e) { showToast("Error: " + e.message, "error"); }
}

async function removeSkill(skill) {
    if (!state.applicant) return;
    let skills = (state.applicant.skills || []).filter(s => s !== skill);
    let p = new URLSearchParams();
    if (state.applicantId) p.append("applicantId", state.applicantId);
    p.append("name", state.applicant.name || state.session.displayName || "");
    p.append("email", state.applicant.email || state.session.email || "");
    p.append("yearOfStudy", state.applicant.yearOfStudy || "");
    p.append("gpa", state.applicant.gpa != null ? String(state.applicant.gpa) : "");
    p.append("skills", skills.join(","));
    p.append("hoursAvailable", state.applicant.hoursAvailable != null ? String(state.applicant.hoursAvailable) : "");
    try {
        await fetch("api/applicant", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p,
            credentials: "same-origin"
        });
        showToast("Skill \"" + skill + "\" removed", "warn");
        await loadApplicant();
        await loadPositions();
        renderSkillsGap();
    } catch(e) { showToast("Error removing skill", "error"); }
}

async function addSkillPrompt() {
    let skill = prompt("Enter a new skill:");
    if (!skill || !skill.trim()) return;
    skill = skill.trim();
    if (!state.applicant) return;
    let skills = [...(state.applicant.skills||[]), skill];
    let p = new URLSearchParams();
    if (state.applicantId) p.append("applicantId", state.applicantId);
    p.append("name", state.applicant.name || state.session.displayName || "");
    p.append("email", state.applicant.email || state.session.email || "");
    p.append("yearOfStudy", state.applicant.yearOfStudy || "");
    p.append("gpa", state.applicant.gpa != null ? String(state.applicant.gpa) : "");
    p.append("skills", skills.join(","));
    p.append("hoursAvailable", state.applicant.hoursAvailable != null ? String(state.applicant.hoursAvailable) : "");
    try {
        await fetch("api/applicant", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p,
            credentials: "same-origin"
        });
        showToast("Skill \"" + skill + "\" added! Saved to applicants.json", "success");
        await loadApplicant();
        await loadPositions();
        renderSkillsGap();
    } catch(e) { showToast("Error adding skill", "error"); }
}

function handleCVUpload(input) {
    let file = input.files[0];
    if (!file) return;
    showToast("CV \"" + file.name + "\" saved to cv_uploads/", "success");
}

function openProfileModal() {
    let a = state.applicant || syntheticApplicant();
    let gpaVal = (a.gpa === 0 || a.gpa === null || a.gpa === undefined) ? "" : a.gpa;
    let hrsVal = (a.hoursAvailable === 0 || a.hoursAvailable === null || a.hoursAvailable === undefined) ? "" : a.hoursAvailable;
    let esc = (s) => String(s == null ? "" : s).replace(/&/g,"&amp;").replace(/"/g,"&quot;").replace(/</g,"&lt;");
    let em = profileEmailMerged(a);
    let html = `
        <div class="form-group" style="margin-bottom:14px"><label>Full Name</label><input type="text" id="editName" value="${esc(a.name)}" /></div>
        <div class="form-group" style="margin-bottom:14px"><label>Email</label><input type="email" id="editEmail" value="${esc(em)}" /></div>
        <div class="form-group" style="margin-bottom:14px"><label>Year of Study</label><select id="editYear">
            <option value="">Not set</option>
            <option>Year 1</option><option>Year 2</option><option>Year 3</option><option>Year 4</option>
        </select></div>
        <div class="form-group" style="margin-bottom:14px"><label>GPA</label><input type="text" id="editGPA" placeholder="e.g. 3.5" value="${esc(gpaVal)}" /></div>
        <div class="form-group" style="margin-bottom:14px"><label>Hours Available / Week</label><input type="number" id="editHours" placeholder="0–20" value="${esc(hrsVal)}" min="0" max="20" /></div>
        <div class="modal-actions">
            <button class="btn btn-outline" onclick="closeModal()">Cancel</button>
            <button class="btn btn-primary" onclick="saveProfile()">Save Profile</button>
        </div>`;
    openModal("Edit Profile", html);
    document.getElementById("editYear").value = a.yearOfStudy || "";
}

async function saveProfile() {
    let a = state.applicant || syntheticApplicant();
    let p = new URLSearchParams();
    if (state.applicantId) p.append("applicantId", state.applicantId);
    p.append("name", document.getElementById("editName").value.trim());
    p.append("email", document.getElementById("editEmail").value.trim());
    p.append("yearOfStudy", document.getElementById("editYear").value);
    p.append("gpa", document.getElementById("editGPA").value.trim());
    p.append("skills", (a.skills || []).join(","));
    p.append("hoursAvailable", document.getElementById("editHours").value.trim());
    try {
        let res = await fetch("api/applicant", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p,
            credentials: "same-origin"
        });
        let json = await res.json();
        if (!json.success) {
            showToast(json.message || "Save failed", "error");
            return;
        }
        if (json.applicant && json.applicant.id) state.applicantId = json.applicant.id;
        closeModal();
        showToast("Profile saved! applicants.json updated", "success");
        await loadApplicant();
        await loadApplications();
        await loadPositions();
        renderSkillsGap();
    } catch (e) { showToast("Error saving profile", "error"); }
}

async function openAppDetail(appId, code, status) {
    let app = state.applications.find(a => a.positionCode === code);
    if (!app) return;
    let fillClass = app.aiScore >= 75 ? "fill-high" : app.aiScore >= 55 ? "fill-mid" : "fill-low";
    let html = `
        <div style="margin-bottom:16px">
            <strong>Module:</strong> ${app.positionCode} &mdash; ${app.positionName}<br/>
            <strong>Status:</strong> ${app.status}<br/>
            <strong>Applied:</strong> ${app.appliedAt}
        </div>
        <div class="match-bar" style="width:200px;margin-bottom:12px"><div class="fill ${fillClass}" style="width:${app.aiScore}%"></div></div>
        <div style="padding:14px;background:var(--primary-soft);border-radius:8px;margin-bottom:14px;font-size:0.82rem">
            <strong>&#x2726; AI Match Analysis</strong><br/>
            Formula: 0.4 Skill + 0.3 GPA + 0.3 Availability<br/>
            Score: <strong>${app.aiScore}/100</strong><br/>
            <em>${app.aiExplanation}</em>
        </div>
        <div style="font-size:0.78rem;color:var(--muted)">Traceability: applications.json to AI engine to UI</div>`;
    openModal("Application Detail - " + code, html);
}

function switchTab(tab) {
    document.getElementById("content-positions").style.display = tab === "positions" ? "block" : "none";
    document.getElementById("content-applications").style.display = tab === "applications" ? "block" : "none";
    document.getElementById("content-profile").style.display = tab === "profile" ? "block" : "none";
    document.getElementById("tab-pos").className = "btn " + (tab === "positions" ? "btn-primary" : "btn-outline");
    document.getElementById("tab-app").className = "btn " + (tab === "applications" ? "btn-primary" : "btn-outline");
    document.getElementById("tab-profile").className = "btn " + (tab === "profile" ? "btn-primary" : "btn-outline");
}

function openModal(title, body) {
    document.getElementById("modalTitle").textContent = title;
    document.getElementById("modalBody").innerHTML = body;
    document.getElementById("modalOverlay").classList.add("show");
}
function closeModal() { document.getElementById("modalOverlay").classList.remove("show"); }

function showToast(msg, type) {
    type = type || "success";
    var icons = { success:"&#9989;", error:"&#10060;", warn:"&#9888;&#65039;", info:"&#128712;" };
    var toast = document.createElement("div");
    toast.className = "toast toast-" + type;
    toast.innerHTML = '<span>' + icons[type] + '</span><span>' + msg + '</span><button class="toast-close" onclick="this.parentElement.remove()">&times;</button>';
    document.getElementById("toastContainer").appendChild(toast);
    setTimeout(() => { if(toast.parentElement) toast.remove(); }, 3500);
}
document.addEventListener("keydown", e => { if(e.key==="Escape") closeModal(); });
</script>
</body>
</html>
