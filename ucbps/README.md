# UCBPS: Universal Call & SMS Bomber Protection System

Defensive protection against SMS/OTP bombing, repeated-call flooding and spam SMS.
**It never sends SMS or places calls.** Testing uses a synthetic-event simulator only.

```
Android app ──► on-device detector ──► (opt-in) hashed indicators ──► Spring Boot API ──► PostgreSQL (+Redis)
   │                                                                        │
   └── call screening / SMS alerts                  ML engine (FastAPI) ◄────┴──► React admin dashboard
```

| Folder | What | Stack |
|---|---|---|
| `android/` | Phone app: 11 screens, SMS analysis, call screening, local simulator | Kotlin, Compose, Room |
| `backend/` | Auth (JWT, roles), reports, reputation, admin stats | Java 21, Spring Boot 3, Flyway |
| `ml-engine/` | SMS spam classifier + flood anomaly detector + risk score | Python, scikit-learn, FastAPI |
| `dashboard/` | Dark admin console with charts | React + Vite |
| `simulator/` | Synthetic attack data for the backend | Python |

## Honest limits
- **Android only.** iOS does not let apps read SMS or intercept calls.
- Calls: rejected through Android's official *call screening* role. SMS: Android only lets the default SMS app hide or delete messages, so UCBPS **alerts and flags**.
- UCBPS cannot stop anyone from *sending*. It controls what you see and hear.
- A number is **never** treated as malicious by frequency alone: SMS blocks need a second, non-frequency signal; cloud flags need 3+ independent reporters.
- ML models are trained on **synthetic** data. Swap in a real licensed dataset before relying on them.
- Hashed numbers are pseudonymous, not anonymous. Uploads are opt-in and erasable (`DELETE /api/v1/me/data`).

## 1. Put it on GitHub (works from a phone browser)
1. Create a new empty repo on github.com.
2. **Add file → Upload files** → upload `ucbps.zip` → commit.
3. **Code → Codespaces → Create codespace.** In its terminal:
   ```bash
   unzip ucbps.zip -d /tmp/u && cp -a /tmp/u/ucbps/. . && rm -rf ucbps.zip /tmp/u
   git add -A && git commit -m "UCBPS" && git push
   ```
4. Open the **Actions** tab. CI runs all tests, builds the dashboard and builds the Android APK.
   Download **ucbps-debug-apk** from the finished run and install it on your Android phone.

(On a PC: unzip, `git init`, add remote, push.)

## 2. Run everything locally
```bash
docker compose up --build            # API :8080, ML :8000, dashboard :5173
python simulator/simulate.py --api http://localhost:8080 --scenario all
```
Dashboard login: `admin@example.test` / `ChangeMe123!` (from `docker-compose.yml`, change it).

Without Docker: `cd backend && mvn spring-boot:run` (needs Postgres: see `docker-compose.yml`), `cd dashboard && npm install && npm run dev`, `cd ml-engine && pip install -r requirements.txt && python train.py && uvicorn app.main:app`.

## 3. Deploy (Render, free tier)
1. render.com → **New → Blueprint** → select this repo (uses `render.yaml`: Postgres + backend + ML + dashboard).
2. Fill the prompted env vars: `ADMIN_EMAIL`, `ADMIN_PASSWORD` (8+ chars).
3. When the services are up:
   - backend → `CORS_ORIGINS` = your dashboard URL
   - dashboard → `VITE_API_URL` = your backend URL, then redeploy the dashboard.
4. Android: set the server URL in app *Settings*, or build with `gradle assembleDebug -PUCBPS_API=https://your-backend.onrender.com`.
Notes: free web services sleep after ~15 min idle (first request takes ~1 min); free Render Postgres expires after 30 days. For longer use, point `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` (and `DB_SSLMODE=require`) at Neon or Supabase.
Redis is optional: set `REDIS_ENABLED=true` and `REDIS_HOST` to use it for rate limiting, otherwise an in-memory counter is used.

## 4. Using the Android app
1. Install the APK, open it, create an account (or *Continue offline*).
2. **SMS protection** → grant SMS + contacts permission. **Call protection** → enable the call-screening role.
3. **Threat detection** → run the safe simulator scenarios and watch decisions appear.
4. Settings → toggle reporting, *Sync global threat feed*, or delete your cloud data.

## API
| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/auth/register`, `/auth/login` | none |
| GET / PUT / DELETE | `/api/v1/me`, `/me/consent`, `/me/data` | user |
| POST | `/api/v1/reports` (hash, country, type, riskScore; consent required) | user |
| GET | `/api/v1/intel/reputation/{hash}`, `/intel/feed?since=` | user |
| GET | `/api/v1/admin/stats`, `/admin/incidents` | ADMIN |
| POST | ML: `/score/sms`, `/score/window` | optional `ML_API_KEY` |

The first ADMIN is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD`; registration can only create normal users.

## Tests
- `backend`: `mvn verify` (unit + MockMvc integration on H2)
- `ml-engine`: `python -m pytest`
- `android`: `gradle :app:testDebugUnitTest` (detector rules)
- `dashboard`: `npm run build`

## Troubleshooting
- **Dashboard "Cannot reach the API"**: backend asleep, wrong `VITE_API_URL`, or `CORS_ORIGINS` not set to the dashboard URL.
- **Backend fails with JWT error**: `JWT_SECRET` must be 32+ characters.
- **Android build fails in CI**: open the failed step in Actions and check the error; the build uses AGP 8.7.2, Kotlin 2.0.21, JDK 17.
- **Calls not blocked**: the call-screening role must be granted; contacts are always allowed by design.
