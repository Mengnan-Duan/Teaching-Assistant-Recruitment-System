# Smart-TA — Tomcat Deployment Guide

# Smart-TA: AI-Powered Recruitment for BUPT

**Streamlining Teaching Assistant recruitment with intelligent matching, role-based workflows, and file-based JSON persistence (no SQL database).**

[![Course](https://img.shields.io/badge/EBU6304-Software_Engineering-1d3557?style=for-the-badge&logo=bookstack&logoColor=white)](#)
[![Group](https://img.shields.io/badge/Group-37-e63946?style=for-the-badge)](#)
[![HTML5](https://img.shields.io/badge/Frontend-HTML5_%2F_CSS3_%2F_JS-E34F26?style=for-the-badge&logo=html5&logoColor=white)](#)
[![Backend](https://img.shields.io/badge/Backend-Java_%7C_JSP_%7C_JSON-f89820?style=for-the-badge&logo=openjdk&logoColor=white)](#-technical-stack)
[![License](https://img.shields.io/badge/License-MIT-2a9d8f?style=for-the-badge)](#)
[![Build](https://img.shields.io/badge/Build-Passing-2a9d8f?style=for-the-badge&logo=githubactions&logoColor=white)](#)
[![Agile](https://img.shields.io/badge/Methodology-Agile_Scrum-457b9d?style=for-the-badge)](#)

<br/>

> _"Replacing scattered emails and opaque selection with a single, intelligent platform."_

**IMPORTANT:** JSP files and `WEB-INF/` must be directly under `SmartTA/`, NOT inside a `WebContent/` subfolder.  
If you copied from `SmartTA/WebContent/`, move everything up one level.

---

## Table of Contents

- [Project Overview](#-project-overview)
- [Key Features](#-key-features)
- [System Architecture](#-system-architecture)
- [Technical Stack](#-technical-stack)
- [Quick Start](#-quick-start)
- [Prototype Walkthrough](#-prototype-walkthrough)
- [Roadmap & Agile Process](#-roadmap--agile-process)
- [Repository Structure](#-repository-structure)
- [Team](#-team)
- [License](#-license)

---

## 📖 Project Overview

Traditional Teaching Assistant (TA) recruitment at BUPT suffers from fragmented communication, manual spreadsheet tracking, and a lack of transparency for all stakeholders. **Smart-TA** is a full-stack system design project that reimagines this process through three core principles:

| Principle | Description |
|-----------|-------------|
| **Role-Based Clarity** | Dedicated dashboards for TAs, Module Organisers (MOs), and Administrators — each seeing only what they need. |
| **AI-Assisted Decision-Making** | Composite matching scores that rank candidates by skill overlap, GPA relevance, and workload availability. |
| **Zero-Database Persistence** | All data is stored as JSON files under `webapps/SmartTA/data/` via `JsonFileStore`. No SQL or NoSQL database; all persistence is real and immediate — not simulated. |

This repository contains the complete deliverables for **EBU6304 Software Engineering (2025–26 Spring)**, from requirements engineering and Agile backlog planning through interactive prototyping and system architecture design.

---

## ✨ Key Features

### 🎯 Intelligent Candidate Matching

Every applicant receives an **AI composite score** (0–100) computed from:
- **Skill Overlap** — percentage of required skills the candidate possesses
- **GPA Relevance** — weighted comparison against module-specific thresholds
- **Availability** — remaining weekly hours vs. the position requirement

Module Organisers can hover over any candidate's score to view a full **AI Explanation Tooltip**, ensuring the matching process is transparent and explainable.

### 📊 Real-Time Workload Monitoring

Administrators have a global **Workload Distribution Panel** that visualises every TA's committed hours against the 20-hour institutional cap. When a TA exceeds the limit, the system:
1. Flags the overload with a visual warning (red bar + OVERLOAD label)
2. Triggers an **AI Rebalancing Recommendation** — "Apply Suggestion" reduces the overloaded TA's hours by 4 via `POST /api/rebalance`
3. Logs the event to `system_logs.json`

### 🗂️ Zero-Database JSON Persistence

All data is stored as JSON files under `webapps/SmartTA/data/` via `JsonFileStore`. On first startup, seed data is generated for positions, applicants, users, and applications. The Admin panel includes:
- A **System Activity Log** viewer showing timestamped read/write events
- A **File Storage Status** dashboard reporting the health of each JSON data file

---

## 🏗 System Architecture

The following diagram illustrates the end-to-end recruitment workflow across all three user roles:

```mermaid
flowchart TD
    subgraph TA ["🎓 Teaching Assistant"]
        A1[Browse Open Positions] --> A2[View AI Match Score & Explanation]
        A2 --> A3[Submit Application]
        A3 --> A4[Track Progress via Timeline]
        A1 -.-> A5[Manage Skill Tags]
        A5 -.-> A6["AI Skill-Gap Analysis<br/>(Missing skills, trending demand)"]
    end

    subgraph MO ["👨‍🏫 Module Organiser"]
        B1[Post TA Position] --> B2[Define Required Skills & Slots]
        B2 --> B3[Review AI-Ranked Candidates]
        B3 --> B4{Accept / Reject}
        B4 -->|Accept| B5[Quota Updated]
        B4 -->|Reject| B6[Candidate Notified]
        B3 -.-> B7["AI Filter & Sort<br/>(by score, skill, GPA, status)"]
    end

    subgraph Admin ["🔧 Administrator"]
        C1[Workload Monitor Dashboard] --> C2{Overload Detected?}
        C2 -->|Yes| C3["AI Rebalancing<br/>Suggestion"]
        C3 --> C4[Apply / Dismiss]
        C1 --> C5[Recruitment Summary Table]
        C1 --> C6["System Log Viewer<br/>(file I/O events)"]
    end

    A3 -->|"saved → applications_data.txt"| B3
    B5 -->|"saved → recruitment_data.txt"| C5
    B1 -->|"saved → positions_data.txt"| A1

    style TA fill:#e8f0f6,stroke:#457b9d,stroke-width:2px
    style MO fill:#e6f5f3,stroke:#2a9d8f,stroke-width:2px
    style Admin fill:#f3e8f6,stroke:#6c3483,stroke-width:2px
```

---

## 🛠 Technical Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| **Frontend** | HTML5, CSS3 (Custom Properties), Vanilla JavaScript | JSP-based role dashboards; live API calls to backend |
| **Backend** | Java Servlet (Jakarta EE 10), Apache Tomcat 10.1 | REST API via ApiServlet; session-based auth with role switching |
| **Persistence** | JSON flat-files via JsonFileStore | Runtime data at webapps/SmartTA/data/; no SQL or NoSQL DB |
| **AI Engine** | Composite scoring engine | Weighted formula: 0.4 Skill + 0.3 GPA + 0.3 Availability |
| **Design System** | CSS Custom Properties (Design Tokens) | 20+ tokens for colours, spacing, shadows, and typography |
| **Typography** | Google Fonts (DM Sans, Playfair Display) | Loaded via CDN for consistent cross-browser rendering |
| **Methodology** | Agile Scrum (4 iterations) | Product Backlog managed in structured format |

### Backend stack (`SmartTA/`)

The deployable web application runs on **Apache Tomcat 10.1+** with **Jakarta EE 10** (`jakarta.servlet.*`, Servlet 6.0 / `web-app` 6.0). Main backend pieces:

| Component | Technology | Role |
|-----------|------------|------|
| **Runtime** | Apache Tomcat 10.x | Servlet container; exploded WAR under `webapps/SmartTA/` |
| **Language** | Java | Business logic, models, JSON API |
| **Views** | JSP (`index.jsp`, `ta.jsp`, `mo.jsp`, `admin.jsp`, `error.jsp`) | Role-based dashboards; client-side JS calls REST endpoints |
| **REST API** | `ApiServlet` → `/api/*` | JSON over HTTP: positions, applicants, applications, logs, workloads, scoring, apply/update flows |
| **Authentication** | `AuthServlet` → `/auth/*` | Session-based login, logout, role switching |
| **Serialization** | Jackson (databind) | Read/write JSON for persistence and API responses |
| **Data access** | `DataStore` + `JsonFileStore` | Singleton in-memory cache with flush to `data/*.json` |
| **Config** | `WEB-INF/web.xml` | Servlet mappings, welcome file, error pages |

> **No SQL/NoDB:** The system stores all data as JSON files via `JsonFileStore` under `webapps/SmartTA/data/`. This satisfies the EBU6304 No-DB constraint while providing real persistence — not a simulation. Each write operation (position creation, application submission, profile update) is immediately flushed to disk and reflected in subsequent reads.

---

## 🚀 Quick Start

### Option A — Prototype (No Server)

```bash
# 1. Clone the repository
git clone https://github.com/your-org/EBU6304-Group-37.git

# 2. Open the prototype in your default browser
start Prototype_group37.html
```

Runs client-side in any modern browser. No build tooling required.

### Option B — Full Application (Tomcat Backend)

```bash
# 1. Copy SmartTA/ into Tomcat webapps
cp -r SmartTA D:\Tomcat\apache-tomcat-10.1.48\webapps\

# 2. Compile the Java backend
cd SmartTA
javac -encoding UTF-8 -cp "D:\Tomcat\apache-tomcat-10.1.48\lib\*" -d WEB-INF/classes `
    WEB-INF/classes/com/bupt/smartta/model/TAPplicant.java
    WEB-INF/classes/com/bupt/smartta/model/Position.java
    WEB-INF/classes/com/bupt/smartta/model/Application.java
    WEB-INF/classes/com/bupt/smartta/model/SystemLog.java
    WEB-INF/classes/com/bupt/smartta/util/JsonFileStore.java
    WEB-INF/classes/com/bupt/smartta/util/DataStore.java
    WEB-INF/classes/com/bupt/smartta/servlet/ApiServlet.java

# 3. Restart Tomcat and open in browser
http://localhost:8080/SmartTA/
```

---

## 🖥 Prototype Walkthrough

The interactive prototype covers three role-based dashboards, each accessible via the top navigation bar:

### TA Dashboard

| Feature | Description |
|---------|-------------|
| **Available Positions** | Filterable job listing with AI match scores and hover-to-explain tooltips |
| **My Applications** | Visual timeline tracker showing each application's progress (Submitted → Review → Interview → Decision) |
| **Skill Management** | Add/remove skill tags interactively; changes trigger simulated file-save feedback |
| **AI Skill-Gap Analysis** | Sidebar panel highlighting missing skills and trending demand this semester |

### MO Portal

| Feature | Description |
|---------|-------------|
| **Post Position** | Complete form with skill input, hour selection, slot count, and deadline — publishes with animated save bar |
| **Review Applicants** | AI-ranked candidate table with composite score breakdown, filterable by score/status/skill |
| **Manage Quotas** | Visual slot management cards with +/- controls and fill-progress indicators |

### Admin Overview

| Feature | Description |
|---------|-------------|
| **Workload Monitor** | Horizontal bar chart of every TA's hours with colour-coded overload warnings |
| **Recruitment Summary** | Module-level table showing applicants, acceptances, and remaining slots |
| **System Logs** | Terminal-styled log viewer with INFO/WARN/ERROR entries referencing file I/O operations |
| **File Storage Status** | Grid showing health status of all simulated data files |

---

## 🗺 Roadmap & Agile Process

Development follows a four-iteration Agile Scrum cycle. Each iteration delivers a shippable increment. Sprint planning prioritises core functions first — working software is delivered in every iteration, not only at the end.

### Smart-TA Development Roadmap

### Smart-TA Development Roadmap

```mermaid
%%{init: {'theme':'base','themeCSS':'.grid .tick text{font-size:13px!important;fill:#0f172a!important;font-weight:600!important;}.grid .tick line{opacity:0.85;}.today line{stroke:#1d4ed8!important;stroke-width:3.5px!important;opacity:1!important;stroke-dasharray:none!important;stroke-linecap:round!important;filter:drop-shadow(0 0 2px #fff) drop-shadow(0 0 8px rgba(29,78,216,0.85)) drop-shadow(0 0 14px rgba(37,99,235,0.45));}','themeVariables':{'primaryColor':'#2563eb','primaryTextColor':'#ffffff','secondaryColor':'#64748b','tertiaryColor':'#475569','lineColor':'#94a3b8','textColor':'#334155','titleColor':'#1e293b','taskBkgColor':'#475569','taskTextColor':'#ffffff','taskTextLightColor':'#ffffff','taskTextDarkColor':'#ffffff','taskTextOutsideColor':'#0f172a','activeTaskBkgColor':'#2563eb','activeTaskBorderColor':'#1d4ed8','gridColor':'#cbd5e1','todayLineColor':'#1d4ed8'},'gantt':{'useWidth':2360,'useMaxWidth':true,'leftPadding':130,'rightPadding':460,'barHeight':36,'barGap':14,'fontSize':14,'sectionFontSize':15,'titleTopMargin':20,'topPadding':96}}}%%
gantt
    dateFormat  YYYY-MM-DD
    axisFormat  %b %d
    todayMarker stroke-width:3px,stroke:#1d4ed8,opacity:0.95,stroke-linecap:round

    section Iteration 1 — Foundation
    Stakeholder Interviews & Survey              :done, i1a, 2026-02-17, 10d
    User Story Writing & Acceptance Criteria     :done, i1b, 2026-02-24, 10d
    Product Backlog v1.0                         :done, i1c, after i1b, 5d

    section Iteration 2 — Design & Prototype
    System Architecture & UML Diagrams          :done, i2a, 2026-03-03, 8d
    Interactive UI Prototype (HTML/CSS/JS)       :done, i2b, after i2a, 14d
    Usability Heuristic Review                   :done, i2c, after i2b, 3d

    section Iteration 3 — Core Implementation
    TA Profile & Application Workflow            :active, i3a, 2026-03-24, 18d
    MO Posting & Applicant Review Module          :active, i3b, 2026-03-31, 14d
    AI Composite Scoring Engine                   :active, i3c, 2026-04-01, 12d
    File I/O Persistence Layer                   :active, i3d, 2026-04-05, 10d

    section Iteration 4 — Testing & Delivery
    Testing & UAT                                   :i4a, 2026-04-14, 14d
    Report & Pres.                                   :i4b, after i4a, 7d
    Project Delivery                                 :milestone, 2026-05-05, 0d
```

### Iteration Highlights

| Iteration | Focus | Key Deliverables |
|-----------|-------|-----------------|
| **1** | Foundation | Stakeholder interviews (3 MOs, 5 TAs), survey results, 18 user stories with MoSCoW priorities, acceptance criteria, Product Backlog v1.0 |
| **2** | Design | System architecture diagram, UML class/sequence diagrams, interactive HTML prototype with 3 role-based dashboards |
| **3** | Implementation | TA profile + job application workflow, MO posting + applicant review, AI scoring engine, file I/O persistence to `.txt` files |
| **4** | Delivery | JUnit integration tests, UAT scenarios, final report, demonstration video |

---

## 🔗 Traceability Matrix

Each prototype feature is directly traceable to a user story in the backlog, which was elicited through a specific fact-finding technique. This ensures every implemented function has a documented rationale.

| Fact-Finding Finding | User Story | Prototype Feature |
|----------------------|------------|-------------------|
| **Interview (MO)**: MOs spend 3+ hours manually matching applicants | "As an MO, I want to see AI-ranked candidates so I can make faster, fairer decisions" | MO Portal → AI-ranked applicant table with composite scores |
| **Interview (TA)**: TAs have no visibility into application status after submission | "As a TA, I want to track my application status in real time" | TA Dashboard → Application timeline tracker |
| **Survey (TAs)**: 80% of TAs report difficulty identifying in-demand skills | "As a TA, I want to see skill-gap analysis so I can improve my profile" | TA Dashboard → AI Skill-Gap Analysis sidebar |
| **Observation (Admin)**: Workload monitoring relies on spreadsheets and emails | "As an Admin, I want a centralised workload dashboard" | Admin Portal → Workload Distribution Panel |
| **Survey (MO)**: MOs need to define required skills per position for matching | "As an MO, I want to post positions with required skills" | MO Portal → Post Position form with skill input |
| **Interview (Admin)**: No centralised log of file I/O operations exists | "As an Admin, I want to view system activity logs" | Admin Portal → System Log Viewer |
| **Survey (TAs)**: TAs apply via scattered emails; CVs are stored inconsistently | "As a TA, I want to upload my CV once and apply to multiple positions" | TA Dashboard → Upload CV + profile persistence |
| **Interview (MO)**: Quota over-allocation occurs when multiple MOs accept the same TA | "As an MO, I want to see real-time quota availability" | MO Portal → Manage Quotas with fill-progress indicators |

---

## 📈 Feedback Response (First Assessment)

Based on the first-assessment feedback, the following improvements have been made:

- **Sprint planning tightened**: Each sprint now has specific, realistic deliverables (e.g., not "Core Module Development" but broken into "TA Profile & Application Workflow", "MO Posting Module", etc.) with estimated effort in person-days.
- **Traceability strengthened**: A traceability matrix has been added above, directly linking each fact-finding finding to its user story and corresponding prototype feature.
- **Workload more balanced**: Iteration 3 tasks are scoped to avoid over-commitment; parallel tracks allow sub-team work without blocking each other.

---

## 📂 Repository Structure

```text
EBU6304-Group-37/
├── Prototype_group37.html       # Interactive UI prototype (standalone, no backend)
├── Report_group37.docx        # System analysis & design report
├── README.md                   # Project overview (you are here)
└── SmartTA/                   # Tomcat Servlet/JSP backend (mid-term demo)
    ├── src/main/java/com/bupt/smartta/
    │   ├── model/             # TAPplicant, Position, Application, SystemLog
    │   ├── dao/               # DataStore singleton + JsonFileStore
    │   ├── servlet/           # ApiServlet (REST API)
    │   └── util/              # JsonFileStore helper
    ├── WebContent/
    │   ├── index.jsp          # Role-selection landing page
    │   ├── ta.jsp             # TA Dashboard (full CRUD)
    │   ├── mo.jsp             # MO Portal (post, review, quota)
    │   ├── admin.jsp          # Admin Overview (workload, logs)
    │   └── WEB-INF/web.xml    # Servlet configuration
    ├── data/                  # JSON persistence (created at runtime)
    ├── cv_uploads/            # Uploaded CV files
    └── README.md              # Deployment guide
```

> **Mid-term demo:** The `SmartTA/` directory contains the fully functional Java Servlet/JSP application. See `SmartTA/README.md` for deployment instructions. The original `Prototype_group37.html` remains available as the standalone visual prototype.

---

## 👥 Team

**Group 37** — EBU6304 Software Engineering, BUPT, Spring 2025–26

| Role | Responsibility |
|------|---------------|
| Product Owner | Requirements elicitation, backlog prioritisation |
| Scrum Master | Sprint planning, stand-ups, retrospectives |
| UI/UX Designer | Prototype design, usability heuristic evaluation |
| System Architect | UML modelling, component design, data flow |
| QA Engineer | Test planning, acceptance criteria verification |

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

_Built with rigour and care for EBU6304 · Beijing University of Posts and Telecommunications_

**[⬆ Back to Top](#smart-ta-ai-powered-recruitment-for-bupt)**

</div>
