# Session Context

Last updated: 2026-04-19 13:21 +08:00

## Goal

Argus-Ops is a local-first Spring Boot web tool intended to:
- scan a target project directory
- generate build or Docker deployment scripts
- optionally use AI for local analysis and remote assist
- execute remote deployment commands over SSH
- return important logs to the web layer

## Current verified state

- Reactor build passes with `mvn -DskipTests package`.
- `aiops-bootstrap` is now packaged as an executable fat jar.
- The app starts successfully with:
  `java -jar aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar`
- A verified instance was listening on `http://127.0.0.1:8080` during the last validation run.
- Core APIs no longer hang when no real AI keys are configured.

## Important code changes

- Root Maven plugin wiring:
  [pom.xml](/D:/codenew/GM/Argus-Ops/pom.xml)
  Added `spring-boot-maven-plugin` so Spring Boot commands resolve cleanly from the repo.

- Executable jar packaging:
  [aiops-bootstrap/pom.xml](/D:/codenew/GM/Argus-Ops/aiops-bootstrap/pom.xml)
  Added `spring-boot-maven-plugin` `repackage` execution so `aiops-bootstrap` produces a runnable jar.

- Local-first fallback logic:
  [aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/ai/SpringAiLocalAdapter.java](/D:/codenew/GM/Argus-Ops/aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/ai/SpringAiLocalAdapter.java)
  Reworked to:
  - inspect the local project directory first
  - generate a fallback build or Docker script locally
  - perform local environment checks without blocking on AI
  - analyze logs locally with simple heuristics
  - call AI only as optional enhancement with timeout protection

- Remote assist fallback logic:
  [aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/ai/SpringAiRemoteAssistAdapter.java](/D:/codenew/GM/Argus-Ops/aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/ai/SpringAiRemoteAssistAdapter.java)
  Reworked so missing or failing remote AI does not break the main flow.

- HTTP error mapping:
  [aiops-web/src/main/java/top/codejava/aiops/web/controller/GlobalExceptionHandler.java](/D:/codenew/GM/Argus-Ops/aiops-web/src/main/java/top/codejava/aiops/web/controller/GlobalExceptionHandler.java)
  Current behavior:
  - invalid JSON -> `400`
  - validation errors -> `400`
  - downstream integration failures (`AIOPS_5001/5002/5003`) -> `502`

## Verified API behavior

- `POST /api/v1/ops/environment/check`
  Returns `200`.
  Verified output for this repo:
  - build tool detected as `maven`
  - runtime detected as `java`
  - Dockerfile missing
  - root-level `src/main/resources/application.yml` missing
  - AI supplement skipped when no real key is present

- `POST /api/v1/ops/build-script`
  Returns `200`.
  Verified output:
  - Maven build command
  - fallback Dockerfile generation block
  - `docker build` and `docker run` commands

- `POST /api/v1/ops/logs/filter`
  Returns `200`.
  Verified output for connection errors:
  - extracts the failing lines
  - returns network-related advice
  - notes that AI local and remote assist were skipped when keys are absent

- `POST /api/v1/ops/deploy/exec`
  Returns:
  - `400` for missing required fields
  - `502` with code `AIOPS_5003` when SSH execution fails downstream

- Invalid JSON payload
  Returns `400` with code `AIOPS_400`.

## Canonical run path

1. Build:
   `mvn -DskipTests package`
2. Start:
   `java -jar aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar`
3. Call APIs under:
   `http://127.0.0.1:8080/api/v1/ops`

## Environment assumptions

- If `LOCAL_AI_KEY` is missing or placeholder-like, local AI enhancement is skipped.
- If `REMOTE_AI_KEY` is missing or placeholder-like, remote AI assist is skipped.
- SSH deployment requires a reachable host and valid credentials.

## Known gaps

- The current web layer exposes the original `OpsController` endpoints, not the fuller Web/SSE flow described in `../5.0web重构.txt`.
- No persistent log storage or H2-backed log analysis flow exists yet.
- No SSE script streaming endpoint exists yet.
- No end-to-end real remote deployment target has been validated yet.

## Useful artifacts

- Validation logs:
  - [run-attempt1.out.log](/D:/codenew/GM/Argus-Ops/run-attempt1.out.log)
  - [run-attempt2.out.log](/D:/codenew/GM/Argus-Ops/run-attempt2.out.log)
  - [run-attempt3.out.log](/D:/codenew/GM/Argus-Ops/run-attempt3.out.log)

- Previous design note:
  `D:/codenew/GM/5.0web重构.txt`
  The file content is in a non-UTF8 encoding and was readable after re-decoding.

## Recommended next step

Implement the actual Web 2.0 flow in this order:
- add a local preflight/domain model instead of ad-hoc string lists
- add persistent deployment log storage
- add script streaming endpoint
- wire remote deploy plus important log return into one use case
