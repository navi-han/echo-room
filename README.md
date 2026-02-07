# echo-room

中文文档: [README.zh-CN.md](./README.zh-CN.md)

Echo Room MVP is a web voice room game scaffold for up to 5 participants with free multi-speaker audio.

## Monorepo layout

- `apps/web`: Vite + React + TypeScript client
- `apps/server`: Spring Boot 3 (Java 17) signaling server (`/api`, `/ws`)
- `packages/shared`: Shared WebSocket protocol types

## Core extension interfaces

- `RTCProvider` (web): current implementation `WebRTCMeshProvider`, replaceable by TRTC/Agora provider
- `RoomStateStore` (server): current implementation `InMemoryRoomStateStore`, replaceable by Redis
- `AIService` (server): current implementation `MockAIService`, replaceable by Spring AI service bean

## Local run

### 1) Install web dependencies

```bash
cd /Users/yanghan/IdeaProjects/echo-room
npm install
```

### 2) Run server

```bash
cd /Users/yanghan/IdeaProjects/echo-room/apps/server
./gradlew bootRun
```

Server health check:

```bash
curl http://localhost:8080/api/health
```

### 3) Run web

```bash
cd /Users/yanghan/IdeaProjects/echo-room/apps/web
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

## Local build & test

```bash
cd /Users/yanghan/IdeaProjects/echo-room/apps/web && npm run build
cd /Users/yanghan/IdeaProjects/echo-room/apps/server && ./gradlew clean test bootJar
```

## WebSocket protocol (MVP)

Client events:

- `join_room`
- `leave_room`
- `signal_offer`
- `signal_answer`
- `signal_ice`
- `mute_state`
- `ai_ping`

Server events:

- `room_snapshot`
- `user_joined`
- `user_left`
- `signal_offer`
- `signal_answer`
- `signal_ice`
- `user_muted`
- `ai_reply`
- `error`

## VPS deployment (Docker Compose + sslip.io)

### 1) Prepare env

```bash
cd /Users/yanghan/IdeaProjects/echo-room
cp deploy/.env.prod.example deploy/.env.prod
```

Edit `deploy/.env.prod`:

- `APP_HOST=<YOUR_VPS_IP>.sslip.io`
- keep `AI_MODE=mock`

### 2) Deploy

```bash
docker compose build
docker compose up -d
```

### 3) Verify

```bash
curl -f https://<YOUR_VPS_IP>.sslip.io/api/health
```

Then open `https://<YOUR_VPS_IP>.sslip.io` from two public devices and join the same room.

## Operational runbook

### Start / stop

```bash
docker compose up -d
docker compose down
```

### View logs

```bash
docker compose logs -f caddy
docker compose logs -f web
docker compose logs -f server
```

### Rollback (image-based)

1. Checkout previous git commit tag.
2. Rebuild and restart:

```bash
docker compose build --no-cache
docker compose up -d
```

## Notes

- No secrets are committed. Use env files only.
- TURN is reserved by env but not enabled by default in MVP.
- Auth is anonymous for MVP.
