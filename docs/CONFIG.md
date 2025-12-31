# 配置项建议（草案）

本文件描述 CivGenesis SDK 的“建议配置项”，用于把能力（压缩/加密/可靠推送/窗口阈值等）做成可配置，并给出推荐默认值。

说明：

- 这里是设计草案，具体 key 命名会在落地 Spring Boot starter 后最终确定。
- 所有默认值都应以“可线上跑”为目标，但仍需按游戏规模压测后调整。

## 0) 已落地的 Spring Boot 配置（当前版本）

以下 key 已在 `civgenesis-spring-boot-starter` 中实现（建议优先按这里使用）：

### 0.1 WebSocket

- `civgenesis.ws.enabled`（默认 false）
- `civgenesis.ws.boss-threads`（默认 1）
- `civgenesis.ws.worker-threads`（默认 0：Netty 默认）
- `civgenesis.ws.port`（默认 8888）
- `civgenesis.ws.path`（默认 `/`）
- `civgenesis.ws.so-backlog`（默认 1024）
- `civgenesis.ws.recv-buf-bytes`（默认 0：使用 Netty 默认）
- `civgenesis.ws.send-buf-bytes`（默认 0：使用 Netty 默认）
- `civgenesis.ws.pooled-allocator`（默认 true）
- `civgenesis.ws.max-frame-bytes`（默认 1MiB）
- `civgenesis.ws.idle-timeout-seconds`（默认 30）
- `civgenesis.ws.ping-before-close`（默认 true）
- `civgenesis.ws.ping-timeout-millis`（默认 3000）

### 0.1.1 TCP（可选）

- `civgenesis.tcp.enabled`（默认 false）
- `civgenesis.tcp.host`（默认 `0.0.0.0`）
- `civgenesis.tcp.port`（默认 9999）
- `civgenesis.tcp.max-frame-bytes`（默认 1MiB）
- `civgenesis.tcp.idle-timeout-seconds`（默认 30）

### 0.2 Dispatcher

- `civgenesis.dispatcher.enabled`（默认 true）
- `civgenesis.dispatcher.shards`（默认 64）
- `civgenesis.dispatcher.max-in-flight-per-connection`（默认 64）
- `civgenesis.dispatcher.max-in-flight-per-shard`（默认 2048）
- `civgenesis.dispatcher.raw-payload-mode`（默认 `RETAIN`）
- `civgenesis.dispatcher.close-on-need-login`（默认 false）
- `civgenesis.dispatcher.request-timeout-millis`（默认 5000）
- `civgenesis.dispatcher.slow-request-millis`（默认 200；设为 0 可关闭）
- `civgenesis.dispatcher.dedup-enabled`（默认 true）
- `civgenesis.dispatcher.dedup-max-entries`（默认 1024）
- `civgenesis.dispatcher.dedup-ttl-millis`（默认 30000）
- `civgenesis.dispatcher.max-buffered-push-count`（默认 2000）
- `civgenesis.dispatcher.max-buffered-push-age-millis`（默认 60000）

### 0.3 系统消息（ClientHello/Resume/Sync）

- `civgenesis.system.enabled`（默认 true）
- `civgenesis.system.gzip-enabled`（默认 false）

> `Resume/Sync` 的鉴权与快照生成是可插拔 SPI：需要你提供 `TokenAuthenticator` 与 `SnapshotProvider` 的实现（见 `docs/SYSTEM_MESSAGES.md`）。

### 0.4 可观测性

见 `docs/OBSERVABILITY.md`。

### 0.5 Jobs

- `civgenesis.jobs.enabled`（默认 false）
- `civgenesis.jobs.thread-name`（默认 `civgenesis-job-`）

## 1) 传输（WebSocket）

