# 进程间通信（IPC）与服务发现（草案）

本节面向“同一台机器上多进程部署”的游戏服务（例如：网关/逻辑/跨服/战斗/回放/聊天等），目标是让进程间通信按 **最佳传输** 自动选路：

- 同机优先：共享内存（SHM）> Unix Domain Socket（UDS）> TCP loopback
- 异机：TCP（或基于 TCP 的 HTTP/2、gRPC）

同时要求：

- 注册中心携带元信息（可用传输能力、端点）
- 统一背压语义（避免把下游打爆）

> 说明：CivGenesis 当前仓库以“框架代码”为主；这里定义能力与协议，不强制具体注册中心实现（可对接 Nacos/Consul/etcd/K8s endpoints 等）。

## 1) 为什么优先 UDS / SHM

同机进程间：

- TCP loopback 仍然会走协议栈、拷贝与调度，延迟与 CPU 成本更高
- UDS 通常更低延迟、更少开销，权限控制也更灵活
- 共享内存队列可以进一步降低拷贝与系统调用开销，适合高吞吐（但实现复杂度更高）

最佳实践建议：

- 默认同机先用 UDS（实现成本可控、收益明显）
- 只有在明确压测证明瓶颈时才引入 SHM 队列（例如 Aeron/Chronicle 等）

## 2) `instanceId`（uint64）编码：区域-主机-进程类型-index

为了让系统能“只靠一个 long 判断是否同机”，建议使用 64-bit `instanceId`，并固定拆分字段：

```
63                                                         0
| region(12) | host(20) | processType(8) | index(12) | reserved(12) |
```

- `region`：区域/机房/逻辑大区（0..4095）
- `host`：主机编号（0..1,048,575）
- `processType`：进程类型（0..255，例如 gateway=1, game=2, cross=3, ...）
- `index`：同类型进程在该主机的序号（0..4095）
- `reserved`：保留（0..4095），用于将来扩展（例如进程 epoch、灰度位等）

判定是否同机：

- `sameHost(a,b) = host(a) == host(b) && region(a) == region(b)`（建议至少比较 region+host）

本仓库参考实现：

- `io.github.cuihairu.civgenesis.registry.InstanceId`

### 2.1 hostId 如何分配（关键）

`hostId` 必须“稳定且唯一”，否则会误判同机。

推荐：

- 物理机/虚机：运维分配 `hostId`（配置文件或环境变量）
- K8s：使用 `nodeId`（节点级别稳定 id），不要使用 pod id

## 3) 注册中心元信息：能力位 + 多端点

每个实例注册时携带：

1. `instanceId`：上文定义的 64-bit id（建议也提供可读 string）
2. `transportCaps`：uint64 位图，表示该实例支持哪些内部传输
3. `endpoints`：多端点列表（同一个服务同时暴露 TCP/UDS/SHM）

### 3.0 Nacos 默认实现（CivGenesis）

CivGenesis 默认提供 Nacos 的注册/发现实现（见模块 `civgenesis-registry-nacos`）。

建议的 Nacos metadata key（稳定字段，避免与其他系统冲突）：

- `cg.instanceId`：uint64（十进制字符串）
- `cg.transportCaps`：uint64（十进制字符串）
- `cg.endpoints`：端点列表（每项 URL-encode 后用 `,` 连接）

### 3.1 `transportCaps` 建议位图（示例）

- bit0: `TCP`
- bit1: `UDS`
- bit2: `SHM_AERON_IPC`
- bit3: `SHM_MMAP_QUEUE`（自研/Chronicle 类）
- bit4: `GRPC`（语义层能力，可选）

### 3.2 endpoints 元信息（示例）

建议把端点以结构化字段注册（或 JSON string），并带 scheme：

- `tcp://10.0.0.12:9001`
- `uds:///var/run/civgenesis/game-2.sock`
- `aeron:ipc?dir=/dev/shm/aeron&streamId=1101`
- `grpc://10.0.0.12:50051`

安全建议：

- UDS 路径权限按进程用户隔离，避免同机越权访问
- SHM 目录（如 `/dev/shm/aeron`）也要权限与清理策略

## 4) 选路策略（Dialer / Resolver）

客户端侧（调用方）拿到“目标实例元信息”后，按以下顺序选择：

1. 若 `sameHost(self, target)` 且双方都支持 `SHM_*`：优先 SHM
2. 否则若 `sameHost` 且双方都支持 `UDS`：使用 UDS
3. 否则使用 TCP

建议暴露统一接口：

- `EndpointResolver`：从注册中心拿到候选实例 + 元信息
- `LinkDialer`：根据元信息选择传输并建立连接

本仓库参考实现（框架层积木）：

- 选路：`io.github.cuihairu.civgenesis.ipc.routing.EndpointSelector`
  - 同机：优先 `AERON_IPC` > `UDS` > `TCP(loopback)`
  - 异机：优先 `TCP(non-loopback)`
- Dialer：
  - UDS：`io.github.cuihairu.civgenesis.ipc.uds.UdsIpcDialer`
  - Aeron：`io.github.cuihairu.civgenesis.ipc.aeron.AeronIpcDialer`

## 5) 背压（Backpressure）最佳实践：统一“信用/配额”语义

无论使用 TCP/UDS/SHM，建议在“框架层”统一背压语义，避免不同传输各自为政：

- 发送端只能在获得“信用”（credits）后发送
- 接收端根据自身队列水位发放/回收信用

实现策略（建议）：

- 单连接有 `maxInFlight`、`maxQueueBytes` 等硬限制
- 当下游繁忙：
  - 请求-响应：快速失败（SERVER_BUSY/BACKPRESSURE，可重试）
  - 流式推送：降低速率、丢弃非关键消息或降级为 best-effort

### 5.1 SHM 队列的背压

如果你希望“共享内存消息队列 + 背压”，推荐优先考虑 Aeron：

- 同机 IPC 通道（`aeron:ipc`）天然支持 backpressure（`offer` 返回值表示是否可写）
- 也能扩展到跨机 UDP（如果未来需要）

> 说明：Aeron 的 IPC 语义更接近“同机 pub/sub”；如果你把同一个 `streamId` 用作多方共享通道，需要在消息里带上 `peerId`/`instanceId` 并过滤，否则会收到不属于自己的消息。本仓库的 `AeronIpcChannel` 已默认忽略“自己发出的消息”（避免自收）。

自研 mmap ring-buffer 也可以做背压，但需要额外设计：

- 生产者/消费者游标
- 多生产者竞争策略
- 内存可见性与 false sharing 处理
- 丢包/重放策略（崩溃恢复）

## 6) 与 CivGenesis 现有设计的衔接

CivGenesis 面向“游戏消息”（客户端）已经定义了：

- 背压：in-flight 限制与 shard 队列满回错误（见 `docs/SECURITY.md`）
- 推送可靠性：`ACK_REQUIRED` 才启用可靠推送（见 `docs/PROTOCOL.md`）

内部 IPC 建议复用同样的原则：

- 关键链路必须有背压
- 非关键链路允许 best-effort

## 7) 参考实现（本仓库）

- UDS：`civgenesis-ipc`（`io.github.cuihairu.civgenesis.ipc.uds.*`）
- SHM（Aeron IPC）：`civgenesis-ipc-aeron`（`io.github.cuihairu.civgenesis.ipc.aeron.*`）

> 说明：这两个实现都是“参考实现/框架层积木”，不绑定业务协议；建议在你的服务间协议上叠加超时、重试与更细的背压策略。
