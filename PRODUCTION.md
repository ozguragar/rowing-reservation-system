# Production Deployment

Checklist and runbook for standing up the Rowing Club system in a real environment. Pair this with [ARCHITECTURE.md](./ARCHITECTURE.md) for the technical reference and [README.md](./README.md) for developer quick-start.

## Pre-flight checklist

Before you run `docker compose up` in production, confirm every item below.

### Secrets (mandatory)

- [ ] Copy `.env.example` to `.env` at the repo root
- [ ] Set `POSTGRES_PASSWORD` to a strong random value
      `openssl rand -base64 24`
- [ ] Set `SPRING_DATASOURCE_PASSWORD` to the **same** value as `POSTGRES_PASSWORD`
- [ ] Set `JWT_SECRET` to a ≥32-byte base64-encoded random value
      `openssl rand -base64 48`
- [ ] Confirm `.env` is gitignored (`grep -n '^\.env' .gitignore`)
- [ ] Set `APP_CORS_ALLOWED_ORIGINS` to your real frontend domain (comma-separated if multiple)

If you start the backend with `SPRING_PROFILES_ACTIVE=prod` while `JWT_SECRET` is still the shipped default, the app **will refuse to boot** with a clear error — this is a deliberate guard.

### Frontend build-time config

- [ ] Rebuild the frontend image with your real API URL:
      ```bash
      cd frontend
      rm -rf .next
      NEXT_PUBLIC_API_URL=https://api.your-domain.example.com/api npx next build
      cd ..
      docker build -t rowing-frontend:prod -f frontend/Dockerfile frontend/
      ```
      `NEXT_PUBLIC_API_URL` is **baked into the JS bundle** at build time. Setting it as an env var on a running container does nothing.

### TLS termination