- `civgenesis.transport=ws`（默认）
- `civgenesis.ws.port=8888`
- `civgenesis.ws.path=/`
- `civgenesis.ws.maxFrameBytes=1048576`（1MiB，按协议与包体大小调整）
- `civgenesis.ws.idleTimeoutSeconds=30`（无业务流量的空闲连接，默认更激进以节省资源）
- `civgenesis.ws.pingBeforeClose=true`（空闲超时前先发一次 PING 探测，避免误杀）
- `civgenesis.ws.pingTimeoutMillis=3000`（PING 后等待 PONG 的时间）

## 1.1) 可观测性（Prometheus / OpenTelemetry）

本仓库提供可选的 metrics 与 tracing（框架内置埋点，见 `docs/OBSERVABILITY.md`）。

- `civgenesis.observability.prometheus.enabled=true`（启动独立 Netty HTTP `/metrics`）
- `civgenesis.observability.prometheus.host=0.0.0.0`
- `civgenesis.observability.prometheus.port=9090`
- `civgenesis.observability.prometheus.path=/metrics`
- `civgenesis.observability.tracing.enabled=true`（开启 OpenTelemetry span）
- `civgenesis.observability.tracing.instrumentation-name=civgenesis`

## 1.2) Jobs（后台任务）

用于“本地后台任务 runner”（建议把全局任务放到独立 jobs 进程里跑，见 `docs/JOBS.md`）：

- `civgenesis.jobs.enabled=false`（默认 false）
- `civgenesis.jobs.thread-name=civgenesis-job-`

TLS（最佳实践）：

- `civgenesis.ws.tls.enabled=true`（生产建议 true，对应 wss://）
- `civgenesis.ws.tls.require=true`（生产建议 true：拒绝明文 ws://）
  - 说明：如果部署在网关/Ingress 已终止 TLS，SDK 侧可配置为 `tls.enabled=false`，但仍建议保证链路加密。

## 2) Dispatcher/线程模型（按 playerId 分片）

- `civgenesis.dispatch.shards=0`（0 表示自动：`cpuCores * 2`，并做上下限）
- `civgenesis.dispatch.queuePerShard=65536`（每分片队列容量，满了要触发背压/断连策略）

Raw handler（ByteBuf）策略（建议）：

- `civgenesis.dispatch.rawPayloadMode=RETAIN`（默认：零拷贝，SDK 自动 release）
  - 可选：`COPY`（更安全，投递前复制到 heap，减少引用计数误用风险）

## 2.1) 会话并发策略

- `civgenesis.session.singleSessionPerPlayer=true`（默认：同 playerId 只允许一个活跃连接）
- `civgenesis.session.onDuplicateLogin=KICK_OLD`（默认；可选：`REJECT_NEW`）

## 2.3) 阻塞任务（Java 21 虚拟线程）

建议所有 DB/HTTP/gRPC 等阻塞操作都不要占用分片线程；使用虚拟线程执行并在完成后回投到分片线程。

- `civgenesis.blocking.enabled=true`
- `civgenesis.blocking.mode=VIRTUAL`（默认；可选：`PLATFORM_POOL`）
- `civgenesis.blocking.platform.maxThreads=256`（仅 `PLATFORM_POOL` 模式下生效）

## 2.2) 实例标识（instanceId）

用于服务发现、选路与“同机判定”（见 `docs/IPC.md`）：

- `civgenesis.instance.id=0`（uint64，建议运维/部署时注入）
- `civgenesis.instance.idText=`（可选：人类可读形式，例如 `r1-h12-t2-i3`）

若没有显式配置，可由部署系统生成，但必须保证 hostId 稳定且唯一。

## 8) 进程间通信（IPC）与背压（建议）

> 当前为设计草案配置项，具体落地模块实现后可调整 key 命名。

启用/禁用内部 IPC：

- `civgenesis.ipc.enabled=true`

能力与端点声明（用于注册中心元信息）：

- `civgenesis.ipc.caps=TCP,UDS,SHM_AERON_IPC`（实例支持能力集合）
- `civgenesis.ipc.tcp.port=9001`
- `civgenesis.ipc.uds.path=/var/run/civgenesis/game-2.sock`
- `civgenesis.ipc.aeron.dir=/dev/shm/aeron`
- `civgenesis.ipc.aeron.streamId=1101`

