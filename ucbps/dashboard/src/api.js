const BASE = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");

async function request(path, { method = "GET", token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return res.status === 204 ? null : res.json();
}

export const api = {
  base: BASE,
  login: (email, password) => request("/api/v1/auth/login", { method: "POST", body: { email, password } }),
  stats: (token) => request("/api/v1/admin/stats", { token }),
  incidents: (token) => request("/api/v1/admin/incidents", { token }),
  reputation: (token, hash) => request(`/api/v1/intel/reputation/${hash}`, { token }),
};
