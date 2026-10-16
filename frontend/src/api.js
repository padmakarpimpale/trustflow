let token;
export async function refreshCsrf() {
  const res = await fetch("/api/csrf", { credentials: "same-origin" });
  if (!res.ok) throw new Error("Cannot connect to the application.");
  token = await res.json();
}
export async function request(path, options = {}) {
  const method = options.method || "GET";
  const headers = { Accept: "application/json", ...options.headers };
  if (method !== "GET") {
    if (!token) await refreshCsrf();
    headers[token.headerName] = token.token;
  }
  if (options.body && !(options.body instanceof URLSearchParams))
    headers["Content-Type"] = "application/json";
  const res = await fetch(path, {
    ...options,
    method,
    headers,
    credentials: "same-origin",
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const error = new Error(
      body.message ||
        (res.status === 401
          ? "Please sign in again."
          : res.status === 403
            ? "Access denied or your session expired. Refresh and try again."
            : "The request could not be completed."),
    );
    error.status = res.status;
    error.fields = body.fields;
    throw error;
  }
  return res.status === 204 ? null : res.json();
}
export async function login(username, password) {
  await refreshCsrf();
  await request("/login", {
    method: "POST",
    body: new URLSearchParams({ username, password }),
  });
  await refreshCsrf(); // Authentication rotates the CSRF token.
  return request("/api/session");
}
export async function logout() {
  await request("/logout", { method: "POST" });
  token = null;
}
