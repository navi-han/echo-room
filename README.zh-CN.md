# echo-room（中文说明）

Echo Room MVP 是一个最多 5 人同时自由说话的 Web 语音房游戏脚手架。

English README: [README.md](./README.md)

## 目录结构

- `apps/web`：Vite + React + TypeScript 前端
- `apps/server`：Spring Boot 3（Java 17）信令服务（`/api`、`/ws`）
- `packages/shared`：前后端共享 WebSocket 协议类型

## 预留可替换接口位

- `RTCProvider`（前端）：当前实现 `WebRTCMeshProvider`，后续可替换 TRTC/Agora
- `RoomStateStore`（后端）：当前实现 `InMemoryRoomStateStore`，后续可替换 Redis
- `AIService`（后端）：当前实现 `MockAIService`，后续可替换 Spring AI

## 本地运行

### 1）安装依赖

```bash
cd /Users/yanghan/IdeaProjects/echo-room
npm install
```

### 2）启动后端（Java 17）

```bash
cd /Users/yanghan/IdeaProjects/echo-room/apps/server
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootRun
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

### 3）启动前端

```bash
cd /Users/yanghan/IdeaProjects/echo-room/apps/web
npm run dev
```

浏览器访问 [http://localhost:5173](http://localhost:5173)。

## 本地构建与测试

```bash
cd /Users/yanghan/IdeaProjects/echo-room/apps/web && npm run build

cd /Users/yanghan/IdeaProjects/echo-room/apps/server
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean test bootJar
```

## WebSocket 协议（MVP）

客户端事件：

- `join_room`
- `leave_room`
- `signal_offer`
- `signal_answer`
- `signal_ice`
- `mute_state`
- `ai_ping`

服务端事件：

- `room_snapshot`
- `user_joined`
- `user_left`
- `signal_offer`
- `signal_answer`
- `signal_ice`
- `user_muted`
- `ai_reply`
- `error`

## VPS 部署（Docker Compose + sslip.io）

### 1）准备环境变量

```bash
cd /Users/yanghan/IdeaProjects/echo-room
cp deploy/.env.prod.example deploy/.env.prod
```

编辑 `deploy/.env.prod`：

- `APP_HOST=<你的VPS公网IP>.sslip.io`
- `AI_MODE=mock`

### 2）部署

```bash
docker compose build
docker compose up -d
```

### 3）验收

```bash
curl -f https://<你的VPS公网IP>.sslip.io/api/health
```

再用两台公网设备访问 `https://<你的VPS公网IP>.sslip.io`，加入同一房间互听。

## 运维命令

启动/停止：

```bash
docker compose up -d
docker compose down
```

查看日志：

```bash
docker compose logs -f caddy
docker compose logs -f web
docker compose logs -f server
```

回滚：

1. `git checkout` 到上一稳定提交。
2. 重建并启动：

```bash
docker compose build --no-cache
docker compose up -d
```

## 说明

- 仓库不提交密钥；仅使用 `.env`。
- TURN 仅预留环境变量，本版默认不启用。
- 当前默认匿名入房，无登录系统。