- [ ] Front both containers with a reverse proxy. See `nginx/nginx.conf.example` for a working starter.
- [ ] Obtain real certs (Let's Encrypt: `certbot --nginx -d your-domain.example.com -d api.your-domain.example.com`).
- [ ] Confirm HSTS, HTTPS redirect, and security headers are live:
      ```bash
      curl -sI https://your-domain.example.com | grep -i strict-transport
      ```

### Database

- [ ] Verify the host port `5432` is **not** exposed (`docker-compose.prod.yml` already overrides this). Postgres should be reachable only from the Docker network.
- [ ] Plan backups:
      ```bash
      # Daily dump to /backups
      docker exec rowing-db pg_dump -U rowing -d rowingclub > /backups/rowingclub-$(date +%F).sql
      ```
- [ ] Test restore on a spare environment at least once before go-live.
- [ ] Document the retention policy (7 daily + 4 weekly + 6 monthly is a reasonable default).

### Monitoring

- [ ] Point an uptime monitor at `https://api.your-domain.example.com/api/health/ready` (liveness + DB check). 15 s interval, 3 failed probes → alert.
- [ ] Also probe `https://your-domain.example.com/` for the frontend.
- [ ] If using a log sink (Datadog, CloudWatch, Loki, ELK): the `prod` Spring profile emits single-line JSON already, so your agent should ingest it as-is.

### Firewall / network

- [ ] Only ports 80 and 443 are open on the host.
- [ ] SSH (22) restricted to your jump host / bastion.
- [ ] Direct access to ports 3000, 8081, 5432 from the public internet is **denied**.

---

## Deployment runbook

### Initial deployment

```bash
# 1. Clone
git clone <repo> rowingclub && cd rowingclub

# 2. Configure secrets
cp .env.example .env
$EDITOR .env                                   # fill in every placeholder

# 3. Build both images with your production API URL baked in
docker build -t rowing-backend:prod -f backend/Dockerfile backend/

cd frontend
rm -rf .next
NEXT_PUBLIC_API_URL=https://api.your-domain.example.com/api npx next build
cd ..
docker build -t rowing-frontend:prod -f frontend/Dockerfile frontend/

# 4. Bring up the stack with the prod overlay
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 5. Wait for healthchecks to pass
docker compose ps
# Both backend + frontend should show "healthy" within ~45 seconds.

# 6. Verify
curl -s https://api.your-domain.example.com/api/health/ready    # {"status":"UP","db":"UP"}
curl -s https://your-domain.example.com/                        # Returns Next.js HTML
```

### Seed the first admin (since DataSeeder is disabled in prod)

Option A — via the register endpoint, then promote to admin manually in SQL:

```bash
curl -X POST https://api.your-domain.example.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Your Name","email":"you@example.com","password":"temp-strong-pass","role":"STUDENT"}'

docker exec rowing-db psql -U rowing -d rowingclub \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';"
```

Option B — insert directly:

```sql
-- Compute BCrypt hash offline or in a throwaway container:
--   docker run --rm eclipse-temurin:21 bash -c "<one-liner>"
-- Then:
INSERT INTO users (full_name, email, password_hash, role, is_finished_basic_training, is_on_school_team, lessons_attended)
VALUES ('You', 'you@example.com', '$2a$10$...', 'ADMIN', true, false, 0);
```

Log in, then use the admin UI for everything else.

### Zero-downtime deploy (rolling)

```bash
# Backend
docker build -t rowing-backend:prod -f backend/Dockerfile backend/
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps backend
# Spring Boot `server.shutdown: graceful` drains in-flight requests for up to 30s.

# Frontend
cd frontend
NEXT_PUBLIC_API_URL=https://api.your-domain.example.com/api npx next build
cd ..
docker build -t rowing-frontend:prod -f frontend/Dockerfile frontend/
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps frontend
```

For true zero downtime across a single-host deploy, run two backend replicas behind nginx and take them out of rotation one at a time. (Single-replica deploys have a 5–30 s blip during restart; healthchecks gate the new container behind load balancers.)

### Rollback

All images are tagged. To revert:

```bash
docker tag rowing-backend:prod-previous rowing-backend:prod
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps backend
```

Tag `:prod-previous` before each deploy so you always have one good rollback target. Schema migrations must be backwards compatible (add-only); otherwise, roll back the database separately from a dump.

---

## Operations

### Rotating the JWT secret

1. Generate a new secret: `openssl rand -base64 48`
2. Update `.env`
3. `docker compose up -d --no-deps backend`
4. All existing access tokens (15 min TTL) become invalid immediately. Refresh tokens will also be rejected — every user has to log in again. This is the expected outcome of rotation; schedule during low-traffic.

### Rotating the DB password

1. Change `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` in `.env` (must match)
2. `docker exec rowing-db psql -U postgres -c "ALTER USER rowing WITH PASSWORD 'new-password';"`
3. `docker compose up -d --no-deps backend`

### Toggling runtime settings

Every public setting (booking hour, cancellation toggle, etc.) is flippable at runtime via the admin UI at `/admin/settings`, or directly:

```bash
TOKEN=$(curl -s -X POST https://api.your-domain.example.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"..."}' | jq -r '.accessToken')

curl -s -X PUT https://api.your-domain.example.com/api/admin/settings/allow_cancellations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"value":"false"}'
```

See [ARCHITECTURE.md §10](./ARCHITECTURE.md#10-deployment--operations) for the full key reference.

### Collecting logs

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

In the `prod` Spring profile, backend logs are single-line JSON — pipe them into your log aggregator.

### Checking health

```bash
# Internal (from host)
curl -s http://localhost:8081/api/health/live
curl -s http://localhost:8081/api/health/ready

# External (through reverse proxy)
curl -s https://api.your-domain.example.com/api/health/ready
```

---

## Incident runbook

### Symptom: `/api/health/ready` returns 503 "db":"DOWN"

1. `docker compose ps` — is postgres running?
2. `docker compose logs postgres | tail -100` — look for crash or disk-full
3. Check the host disk: `df -h`
4. Restart: `docker compose restart postgres` — then re-check readiness
5. If the DB container wedged with data corruption, restore from last backup.

### Symptom: login rate-limit hits normal users

The backend rate-limits `/api/auth/*` to 10 requests/minute/IP. If legitimate traffic trips this, check:

1. Shared-NAT scenarios (many users behind one IP). Consider raising the limit in `AuthRateLimitFilter.MAX_REQUESTS` and rebuilding.
2. Frontend-side bug causing repeated login calls (check browser devtools).
3. Rogue script or bot — inspect nginx access log for patterns.

### Symptom: users complain about stale settings

When an admin flips `disable_availability` or similar, clients that were already logged in won't see the change until their SettingsContext re-fetches — typically on next login or when they land on a page that calls `refresh()`. To force-propagate, have all users log out and back in, or document it as a "takes effect on next session" caveat.

### Symptom: booking shows "seat was just taken"

That's the optimistic-lock conflict handler working — two users raced on the same boat. The user just retries. If the frequency is high, it indicates demand exceeding capacity during a specific window — schedule more boats or stagger session times.

---

## Known prod constraints

These are things that should be addressed before very large-scale deployment; see [ARCHITECTURE.md §12](./ARCHITECTURE.md#12-known-limitations--future-work) for the full list.

- **DataSeeder is disabled in `prod`**, so the DB starts empty. Bootstrap your first admin user as described above.
- **Hibernate `ddl-auto: validate`** in prod — schema changes require an explicit migration strategy (Flyway/Liquibase recommended for large deployments).
- **No multi-tenancy** — single club per deployment.
- **Frontend API URL is baked at build** — multi-region / multi-env deployments need separate image builds per env.
- **In-memory rate limiter** — resets on container restart. For multi-replica backends, replace with a Redis-backed limiter.
