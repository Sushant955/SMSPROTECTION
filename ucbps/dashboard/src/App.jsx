import { useCallback, useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "./api.js";

const TYPE_LABELS = {
  SMS_FLOOD: "SMS flood",
  OTP_FLOOD: "OTP flood",
  CALL_FLOOD: "Call flood",
  SPAM_SMS: "Spam SMS",
};
const COLORS = ["#22d3ee", "#a78bfa", "#f472b6", "#fbbf24"];

function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const r = await api.login(email, password);
      if (r.role !== "ADMIN") throw new Error("not-admin");
      onLogin(r.token);
    } catch (err) {
      setError(err.message === "not-admin" ? "This console is for administrators only." : "Login failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="center">
      <form className="card login" onSubmit={submit}>
        <h1>🛡 UCBPS</h1>
        <p className="muted">Threat intelligence console</p>
        <input type="email" placeholder="Admin email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <p className="err">{error}</p>}
        <button disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
        <p className="muted small">API: {api.base}</p>
      </form>
    </div>
  );
}

function Stat({ label, value, tone }) {
  return (
    <div className={`card stat ${tone || ""}`}>
      <div className="muted">{label}</div>
      <div className="big">{value ?? "–"}</div>
    </div>
  );
}

function Lookup({ token }) {
  const [hash, setHash] = useState("");
  const [res, setRes] = useState(null);
  const [err, setErr] = useState("");

  async function go(e) {
    e.preventDefault();
    setErr("");
    setRes(null);
    try {
      setRes(await api.reputation(token, hash.trim().toLowerCase()));
    } catch {
      setErr("Enter a valid 64-character hash.");
    }
  }

  return (
    <div className="card">
      <h3>Number reputation lookup</h3>
      <form className="row" onSubmit={go}>
        <input placeholder="64-char number hash" value={hash} onChange={(e) => setHash(e.target.value)} />
        <button>Check</button>
      </form>
      {err && <p className="err">{err}</p>}
      {res && (
        <p>
          {res.flagged ? "🚩 Flagged" : "✅ Not flagged"} · score {res.score.toFixed(2)} · {res.distinctReporters} independent
          reporters · {res.reportCount} reports
        </p>
      )}
    </div>
  );
}

function Dashboard({ token, onLogout }) {
  const [stats, setStats] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [s, i] = await Promise.all([api.stats(token), api.incidents(token)]);
      setStats(s);
      setIncidents(i);
      setError("");
    } catch (err) {
      if (err.status === 401 || err.status === 403) onLogout();
      else setError("Cannot reach the API. If the backend is on a free host it may be waking up — retrying.");
    }
  }, [token, onLogout]);

  useEffect(() => {
    load();
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, [load]);

  const typeData = stats ? Object.entries(stats.byType).map(([k, v]) => ({ name: TYPE_LABELS[k] || k, value: v })) : [];
  const countryData = stats ? stats.byCountry.slice(0, 12) : [];

  return (
    <div className="page">
      <header>
        <h1>🛡 UCBPS Threat Console</h1>
        <button className="ghost" onClick={onLogout}>Sign out</button>
      </header>
      {error && <p className="err">{error}</p>}

      <div className="grid4">
        <Stat label="Protected devices" value={stats?.protectedDevices} />
        <Stat label="Suspicious events" value={stats?.totalEvents} tone="warn" />
        <Stat label="SMS threats" value={stats?.smsEvents} />
        <Stat label="Call threats" value={stats?.callEvents} />
      </div>
      <div className="grid4">
        <Stat label="Flagged numbers (≥3 independent reporters)" value={stats?.flaggedNumbers} tone="danger" />
      </div>

      <div className="grid2">
        <div className="card">
          <h3>Threats by type</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={typeData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#243044" />
              <XAxis dataKey="name" stroke="#8aa0bd" />
              <YAxis allowDecimals={false} stroke="#8aa0bd" />
              <Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #243044" }} />
              <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                {typeData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="card">
          <h3>Global threat distribution (by country)</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={countryData} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" stroke="#243044" />
              <XAxis type="number" allowDecimals={false} stroke="#8aa0bd" />
              <YAxis type="category" dataKey="country" stroke="#8aa0bd" width={40} />
              <Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #243044" }} />
              <Bar dataKey="count" fill="#22d3ee" radius={[0, 6, 6, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="card">
        <h3>Recent security incidents</h3>
        <div className="scroll">
          <table>
            <thead>
              <tr><th>When</th><th>Type</th><th>Country</th><th>Risk</th><th>Number (hash prefix)</th></tr>
            </thead>
            <tbody>
              {incidents.length === 0 && <tr><td colSpan="5" className="muted">No incidents yet.</td></tr>}
              {incidents.map((i) => (
                <tr key={i.id}>
                  <td>{new Date(i.createdAt).toLocaleString()}</td>
                  <td>{TYPE_LABELS[i.type] || i.type}</td>
                  <td>{i.country}</td>
                  <td><span className={`pill ${i.riskScore >= 0.75 ? "danger" : "warn"}`}>{Math.round(i.riskScore * 100)}%</span></td>
                  <td className="mono">{i.numberHashPrefix}…</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <Lookup token={token} />
    </div>
  );
}

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem("ucbps_token") || "");
  const login = (t) => { sessionStorage.setItem("ucbps_token", t); setToken(t); };
  const logout = useCallback(() => { sessionStorage.removeItem("ucbps_token"); setToken(""); }, []);
  return token ? <Dashboard token={token} onLogout={logout} /> : <Login onLogin={login} />;
}
