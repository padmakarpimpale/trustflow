const { chromium } = require("../frontend/node_modules/playwright");
const path = require("path");
const os = require("os");
const { spawn } = require("child_process");
const fs = require("fs");
const assert = require("assert/strict");
const root = path.resolve(__dirname, "..");
const qa = fs.mkdtempSync(path.join(os.tmpdir(), "trustflow-browser-"));
console.log("Test evidence:", qa);
const log = (name) => fs.openSync(path.join(qa, name + ".log"), "w");
const ai = spawn(
  process.env.AI_PYTHON || "python",
  ["-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", "18000"],
  { cwd: root + "/ai-service", stdio: ["ignore", log("ai"), log("ai-error")] },
);
const app = spawn(
  "java",
  [
    "-jar",
    "target/trustflow-1.0.0.jar",
    "--server.port=18080",
    "--trustflow.ai.url=http://127.0.0.1:18000/predict",
    "--spring.datasource.url=jdbc:h2:mem:uitest;DB_CLOSE_DELAY=-1",
  ],
  { cwd: root, stdio: ["ignore", log("java"), log("java-error")] },
);
let browser;
const evidence = [];
async function ready(url) {
  for (let i = 0; i < 100; i++) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {}
    await new Promise((r) => setTimeout(r, 500));
  }
  throw Error("Server not ready: " + url);
}
(async () => {
  await ready("http://127.0.0.1:18000/health");
  await ready("http://127.0.0.1:18080/app/index.html");
  browser = await chromium.launch({
    ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}),
    headless: true,
    args: ["--no-sandbox"],
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("http://127.0.0.1:18080/app/index.html");
  await page.getByRole("heading", { name: "Welcome back" }).waitFor();
  assert.equal(await page.getByText("Local demonstration accounts").count(), 0);
  assert.equal(await page.getByText("TrustFlow123!", { exact: true }).count(), 0);
  await page.screenshot({ path: path.join(qa, "login.png"), fullPage: true });
  async function signIn(user) {
    await page.getByLabel("Username", { exact: true }).fill(user);
    await page.getByLabel("Password", { exact: true }).fill("TrustFlow123!");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.getByRole("heading", { name: "Decision overview" }).waitFor();
  }
  async function signOut() {
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await page.getByRole("heading", { name: "Welcome back" }).waitFor();
  }
  async function api(path, method = "GET", body) {
    return page.evaluate(
      async ({ path, method, body }) => {
        const csrf = await (await fetch("/api/csrf")).json();
        const r = await fetch(path, {
          method,
          headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
          body: body ? JSON.stringify(body) : undefined,
        });
        return { status: r.status, body: r.status === 204 ? null : await r.json() };
      },
      { path, method, body },
    );
  }
  async function submit(title, description, amount) {
    await page.getByRole("link", { name: "New request", exact: true }).click();
    await page.getByLabel("Request title").fill(title);
    await page.getByLabel("Business justification").fill(description);
    await page.getByLabel("Amount (SGD)").fill(String(amount));
    await page.getByRole("button", { name: "Submit for evaluation" }).click();
    await page.getByRole("heading", { name: title, exact: true }).waitFor();
    const id = page.url().split("case/")[1];
    return (await api("/api/cases/" + id)).body.record;
  }
  await signIn("employee");
  const description = "Taxi fare for a client meeting approved by my manager with receipt attached";
  const low = await submit("Client meeting transport", description, 80);
  assert.equal(low.status, "APPROVED");
  assert.ok(low.modelVersion.startsWith("tfidf-lr-v2"));
  evidence.push("Real model auto-approval via React: passed");
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  const unsafe = await submit(
    "Business travel expense",
    "Taxi fare for a client meeting not approved by my manager with no receipt attached",
    20,
  );
  assert.equal(unsafe.recommendation, "APPROVE");
  assert.ok(unsafe.confidence >= 0.8);
  assert.equal(unsafe.status, "PENDING_REVIEW");
  assert.match(unsafe.escalationReason, /text-guard-v1/);
  evidence.push("High-score negated approval escalated by text guard: passed");
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  const uncertain = await submit("Business travel expense", "Taxi fare for a client meeting", 20);
  assert.equal(uncertain.status, "PENDING_REVIEW");
  assert.ok(uncertain.confidence < 0.8);
  evidence.push("Short low-score request escalated: passed");
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  const high = await submit("Conference travel", description, 1200);
  assert.equal(high.status, "PENDING_REVIEW");
  assert.equal(high.recommendation, "APPROVE");
  evidence.push("Amount escalation despite model APPROVE: passed");
  await page.screenshot({ path: path.join(qa, "case-employee.png"), fullPage: true });
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  const privateCase = await submit(
    "Personal expense",
    "Personal gaming subscription for private entertainment unrelated to work",
    80,
  );
  assert.equal(privateCase.recommendation, "REJECT");
  assert.equal(privateCase.status, "PENDING_REVIEW");
  evidence.push("Same amount, different text produces REJECT: passed");
  assert.equal((await api("/api/admin/policies")).status, 403);
  assert.equal(
    await page.evaluate(
      async () =>
        (
          await fetch("/api/cases", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: "{}",
          })
        ).status,
    ),
    403,
  );
  evidence.push("Employee admin access and missing CSRF blocked: passed");
  await signOut();
  await signIn("reviewer");
  await page.getByRole("link", { name: "Review queue", exact: true }).click();
  await page.getByRole("link", { name: "Conference travel", exact: true }).click();
  await page.getByRole("heading", { name: "Your decision" }).waitFor();
  await page.screenshot({ path: path.join(qa, "reviewer.png"), fullPage: true });
  await page.getByRole("button", { name: "Reject", exact: true }).click();
  await page
    .getByLabel("Decision reason")
    .fill("Conference expense exceeds the available department budget.");
  await page.getByRole("button", { name: "Record rejection" }).click();
  await page.getByRole("heading", { name: "Human decision: Rejected" }).waitFor();
  const detail = (await api("/api/cases/" + high.id)).body;
  assert.equal(detail.review.overridden, true);
  assert.equal(detail.audit.at(-1).action, "OVERRIDDEN");
  evidence.push("Reviewer override and audit history via React: passed");
  await signOut();
  await signIn("employee");
  await page
    .getByRole("link", { name: /Notifications/ })
    .first()
    .click();
  await page.getByRole("link", { name: `Case #${high.id} was rejected`, exact: true }).waitFor();
  await page.getByRole("button", { name: "Mark as read" }).first().click();
  evidence.push("Employee notification and mark-as-read: passed");
  await signOut();
  await signIn("employee2");
  assert.equal((await api("/api/cases/" + high.id)).status, 403);
  evidence.push("Cross-employee ownership isolation: passed");
  await signOut();
  await signIn("admin");
  await page.screenshot({ path: path.join(qa, "admin-dashboard.png"), fullPage: true });
  await page.getByRole("link", { name: "Review policies", exact: true }).click();
  await page.getByLabel("Amount threshold (SGD)").fill("50");
  await page.getByRole("button", { name: "Save policy" }).first().click();
  await page.getByRole("status").waitFor();
  await page.screenshot({ path: path.join(qa, "policies.png"), fullPage: true });
  await signOut();
  await signIn("employee");
  const changed = await submit("Policy threshold example", description, 80);
  assert.equal(changed.status, "PENDING_REVIEW");
  assert.equal((await api("/api/cases/" + low.id)).body.record.status, "APPROVED");
  evidence.push("Admin policy change affects only new submissions: passed");
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  await page.getByRole("link", { name: "New request", exact: true }).click();
  await page.getByRole("button", { name: /IT access/ }).click();
  assert.equal(await page.getByLabel("Amount (SGD)").count(), 0);
  await page.getByLabel("Request title").fill("Production investigation");
  await page
    .getByLabel("Business justification")
    .fill("Temporary administrator privileges for urgent incident investigation");
  await page.getByLabel("Access level").selectOption("ADMIN");
  await page.getByRole("button", { name: "Submit for evaluation" }).click();
  await page.getByRole("heading", { name: "Production investigation", exact: true }).waitFor();
  evidence.push("Conditional IT-access form and submission: passed");
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  await page.screenshot({ path: path.join(qa, "dashboard.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: path.join(qa, "mobile.png"), fullPage: true });
  console.log(
    "Mobile widths",
    await page.evaluate(() => ({
      page: document.documentElement.scrollWidth,
      viewport: innerWidth,
    })),
  );
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  evidence.push("Mobile layout without page overflow: passed");
  await page.setViewportSize({ width: 1440, height: 1000 });
  ai.kill("SIGTERM");
  await new Promise((r) => ai.once("exit", r));
  const fallback = await api("/api/cases", "POST", {
    type: "EXPENSE",
    title: "Service outage check",
    description,
    amount: 30,
  });
  assert.equal(fallback.status, 201);
  assert.equal(fallback.body.status, "PENDING_REVIEW");
  assert.equal(fallback.body.modelVersion, "unavailable");
  evidence.push("Python outage retains case and routes to human: passed");
  assert.deepEqual(errors, []);
  evidence.push("No browser JavaScript errors: passed");
  fs.writeFileSync(
    path.join(qa, "results.json"),
    JSON.stringify(
      {
        evidence,
        modelVersion: low.modelVersion,
        lowScore: low.confidence,
        privateScore: privateCase.confidence,
      },
      null,
      2,
    ),
  );
  console.log(evidence.join("\n"));
})()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (browser) await browser.close();
    app.kill("SIGTERM");
    ai.kill("SIGTERM");
  });
