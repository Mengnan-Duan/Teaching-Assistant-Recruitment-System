# Smart-TA: BUPT International School TA Recruitment System

**Version:** 3.0
**Group:** EBU6304 — Group 37
**Course:** BUPT International School

---

## Overview

Smart-TA is a Jakarta EE web application designed to streamline the end-to-end process of recruiting and managing Teaching Assistants (TAs) for university courses. It serves three distinct user roles — **TA**, **Module Organiser (MO)**, and **Administrator (ADMIN)** — through dedicated dashboards, automated AI-powered applicant scoring, and an LLM-integrated workload rebalancing feature.

### Key Features

- **Role-based dashboards** — dedicated UI for TAs, Module Organisers, and Administrators
- **AI scoring engine** — composite match score (0–100) based on skill match (40%), GPA (30%), and availability (30%)
- **LLM integration** — Bailian (Qwen) API generates human-readable applicant match analysis and workload rebalancing suggestions
- **MO↔TA messaging** — direct in-app messaging between Module Organisers and Teaching Assistants
- **CV management** — secure upload/download with role-based access control
- **Workload monitoring** — real-time TA workload tracking with AI-powered rebalancing advice
- **Audit logging** — comprehensive system log of all data access and modifications
- **JSON file persistence** — all data persisted in structured JSON files with atomic writes and backup
- **Full unit test suite** — 162+ JUnit 5 tests with mock and assertj assertions

---

## Technology Stack

| Component | Technology |
|---|---|
| Runtime | Apache Tomcat 10.x (Jakarta EE 10 / Servlet 6.0) |
| Language | Java 17 |
| Build Tool | Maven 3.x |
| Frontend | JSP + Vanilla JavaScript |
| Data Format | JSON (Jackson 2.17) |
| Testing | JUnit 5.11, Mockito 5.14, AssertJ 3.27 |
| Code Coverage | JaCoCo 0.8.12 |
| LLM | Bailian (Qwen-plus) via HTTP REST API |
| Data Storage | JSON files (no external database required) |

---

## Prerequisites

- **Java 17** or higher (JDK, not just JRE)
- **Apache Tomcat 10.x** (or any Servlet 6.0 compatible container)
- **Maven 3.6** or higher (for building and testing)
- **Internet access** (for LLM API calls; falls back to template analysis when unavailable)

---

## Project Structure

