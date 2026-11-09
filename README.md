# Duelyard

Competitive 1v1 mini-games (darts, mini golf, 8-ball pool and rocket launcher), each with its own Elo ladder.

## Repository layout

```
backend/            Spring Boot 4.1 API (Java 25, Maven wrapper)
  src/main/java/com/duelyard/
    config/         Security and CORS configuration
    common/         Shared error handling
    game/           Game catalog and the GameModule extension point
      darts/ minigolf/ pool/ rocket/   One module per game
    auth/ user/ rating/ leaderboard/
    multiplayer/ matchmaking/ match/   Reserved for later phases (see package-info.java)
frontend/           Next.js 16 (App Router, TypeScript, Tailwind CSS 4)
  src/
    app/            Routes
    components/     Reusable UI (layout, ui, games, rank)
    games/          Per-game presentation; gameplay code lands in games/<slug>/
    services/       Server-side API client
    types/          API and domain types
    hooks/ contexts/  Reserved for later phases
    lib/            Pure helpers
docker-compose.yml  Local PostgreSQL and Redis
```

## Prerequisites

- Java 25
- Node.js 20.9 or newer
- Docker Desktop to run PostgreSQL and Redis locally (or your own PostgreSQL 18 matching the `DB_*` settings below)

Tests do **not** need Docker: they start a real embedded PostgreSQL 18 automatically.

## Running locally

```bash
# 1. Infrastructure: the backend won't start without PostgreSQL
docker compose up -d
#    No Docker? Run a real PostgreSQL 18 from the backend's test classpath instead (same settings):
#    cd backend && ./mvnw test-compile exec:java -Dexec.mainClass=com.duelyard.support.LocalPostgres -Dexec.classpathScope=test

# 2. Backend: http://localhost:8080
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run

# 3. Frontend: http://localhost:3000
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

## Tests

```bash
cd backend && ./mvnw test
cd frontend && npm test && npm run lint && npx tsc --noEmit && npm run build
```

## Configuration

| Variable | Where | Default | Purpose |
| --- | --- | --- | --- |
| `SERVER_PORT` | backend | `8080` | API port |
| `APP_CORS_ALLOWED_ORIGINS` | backend | `http://localhost:3000` | Comma-separated browser origins allowed to call the API |
| `APP_LOG_LEVEL` | backend | `INFO` | Log level for `com.duelyard` |
| `DB_URL` | backend | `jdbc:postgresql://localhost:5432/duelyard` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | backend | match `docker-compose.yml` | Database credentials; **always override in production** |
| `DB_POOL_SIZE` | backend | `10` | Max connection pool size |
| `COGNITO_REGION` | backend + frontend | `us-east-1` | User pool region |
| `COGNITO_USER_POOL_ID` | backend | (placeholder) | e.g. `us-east-1_AbC123` |
| `COGNITO_CLIENT_IDS` | backend | (placeholder) | Comma-separated app client ids whose tokens are accepted |
| `API_BASE_URL` | frontend (server only) | `http://localhost:8080` | Where the Next.js server reaches the API |
| `APP_URL` | frontend (server only) | (none) | Public URL of the site, e.g. `http://localhost:3000` |
| `COGNITO_CLIENT_ID` / `COGNITO_CLIENT_SECRET` | frontend (server only) | (none) | App client credentials |
| `COGNITO_DOMAIN` | frontend (server only) | (none) | e.g. `https://duelyard.auth.us-east-1.amazoncognito.com` |

Without the Cognito variables the site still runs; sign-in pages show a "not configured" notice.

## Database

The schema is owned by Flyway migrations in `backend/src/main/resources/db/migration`. Hibernate runs
with `ddl-auto: validate`, so startup fails if entities and schema drift apart. Never edit an applied
migration; add a new `V<n>__description.sql` instead.

| Table | Purpose |
| --- | --- |
| `users` | Accounts linked to Cognito by unique `cognito_sub`; usernames unique case-insensitively |
| `games` | One row per game, seeded by migration; startup fails if a `GameModule` has no row |
| `player_game_stats` | Rating (starts at 1000), peak rating, ranked/casual W-L-D per user per game |
| `matches` / `match_players` | Match lifecycle and participants (UUIDv7 ids, optimistic locking) |
| `rating_history` | One row per player per rated match; uniqueness prevents double rating updates |
| `match_events` | Ordered, unique-sequence event log per match with JSONB payloads |
| `friendships` / `game_invitations` | Future features (schema only) |

## Ratings

Every game has its own Elo rating (start 1000, K = 32, win/draw/loss = 1/0.5/0). Matches end
only through `MatchCompletionService.complete(...)`, which game modules call once the server has
decided the outcome. In a single transaction holding a row lock on the match it records results,
updates ratings, peak ratings and W/L/D, and writes one `rating_history` row per player. Ranked
changes are zero-sum (rounding is symmetric). Casual matches update W/L/D only. Completing an
already-completed match is a no-op, and the database rejects a second rating change per player
per match.

