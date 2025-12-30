# CivGenesis

[![CI](https://github.com/cuihairu/civgenesis/actions/workflows/ci.yml/badge.svg)](https://github.com/cuihairu/civgenesis/actions/workflows/ci.yml)
[![Docs](https://github.com/cuihairu/civgenesis/actions/workflows/docs.yml/badge.svg)](https://github.com/cuihairu/civgenesis/actions/workflows/docs.yml)
[![codecov](https://codecov.io/gh/cuihairu/civgenesis/branch/main/graph/badge.svg)](https://codecov.io/gh/cuihairu/civgenesis)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.2.1-02303A?logo=gradle&logoColor=white)
[![License](https://img.shields.io/github/license/cuihairu/civgenesis)](LICENSE)

Java 21 / Spring Boot / Netty game server framework for building real-time game backends with a custom WS binary protocol, annotation-based routing, sharded execution, reliable push/resume, observability, and service discovery.

This repository contains framework/runtime code only (no gameplay/business logic).

License: Apache-2.0 (see `LICENSE`).

## Why CivGenesis

- Netty-only message pipeline (no Spring MVC/Tomcat in the hot path)
- Custom protocol: WebSocket Binary + TLV envelope (extensible, skip-unknown) + Protobuf payload
- Req/Resp with dedup-friendly `seq`, plus reliable push with `ACK_REQUIRED` + resume window
- Sharded execution model for game state (by `playerId` / channel)
- Optional Prometheus metrics + OpenTelemetry tracing
- Nacos-based service discovery + gRPC/IPC integration points

## Architecture

```mermaid
flowchart LR
  subgraph Client
    U[Unity SDK]:::c
    T[TS SDK - Cocos/LayaAir]:::c
  end

  U -->|WS Binary + TLV| WS
  T -->|WS Binary + TLV| WS

  subgraph GameGateway["Game Gateway - Netty"]
    WS[Netty WebSocket Server]:::s
    DEC[TLV Frame Decoder/Encoder]:::s
    DISP[Dispatcher Runtime - @GameController/@GameRoute]:::s
    SHARD[Shard Executor - player/channel]:::s
    SCHED[HashedWheel Scheduler]:::s
  end

  WS --> DEC --> DISP --> SHARD
  SHARD -->|invoke handler| H[Game Handlers - your code]:::a
  H -->|RESP/PUSH| DISP
  SCHED --> SHARD

  subgraph Infra["Infra"]
    NACOS[Nacos Registry/Discovery]:::i
    GRPC[gRPC / IPC endpoints]:::i
    OBS[Prometheus / OTel]:::i
    JOBS[Jobs Runner - local + leader-only SPI]:::i
  end

  DISP --- OBS
  WS --- OBS
  JOBS --- OBS
  DISP --- GRPC
  GRPC --- NACOS
  classDef c fill:#1f2937,stroke:#0b1220,color:#ffffff
  classDef s fill:#0ea5e9,stroke:#075985,color:#ffffff
  classDef a fill:#22c55e,stroke:#15803d,color:#ffffff
  classDef i fill:#a855f7,stroke:#6b21a8,color:#ffffff
```

## Repo layout

```
.
├── civgenesis-core/                 # protocol, errors, sharded executors, observability SPI
├── civgenesis-codec-tlv/            # TLV varint codec
├── civgenesis-protocol-system/      # system.proto (reserved msgId range)
├── civgenesis-codec-protobuf/       # protobuf payload codec + system Error
├── civgenesis-dispatcher/           # @GameController/@GameRoute + route scan + dispatcher runtime
├── civgenesis-scheduler/            # hashed wheel scheduler (time wheel)
├── civgenesis-transport-netty-ws/   # Netty WebSocket transport
├── civgenesis-registry/             # registry/discovery SPI
├── civgenesis-registry-nacos/       # Nacos implementation
├── civgenesis-rpc-grpc/             # gRPC helpers
├── civgenesis-jobs/                 # background jobs (local + leader-only lease SPI)
├── civgenesis-spring-boot-starter/  # auto-wiring (no MVC), properties, lifecycle
├── clients/
│   ├── unity/                       # Unity (C#) client SDK (protocol + connection mgmt)
│   └── ts/                          # TypeScript client SDK (Cocos/LayaAir)
└── docs/                            # VuePress docs (published via GitHub Pages)
```

## Design principles (high-level)

- Keep the hot path minimal: Netty -> decode -> dispatch -> shard -> handler
- Prefer deterministic state handling: shard by `playerId` for ordered execution
- Treat reliability as a protocol feature: `seq` for Req/Resp retries; `pushId` + `ACK_REQUIRED` for reliable push
- Prefer “resume window, else full sync”: small gap replay, large gap snapshot
- Observability is opt-in but first-class: metrics & tracing should be cheap when disabled

## Quick start (server)

Spring Boot is used for wiring/lifecycle only. Game messages do not go through Spring MVC.

1) Add dependency (your app)

```kotlin
dependencies {
  implementation("io.github.cuihairu:civgenesis-spring-boot-starter:<version>")
}
```

2) Write routes

```java
@GameController
public class EchoController {
  @GameRoute(id = 1000, open = true)
  public void echo(RequestContext ctx, EchoReq req) {
    ctx.reply(EchoResp.newBuilder().setText(req.getText()).build());
  }
}
```

3) Provide system integrations (required)

- Implement token authentication for `Resume`:
  - SPI: `io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator`
- Implement snapshot generation for `SyncSnapshot` (recommended):
  - SPI: `io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider`

4) Configure

```yaml
civgenesis:
  dispatcher:
    enabled: true
    shards: 64
  ws:
    enabled: true
    port: 8080
    path: /ws
```

## Docs

- Docs index: `docs/README.md`
- GitHub Pages (CI published): `https://cuihairu.github.io/civgenesis/`
- Protocol: `docs/PROTOCOL.md`
- Protobuf workflow: `docs/PROTOBUF.md`
- Dispatcher/routing: `docs/DISPATCHER.md`
- Observability: `docs/OBSERVABILITY.md`
- Jobs (local/distributed): `docs/JOBS.md`
- Client SDKs: `docs/CLIENT_SDK.md`

## Client SDKs (protocol only)

- Unity（C#）：`clients/unity/`
- TypeScript（Cocos/LayaAir）：`clients/ts/`

## Build

```bash
./gradlew build
```

## Docs site (VuePress)

Local preview:

```bash
npm ci
npm run docs:dev
```

Build static site:

```bash
npm ci
npm run docs:build
```