选路策略（同机优先）：

- `civgenesis.ipc.preferLocal=SHM_AERON_IPC,UDS,TCP`
- `civgenesis.ipc.preferRemote=TCP`

内部背压建议：

- `civgenesis.ipc.maxInFlight=256`
- `civgenesis.ipc.maxQueueBytes=8388608`（8MiB）

## 3) Req/Resp 去重与超时

- `civgenesis.reqresp.dedup.enabled=true`
- `civgenesis.reqresp.dedup.maxEntries=1024`（每玩家缓存最近 N 个 seq 的响应）
- `civgenesis.reqresp.dedup.ttlMillis=30000`（与客户端重试窗口相匹配）

in-flight 与背压（建议）：

- `civgenesis.reqresp.maxInFlightPerConnection=64`（超过则返回 BACKPRESSURE 错误；in-flight 包含 deferred）
- `civgenesis.reqresp.requestTimeoutMillis=10000`（服务端侧“未完成响应”超时；超时后返回错误并进入去重缓存）
- `civgenesis.dispatch.backpressure.onShardQueueFull=ERROR`（默认：回错误；可选：`CLOSE`）

## 4) 可靠推送（ACK_REQUIRED）

- `civgenesis.push.reliable.enabled=true`
- `civgenesis.push.reliable.maxBufferedPushCount=2000`（N）
- `civgenesis.push.reliable.maxBufferedPushAgeMillis=60000`（T）
- `civgenesis.push.ack.intervalMillis=500`（客户端若不捎带 ACK，则建议 ack 周期）

## 5) 断线重连策略

断线重连走“能续传就续传，否则全量 SyncSnapshot”的兜底策略：

- `civgenesis.resume.enabled=true`
- `civgenesis.sync.snapshotAckRequired=true`（建议：快照用可靠推送确保送达）

## 5.1) 系统消息开关（建议）

若你已有独立登录服务（HTTP/TCP），推荐把游戏服侧的“显式 Login”关闭，只保留 `Resume` 作为统一入口。

- `civgenesis.system.loginMessageEnabled=false`（默认：关闭 `msgId=2 Login`）
- `civgenesis.system.resumeMessageEnabled=true`（默认：开启 `msgId=3 Resume`）

## 6) 压缩（COMPRESS）

建议支持（按服务端允许列表与偏好顺序协商）：

- `NONE`
- `ZSTD`（推荐默认）
- `LZ4`（低延迟）
- `GZIP`（兼容）

配置建议：

- `civgenesis.compress.enabled=true`
- `civgenesis.compress.allowed=ZSTD,LZ4,GZIP,NONE`
- `civgenesis.compress.prefer=ZSTD`
- `civgenesis.compress.minPayloadBytes=256`（小包不压缩）
- `civgenesis.compress.level=3`（ZSTD/GZIP 等可选）

最佳实践补充：

- 涉及敏感信息（例如登录 token）的响应/请求可以选择不压缩（以 frame flags 控制）。

## 7) 加密（ENCRYPT）

最佳实践：

- 生产环境优先 `wss://`（TLS）解决传输加密与证书校验。
- 应用层加密作为可选能力：只在确有需求时启用（会增加 CPU/复杂度）。

建议协商算法（按允许列表与偏好）：

- `TLS`（优先）
- `CHACHA20_POLY1305`
- `AES_256_GCM`
- `NONE`

配置建议：

- `civgenesis.encrypt.enabled=true`
- `civgenesis.encrypt.allowed=TLS,CHACHA20_POLY1305,AES_256_GCM,NONE`
- `civgenesis.encrypt.prefer=TLS`
- `civgenesis.encrypt.require=true`（生产建议 true：强制要求至少 TLS 或应用层加密）

说明：

- `flags.ENCRYPT` 仅表示“应用层加密”；若使用 TLS，建议不设置该 flag。
