# Deploy to Render (Free Tier)

> Cold start time: ~30s on free plan (web service sleeps after 15 min inactivity).  
> For always-on demo: upgrade to Render Starter ($7/mo).

## Prerequisites

- GitHub account with this repo pushed
- [Render](https://render.com) account (free)
- [Upstash](https://upstash.com) account (free Redis)
- [PlanetScale](https://planetscale.com) account (free MySQL) — OR use Render Managed DB ($7/mo)

---

## Step 1: Create a free MySQL database

### Option A — PlanetScale (free, no credit card)
1. Create account at https://planetscale.com
2. Create database: `ecom-db`, region: closest to you
3. Create branch `main` → go to **Connect** tab
4. Select **Connect with: Java / JDBC** → copy the connection string
5. Note: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

> PlanetScale doesn't support foreign key constraints by default.  
> Enable with: `SET foreign_key_checks=0` in branch settings, OR use Vitess-safe FK mode.

### Option B — Render Managed DB ($7/mo)
1. In Render dashboard: New → PostgreSQL (or MySQL)
2. Copy the Internal Connection String
3. Update `DB_URL` format accordingly

---

## Step 2: Create a free Redis (Upstash)

1. Go to https://upstash.com → Create Database
2. Region: same as your Render region
3. Copy: **Endpoint**, **Port**, **Password**
4. Set: `REDIS_HOST=<endpoint>`, `REDIS_PORT=<port>`, `REDIS_PASSWORD=<password>`

> Upstash uses TLS — set `REDIS_HOST` to the endpoint without `rediss://` prefix.  
> In `application.yml` ensure `spring.data.redis.ssl.enabled=true` for Upstash.

---

## Step 3: Deploy to Render

1. Push this repo to GitHub (already done if you're reading this)
2. Go to https://dashboard.render.com → **New** → **Web Service**
3. Connect your GitHub repo
4. Render detects `render.yaml` automatically — confirm settings
5. Set the following **environment variables** in Render dashboard:

| Variable | Value | Required |
|---|---|:---:|
| `DB_URL` | `jdbc:mysql://<host>/<db>?useSSL=true&serverTimezone=UTC` | ✅ |
| `DB_USERNAME` | from PlanetScale | ✅ |
| `DB_PASSWORD` | from PlanetScale | ✅ |
| `JWT_SECRET` | `openssl rand -base64 32` | ✅ |
| `REDIS_HOST` | Upstash endpoint | ✅ |
| `REDIS_PORT` | `6379` (or Upstash port) | ✅ |
| `REDIS_PASSWORD` | Upstash password | ✅ |
| `CORS_ALLOWED_ORIGINS` | your frontend URL | optional |
| `MAIL_ENABLED` | `false` | optional |

6. Click **Deploy** → wait ~3-5 min for first build

---

## Step 4: Verify

```bash
# Health check
curl https://your-app.onrender.com/api/v1/actuator/health

# Swagger UI
open https://your-app.onrender.com/swagger-ui.html
```

> Note: Swagger UI is **disabled** in prod profile.  
> To enable for demo: add env var `SPRINGDOC_SWAGGER_UI_ENABLED=true`

---

## Upstash Redis + TLS setup

Add to `application-prod.yml` if not already present:

```yaml
spring:
  data:
    redis:
      ssl:
        enabled: true
```

Or set env var: `SPRING_DATA_REDIS_SSL_ENABLED=true`

---

## Troubleshooting

| Issue | Fix |
|---|---|
| App won't start | Check `DB_URL` format includes `?useSSL=true` |
| Redis connection error | Verify Upstash host/password, enable TLS |
| Flyway migration fails | Ensure DB user has DDL permissions |
| 502 on Render | App crashed — check Render logs |
| Cold start 30s | Expected on free tier — upgrade or ping keepalive |