```
SmartTA/
├── pom.xml                          # Maven build configuration
├── .env                             # Environment variables (DASHSCOPE_API_KEY)
├── admin.jsp                        # Admin dashboard (JSP)
├── index.jsp                        # Home/landing page (JSP)
├── mo.jsp                           # MO (Module Organiser) dashboard (JSP)
├── ta.jsp                           # TA dashboard (JSP)
├── error.jsp                        # Error page
├── login.html                       # Login form
├── register.html                    # Registration form
├── sources.txt                     # Source file inventory
├── screenshot-capture.js           # Playwright screenshot tool
├── package.json                     # Node.js dependencies for screenshot tool
│
├── css/
│   └── smartta-shell.css           # Shared stylesheet
│
├── cv_uploads/                      # Uploaded CV files (UUID-named PDFs)
│
├── data/                           # JSON data files (application data store)
│   ├── applicants.json
│   ├── applications.json
│   ├── mota_messages.json
│   ├── positions.json
│   ├── system_config.json
│   ├── system_logs.json
│   ├── users.json
│   └── workloads.json
│
├── src/
│   ├── main/
│   │   ├── java/com/bupt/smartta/
│   │   │   ├── filter/
│   │   │   │   └── SecurityHeadersFilter.java   # HTTP security headers
│   │   │   ├── listener/
│   │   │   │   └── AppContextListener.java      # App startup listener
│   │   │   ├── model/
│   │   │   │   ├── Application.java              # TA application record
│   │   │   │   ├── MoTaMessage.java             # MO↔TA message
│   │   │   │   ├── Position.java                # Job posting
│   │   │   │   ├── SystemConfig.java            # System configuration
│   │   │   │   ├── SystemLog.java              # Audit log entry
│   │   │   │   ├── TAPplicant.java              # TA applicant profile
│   │   │   │   └── User.java                   # User account
│   │   │   ├── servlet/
│   │   │   │   ├── ApiServlet.java             # Main REST API
│   │   │   │   ├── AuthServlet.java            # Login/Register/Session
│   │   │   │   ├── DownloadServlet.java        # CV download
│   │   │   │   └── UploadServlet.java          # CV upload
│   │   │   └── util/
│   │   │       ├── DataStore.java              # Data access layer (singleton)
│   │   │       ├── JsonFileStore.java          # Thread-safe JSON file I/O
│   │   │       └── LLMService.java             # Bailian API integration
│   │   └── webapp/WEB-INF/
│   │       ├── web.xml                        # Servlet and session config
│   │       └── classes/                       # Compiled servlet classes
│   └── test/
│       ├── java/com/bupt/smartta/
│       │   ├── model/                         # Model unit tests
│       │   ├── servlet/                       # Servlet & API tests
│       │   └── util/                          # Utility tests
│       └── resources/data/                     # Test JSON fixtures
│
├── screenshots/                      # Application screenshots (for documentation)
│   ├── 01-login.png
│   ├── 02-register.png
│   ├── 03-ta-available_positions.png
│   ├── 04-ta-my_applications.png
│   ├── 05-ta-my_profile.png
│   ├── 06-ta-my_positions.png
│   ├── 07-ta-mo_messages.png
│   ├── 08-mo-my_modules.png
│   ├── 09-mo-post_positions.png
│   ├── 10-mo-manage_quotas.png
│   ├── 11-mo-my_tas&messages.png
│   ├── 12-mo-my_profile.png
│   └── 13-admin.png
│
├── User-Manual.md                   # End-user manual
└── README.md                        # This file
```

---

## Setup and Configuration

### 1. Configure the LLM API Key (Optional)

The application works without the API key (falls back to template-based analysis). To enable full LLM features:

**Option A — Environment Variable (recommended for production):**

```bash
# Windows
set DASHSCOPE_API_KEY=your_api_key_here

# Linux/macOS
export DASHSCOPE_API_KEY=your_api_key_here
```

**Option B — `.env` File (in the SmartTA webapp directory):**

```env
DASHSCOPE_API_KEY=your_api_key_here
```

The API key is read from (in order of priority):
1. `DASHSCOPE_API_KEY` environment variable
2. `${catalina.base}/webapps/SmartTA/.env`
3. `D:/Tomcat/apache-tomcat-10.1.48/webapps/SmartTA/.env`

### 2. Build the Application

```bash
# Navigate to the project directory
cd D:/Tomcat/apache-tomcat-10.1.48/webapps/SmartTA

# Clean and compile
mvn clean compile

# Run tests
mvn test

# Generate test reports
mvn surefire-report:report
```

The compiled classes are output to `src/main/webapp/WEB-INF/classes/` so Tomcat can load them directly without needing to redeploy.

### 3. Deploy to Tomcat

The application is already deployed at `${catalina.base}/webapps/SmartTA/`. After recompiling:

```bash
mvn clean compile
```

Tomcat will pick up the updated classes automatically on the next request (no restart needed).

### 4. Access the Application

```
http://localhost:8081/SmartTA/
```

> **Note:** The default Tomcat port in this installation is **8081**, not 8080. Check your `server.xml` for the actual port.

---

## Demo Accounts

The system is pre-configured with demo accounts. Log in at the landing page:

| Username | Password | Role | Description |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | System Administrator |
| `mosmith` | `mo123` | MO | Module Organiser (Dr. J. Smith) |
| `zhangwei` | `ta123` | TA | Teaching Assistant |
| `limei` | `ta123` | TA | Teaching Assistant |

---

## User Roles and Capabilities

