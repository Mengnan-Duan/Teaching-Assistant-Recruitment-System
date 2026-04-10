# Smart-TA — Tomcat Deployment Guide

## Directory Structure

```
D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA\
│
├─ index.jsp          (Home — role selection)
├─ ta.jsp             (TA Dashboard)
├─ mo.jsp             (MO Portal)
├─ admin.jsp          (Administrator Overview)
├─ error.jsp          (404/500 error page)
│
└─ WEB-INF/
    ├─ web.xml        (Servlet mappings, Jakarta EE 10)
    │
    └─ classes/
        └─ com/bupt/smartta/
            ├─ model/
            │   ├─ TAPplicant.java
            │   ├─ Position.java
            │   ├─ Application.java
            │   └─ SystemLog.java
            ├─ util/
            │   ├─ JsonFileStore.java
            │   └─ DataStore.java
            └─ servlet/
                └─ ApiServlet.java
```

**IMPORTANT:** JSP files and `WEB-INF/` must be directly under `SmartTA/`, NOT inside a `WebContent/` subfolder.  
If you copied from `SmartTA/WebContent/`, move everything up one level.

---

## Compilation (Windows PowerShell)

```powershell
$CATALINA_HOME = "D:\Tomcat\apache-tomcat-10.1.48"
$APP_HOME = "$CATALINA_HOME\webapps\SmartTA"
$SRC = "$APP_HOME\WEB-INF\classes"

# Compile all Java sources (UTF-8 encoding, classpath includes Jackson + Jakarta Servlet API)
javac -encoding UTF-8 `
    -cp "$CATALINA_HOME\lib\*" `
    -d $SRC `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\model\TAPplicant.java" `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\model\Position.java" `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\model\Application.java" `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\model\SystemLog.java" `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\util\JsonFileStore.java" `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\util\DataStore.java" `
    "$APP_HOME\WEB-INF\classes\com\bupt\smartta\servlet\ApiServlet.java"
```

**Key points:**
- `-cp` must include `D:\Tomcat\...\lib\*` (contains `jakarta.servlet-api.jar` and `jackson-databind.jar`)
- `-d` must point to `...\webapps\SmartTA\WEB-INF\classes` (NOT into `src/`)
- Tomcat 10 uses `jakarta.servlet.*` — NOT `javax.servlet.*`

---

## Running

1. Compile (see above)
2. Restart Tomcat:
   ```powershell
   & "$CATALINA_HOME\bin\shutdown.bat"
   & "$CATALINA_HOME\bin\startup.bat"
   ```
3. Open: `http://localhost:8080/SmartTA/`

---

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/SmartTA/api/positions` | List all TA positions |
| GET | `/SmartTA/api/applications` | List all applications |
| GET | `/SmartTA/api/applications?applicantId=A001` | Applications for a specific TA |
| GET | `/SmartTA/api/applicants` | List all TA applicants |
| GET | `/SmartTA/api/logs` | System activity log (last 50 entries) |
| GET | `/SmartTA/api/workloads` | TA workload hours |
| GET | `/SmartTA/api/score?applicantId=A001&positionCode=EBU6304` | AI match score |
| POST | `/SmartTA/api/apply` | Submit an application |
| POST | `/SmartTA/api/position` | Post a new TA position |
| POST | `/SmartTA/api/updateStatus` | Accept/Reject an application |
| POST | `/SmartTA/api/applicant` | Create/update TA profile |
| POST | `/SmartTA/api/workload` | Update TA workload hours |

---

## Data Storage

All data is stored as JSON files under:
```
D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA\data\
  positions.json
  applicants.json
  applications.json
  system_logs.json
  workloads.json
```

The `data/` directory is created automatically on first startup (via `DataStore`).

---

## Troubleshooting

### 404 on `mo.jsp`, `ta.jsp`, `admin.jsp`
- JSP files must be at `webapps/SmartTA/mo.jsp`, NOT `webapps/SmartTA/WebContent/mo.jsp`
- If you copied from Git repo, move JSPs out of `WebContent/` up to `SmartTA/`

### 404 on `/api/*`
- Servlet class must be compiled to `WEB-INF/classes/com/bupt/smartta/servlet/ApiServlet.class`
- `web.xml` must have `<url-pattern>/api/*</url-pattern>` (defined explicitly, no `@WebServlet` annotation needed)

### 500 / ClassNotFoundException
- Recompile after any Java change: `javac ... -d WEB-INF/classes ...`
- Restart Tomcat after recompiling

### Empty data
- Delete `data/*.json` files and restart Tomcat — seed data will be regenerated automatically
