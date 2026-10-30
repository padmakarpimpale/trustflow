import React, { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";
import {
  LayoutDashboard,
  Plus,
  Bell,
  SlidersHorizontal,
  LogOut,
  ArrowUpRight,
  ArrowLeft,
  Search,
  ShieldCheck,
  Check,
  Clock3,
  X,
  RefreshCw,
  FileText,
  Activity,
  ChevronRight,
  Menu,
} from "lucide-react";
import { request, login, logout } from "./api";
import "./style.css";

const label = (value) =>
  ({
    PENDING_REVIEW: "Needs review",
    APPROVED: "Approved",
    REJECTED: "Rejected",
    EXPENSE: "Expense claim",
    ACCESS: "IT access",
    SUBMITTER: "Employee",
    REVIEWER: "Reviewer",
    ADMIN: "Administrator",
    APPROVE: "Approve",
    REJECT: "Reject",
    REVIEW: "Review",
  })[value] || value;
const money = (value) =>
  new Intl.NumberFormat("en-SG", { style: "currency", currency: "SGD" }).format(
    value,
  );
const date = (value) =>
  value
    ? new Date(value).toLocaleString("en-SG", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "—";
const go = (route) => {
  window.location.hash = route;
};
function Badge({ value }) {
  return (
    <span className={"badge " + value.toLowerCase()}>
      <span />
      {label(value)}
    </span>
  );
}
function ErrorBox({ error }) {
  return (
    error && (
      <div className="error" role="alert">
        {error.message || error}
      </div>
    )
  );
}
function Empty({ title, detail }) {
  return (
    <div className="empty">
      <FileText size={32} />
      <h3>{title}</h3>
      <p>{detail}</p>
    </div>
  );
}
function App() {
  const [session, setSession] = useState(null),
    [booting, setBooting] = useState(true),
    [bootError, setBootError] = useState(null);
  const [route, setRoute] = useState(location.hash.slice(1) || "dashboard");
  const [records, setRecords] = useState({ total: 0 }),
    [notices, setNotices] = useState([]),
    [error, setError] = useState(null),
    [loading, setLoading] = useState(false),
    [menu, setMenu] = useState(false);
  useEffect(() => {
    const change = () => {
      setRoute(location.hash.slice(1) || "dashboard");
      setError(null);
      setMenu(false);
    };
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  useEffect(() => {
    request("/api/session")
      .then(setSession)
      .catch((e) => {
        if (e.status !== 401) setBootError(e);
      })
      .finally(() => setBooting(false));
  }, []);
  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const [cases, alerts] = await Promise.all([
        request("/api/cases/summary"),
        request("/api/notifications"),
      ]);
      setRecords(cases);
      setNotices(alerts);
    } catch (e) {
      if (e.status === 401) setSession(null);
      else setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    if (session) reload();
  }, [session]);
  if (booting)
    return <div className="loading-page">Opening your workspace…</div>;
  if (!session)
    return (
      <Login
        onLogin={(user) => {
          setSession(user);
          go("dashboard");
        }}
        initialError={bootError}
      />
    );
  const unread = notices.filter((n) => !n.read).length;
  const titles = {
    dashboard: "Overview",
    cases: "All cases",
    pending: "Review queue",
    new: "New request",
    notifications: "Notifications",
    policies: "Review policies",
  };
  const nav = [
    ["dashboard", LayoutDashboard, "Overview"],
    [
      "cases",
      FileText,
      session.role === "SUBMITTER" ? "My cases" : "All cases",
    ],
    ...(session.role === "REVIEWER"
      ? [["pending", Clock3, "Review queue"]]
      : []),
    ["notifications", Bell, "Notifications"],
    ...(session.role === "ADMIN"
      ? [["policies", SlidersHorizontal, "Review policies"]]
      : []),
  ];
  return (
    <div className="app-shell">
      <aside className={"sidebar " + (menu ? "open" : "")}>
        <a className="brand" href="#dashboard">
          <span className="brand-icon">
            <ShieldCheck size={23} />
          </span>
          TrustFlow<span className="brand-dot">.</span>
        </a>
        <div className="workspace-label">DECISION WORKSPACE</div>
        <nav>
          {nav.map(([key, Icon, title]) => (
            <a
              key={key}
              href={"#" + key}
              className={route === key ? "active" : ""}
            >
              <Icon size={18} />
              {title}
              {key === "notifications" && unread > 0 && <small>{unread}</small>}
            </a>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="account">
            <span className="avatar">{session.username[0].toUpperCase()}</span>
            <div>
              <strong>{session.username}</strong>
              <small>{label(session.role)}</small>
            </div>
            <button
              className="icon-button"
              title="Sign out"
              aria-label="Sign out"
              onClick={async () => {
                try {
                  await logout();
                  setSession(null);
                } catch (e) {
                  setError(e);
                }
              }}
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <button
            className="icon-button mobile-menu"
            aria-label="Open navigation"
            onClick={() => setMenu(!menu)}
          >
            <Menu />
          </button>
          <div>
            <span className="breadcrumb">Workspace</span>
            <ChevronRight size={13} />
            <strong>{titles[route] || "Case details"}</strong>
          </div>
          <div>
            {session.aiMode !== "http" && (
              <span className="mode">Simulation mode</span>
            )}
            <button
              className="icon-button"
              aria-label="Refresh workspace"
              onClick={reload}
            >
              <RefreshCw size={17} className={loading ? "spin" : ""} />
            </button>
          </div>
        </header>
        <main>
          <ErrorBox error={error} />
          {route.startsWith("case/") ? (
            <Detail
              id={route.split("/")[1]}
              session={session}
              onChange={reload}
            />
          ) : route === "new" ? (
            session.role === "SUBMITTER" ? (
              <NewCase
                onCreated={async (c) => {
                  await reload();
                  go("case/" + c.id);
                }}
              />
            ) : (
              <Empty
                title="Employee access required"
                detail="Only employees can submit a request."
              />
            )
          ) : route === "notifications" ? (
            <Notifications notices={notices} refresh={reload} />
          ) : route === "policies" ? (
            session.role === "ADMIN" ? (
              <Policies />
            ) : (
              <Empty title="Administrator access required" />
            )
          ) : (
            <Dashboard
              records={records}
              session={session}
              route={route}
              loading={loading}
            />
          )}
        </main>
        <footer>
          TrustFlow <span>Human-in-the-loop decision review</span>
        </footer>
      </div>
    </div>
  );
}
function Login({ onLogin, initialError }) {
  const [username, setUsername] = useState(""),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(initialError);
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onLogin(await login(username, password));
    } catch (e) {
      setError(e.status === 401 ? "Username or password is incorrect." : e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login">
      <section className="login-panel">
        <form onSubmit={submit}>
          <a className="brand login-brand" href="#">
            TrustFlow
          </a>
          <h2>Welcome back</h2>
          <p className="muted">Sign in to manage requests and decisions.</p>
          <ErrorBox error={error} />
          <label>
            Username
            <input
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter your username"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
            />
          </label>
          <button className="primary full" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
            <ArrowUpRight size={18} />
          </button>
        </form>
      </section>
    </div>
  );
}
function Dashboard({ records, session, route, loading }) {
  const [search, setSearch] = useState(""),
    [filter, setFilter] = useState("ALL"),
    [page, setPage] = useState(1),
    [analytics, setAnalytics] = useState(null);
  useEffect(() => {
    setPage(1);
    setFilter(route === "pending" ? "PENDING_REVIEW" : "ALL");
  }, [route]);
  useEffect(() => {
    if (session.role === "ADMIN")
      request("/api/admin/analytics")
        .then(setAnalytics)
        .catch(() => setAnalytics(null));
  }, [session, records]);
  const [result, setResult] = useState({
    content: [],
    totalElements: 0,
    totalPages: 0,
  });
  const [queryError, setQueryError] = useState(null);
  const [queryLoading, setQueryLoading] = useState(false);
  const [type, setType] = useState("ALL"),
    [sort, setSort] = useState("newest");
  const count = (status) => records[status] || 0;
  const filtered = result.content;
  const pages = Math.max(1, result.totalPages);
  useEffect(() => setPage(1), [search, filter, type, sort]);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setQueryLoading(true);
      setQueryError(null);
      const params = new URLSearchParams({
        page: String(page - 1),
        size: "8",
        q: search,
        sort,
      });
      if (filter !== "ALL") params.set("status", filter);
      if (type !== "ALL") params.set("type", type);
      try {
        const data = await request("/api/cases/page?" + params, {
          signal: controller.signal,
        });
        if (active) {
          setResult(data);
          if (data.totalPages > 0 && page > data.totalPages)
            setPage(data.totalPages);
        }
      } catch (e) {
        if (active && e.name !== "AbortError") setQueryError(e);
      } finally {
        if (active) setQueryLoading(false);
      }
    }, 200);
    return () => {
      active = false;
      clearTimeout(timer);
      controller.abort();
    };
  }, [page, search, filter, type, sort, records]);
  const overview = route === "dashboard";
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>
            {overview
              ? "Decision overview"
              : route === "pending"
                ? "Ready for your review"
                : session.role === "SUBMITTER"
                  ? "Your requests"
                  : "All cases"}
          </h1>
        </div>
        {session.role === "SUBMITTER" && (
          <a className="primary" href="#new">
            <Plus size={18} />
            New request
          </a>
        )}
      </div>
      {overview && (
        <>
          <div className="stats">
            {[
              ["Total cases", records.total, FileText, "total"],
              ["Needs review", count("PENDING_REVIEW"), Clock3, "pending"],
              ["Approved", count("APPROVED"), Check, "approved"],
              ["Rejected", count("REJECTED"), X, "rejected"],
            ].map(([name, value, Icon, color]) => (
              <div className="stat" key={name}>
                <div>
                  <span>{name}</span>
                  <Icon size={19} />
                </div>
                <strong>{value}</strong>
                <span className={"stat-line " + color} />
              </div>
            ))}
          </div>
          {analytics && (
            <div className="analytics">
              <span>
                <b>{analytics.reviewCount}</b> Human decisions
              </span>
              <span>
                <b>{analytics.overrides}</b> Recommendation overrides
              </span>
              <span>
                <b>{analytics.reviewMinutes} min</b> Average time to decision
              </span>
            </div>
          )}
        </>
      )}
      <section className="card case-list">
        <div className="section-heading">
          <div>
            <h2>{overview ? "Case activity" : "Requests"}</h2>
            <p>
              {result.totalElements}{" "}
              {result.totalElements === 1 ? "case" : "cases"} in this view
            </p>
          </div>
          <div className="search">
            <Search size={17} />
            <input
              aria-label="Search cases"
              placeholder="Search title, ID or employee"
              maxLength={120}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        <div className="filters">
          {["ALL", "PENDING_REVIEW", "APPROVED", "REJECTED"].map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={filter === f ? "selected" : ""}
            >
              {f === "ALL" ? "All cases" : label(f)}
            </button>
          ))}
        </div>
        <div className="filters">
          <select
            aria-label="Case type"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            <option value="ALL">All types</option>
            <option value="EXPENSE">Expense claims</option>
            <option value="ACCESS">Access requests</option>
          </select>
          <select
            aria-label="Sort cases"
            value={sort}
            onChange={(e) => setSort(e.target.value)}
          >
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
            <option value="amount">Highest amount</option>
          </select>
        </div>
        <ErrorBox error={queryError} />
        {queryLoading ? (
          <p className="empty">Loading cases…</p>
        ) : !filtered.length ? (
          <Empty
            title={records.total ? "No matching cases" : "No requests yet"}
            detail={
              records.total
                ? "Try another search or status filter."
                : "New requests will appear here with their status and recommendation."
            }
          />
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Case</th>
                    <th>Type / amount</th>
                    <th>Status</th>
                    <th>Recommendation</th>
                    <th>Submitted</th>
                    <th>
                      <span className="sr-only">Open</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <a className="case-title" href={"#case/" + c.id}>
                          {c.title}
                        </a>
                        <small>
                          #{String(c.id).padStart(3, "0")} · {c.owner}
                        </small>
                      </td>
                      <td>
                        {label(c.type)}
                        <small>
                          {c.type === "EXPENSE"
                            ? money(c.amount)
                            : c.accessLevel === "ADMIN"
                              ? "Administrator access"
                              : "Standard access"}
                        </small>
                      </td>
                      <td>
                        <Badge value={c.status} />
                      </td>
                      <td>
                        {label(c.recommendation)}
                        <small>
                          {c.modelVersion === "unavailable"
                            ? "Model unavailable"
                            : `${Math.round(c.confidence * 100)}% model score`}
                        </small>
                      </td>
                      <td className="date">{date(c.createdAt)}</td>
                      <td>
                        <a
                          aria-label={"Open case " + c.id}
                          href={"#case/" + c.id}
                        >
                          <ArrowUpRight size={18} />
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="pagination">
              <span>
                Page {page} of {pages}
              </span>
              <div>
                <button disabled={page === 1} onClick={() => setPage(page - 1)}>
                  Previous
                </button>
                <button
                  disabled={page === pages}
                  onClick={() => setPage(page + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </>
  );
}
function NewCase({ onCreated }) {
  const [type, setType] = useState("EXPENSE"),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  async function submit(e) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const record = await request("/api/cases", {
        method: "POST",
        body: JSON.stringify({
          type,
          title: f.get("title"),
          description: f.get("description"),
          amount: type === "EXPENSE" ? Number(f.get("amount")) : null,
          accessLevel: type === "ACCESS" ? f.get("accessLevel") : null,
        }),
      });
      await onCreated(record);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <a className="back" href="#dashboard">
        <ArrowLeft size={16} />
        Back to overview
      </a>
      <div className="page-heading">
        <div>
          <h1>What do you need approved?</h1>
          <p>Give the model and your reviewer the context they need.</p>
        </div>
      </div>
      <div className="form-layout">
        <form className="card form-card" onSubmit={submit}>
          <h2>Request details</h2>
          <div className="type-selector">
            {["EXPENSE", "ACCESS"].map((t) => (
              <button
                type="button"
                key={t}
                className={type === t ? "selected" : ""}
                onClick={() => setType(t)}
              >
                {t === "EXPENSE" ? <FileText /> : <ShieldCheck />}
                <span>
                  <strong>{label(t)}</strong>
                  <small>
                    {t === "EXPENSE"
                      ? "Travel, supplies or business costs"
                      : "Company systems and permissions"}
                  </small>
                </span>
                {type === t && <Check size={18} />}
              </button>
            ))}
          </div>
          <ErrorBox error={error} />
          <label>
            Request title
            <input
              name="title"
              placeholder={
                type === "EXPENSE"
                  ? "e.g. Client meeting travel"
                  : "e.g. Reporting dashboard access"
              }
              maxLength={120}
              required
            />
            {error?.fields?.title && (
              <small className="field-error">{error.fields.title}</small>
            )}
          </label>
          <label>
            Business justification
            <textarea
              name="description"
              rows={5}
              minLength={10}
              maxLength={2000}
              placeholder="Explain the purpose, business need and any supporting information…"
              required
            />
            {error?.fields?.description && (
              <small className="field-error">{error.fields.description}</small>
            )}
            <small>
              Include approval or receipt details where relevant. Do not include
              passwords or confidential data.
            </small>
          </label>
          {type === "EXPENSE" ? (
            <label>
              Amount (SGD)
              <input
                key="amount"
                type="number"
                name="amount"
                min="0.01"
                max="9999999999.99"
                step="0.01"
                placeholder="0.00"
                required
              />
            </label>
          ) : (
            <label>
              Access level
              <select key="access" name="accessLevel">
                <option value="STANDARD">Standard access</option>
                <option value="ADMIN">Administrator access</option>
              </select>
              <small>Administrator access always requires human review.</small>
            </label>
          )}
          <div className="form-actions">
            <a href="#dashboard">Cancel</a>
            <button className="primary" disabled={busy}>
              {busy ? "Evaluating request…" : "Submit for evaluation"}
              <ArrowUpRight size={18} />
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
function Detail({ id, session, onChange }) {
  const [data, setData] = useState(null),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false),
    [decision, setDecision] = useState("APPROVED");
  async function load() {
    setError(null);
    try {
      setData(await request("/api/cases/" + id));
    } catch (e) {
      setError(e);
    }
  }
  useEffect(() => {
    setData(null);
    load();
  }, [id]);
  async function review(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = new FormData(e.currentTarget);
    try {
      const result = await request(`/api/cases/${id}/review`, {
        method: "POST",
        body: JSON.stringify({
          version: data.record.version,
          decision,
          reason: form.get("reason"),
        }),
      });
      setData(result);
      await onChange();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (!data)
    return (
      <>
        <a className="back" href="#cases">
          <ArrowLeft size={16} />
          Back to cases
        </a>
        <ErrorBox error={error} />
        {!error && <p>Loading case…</p>}
      </>
    );
  const c = data.record;
  return (
    <>
      <a className="back" href="#cases">
        <ArrowLeft size={16} />
        Back to cases
      </a>
      <div className="page-heading">
        <div>
          <span className="eyebrow">
            CASE #{String(c.id).padStart(3, "0")} ·{" "}
            {label(c.type).toUpperCase()}
          </span>
          <h1>{c.title}</h1>
          <p>
            Submitted by {c.owner} · {date(c.createdAt)}
          </p>
        </div>
        <Badge value={c.status} />
      </div>
      <ErrorBox error={error} />
      <div className="detail-grid">
        <section className="card detail-card">
          <h2>Request details</h2>
          <div className="detail-facts">
            <div>
              <span>Request type</span>
              <strong>{label(c.type)}</strong>
            </div>
            <div>
              <span>{c.type === "EXPENSE" ? "Amount" : "Access level"}</span>
              <strong>
                {c.type === "EXPENSE"
                  ? money(c.amount)
                  : c.accessLevel === "ADMIN"
                    ? "Administrator"
                    : "Standard"}
              </strong>
            </div>
          </div>
          <h3>Business justification</h3>
          <p className="preserve">{c.description}</p>
          <div className="routing">
            <Clock3 size={19} />
            <div>
              <strong>Routing decision</strong>
              <p>{c.escalationReason}</p>
            </div>
          </div>
        </section>
        <section className="card detail-card recommendation">
          <div className="section-heading">
            <h2>Model recommendation</h2>
            <Activity size={21} />
          </div>
          <strong className="recommendation-label">
            {label(c.recommendation)}
          </strong>
          <div className="score-label">
            <span>Model score</span>
            <strong>{Math.round(c.confidence * 100)}%</strong>
          </div>
          <div className="score-track">
            <span style={{ width: `${c.confidence * 100}%` }} />
          </div>
          <small>
            Score indicates model preference, not verified accuracy.
          </small>
          <details className="model-details">
            <summary>Model details</summary>
            <p>{c.explanation}</p>
            <small>
              Model: {c.modelVersion}
              <br />
              Policy version: {c.policyVersion}
              <br />
              {c.policyMinConfidence == null
                ? "Historical policy snapshot unavailable"
                : `Saved policy: minimum score ${c.policyMinConfidence}, amount limit ${c.policyAmountThreshold}, mandatory review ${c.policyAlwaysReview ? "yes" : "no"}`}
              <br />
              Routing rules: {c.routingVersion || "not recorded"}
            </small>
            <small>
              Trained on synthetic examples. Supporting documents are not
              verified.
            </small>
          </details>
        </section>
      </div>
      {data.review && (
        <section className="card decision-card">
          <ShieldCheck />
          <div>
            <h2>Human decision: {label(data.review.decision)}</h2>
            <p className="preserve">{data.review.reason}</p>
            <small>
              {data.review.reviewer} · {date(data.review.createdAt)}
            </small>
            {data.review.overridden && (
              <span className="override">
                Overrides the model recommendation
              </span>
            )}
          </div>
        </section>
      )}
      {c.status === "PENDING_REVIEW" && session.role === "REVIEWER" && (
        <form className="card form-card review-form" onSubmit={review}>
          <div className="section-heading">
            <div>
              <h2>Your decision</h2>
              <p>
                Review the evidence and record your reasoning. This decision is
                final.
              </p>
            </div>
            <ShieldCheck />
          </div>
          <div className="decision-options">
            <button
              type="button"
              className={decision === "APPROVED" ? "selected" : ""}
              onClick={() => setDecision("APPROVED")}
            >
              <Check size={18} />
              Approve
            </button>
            <button
              type="button"
              className={decision === "REJECTED" ? "selected reject" : ""}
              onClick={() => setDecision("REJECTED")}
            >
              <X size={18} />
              Reject
            </button>
          </div>
          <label>
            Decision reason
            <textarea
              name="reason"
              rows={3}
              minLength={5}
              maxLength={1000}
              placeholder="Explain why you are approving or rejecting this request…"
              required
            />
          </label>
          <div className="form-actions">
            <button type="button" onClick={load}>
              Refresh case
            </button>
            <button className="primary" disabled={busy}>
              {busy
                ? "Recording…"
                : "Record " +
                  (decision === "APPROVED" ? "approval" : "rejection")}
            </button>
          </div>
        </form>
      )}
      <section className="card detail-card">
        <h2>Decision history</h2>
        <p className="muted"></p>
        <div className="timeline">
          {data.audit.map((a, i) => (
            <div className="timeline-item" key={i}>
              <span className="timeline-dot" />
              <div>
                <strong>
                  {a.action === "SUBMITTED"
                    ? "Request submitted"
                    : a.action === "EVALUATED"
                      ? "Recommendation and policy evaluated"
                      : a.action === "OVERRIDDEN"
                        ? "Recommendation overridden"
                        : "Human review recorded"}
                </strong>
                <small>
                  {a.actor} · {date(a.createdAt)}
                </small>
                <p>{a.detail}</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
function Notifications({ notices, refresh }) {
  const [error, setError] = useState(null),
    [busy, setBusy] = useState(null);
  async function read(id) {
    setBusy(id);
    try {
      await request(`/api/notifications/${id}/read`, { method: "POST" });
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Notifications</h1>
          <p>Request updates and cases waiting for your attention.</p>
        </div>
      </div>
      <ErrorBox error={error} />
      <section className="card">
        {!notices.length ? (
          <Empty
            title="You're all caught up"
            detail="New case updates will appear here."
          />
        ) : (
          notices.map((n) => (
            <div key={n.id} className={"notice " + (!n.read ? "unread" : "")}>
              <Bell size={20} />
              <div>
                <a href={"#case/" + n.caseId}>{n.message}</a>
                <small>{date(n.createdAt)}</small>
              </div>
              {!n.read && (
                <button disabled={busy === n.id} onClick={() => read(n.id)}>
                  Mark as read
                </button>
              )}
            </div>
          ))
        )}
      </section>
    </>
  );
}
function PolicyHistory({ id }) {
  const [data, setData] = useState(null),
    [error, setError] = useState(null),
    [page, setPage] = useState(0);
  useEffect(() => {
    let active = true;
    request(`/api/admin/policies/${id}/history?page=${page}`)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((error) => {
        if (active) setError(error);
      });
    return () => {
      active = false;
    };
  }, [id, page]);
  return (
    <details className="model-details">
      <summary>Policy change history</summary>
      <ErrorBox error={error} />
      {data?.content.map((change) => (
        <div key={change.id}>
          <strong>
            {change.actor} · {date(change.createdAt)}
          </strong>
          <p>Before: {change.beforeValues}</p>
          <p>After: {change.afterValues}</p>
        </div>
      ))}
      {data && !data.content.length && <p>No policy changes recorded.</p>}
      {data?.totalPages > 1 && (
        <div className="pagination">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            Previous
          </button>
          <span>
            {page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      )}
    </details>
  );
}
function Policies() {
  const [policies, setPolicies] = useState([]),
    [error, setError] = useState(null),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(null);
  async function load() {
    setPolicies(await request("/api/admin/policies"));
  }
  useEffect(() => {
    load().catch(setError);
  }, []);
  async function save(e, p) {
    e.preventDefault();
    setBusy(p.id);
    setError(null);
    setMessage("");
    const f = new FormData(e.currentTarget);
    try {
      await request("/api/admin/policies/" + p.id, {
        method: "PUT",
        body: JSON.stringify({
          version: p.version,
          amountThreshold: Number(
            f.get("amountThreshold") ?? p.amountThreshold,
          ),
          minConfidence: Number(f.get("minConfidence")),
          alwaysReview: f.has("alwaysReview"),
        }),
      });
      await load();
      setMessage("Policy saved. The new rules apply to future submissions.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Review policies</h1>
          <p>Define when automation ends and human judgement begins.</p>
        </div>
      </div>
      <ErrorBox error={error} />
      {message && (
        <div className="success" role="status">
          {message}
        </div>
      )}
      <div className="detail-grid">
        {policies.map((p) => (
          <form
            className="card form-card"
            key={p.id + ":" + p.version}
            onSubmit={(e) => save(e, p)}
          >
            <h2>{label(p.type)}</h2>
            <p className="muted">Policy version {p.version}</p>
            {p.type === "EXPENSE" && (
              <label>
                Amount threshold (SGD)
                <input
                  name="amountThreshold"
                  type="number"
                  min="0"
                  step="0.01"
                  max="9999999999.99"
                  defaultValue={p.amountThreshold}
                  required
                />
                <small>Amounts above this limit require review.</small>
              </label>
            )}
            <label>
              Minimum model score
              <input
                name="minConfidence"
                type="number"
                min="0"
                max="1"
                step="0.01"
                defaultValue={p.minConfidence}
                required
              />
              <small>Scores below this value require review. Enter 0–1.</small>
            </label>
            <label className="checkbox">
              <input
                type="checkbox"
                name="alwaysReview"
                defaultChecked={p.alwaysReview}
              />
              Always require human review
            </label>
            <p className="model-note">
              {p.type === "ACCESS"
                ? "Administrator access always requires review, regardless of this setting."
                : "REJECT and REVIEW recommendations always go to a human."}
            </p>
            <button className="primary" disabled={busy === p.id}>
              {busy === p.id ? "Saving…" : "Save policy"}
            </button>
            <PolicyHistory id={p.id} />
          </form>
        ))}
      </div>
      <p className="muted">
        Existing decisions and case history are not changed by policy updates.
      </p>
    </>
  );
}
createRoot(document.getElementById("root")).render(<App />);