### Teaching Assistant (TA)
- Register and manage personal profile (name, email, year, GPA, skills, availability)
- Upload and manage CV (PDF/DOC/DOCX, max 5 MB)
- Browse available positions and apply
- Track application status (Submitted → Under Review → Accepted/Rejected)
- View accepted positions ("My Positions")
- Send and receive messages with the MO of accepted positions

### Module Organiser (MO)
- Post and manage TA job positions (code, name, skills, hours, slots, deadline)
- View and review applicants for their posted positions
- Download applicant CVs
- Accept or reject applications (with automatic slot management)
- Send and receive messages with applicants
- Monitor workload of accepted TAs

### Administrator (ADMIN)
- View system-wide workload overview of all TAs
- AI-powered workload rebalancing suggestions
- Manage user accounts (view, create, delete)
- Manage TA applicant records
- View system audit logs
- View data file status and traceability information

---

## Building and Testing

### Run Unit Tests

```bash
mvn test
```

### Generate Test Reports

```bash
mvn surefire-report:report
```

HTML reports will be generated in `target/site/surefire-report.html`.

### Generate Code Coverage Report

```bash
mvn test jacoco:report
```

Coverage reports will be in `target/site/jacoco/index.html`.

### Generate Screenshots

If you need to regenerate the application screenshots:

```bash
# Install Playwright (one-time)
npx playwright install chromium

# Run the screenshot tool
node screenshot-capture.js
```

Screenshots will be saved to the `screenshots/` directory.

---

## Data Storage

All application data is stored as JSON files in the `data/` directory:

| File | Description |
|---|---|
| `users.json` | User accounts and roles |
| `applicants.json` | TA applicant profiles |
| `applications.json` | TA applications for positions |
| `positions.json` | Job position postings |
| `workloads.json` | TA workload hours mapping |
| `system_logs.json` | Audit log of data operations |
| `mota_messages.json` | MO↔TA messages |
| `system_config.json` | System-wide configuration and demo accounts |

Data files are protected by:
- **Atomic writes** with temporary files and rename
- **Automatic backups** (`.bak.json`) on each save
- **File locking** via dedicated `.lock` files
- **Path safety validation** against path traversal attacks

---

## API Reference

The backend exposes a REST API at `/api/*`. Key endpoints:

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/auth/login` | User login | Public |
| POST | `/auth/register` | User registration | Public |
| POST | `/auth/logout` | User logout | Authenticated |
| GET | `/api/config` | System configuration | Authenticated |
| GET/POST | `/api/positions` | List/create positions | Authenticated |
| GET/POST | `/api/applications` | List/submit applications | Authenticated |
| GET/PUT | `/api/applicants` | List/update TA profiles | Authenticated |
| GET/PUT | `/api/workloads` | View/update TA workloads | Authenticated |
| POST | `/api/rebalance` | AI workload rebalancing | ADMIN |
| GET | `/api/logs` | System audit logs | ADMIN |
| GET/POST | `/api/messages` | MO↔TA messages | Authenticated |

---

## Troubleshooting

### Login fails with "Invalid username or password"
- Ensure you are using the correct demo account credentials (see table above)
- Check that the `users.json` file exists and contains valid data

### LLM analysis shows template text instead of AI-generated content
- The Bailian API key is missing or invalid
- Set `DASHSCOPE_API_KEY` in the `.env` file or environment variable
- Check that the server has internet access

### Tomcat throws `net::ERR_CONNECTION_REFUSED`
- Ensure Tomcat is running: `netstat -ano | findstr LISTENING`
- The default port for this installation is **8081**
- Update the `BASE_URL` in `screenshot-capture.js` if your port differs

### Classes not updating after recompile
- Ensure the Maven compiler output directory is set to `src/main/webapp/WEB-INF/classes`
- Run `mvn clean compile` to force a full rebuild

---

## Acknowledgements

Developed by **EBU6304 Group 37** for the BUPT International School TA Recruitment System project.

Technology stack: Jakarta EE 10, Apache Tomcat 10.1, Java 17, Maven, Jackson, JUnit 5, Playwright.
