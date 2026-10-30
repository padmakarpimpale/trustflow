# TrustFlow

Human-in-the-Loop AI Decision Review and Escalation Platform

TrustFlow processes employee expense claims and access requests. A trained text classifier recommends a decision. Spring Boot checks that recommendation against business policies, sends uncertain or restricted cases to a reviewer, and records the outcome and its reasons.

## Architecture

| Part | Implementation |
| --- | --- |
| User interface | React, Vite, CSS, REST requests |
| Business backend | Java 17+, Spring Boot, controller/service/repository layers |
| Security | Spring Security sessions, BCrypt passwords, CSRF, role and ownership checks |
| Persistence | Spring Data JPA, Hibernate, MySQL 8.4 and Flyway migrations |
| Inference | Python FastAPI, scikit-learn TF-IDF and logistic regression |
| Notifications | Transactional database outbox, Kafka, idempotent consumer and dead-letter topic |
| Operations | Docker Compose, Actuator health, request IDs, GitHub Actions |

This is a layered Java backend with a separately deployed Python inference service. Business modules share the Java application and database; they are not independently deployed microservices. Notifications are event-driven; inference uses synchronous HTTP. Service discovery, an API gateway and full distributed tracing are not implemented.

The full demonstration uses MySQL and Kafka. A simpler H2/local-notification profile remains available for development and tests.

## Decision workflow

1. An employee submits an expense claim or access request.
2. Python returns APPROVE, REVIEW or REJECT, a model score and supporting terms.
3. Spring Boot automatically approves only when the recommendation is APPROVE, the score meets policy, the amount is within policy, mandatory review is disabled, and neither privileged access nor the text safeguard requires review.
4. All other cases go to a reviewer, including AI failures. There is no automatic rejection.
5. The reviewer approves or rejects with a reason. A decision against APPROVE/REJECT is recorded as an override. Version checks and a unique review constraint prevent two final decisions.
6. The case, audit entries and notification outbox entries commit together. Kafka delivers notifications asynchronously; duplicate deliveries do not create duplicate notices.

Defaults are minimum score 0.80, expense limit SGD500, and mandatory review for access requests. Administrators can change policy. Each new case saves the policy values, policy version, model version and text-guard version used. Policy changes have their own history. Historical cases without snapshots show that the values are unavailable.

## Run on Windows with Docker (recommended)

Install Docker Desktop with Linux containers, and Git. Start Docker Desktop before running these commands. Java, Python, Node and MySQL are built or run inside containers.

From a new folder in PowerShell:

```powershell
git clone --branch feature/architecture-upgrade https://github.com/padmakarpimpale/trustflow.git trustflow-updated
cd trustflow-updated
docker compose -f compose.yaml -f compose.integrated.yaml up --build -d
docker compose -f compose.yaml -f compose.integrated.yaml ps
```

The first build downloads dependencies and takes several minutes. Open **http://localhost:8080** after startup. Check readiness at **http://localhost:8080/actuator/health/readiness**. Python documentation is at http://localhost:8000/docs; Python has no homepage at `/`.

If startup fails, inspect the logs:

```powershell
docker compose -f compose.yaml -f compose.integrated.yaml logs --tail=100 app ai mysql kafka
```

Stop the stack while preserving the database:

```powershell
docker compose -f compose.yaml -f compose.integrated.yaml down
```

MySQL data is stored in a named volume. Do not add `-v` when stopping a stack containing data you want to keep. The integration-test workflow uses disposable data and removes only its own runner volume.

### Demo accounts

| Username | Role |
| --- | --- |
| employee / employee2 | Submit and track their own requests |
| reviewer / reviewer2 | Review cases and record decisions |
| admin | View analytics, policies and policy-change history |

Password: `TrustFlow123!`. These are seeded local coursework accounts, not production credentials. Credentials are documented here, not displayed on the sign-in page.

## Run Java in IntelliJ with MySQL and Kafka

Use JDK21, Node22 and Docker Desktop. Open the root `pom.xml` in IntelliJ and allow Maven to import. Do not run the Docker Java app and the IntelliJ Java app at the same time on port 8080.

Start the dependencies only:

```powershell
docker compose -f compose.yaml -f compose.integrated.yaml stop app
docker compose -f compose.yaml -f compose.integrated.yaml up -d mysql ai kafka
cd frontend
npm ci
npm run build
cd ..
```

Configure `TrustflowApplication` with the repository root as its working directory and these environment variables (one per entry in IntelliJ):

```text
SPRING_PROFILES_ACTIVE=mysql,integrated
DB_URL=jdbc:mysql://localhost:3307/trustflow
DB_USER=trustflow
DB_PASSWORD=coursework-demo-only
AI_URL=http://localhost:8000/predict
KAFKA_SERVERS=localhost:9094
```

