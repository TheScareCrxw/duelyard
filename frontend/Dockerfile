# Next.js production image (standalone output). Build context: frontend/
#   docker build --build-arg NEXT_PUBLIC_REALTIME_URL=wss://api.example.com/ws -t duelyard-frontend .
# Runtime configuration (API_BASE_URL, APP_URL, COGNITO_*) comes from environment variables.

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Public, build-time value: where browsers open the game WebSocket.
ARG NEXT_PUBLIC_REALTIME_URL=ws://localhost:8080/ws
ENV NEXT_PUBLIC_REALTIME_URL=$NEXT_PUBLIC_REALTIME_URL
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
USER app
EXPOSE 3000
CMD ["node", "server.js"]
