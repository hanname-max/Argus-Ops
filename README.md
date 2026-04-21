# Argus-Ops

Argus-Ops is now packaged as a single runnable `jar`.

Users no longer need to:
- start a separate frontend service
- install Node.js just to open the UI
- run two terminals for backend and frontend

Download the jar, run it, and open the page. The backend API and the frontend console are served by the same Spring Boot process.

## What Changed

- The deployment console frontend is now bundled into the Java application
- The packaged jar can serve:
  - `/`
  - `/app.js`
  - `/app.css`
  - `/api/...`
- The app can automatically open the browser on startup on desktop environments

## Quick Start

Runnable package:

```text
release/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

Requirements:

- `JDK 21`

Run:

```powershell
java -jar .\release\aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

Default addresses:

- UI: `http://127.0.0.1:8080/`
- API: `http://127.0.0.1:8080/api/v1/ops`
- Workflow API: `http://127.0.0.1:8080/api/v1/workflow`

If the desktop environment is available, the browser will open automatically after startup.

## 30-Second Check

Check the port:

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 8080
```

Check backend health:

```powershell
curl http://127.0.0.1:8080/actuator/health
```

If the port is open and the health endpoint returns `{"status":"UP"}`, the single-jar app is ready.

## Optional AI Environment Variables

The application can still start without real AI credentials, but AI-enhanced functions may fall back or be skipped.

```powershell
$env:LOCAL_AI_KEY="your-local-key"
$env:LOCAL_AI_BASE_URL="https://api.openai.com/v1"
$env:LOCAL_AI_MODEL="gpt-4o-mini"

$env:REMOTE_AI_KEY="your-remote-key"
$env:REMOTE_AI_BASE_URL="https://api.openai.com/v1"
$env:REMOTE_AI_MODEL="gpt-4o-mini"
```

## Rebuild From Source

```powershell
mvn -DskipTests clean package
```

The rebuilt jar will be generated at:

```text
aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

## Core Capabilities

- local project preparation and stack detection
- remote host probing over SSH
- deployment preview generation
- remote deployment execution
- important log extraction and diagnosis

## User Experience Goal

The intended flow is now:

1. Download one jar
2. Run one command
3. Open one page
4. Complete the whole deployment flow in one workspace

No extra frontend process is required.