Run `TrustflowApplication.java`, then open http://localhost:8080. For frontend development, `npm run dev` inside `frontend` proxies API requests to Java. Rebuild with `npm run build` to package UI changes into the Java app.

### Simple local development without Docker

The default Java profile uses persistent H2 and local notifications. Install JDK21 and Python 3.11 or 3.12. Check installed Python versions with `py --list`; use an installed supported version instead of assuming 3.12 exists.

```powershell
cd ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe train.py
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Leave Python running. In a second PowerShell window at the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

H2 files are under `data/` relative to Java's working directory. Use the same working directory each time. Switching to MySQL does not copy existing H2 records.

## Database migrations, export and restore

Fresh MySQL installations run Flyway V1/V2/V3, then Hibernate validates the schema. Existing unmanaged MySQL databases are not automatically baselined. Back up first and follow `database/UPGRADE.md`; do not delete existing data to bypass migration errors.

With MySQL client tools on PATH, export from the host (the Docker MySQL host port is 3307):

```powershell
.\scripts\export-mysql.ps1
.\scripts\restore-mysql.ps1 -Database trustflow_restore
```

The scripts prompt for passwords. Export uses the `trustflow` user (`coursework-demo-only` by default); restore uses root (`local-root-demo-only` by default). Restore creates a separate new database and refuses to replace `trustflow`. The dump includes user password hashes, so keep it within the coursework submission. The old H2 sample dump is not a MySQL export.

## Demonstration (5–10 minutes)

Use title **Business travel expense**, type **Expense claim**, and this justification for the first two cases:

> Taxi fare for a client meeting approved by my manager with receipt attached

| Step | Expected result under default policy |
| --- | --- |
| Employee submits SGD20 | Automatic approval |
| Employee submits SGD1200 | Human review because amount exceeds 500 |
| Employee submits SGD20 with “Taxi fare for a client meeting” | Human review because model score is low |
| Employee submits SGD20 with “Taxi fare for a client meeting not approved by my manager with no receipt attached” | Text safeguard requires review despite a high model score |
| Reviewer rejects the SGD1200 case with a budget reason | Final rejection, override recorded in audit history |
| Employee opens notifications | Decision notification is visible |
| Admin changes expense threshold to 10 | History records the change; previous case retains its original policy snapshot |

Restore the expense threshold to 500 after the demonstration if you want to repeat the default examples. Receipts and manager approval are text supplied by the employee; the application does not verify them.

## Verification

```powershell
.\mvnw.cmd verify
cd frontend
npm ci
npm run build
cd ..\ai-service
.\.venv\Scripts\python.exe train.py
.\.venv\Scripts\python.exe -m unittest -v
.\.venv\Scripts\python.exe evaluate.py
```

Java tests cover routing, security, validation, concurrent review, policy snapshots/history, pagination and outbox retries. Python tests cover inference and its response contract. `scripts/browser-check.cjs` exercises the React workflow against an isolated in-memory H2 app and real inference service; it needs Playwright Chromium and `AI_PYTHON` set to the Python environment executable.

GitHub Actions also starts the full MySQL/Kafka Compose stack and runs `scripts/integration-check.py`. This checks migrations, real-model routing, Kafka outage/recovery, duplicate delivery, poison messages, AI failure, restart persistence and dump/restore. Run it only against a fresh isolated demonstration stack: it creates cases, changes policy, stops services and creates `trustflow_restore`. Its output and service logs are uploaded as `integrated-evidence`. Check the actual workflow result before treating these checks as passed.

Verified on 30 October 2026: 54 Java tests, five Python tests and 16 browser workflow checks passed. The [full-stack CI run](https://github.com/padmakarpimpale/trustflow/actions/runs/40994889018) passed with real MySQL and Kafka, including recovery and restore. Its `integrated-evidence` artifact contains service logs, results and a test database dump. Windows PowerShell helper scripts were reviewed but were not executed on Windows in that run.

The additional synthetic challenge set classified 14 of 18 examples correctly (77.8%). A score-only gate would admit one example labelled for review, which is why model scores are not the sole approval condition. This is a small diagnostic set, not a deployment accuracy estimate.

## Scope and limitations

The classifier learns from 90 synthetic training examples. The 30-example synthetic holdout shares similar vocabulary and does not establish real-world accuracy. An additional 18-example challenge evaluation reports errors, log loss, Brier score and hypothetical score-only routing; it is also synthetic, not external validation. Scores are uncalibrated and must not be described as verified accuracy. Reviewer decisions do not automatically retrain the model.

This project demonstrates controlled automation, human escalation and traceable decisions for expense and access requests. It does not reimburse money, provision access, check attachments, provide a tamper-proof audit ledger or run a highly available Kafka cluster. Request IDs support log correlation, not full distributed tracing. Future applications could include procurement approval and document triage after domain-specific data collection, evaluation and integration.

## Optional Gmail notifications

Add `compose.email.yaml` to the existing MySQL/Kafka stack to enable email. It uses Spring Mail and a second Kafka consumer group, `trustflow-email`. The existing `trustflow-notifications` group continues to create in-app notifications independently.

Three events send email: REVIEW_REQUIRED to the configured reviewer, CASE_APPROVED to the employee (automatic or human approval), and CASE_REJECTED to the employee with the reviewer's reason. Pending-submission acknowledgements stay in-app only. By default, only the demo accounts `employee` and `reviewer` are mapped to `EMAIL_DEMO_TO`. They can share your own inbox for a demonstration; `employee2`, `reviewer2` and `admin` do not send emails unless separately configured. Multiple in-app rows for different recipients are not duplicates.

### Enable real email locally

1. Enable Google 2-Step Verification and create a Google App Password for TrustFlow at https://myaccount.google.com/apppasswords . The option may be unavailable for restricted accounts. Use an app password, not your normal Google password. Do not share it in chat, screenshots or GitHub.
2. Update your checkout on `feature/architecture-upgrade` with `git pull --ff-only`.
3. Copy `.env.email.example` to `.env` in the project root. If you already have `.env`, keep its existing database settings and add the email settings instead of overwriting it.
4. Set `SMTP_USERNAME` to your Gmail address, `SMTP_PASSWORD` to the app password without display spaces, and `EMAIL_DEMO_TO` to your own destination inbox. For the demo, sender and destination can be the same Gmail account.
5. Rebuild/start with all three files:

```powershell
docker compose -f compose.yaml -f compose.integrated.yaml -f compose.email.yaml up --build -d
```

Flyway applies additive migration V3 to the existing MySQL database; keep the existing Docker volume. It creates `email_delivery` and widens the outbox payload. No existing cases are deleted. After readiness is UP, log in as `employee`, submit an expense above SGD500 under default policy, then review it as `reviewer`. Check your Gmail Inbox and Spam folder for the review request and the final decision. A local URL in the email opens on the computer running TrustFlow; localhost links will not reach that computer from a phone.

Google may limit or block SMTP submission depending on account policy and sending limits. No paid email provider is required for this small demonstration. Reference: https://support.google.com/accounts/answer/185833 . Gmail SMTP uses STARTTLS on port 587; connection, read and write timeouts are bounded.

Do not paste the output of `docker compose config` or container environment inspection into chat when real credentials are set: those can reveal secrets. `.env` is excluded from Git and Docker build context, but remains a local plaintext credential file accessible to you and Docker.

### Delivery status and retries

The email Kafka consumer first saves one database delivery row per event. A scheduled sender tries pending deliveries, separately from Kafka consumption. This prevents a slow SMTP server from blocking the in-app consumer or case submission. The table contains event-time content, so a delayed review request may describe an earlier status; recipients must sign in to see the current case.

```sql
SELECT id, case_id, username, recipient, status, attempts, last_error, sent_at
FROM email_delivery ORDER BY id DESC LIMIT 10;
```

`PENDING` means waiting or retrying. `SENT` means SMTP accepted the message, not verified inbox delivery or reading. `FAILED` means five attempts were exhausted. Retry delays are 30, 60, 120 and 240 seconds. Only exception class names are recorded, not SMTP responses or credentials. Failed records remain for inspection; this version has no retry button. After fixing configuration, a new demo case is the simplest way to verify delivery again.

Repeated Kafka delivery is deduplicated by event ID and recipient locking. A crash between SMTP acceptance and the database commit can still send the same email twice: this is not an exactly-once email guarantee. Invalid messages or exhausted Kafka intake retries go to a separate `trustflow-notifications.email.DLT` topic. Legacy events without an email event type are ignored by the email consumer. Typed events accumulated while email was disabled may be sent when enabled later; enabling email is not restricted to future cases.

To disable sending, recreate the app using just the original two Compose files:

```powershell
docker compose -f compose.yaml -f compose.integrated.yaml up -d app
```

### Local SMTP test without external delivery

`compose.email-test.yaml` enables Mailpit, a local SMTP capture server. It does not send to Gmail. Do not combine this file with `compose.email.yaml`.

```powershell
docker compose -f compose.yaml -f compose.integrated.yaml -f compose.email-test.yaml up --build -d
```

Captured messages appear at http://localhost:8025 . CI uses this mode to verify actual SMTP submission, independent in-app delivery during SMTP outages, retry/recovery, email content and replay protection. It does not prove Gmail inbox delivery. `scripts/email-integration-check.py` is for an isolated test stack after `scripts/integration-check.py`, not your live demonstration database.