## Authentication

```
Browser ──(HttpOnly cookies)──> Next.js server ──(Bearer access token)──> Spring Boot API
                                   │                                          │
                                   └── Cognito user-pool API / OAuth ─────────┘ (JWKS, GetUser)
```

- The browser never sees a token. The Next.js server signs users in with Cognito and stores the
  access and refresh tokens in `HttpOnly`, `SameSite=Lax` cookies (`Secure` with the `__Host-`
  prefix in production).
- `src/proxy.ts` refreshes the access token shortly before it expires using the refresh token, so
  sessions last until the refresh token expires (30 days) or the user signs out.
- Sign-out revokes the refresh token at Cognito, clears the cookies and ends the Cognito
  managed-login session.
- Players sign in with their **username or email**. The Cognito username is a hidden random id;
  the visible username is Cognito's `preferred_username` alias, which the backend claims when the
  account is created and updates on every rename (renames: once per 30 days). The name chosen at
  sign-up travels in `custom:desired_username`, because Cognito can't set an alias before the
  account is confirmed. Until the first sign-in, Cognito only knows the email; the sign-in form
  covers this on the device that registered.
- Spring Boot validates every access token: signature (pool JWKS), issuer, expiry,
  `token_use=access` and `client_id`. On a user's first request it creates their account and a
  1000 rating in every game. Google users get a username derived from their email.

### Cognito setup (one-time)

Scripted (recommended), with the AWS CLI signed in to your account:

```bash
AWS_REGION=us-east-1 infra/cognito/setup-cognito.sh dev   # user pool, domain, app client, local .env files
# create the Google OAuth client (redirect URI is printed above), then:
cp infra/cognito/.env.google.example infra/cognito/.env.google   # fill in; gitignored
infra/cognito/add-google.sh dev
```

Both scripts are safe to re-run. What they configure, if you prefer the AWS console:


1. **User pool**: create one with application type *Traditional web application*.
   - Sign-in identifiers: **Username** and **Email**. Self-registration on, required attribute
     `email`, Cognito-assisted email verification.
   - Password policy: minimum 8, require uppercase, lowercase, number and symbol (the default).
   - Keep usernames case-insensitive and "prevent user existence errors" enabled (defaults).
2. **App client** (generated with a client secret):
   - Auth flows: `ALLOW_USER_PASSWORD_AUTH`, `ALLOW_REFRESH_TOKEN_AUTH`. Token revocation enabled.
     Refresh token rotation **off**.
   - Token lifetimes: access 60 minutes, refresh 30 days.
   - Managed login: callback URL `http://localhost:3000/auth/callback`, sign-out URL
     `http://localhost:3000/` (add your production URLs too). Grant type *Authorization code*.
     Scopes `openid`, `email`, `profile`, `aws.cognito.signin.user.admin`.
3. **Domain**: create a Cognito domain (this becomes `COGNITO_DOMAIN`).
4. **Google**: in Google Cloud Console create an OAuth client (Web application) with authorized
   redirect URI `https://<your-cognito-domain>/oauth2/idpresponse`. In Cognito, add Google as a
   social provider with that client id/secret, scopes `openid email profile`, and map
   `email` → `email` and `email_verified` → `email_verified`. Enable Google on the app client.
5. **Email**: the default Cognito sender is limited to a few emails per day; configure Amazon SES
   before launch.
6. Put the values in `frontend/.env.local` and the backend environment.

## API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/games` | public | List all games |
| GET | `/api/games/{slug}` | public | One game; 404 problem response if unknown |
| GET | `/api/users/me` | bearer | The caller's account (created on first call) |
| PATCH | `/api/users/me` | bearer | Change username, avatar (`preset:<emblem>:<color>`) and/or profile visibility |
| GET | `/api/users/username-availability?username=` | public | Format, reserved-name and availability check |
| GET | `/api/players/{username}` | public | Profile: ratings, totals, recent matches (stats hidden if private, except to the owner) |
| GET | `/api/players/{username}/matches?page=&size=` | public | Paginated match history (403 if private) |
| GET | `/api/players/{username}/rating-history?game=&limit=` | public | Rating after each ranked match, oldest first (403 if private) |
| GET | `/actuator/health` | public | Health check |

Every other route requires a valid Cognito access token.

## Adding a game

1. Backend: create `game/<name>/<Name>Module.java` implementing `GameModule` and annotated with `@Component`, and add a Flyway migration inserting its `games` row.
2. Frontend: add an entry to `src/games/registry.ts` for its accent color, icon and blurb. Unknown slugs still render with a neutral style.
