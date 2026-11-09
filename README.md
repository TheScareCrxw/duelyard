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
- Docker Desktop (needed from Phase 2 onward for PostgreSQL and Redis)

## Running locally

```bash
# 1. Infrastructure (from Phase 2 onward)
docker compose up -d

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
cd frontend && npm run lint && npx tsc --noEmit && npm run build
```

## Configuration

| Variable | Where | Default | Purpose |
| --- | --- | --- | --- |
| `SERVER_PORT` | backend | `8080` | API port |
| `APP_CORS_ALLOWED_ORIGINS` | backend | `http://localhost:3000` | Comma-separated browser origins allowed to call the API |
| `APP_LOG_LEVEL` | backend | `INFO` | Log level for `com.duelyard` |
| `API_BASE_URL` | frontend (server only) | `http://localhost:8080` | Where the Next.js server reaches the API |

## API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/games` | public | List all games |
| GET | `/api/games/{slug}` | public | One game; 404 problem response if unknown |
| GET | `/actuator/health` | public | Health check |

Every other route requires authentication (returns 401 until Cognito is wired up in Phase 3).

## Adding a game

1. Backend: create `game/<name>/<Name>Module.java` implementing `GameModule` and annotated with `@Component`. It's picked up automatically.
2. Frontend: add an entry to `src/games/registry.ts` for its accent color, icon and blurb. Unknown slugs still render with a neutral style.
