# TrustFlow

Human-in-the-Loop AI Decision Review and Escalation Platform

TrustFlow is a coursework application that combines machine-learning recommendations with business rules to process expense claims and access requests. Cases that require judgement are sent to a human reviewer, and each decision is recorded in an audit history.

## Features

- Role-based login for employees, reviewers and administrators
- Expense claim and access request submission
- Machine-learning recommendations with model scores
- Automatic approval for eligible requests
- Human review and recommendation overrides
- Configurable review policies
- Notifications and audit history
- Administrative dashboard

## Technology

| Component | Technology |
|---|---|
| Frontend | React, Vite, CSS |
| Backend | Java, Spring Boot, REST APIs |
| Authentication | Spring Security |
| Persistence | Spring Data JPA, Hibernate |
| Default database | H2 |
| Optional database | MySQL |
| Model service | Python, FastAPI, scikit-learn |
| Model | TF-IDF and logistic regression |
| Optional messaging | Apache Kafka |
| Build and deployment | Maven, Docker, GitHub Actions |

## Architecture

The application contains a React frontend, a Spring Boot business backend and a separate Python inference service.

React communicates with Spring Boot through REST APIs. Spring Boot requests a prediction from Python, applies the review policies and saves the result. Kafka is available through the optional integrated configuration.

## How decisions work

A request is automatically approved only when:

- The model recommends APPROVE.
- Its score meets the configured minimum.
- The request passes the amount and access checks.
- Mandatory human review is disabled for that request type.
- The text review safeguard does not flag the request.
- The model service returns a valid response.

Otherwise, the case is sent for human review. The application does not automatically reject requests.

Default settings:

| Setting | Value |
|---|---|
| Minimum model score | 0.80 |
| Expense amount limit | SGD500 |
| Access requests | Human review required |

Administrators can change the policies. Changes apply to new submissions; existing decisions retain their history.

A separate text safeguard sends requests containing indications of negation, missing evidence or unresolved approval to a reviewer, even when the model score is high.

## Requirements

- JDK 21
- Python 3.12
- IntelliJ IDEA or another Java IDE
- Git

Node.js is required only when editing or rebuilding the React frontend. Built frontend files are included.

## Run locally

### 1. Clone the repository

Run in Windows PowerShell:

```powershell
git clone https://github.com/padmakarpimpale/trustflow.git
cd trustflow
```

### 2. Start the Python service

Run these commands one at a time:

```powershell
cd ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe train.py
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Leave this terminal running.

Training creates the model artifact using the installed dependencies. It is required during initial setup and after changing the training data or relevant dependencies.

### 3. Start Spring Boot

In IntelliJ:

1. Open the repository folder containing `pom.xml`.
2. Allow Maven to load the dependencies.
3. Select JDK 21.
4. Set the run working directory to the repository root.
5. Run `TrustflowApplication.java`.

Alternatively, open a second PowerShell window at the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

### 4. Open TrustFlow

Application: http://localhost:8080

AI API documentation: http://127.0.0.1:8000/docs

The Python service has no homepage at `/`, so opening port 8000 directly may return 404.

## Local demonstration accounts

| Username | Role |
|---|---|
| employee | Submit and track requests |
| reviewer | Review cases and record decisions |
| admin | View analytics and manage policies |

Password for these local accounts: `TrustFlow123!`

These seeded accounts are for coursework demonstrations, not public deployment.

## Example requests

Use request type **Expense claim**, title **Business travel expense** and amount **SGD20**.

| Justification | Expected routing under default settings |
|---|---|
| Taxi fare for a client meeting | Human review because of the low model score |
| Taxi fare for a client meeting approved by my manager with receipt attached | Automatic approval |
| Taxi fare for a client meeting not approved by my manager with no receipt attached | Human review because of the text safeguard |

These examples demonstrate the workflow. Statements about receipts or approval are not independently verified by the application.

## Frontend development

With Spring Boot running, open PowerShell at the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

To rebuild the frontend:

```powershell
npm run build
```

Vite writes the built files into Spring Boot's static resources. Restart the Java application after rebuilding.

## Tests

From the repository root:

```powershell
.\mvnw.cmd verify
```

For the Python service:

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m unittest -v
```

The latest local verification included 44 Java tests, 5 Python tests and an integrated browser workflow. GitHub Actions also builds the frontend and runs the Java and Python tests.

## Project structure

```text
ai-service/     Python inference service, training data and model
database/       Database scripts and sample dump
frontend/       React source and Vite configuration
scripts/        Browser checks and database export script
src/main/       Spring Boot source and resources
src/test/       Java tests
.github/        GitHub Actions workflow
pom.xml         Maven configuration
```

## Database

The default configuration uses a local H2 database stored under `data/` relative to the Java application's working directory.

Use a consistent working directory to avoid creating separate databases. Stop the application before backing up its database files.

If startup reports that the database is already in use, stop other TrustFlow instances or disconnect another H2 connection. Do not delete the database to resolve a lock.

## Model limitations

The classifier is trained on 90 synthetic examples and evaluated on 30 separately authored synthetic examples. The datasets share similar vocabulary and do not establish real-world accuracy.

The model score is uncalibrated. A score of 90% does not mean the decision has a verified 90% probability of being correct.

The text safeguard is conservative and rule-based. It may send harmless requests for review and may miss unfamiliar wording. The application does not verify receipts, confirm manager approval or automatically retrain from reviewer decisions.

TrustFlow demonstrates an oversight workflow; it is not a production financial or access-control decision system.
