# Deploying Duelyard to AWS

This is the recommended production layout. Nothing here is provisioned automatically; each step
creates billable AWS resources, so run them deliberately.

```
            ┌──────────── Route 53 ────────────┐
            │  app.example.com   api.example.com│
            └───────┬──────────────────┬────────┘
                    ▼                  ▼
        Application Load Balancer (HTTPS, ACM certificate, idle timeout 300 s)
            │ host: app.*              │ host: api.*  (REST + /ws WebSockets)
            ▼                          ▼
   ECS Fargate: frontend (Next.js)   ECS Fargate: backend (Spring Boot) ── RDS PostgreSQL 18
            │                          │
            └── Cognito (user pool, managed-login domain, Google IdP) ── SES (email)
```

## Why this shape

- **Two services, two hostnames.** Browsers load pages from `app.` and open the game WebSocket to
  `api./ws` with a one-time ticket. Access tokens never leave the Next.js server.
- **One backend task (for now).** Live match rooms and matchmaking queues are held in memory, so
  every player must reach the same instance. Run `desiredCount = 1` (ECS replaces it if it dies;
  matches in flight are lost, results already recorded are not). To scale out, move queues to
  Redis (ElastiCache) behind the existing `Matchmaker` interface and route each match to the
  instance that owns it. See `docs/games.md`.
- **The frontend scales freely**: it holds no state.

## 1. Network and database

1. VPC with two public subnets (load balancer) and two private subnets (tasks, database).
2. **RDS PostgreSQL 18**, private subnets, encrypted, automated backups, Multi-AZ for production.
   Create database `duelyard` and a user with ownership of it. Flyway creates the schema and the
   `pg_trgm` extension on first start (it is a trusted extension on RDS).
3. Store the database password in **Secrets Manager**.

## 2. Cognito for production

```bash
AWS_REGION=us-east-1 APP_URL=https://app.example.com infra/cognito/setup-cognito.sh prod
GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... infra/cognito/add-google.sh prod   # or infra/cognito/.env.google
```

This creates a separate `duelyard-prod` pool with callback `https://app.example.com/auth/callback`
and sign-out URL `https://app.example.com/`. Then:

- Add `https://<prod cognito domain>/oauth2/idpresponse` to the Google OAuth client's redirect URIs
  and publish the Google consent screen.
- Configure **Amazon SES** (verified domain, production access) as the pool's email sender. The
  default Cognito sender only allows a few emails per day.
- Put the prod app client secret in Secrets Manager.

## 3. Images

Build and push to ECR (CI can do this on tags):

```bash
docker build -t <acct>.dkr.ecr.us-east-1.amazonaws.com/duelyard-backend:<tag> backend
docker build --build-arg NEXT_PUBLIC_REALTIME_URL=wss://api.example.com/ws \
  -t <acct>.dkr.ecr.us-east-1.amazonaws.com/duelyard-frontend:<tag> frontend
```

`NEXT_PUBLIC_REALTIME_URL` is compiled into the browser bundle; everything else is runtime config.

## 4. ECS services

**Backend** (port 8080, 1 vCPU / 2 GB is plenty to start):

| Variable | Value |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/duelyard` |
| `DB_USERNAME` / `DB_PASSWORD` | from Secrets Manager |
| `COGNITO_REGION` / `COGNITO_USER_POOL_ID` / `COGNITO_CLIENT_IDS` | prod pool and client id |
| `APP_CORS_ALLOWED_ORIGINS` | `https://app.example.com` (also the WebSocket origin allow-list) |

**Frontend** (port 3000, 0.5 vCPU / 1 GB, 2+ tasks):

| Variable | Value |
| --- | --- |
| `API_BASE_URL` | `https://api.example.com` (or the backend's internal service-discovery URL) |
| `APP_URL` | `https://app.example.com` |
| `COGNITO_REGION` / `COGNITO_CLIENT_ID` / `COGNITO_DOMAIN` | prod values |
| `COGNITO_CLIENT_SECRET` | from Secrets Manager |

In production the frontend sets cookies with `Secure` and the `__Host-` prefix automatically.

## 5. Load balancer

- HTTPS listener (ACM certificate for both hostnames); HTTP redirects to HTTPS.
- Host rules: `app.example.com` to the frontend target group, `api.example.com` to the backend.
- **Idle timeout 300 s** so quiet WebSockets aren't cut (clients also ping every 25 s).
- Health checks: backend `GET /actuator/health`, frontend `GET /`.
- No stickiness needed while the backend runs one task.

## 6. Operations

- Logs: ECS awslogs driver to CloudWatch Logs; alarm on backend 5xx and task restarts.
- Database: point-in-time recovery on; restore-test occasionally.
- Deploys: backend restarts end in-flight matches (they are abandoned after the disconnect grace,
  with no rating change). Deploy during quiet hours until matches survive restarts.
- Security: tasks in private subnets; the database accepts connections only from the backend's
  security group; no secrets in images or `NEXT_PUBLIC_` variables.

## Production checklist

- [ ] Separate prod Cognito pool; Google consent screen published; SES configured
- [ ] RDS backups and Multi-AZ; password in Secrets Manager
- [ ] HTTPS everywhere; `APP_CORS_ALLOWED_ORIGINS` set to the real app origin
- [ ] Backend `desiredCount = 1` (until rooms are distributed)
- [ ] ALB idle timeout ≥ 300 s
- [ ] CloudWatch alarms
