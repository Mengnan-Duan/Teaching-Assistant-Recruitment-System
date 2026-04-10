<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true" session="true" %>
<%
    String moCurrentRole = (String) session.getAttribute("currentRole");
    if (moCurrentRole == null) {
        response.sendRedirect("index.jsp");
        return;
    }
    if (!"MO".equals(moCurrentRole) && !"ADMIN".equals(moCurrentRole)) {
        if ("TA".equals(moCurrentRole)) {
            response.sendRedirect("ta.jsp");
        } else {
            response.sendRedirect("index.jsp");
        }
        return;
    }
%>
<!DOCTYPE html>
<html lang="en" class="smartta-shell">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>MO Portal · Smart-TA</title>
<link href="https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,300;9..40,500;9..40,700&family=Playfair+Display:wght@600;700&display=swap" rel="stylesheet" />
<link rel="stylesheet" href="css/smartta-shell.css" />
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
body { font-family:var(--font-body); color:var(--ink); line-height:1.6; }
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

.dashboard { display:grid; grid-template-columns:1fr 340px; gap:24px; padding:28px 32px; max-width:1200px; margin:0 auto; }
.hero-banner {
    grid-column:1/-1; border-radius:var(--radius); padding:28px 32px;
    background:linear-gradient(135deg,#264653 0%,#2a9d8f 100%);
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

.skill-tag { display:inline-block; background:var(--primary-soft); color:var(--primary); padding:3px 10px; border-radius:100px; font-size:0.75rem; font-weight:500; margin:2px; }

.match-bar { height:8px; background:#eee; border-radius:4px; overflow:hidden; display:inline-block; width:80px; vertical-align:middle; margin-right:6px; }
.fill { height:100%; border-radius:4px; }
.fill-high { background:var(--success); }
.fill-mid { background:var(--warn); }
.fill-low { background:var(--accent); }

.status-chip { display:inline-block; padding:3px 10px; border-radius:100px; font-size:0.75rem; font-weight:600; }
.status-chip.status-submitted { background:#e3f2fd; color:#1565c0; }
.status-chip.status-review { background:#fff8e1; color:#f57f17; }
.status-chip.status-accepted { background:var(--success-soft); color:var(--success); }
.status-chip.status-rejected { background:var(--accent-soft); color:var(--accent); }

.ai-panel {
    background:linear-gradient(135deg,#264653,#2a9d8f);
    border-radius:var(--radius); padding:22px; color:#fff; margin-bottom:16px;
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
.btn-outline { background:transparent; color:var(--success); border-color:var(--success); }
.btn-outline:hover { background:var(--success-soft); }
.btn-accept { background:var(--success); color:#fff; border-color:var(--success); }
.btn-reject { background:var(--accent); color:#fff; border-color:var(--accent); }
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
    background:#fff; color:var(--ink); outline:none; transition:var(--transition);
}
input:focus, select:focus, textarea:focus { border-color:var(--primary); }
textarea { resize:vertical; min-height:80px; }
.file-save-bar { display:none; align-items:center; gap:10px; margin-top:14px; padding:12px 16px; background:var(--success-soft); border-radius:var(--radius-sm); font-size:0.82rem; color:var(--success); }
.file-save-bar.active { display:flex; }
.file-save-spinner { width:16px; height:16px; border:2px solid var(--success); border-top-color:transparent; border-radius:50%; animation:spin 0.8s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }

#toastContainer { position:fixed; bottom:24px; right:24px; z-index:9999; display:flex; flex-direction:column; gap:8px; }
.toast { display:flex; align-items:center; gap:10px; padding:12px 18px; border-radius:var(--radius-sm); font-size:0.85rem; font-weight:500; box-shadow:0 4px 20px rgba(0,0,0,0.15); animation:slideIn 0.25s ease; max-width:360px; }
.toast-success { background:#fff; border-left:4px solid var(--success); color:var(--ink); }
.toast-error { background:#fff; border-left:4px solid var(--accent); color:var(--ink); }
.toast-warn { background:#fff; border-left:4px solid var(--warn); color:var(--ink); }
.toast-info { background:#fff; border-left:4px solid var(--primary); color:var(--ink); }
.toast-close { background:none; border:none; cursor:pointer; font-size:1.1rem; margin-left:auto; color:var(--muted); }
@keyframes slideIn { from { transform:translateX(100%); opacity:0; } to { transform:translateX(0); opacity:1; } }

.modal-overlay { display:none; position:fixed; inset:0; background:rgba(26,26,46,0.5); z-index:1000; align-items:center; justify-content:center; backdrop-filter:blur(4px); }
.modal-overlay.show { display:flex; }
.modal { background:var(--card); border-radius:var(--radius); padding:28px; width:90%; max-width:540px; box-shadow:0 20px 60px rgba(0,0,0,0.2); animation:modalIn 0.2s ease; }
.modal h2 { font-family:var(--font-display); font-size:1.3rem; margin-bottom:16px; }
.modal-actions { display:flex; gap:10px; justify-content:flex-end; margin-top:20px; }
@keyframes modalIn { from { transform:scale(0.95); opacity:0; } to { transform:scale(1); opacity:1; } }

table { width:100%; border-collapse:collapse; margin-top:12px; }
th { text-align:left; padding:10px 12px; font-size:0.78rem; font-weight:700; color:var(--muted); border-bottom:2px solid var(--border); }
td { padding:12px 12px; border-bottom:1px solid var(--border); font-size:0.85rem; vertical-align:middle; }
tr:hover td { background:#fafaf8; }
tr:last-child td { border-bottom:none; }

.filter-bar { display:flex; align-items:center; gap:10px; margin-bottom:16px; font-size:0.82rem; flex-wrap:wrap; }
.filter-bar select, .filter-bar input { padding:6px 10px; font-size:0.82rem; border-radius:6px; }

.sidebar-card { background:var(--card); border-radius:var(--radius); padding:20px; box-shadow:var(--shadow-sm); margin-bottom:16px; }
.sidebar-card h3 { font-size:0.92rem; font-weight:700; margin-bottom:12px; }

.progress-bar { height:10px; background:#eee; border-radius:5px; overflow:hidden; margin-top:6px; }
.progress-fill { height:100%; border-radius:5px; transition:width 0.3s; }
.progress-fill.open { background:var(--success); }
.progress-fill.closed { background:var(--accent); }

.quota-item { display:flex; align-items:center; justify-content:space-between; padding:12px 0; border-bottom:1px solid var(--border); }
.quota-item:last-child { border-bottom:none; }
.quota-info .quota-code { font-weight:700; font-size:0.92rem; }
.quota-info .quota-meta { font-size:0.78rem; color:var(--muted); }
.quota-controls { display:flex; align-items:center; gap:8px; }
.quota-btn { width:28px; height:28px; border-radius:6px; border:1.5px solid var(--border); background:#fff; cursor:pointer; font-size:1rem; font-weight:700; color:var(--ink); display:flex; align-items:center; justify-content:center; }
.quota-btn:hover { border-color:var(--primary); color:var(--primary); }
.quota-number { font-weight:700; font-size:1rem; min-width:40px; text-align:center; }

.version-tag { background:var(--success-soft); color:var(--success); padding:3px 10px; border-radius:100px; font-size:0.72rem; font-weight:700; }
.topbar .back-btn { font-size:0.82rem; color:var(--muted); display:flex; align-items:center; gap:6px; cursor:pointer; }
.topbar .back-btn:hover { color:var(--ink); }
</style>
</head>
<body class="smartta-shell">

<div class="topbar">
    <a href="#" class="back-btn" onclick="event.preventDefault();doMoLogout();" title="Sign out">&#8592; Smart-TA</a>
    <div style="display:flex;align-items:center;gap:10px;">
        <div id="moUserInfo" style="display:flex;align-items:center;gap:8px;font-size:0.82rem;color:var(--muted);">
            <span>&#128100;</span>
            <span id="moUserName" style="font-weight:600;color:var(--ink)"></span>
            <div class="role-switcher" style="position:relative;display:inline-block;">
                <button type="button" onclick="toggleMoRoleMenu()" id="moRoleSwitchBtn" style="background:var(--primary-soft);border:1.5px solid var(--primary);color:var(--primary);padding:4px 10px;border-radius:100px;font-size:0.75rem;font-weight:700;cursor:pointer;font-family:var(--font-body);">
                    <span id="moCurrentRoleLabel">MO</span> &#9660;
                </button>
                <div id="moRoleMenu" style="display:none;position:absolute;top:calc(100% + 6px);right:0;background:#fff;border:1.5px solid var(--border);border-radius:var(--radius-sm);box-shadow:var(--shadow-md);min-width:220px;z-index:200;overflow:hidden;"></div>
            </div>
        </div>
        <span class="version-tag">v2.0</span>
    </div>
</div>

<div class="dashboard">
    <div class="hero-banner">
        <h1>Module Organiser Portal</h1>
        <p>Post TA positions, review AI-ranked applicants, and manage recruitment quotas.</p>
    </div>

    <!-- MAIN CONTENT -->
    <div>
        <!-- SUB-TABS -->
        <div style="display:flex;gap:6px;margin-bottom:20px;">
            <button class="btn btn-primary" id="tab-post" onclick="switchTab('post')">Post Position</button>
            <button class="btn btn-outline" id="tab-review" onclick="switchTab('review')">Review Applicants</button>
            <button class="btn btn-outline" id="tab-quota" onclick="switchTab('quota')">Manage Quotas</button>
        </div>

        <!-- POST POSITION TAB -->
        <div id="content-post">
            <div class="card-section">
                <div class="section-header">
                    <h2>Post a New TA Position</h2>
                </div>
                <div class="form-grid">
                    <div class="form-group"><label>Module Code *</label><input type="text" id="posCode" placeholder="e.g. EBU6304" /></div>
                    <div class="form-group"><label>Module Name *</label><input type="text" id="posName" placeholder="e.g. Software Engineering" /></div>
                    <div class="form-group"><label>Required Skills *</label><input type="text" id="posSkills" placeholder="Java, Agile, Git (comma separated)" /></div>
                    <div class="form-group">
                        <label>Hours per Week</label>
                        <select id="posHours">
                            <option>4</option><option>6</option><option selected>8</option><option>10</option>
                        </select>
                    </div>
                    <div class="form-group"><label>Application Deadline</label><input type="date" id="posDeadline" value="2026-04-30" /></div>
                    <div class="form-group">
                        <label>Number of TA Slots</label>
                        <select id="posSlots">
                            <option>1</option><option selected>2</option><option>3</option><option>4</option><option>5</option>
                        </select>
                    </div>
                    <div class="form-group full">
                        <label>Posted By</label>
                        <input type="text" id="posPostedBy" placeholder="e.g. Dr. J. Smith" />
                    </div>
                    <div class="form-group full">
                        <label>Job Description</label>
                        <textarea id="posDesc" placeholder="Describe responsibilities, preferred qualifications..."></textarea>
                    </div>
                    <div class="form-group full" style="flex-direction:row;gap:12px;justify-content:flex-end;">
                        <button class="btn btn-outline" onclick="saveDraft()">Save as Draft</button>
                        <button class="btn btn-primary" onclick="publishPosition()">Publish Position</button>
                    </div>
                </div>
                <div class="file-save-bar" id="fileSaveBar">
                    <div class="file-save-spinner"></div>
                    <span>Saving position...</span>
                </div>
            </div>
        </div>

        <!-- REVIEW APPLICANTS TAB -->
        <div id="content-review" style="display:none">
            <div class="card-section">
                <div class="section-header">
                    <h2>Applicants — <span id="selectedPosLabel">Select a position</span></h2>
                    <div class="section-header-actions">
                        <span class="ai-inline-badge">AI &#x2605; DeepSeek</span>
                        <span class="pill-count" id="applicantCount">0 Applicants</span>
                    </div>
                </div>
                <div class="filter-bar">
                    <label>Position:</label>
                    <select id="positionSelect" onchange="loadApplicantsForPosition()">
                        <option value="">-- Select Position --</option>
                    </select>
                    <label style="margin-left:12px">Sort:</label>
                    <select id="applicantSort" onchange="renderApplicantTable()">
                        <option value="score">AI Score</option>
                        <option value="gpa">GPA</option>
                        <option value="name">Name</option>
                    </select>
                </div>
                <div id="applicantsTable"></div>
            </div>
        </div>

        <!-- MANAGE QUOTAS TAB -->
        <div id="content-quota" style="display:none">
            <div class="card-section">
                <div class="section-header">
                    <h2>TA Slot Quotas</h2>
                    <button class="btn btn-primary btn-sm" onclick="saveQuotas()">Save Changes</button>
                </div>
                <div id="quotasList"></div>
                <div class="file-save-bar" id="quotaSaveBar">
                    <div class="file-save-spinner"></div>
                    <span>Saving quotas...</span>
                </div>
            </div>
        </div>
    </div>

    <!-- SIDEBAR -->
    <div>
        <div class="ai-panel">
            <div class="ai-badge">AI SUMMARY</div>
            <h3>Recruitment Overview</h3>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px">
                <div style="text-align:center;padding:10px;background:rgba(255,255,255,0.1);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700" id="statTotal">0</div>
                    <div style="font-size:0.72rem;opacity:0.8">Total Positions</div>
                </div>
                <div style="text-align:center;padding:10px;background:rgba(255,255,255,0.1);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700" id="statTotalApps">0</div>
                    <div style="font-size:0.72rem;opacity:0.8">Applications</div>
                </div>
                <div style="text-align:center;padding:10px;background:rgba(255,255,255,0.1);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700" id="statAccepted">0</div>
                    <div style="font-size:0.72rem;opacity:0.8">Accepted</div>
                </div>
                <div style="text-align:center;padding:10px;background:rgba(255,255,255,0.1);border-radius:8px">
                    <div style="font-size:1.4rem;font-weight:700" id="statAvgScore">--</div>
                    <div style="font-size:0.72rem;opacity:0.8">Avg AI Score</div>
                </div>
            </div>
        </div>
        <div class="sidebar-card">
            <h3>Data Traceability</h3>
            <div id="dataTraceInfo" style="font-size:0.78rem;color:var(--muted);line-height:2">
                <!-- Loaded from backend /api?action=config -->
                <div>&#9679; Positions: <code>positions.json</code></div>
                <div>&#9679; Applications: <code>applications.json</code></div>
                <div>&#9679; Quotas: <code>quotas.json</code></div>
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
let moState = {
    csrfToken: null,
    positions: [], applicants: [], applications: [], session: {}, quotaChanges: {},
    positionDefaults: null,
    dataTraceability: null
};

(async function init() {
    await moCheckSession();
    if (!moState.session.username) return;
    document.getElementById("posDeadline").min = new Date().toISOString().split("T")[0];
    await Promise.all([loadAll(), loadSystemConfig()]);
})();

// Load system config from backend (position defaults, data traceability)
async function loadSystemConfig() {
    try {
        let res = await fetch("api?action=config");
        if (res.ok) {
            let cfg = await res.json();
            moState.positionDefaults = cfg.positionDefaults || {};
            moState.dataTraceability = cfg.dataTraceability || {};
            applyPositionDefaults();
            renderDataTrace();
        }
    } catch(e) {
        console.warn("[SmartTA] Failed to load system config:", e);
    }
}

function applyPositionDefaults() {
    let pd = moState.positionDefaults;
    if (!pd) return;
    let deadlineEl = document.getElementById("posDeadline");
    let hoursEl = document.getElementById("posHours");
    let slotsEl = document.getElementById("posSlots");
    let postedByEl = document.getElementById("posPostedBy");
    if (deadlineEl && pd.defaultDeadline) deadlineEl.value = pd.defaultDeadline;
    if (hoursEl && pd.defaultHours) {
        let opt = hoursEl.querySelector("option[value='" + pd.defaultHours + "']");
        if (opt) { hoursEl.value = pd.defaultHours; } else {
            // Add option if not present
            let o = document.createElement("option");
            o.value = pd.defaultHours; o.text = pd.defaultHours;
            hoursEl.appendChild(o); hoursEl.value = pd.defaultHours;
        }
    }
    if (slotsEl && pd.defaultSlots) {
        let opt = slotsEl.querySelector("option[value='" + pd.defaultSlots + "']");
        if (opt) { slotsEl.value = pd.defaultSlots; } else {
            let o = document.createElement("option");
            o.value = pd.defaultSlots; o.text = pd.defaultSlots;
            slotsEl.appendChild(o); slotsEl.value = pd.defaultSlots;
        }
    }
    if (postedByEl && pd.defaultPostedBy) postedByEl.placeholder = pd.defaultPostedBy;
}

function renderDataTrace() {
    let dt = moState.dataTraceability;
    let el = document.getElementById("dataTraceInfo");
    if (!el || !dt) return;
    el.innerHTML = '<div>&#9679; Positions: <code>' + (dt.positions || 'positions.json') + '</code></div>' +
        '<div>&#9679; Applications: <code>' + (dt.applications || 'applications.json') + '</code></div>' +
        '<div>&#9679; Quotas: <code>quotas.json</code></div>';
}

async function moCheckSession() {
    try {
        let res = await fetch("auth/session");
        let json = await res.json();
        if (!json.authenticated) { window.location.href = "index.jsp"; return; }
        moState.session = {
            username: json.username,
            displayName: json.displayName,
            currentRole: json.currentRole,
            roles: json.roles || []
        };
        moState.csrfToken = json.csrfToken || sessionStorage.getItem("csrfToken") || "";
        sessionStorage.setItem("csrfToken", moState.csrfToken);
        renderMoUserInfo();
        renderMoRoleSwitcher();
    } catch(e) { window.location.href = "index.jsp"; }
}

function renderMoUserInfo() {
    let el = document.getElementById("moUserName");
    if (el) el.textContent = moState.session.displayName || "";
    el = document.getElementById("moCurrentRoleLabel");
    if (el) el.textContent = moState.session.currentRole || "";
}

function renderMoRoleSwitcher() {
    let menu = document.getElementById("moRoleMenu");
    let roles = moState.session.roles || [];
    let current = moState.session.currentRole;
    let roleIcons = { TA: "&#127891;", MO: "&#127979;", ADMIN: "&#9881;" };
    let roleNames = { TA: "Teaching Assistant", MO: "Module Organiser", ADMIN: "Administrator" };
    let html = '<div style="padding:8px 12px;font-size:0.72rem;font-weight:700;color:var(--muted);text-transform:uppercase;letter-spacing:0.05em;">Switch Role</div>';
    roles.forEach(r => {
        let isActive = r === current;
        let icon = roleIcons[r] || '&#128100;';
        let roleName = roleNames[r] || '';
        let activeStyle = isActive ? 'background:var(--primary-soft);color:var(--primary);' : 'color:var(--ink);';
        let hoverBg = isActive ? 'var(--primary-soft)' : 'var(--surface)';
        let hoverOut = isActive ? 'var(--primary-soft)' : 'transparent';
        let checkMark = isActive ? '<span style="margin-left:auto;color:var(--primary)">&#10003;</span>' : '';
        html += '<div onclick="moSwitchRole(\'' + r + '\')" style="padding:10px 14px;cursor:pointer;display:flex;align-items:center;gap:8px;font-size:0.85rem;font-weight:600;' + activeStyle + '" onmouseover="this.style.background=\'' + hoverBg + '\'" onmouseout="this.style.background=\'' + hoverOut + '\'">' +
            '<span style="font-size:1rem">' + icon + '</span>' +
            '<div><div style="font-weight:700">' + r + '</div><div style="font-size:0.68rem;color:var(--muted)">' + roleName + '</div></div>' +
            checkMark +
        '</div>';
    });
    if (menu) menu.innerHTML = html;
}

function toggleMoRoleMenu() {
    let menu = document.getElementById("moRoleMenu");
    menu.style.display = menu.style.display === "block" ? "none" : "block";
    event.stopPropagation();
}
document.addEventListener("click", () => { var m = document.getElementById("moRoleMenu"); if(m) m.style.display="none"; });

async function moSwitchRole(role) {
    if (role === moState.session.currentRole) return;
    let btn = document.getElementById("moRoleSwitchBtn");
    if (btn) btn.disabled = true;
    try {
        let csrf = moState.csrfToken || sessionStorage.getItem("csrfToken") || "";
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

async function doMoLogout() {
    try { await fetch("auth/logout", { method: "POST" }); } catch(e) {}
    sessionStorage.removeItem("csrfToken");
    window.location.href = "index.jsp";
}

async function loadAll() {
    try {
        let [pRes, aRes, appRes] = await Promise.all([
            fetch("api/positions"),
            fetch("api/applicants"),
            fetch("api/applications")
        ]);
        let [pJson, aJson, appJson] = await Promise.all([pRes.json(), aRes.json(), appRes.json()]);
        moState.positions = pJson.positions || [];
        moState.applicants = aJson.applicants || [];
        moState.applications = appJson.applications || [];
        renderSidebarStats();
        renderPositionSelect();
        renderQuotas();
    } catch(e) { console.error(e); }
}

function renderSidebarStats() {
    document.getElementById("statTotal").textContent = moState.positions.length;
    document.getElementById("statTotalApps").textContent = moState.applications.length;
    document.getElementById("statAccepted").textContent = moState.applications.filter(a => a.status === "Accepted").length;
    let scores = moState.applications.map(a => a.aiScore);
    document.getElementById("statAvgScore").textContent = scores.length ? Math.round(scores.reduce((a,b)=>a+b,0)/scores.length) : "--";
}

function renderPositionSelect() {
    let sel = document.getElementById("positionSelect");
    let options = '<option value="">-- Select Position --</option>';
    moState.positions.forEach(p => { options += '<option value="' + p.code + '">' + p.code + ' — ' + p.name + '</option>'; });
    sel.innerHTML = options;
}

function loadApplicantsForPosition() {
    let code = document.getElementById("positionSelect").value;
    document.getElementById("selectedPosLabel").textContent = code ? code : "Select a position";
    document.getElementById("applicantCount").textContent = "0 Applicants";
    if (!code) { document.getElementById("applicantsTable").innerHTML = ""; return; }
    renderApplicantTable();
}

function computeAIScore(applicant, requiredSkills) {
    let req = requiredSkills || [];
    let matched = req.filter(s => applicant.skills.includes(s)).length;
    let skillScore = req.length > 0 ? (matched / req.length) * 100 : 0;
    let gpaScore = (applicant.gpa / 4.0) * 100;
    let availScore = Math.min((applicant.hoursAvailable / 20) * 100, 100);
    return Math.round(0.4 * skillScore + 0.3 * gpaScore + 0.3 * availScore);
}

function renderApplicantTable() {
    let code = document.getElementById("positionSelect").value;
    let sort = document.getElementById("applicantSort").value;
    let container = document.getElementById("applicantsTable");
    let pos = moState.positions.find(p => p.code === code);
    if (!pos) { container.innerHTML = ""; return; }
    let reqSkills = pos.skillsList || [];
    let relevantApps = moState.applications.filter(a => a.positionCode === code);
    let rows = relevantApps.map(app => {
        let applicant = moState.applicants.find(a => a.id === app.applicantId);
        if (!applicant) return null;
        let score = applicant.aiScore || computeAIScore(applicant, reqSkills);
        applicant._score = score;
        return { app: app, applicant: applicant, score: score };
    }).filter(Boolean);
    rows.sort((a, b) => sort === "gpa" ? b.applicant.gpa - a.applicant.gpa : sort === "name" ? a.applicant.name.localeCompare(b.applicant.name) : b.score - a.score);
    document.getElementById("applicantCount").textContent = relevantApps.length + " Applicants";
    if (!rows.length) {
        container.innerHTML = '<p style="color:var(--muted);text-align:center;padding:20px">No applications for this position yet.</p>';
        return;
    }
    let tbodyHtml = "";
    rows.forEach((r, idx) => {
        let fillClass = r.score >= 75 ? "fill-high" : r.score >= 55 ? "fill-mid" : "fill-low";
        let rankColor = idx === 0 ? "var(--success)" : "var(--ink)";
        let statusMap = { "Submitted":"status-submitted","Under Review":"status-review","Accepted":"status-accepted","Rejected":"status-rejected" };
        let statusClass = statusMap[r.app.status] || "status-submitted";
        let skillTagHtml = "";
        reqSkills.forEach(s => {
            let has = r.applicant.skills.includes(s);
            let tagStyle = has ? "" : "background:var(--accent-soft);color:var(--accent)";
            let missLabel = has ? "" : " (miss)";
            skillTagHtml += '<span class="skill-tag" style="' + tagStyle + '">' + s + missLabel + '</span>';
        });
        let canAccept = r.app.status !== "Accepted" ? "" : "disabled";
        let canReject = r.app.status !== "Rejected" ? "" : "disabled";
        let scoreColor = r.score >= 75 ? "var(--success)" : r.score >= 55 ? "#b8860b" : "var(--accent)";
        tbodyHtml += "<tr onclick=\"showAppLLMDetail('" + r.app.id + "','" + r.app.applicantId + "','" + r.app.positionCode + "')\" style=\"cursor:pointer\" title=\"Click for AI analysis\">" +
            '<td><strong style="color:' + rankColor + '">#' + (idx+1) + '</strong></td>' +
            '<td><strong>' + r.applicant.name + '</strong><br/><span style="font-size:0.78rem;color:var(--muted)">' + r.applicant.yearOfStudy + ' &middot; ' + r.applicant.hoursAvailable + 'h avail</span></td>' +
            '<td>' + skillTagHtml + '</td>' +
            '<td><strong>' + r.applicant.gpa + '</strong></td>' +
            '<td>' +
                '<div style="display:flex;align-items:center;gap:8px;">' +
                    '<div class="match-bar" style="width:80px"><div class="fill ' + fillClass + '" style="width:' + r.score + '%"></div></div>' +
                    '<strong style="color:' + scoreColor + '">' + r.score + '</strong>' +
                '</div>' +
            '</td>' +
            '<td><span class="status-chip ' + statusClass + '">' + r.app.status + '</span></td>' +
            '<td>' +
                '<button class="btn btn-accept btn-sm" onclick="updateStatus(\'' + r.app.id + '\',\'Accepted\')" ' + canAccept + '>Accept</button>' +
                '<button class="btn btn-reject btn-sm" onclick="updateStatus(\'' + r.app.id + '\',\'Rejected\')" ' + canReject + '>Reject</button>' +
            '</td>' +
        "</tr>";
    });
    container.innerHTML = "<table>" +
        "<thead><tr><th>Rank</th><th>Applicant</th><th>Required Skills</th><th>GPA</th><th>AI Score</th><th>Status</th><th>Actions</th></tr></thead>" +
        "<tbody>" + tbodyHtml + "</tbody>" +
    "</table>";
}

async function showAppLLMDetail(appId, applicantId, positionCode) {
    let app = moState.applications.find(a => a.id === appId);
    let applicant = moState.applicants.find(a => a.id === applicantId);
    let pos = moState.positions.find(p => p.code === positionCode);
    if (!app || !applicant || !pos) return;

    let reqSkills = pos.skillsList || [];
    let matched = reqSkills.filter(s => applicant.skills.includes(s)).length;
    let gpaScore = Math.round((applicant.gpa / 4.0) * 100);
    let availScore = Math.min(Math.round((applicant.hoursAvailable / 20) * 100), 100);
    let skillScore = reqSkills.length > 0 ? Math.round((matched / reqSkills.length) * 100) : 0;
    let totalScore = Math.round(0.4 * skillScore + 0.3 * gpaScore + 0.3 * availScore);

    let detailHtml = `
        <div style="margin-bottom:14px">
            <strong>Applicant:</strong> ${applicant.name} &nbsp;(${applicant.yearOfStudy})<br/>
            <strong>Position:</strong> ${pos.code} &mdash; ${pos.name}<br/>
            <strong>Status:</strong> ${app.status} &nbsp; <strong>Applied:</strong> ${app.appliedAt}
        </div>
        <div style="display:flex;gap:16px;margin-bottom:14px;flex-wrap:wrap">
            <div><span style="font-size:0.75rem;color:var(--muted)">AI Score</span><br/><strong style="font-size:1.4rem;color:var(--primary)">${totalScore}</strong></div>
            <div><span style="font-size:0.75rem;color:var(--muted)">Skill Match</span><br/><strong>${skillScore}%</strong><br/><span style="font-size:0.72rem">${matched}/${reqSkills.length} matched</span></div>
            <div><span style="font-size:0.75rem;color:var(--muted)">GPA</span><br/><strong>${gpaScore}%</strong><br/><span style="font-size:0.72rem">${applicant.gpa}/4.0</span></div>
            <div><span style="font-size:0.75rem;color:var(--muted)">Availability</span><br/><strong>${availScore}%</strong><br/><span style="font-size:0.72rem">${applicant.hoursAvailable}h/week</span></div>
        </div>`;

    if (app.llmExplanation) {
        detailHtml += `<div style="padding:14px;background:#e8f4fd;border-radius:8px;font-size:0.82rem;white-space:pre-wrap;line-height:1.7;margin-bottom:12px">
            <strong>&#x2726; DeepSeek AI Analysis</strong>
            <span style="font-size:0.72rem;color:var(--muted);display:block;margin-bottom:8px">Powered by DeepSeek LLM</span>
            ${app.llmExplanation.replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\n/g,'<br/>')}
        </div>`;
    } else {
        detailHtml += `<div style="padding:14px;background:var(--primary-soft);border-radius:8px;font-size:0.82rem">
            <strong>&#x2726; AI Match Analysis</strong><br/>
            Formula: 0.4×Skill + 0.3×GPA + 0.3×Availability<br/>
            <em>${app.aiExplanation || 'No analysis available.'}</em>
        </div>`;
    }

    openModal("AI Match Analysis &mdash; " + applicant.name, detailHtml);
}

async function publishPosition() {
    if (moState.positions.some(p => p.code === code)) {
    showToast("Module code already exists!", "error");
    return;
}
    let code = document.getElementById("posCode").value.trim();
    let name = document.getElementById("posName").value.trim();
    let skills = document.getElementById("posSkills").value.trim();
    let hours = document.getElementById("posHours").value;
    let slots = document.getElementById("posSlots").value;
    let deadline = document.getElementById("posDeadline").value;
    let postedBy = document.getElementById("posPostedBy").value.trim();
    let desc = document.getElementById("posDesc").value.trim();
    if (!code || !name || !skills) {
        showToast("Please fill in code, name, and required skills", "error"); return;
    }
    if (!confirm("Are you sure you want to publish this position?")) return;

    let bar = document.getElementById("fileSaveBar");
    bar.classList.add("active");
    let submitBtn = document.querySelector("#content-post .btn-primary");
    if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = "Publishing..."; }

    try {
        let p = new URLSearchParams();
        p.append("_csrf", moState.csrfToken || sessionStorage.getItem("csrfToken") || "");
        p.append("code", code); p.append("name", name); p.append("requiredSkills", skills);
        p.append("hoursPerWeek", hours); p.append("totalSlots", slots);
        p.append("deadline", deadline); p.append("postedBy", postedBy || (moState.positionDefaults && moState.positionDefaults.defaultPostedBy) || "MO");
        p.append("description", desc);
        let res = await fetch("api/position", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p.toString(),
            credentials: "same-origin"
        });
        let json = await res.json();
        bar.classList.remove("active");
        if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = "Publish Position"; }
        if (json.success) {
            showToast("Position " + code + " published successfully!", "success");
            await loadAll();
            document.getElementById("posCode").value = "";
            document.getElementById("posName").value = "";
            document.getElementById("posSkills").value = "";
            document.getElementById("posDesc").value = "";
        } else {
            showToast(json.message || json.error || "Failed to publish", "error");
        }
    } catch(e) {
        bar.classList.remove("active");
        if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = "Publish Position"; }
        showToast("Error: " + e.message, "error");
    }
}

function saveDraft() {
    showToast("Draft saved successfully", "info");
}

async function updateStatus(appId, status) {
    let confirmMsg = status === "Accepted"
        ? "Are you sure you want to accept this applicant?"
        : "Are you sure you want to reject this applicant?";
    if (!confirm(confirmMsg)) return;
    let btnGroup = event.target.closest("td");
    if (btnGroup) {
        let btns = btnGroup.querySelectorAll("button");
        btns.forEach(b => { b.disabled = true; b.textContent = "..."; });
    }
    try {
        let p = new URLSearchParams();
        p.append("_csrf", moState.csrfToken);
        p.append("applicationId", appId);
        p.append("status", status);
        let res = await fetch("api/updateStatus", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: p.toString(),
            credentials: "same-origin"
        });
        let json = await res.json();
        if (json.success) {
            showToast("Application " + status.toLowerCase() + " successfully!", "success");
            await loadAll();
        } else {
            showToast(json.message || "Failed to update", "error");
            await loadAll();
        }
    } catch(e) {
        showToast("Error: " + e.message, "error");
        await loadAll();
    }
}

function renderQuotas() {
    let container = document.getElementById("quotasList");
    let html = "";
    moState.positions.forEach(p => {
        let pct = p.totalSlots > 0 ? (p.filledSlots / p.totalSlots * 100) : 0;
        let fillClass = p.isOpen ? "open" : "closed";
        let pendingTotal = moState.quotaChanges[p.code];
        let displayTotal = pendingTotal !== undefined ? pendingTotal : p.totalSlots;
        let isModified = pendingTotal !== undefined;
        let unsavedTag = isModified ? ' <span style="color:var(--warn);font-size:0.72rem">(unsaved)</span>' : '';
        html += '<div class="quota-item">' +
            '<div class="quota-info">' +
                '<div class="quota-code">' + p.code + ' — ' + p.name + unsavedTag + '</div>' +
                '<div class="quota-meta">' + p.filledSlots + ' filled / ' + displayTotal + ' total &middot; Deadline: ' + p.deadline + '</div>' +
                '<div class="progress-bar"><div class="progress-fill ' + fillClass + '" style="width:' + pct + '%"></div></div>' +
            '</div>' +
            '<div class="quota-controls">' +
                '<span class="quota-number" id="quota-' + p.code + '">' + p.filledSlots + ' / ' + displayTotal + '</span>' +
                '<button class="quota-btn" onclick="adjustQuota(\'' + p.code + '\',1)">+</button>' +
                '<button class="quota-btn" onclick="adjustQuota(\'' + p.code + '\',-1)">-</button>' +
            '</div>' +
        '</div>';
    });
    container.innerHTML = html;
}

function adjustQuota(code, delta) {
    let el = document.getElementById("quota-" + code);
    let parts = el.textContent.trim().split(" / ");
    let filled = parseInt(parts[0]);
    let total = parseInt(parts[1]) + delta;
    if (total < filled) total = filled;
    if (total > 10) total = 10;
    el.textContent = filled + " / " + total;
    moState.quotaChanges[code] = total;
    showToast("Quota updated to " + total + " slots (unsaved)", "warn");
}

async function saveQuotas() {
    let bar = document.getElementById("quotaSaveBar");
    bar.classList.add("active");
    let saved = 0, failed = 0;
    let csrf = moState.csrfToken || sessionStorage.getItem("csrfToken") || "";
    try {
        for (let [code, totalSlots] of Object.entries(moState.quotaChanges)) {
            let p = new URLSearchParams();
            p.append("_csrf", csrf);
            p.append("positionCode", code);
            p.append("totalSlots", totalSlots);
            let res = await fetch("api/quota", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
                body: p.toString(),
                credentials: "same-origin"
            });
            let json = await res.json();
            if (json.success) saved++;
            else failed++;
        }
        bar.classList.remove("active");
        if (failed === 0) {
            showToast("All quota changes saved successfully!", "success");
            moState.quotaChanges = {};
            await loadAll();
            renderQuotas();
        } else {
            showToast(saved + " saved, " + failed + " failed", "error");
        }
    } catch(e) {
        bar.classList.remove("active");
        showToast("Error saving quotas: " + e.message, "error");
    }
}

function switchTab(tab) {
    document.getElementById("content-post").style.display = tab === "post" ? "block" : "none";
    document.getElementById("content-review").style.display = tab === "review" ? "block" : "none";
    document.getElementById("content-quota").style.display = tab === "quota" ? "block" : "none";
    document.getElementById("tab-post").className = "btn " + (tab === "post" ? "btn-primary" : "btn-outline");
    document.getElementById("tab-review").className = "btn " + (tab === "review" ? "btn-primary" : "btn-outline");
    document.getElementById("tab-quota").className = "btn " + (tab === "quota" ? "btn-primary" : "btn-outline");
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
