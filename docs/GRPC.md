# gRPC（与 Node.js 战斗进程互通）

很多游戏会把“战斗模拟/回放/AI”等高变动逻辑拆成独立进程（历史上常见 Node.js），并通过 gRPC 让 Java 主服调用。

本节给出 CivGenesis 的最佳实践约定：

- 服务发现：默认 Nacos
- 传输：`grpc://host:port`（跨机与同机都可用）
- 同机优化（可选）：未来可扩展 `grpc+uds://...` 或 SHM，但优先把整体链路跑通与做背压

## 1) 为什么 gRPC 适合战斗进程

- 多语言生态成熟（Java / Node.js 都好用）
- 基于 Protobuf，IDL 清晰，兼容性好
- 支持流式（server/client streaming），容易做“推送/回放”

## 2) Nacos 元信息约定

当战斗进程注册到 Nacos 时，建议在 metadata 中声明：

- `cg.transportCaps` 包含 `GRPC`（见 `civgenesis-registry/TransportCaps`）
- `cg.endpoints` 包含 `grpc://ip:port`

对应文档：`docs/IPC.md`、`docs/REGISTRY_NACOS.md`

## 3) Java 侧调用（框架代码）

CivGenesis 提供最小的 gRPC channel 工具（不绑定具体业务服务）：

- 模块：`civgenesis-rpc-grpc`
- `GrpcChannels.forUri("grpc://10.0.0.12:50051", GrpcClientConfig.insecure())`

> 生产环境可以使用 TLS；内部集群也常见 plaintext + 传输层隔离（取决于你的安全基线）。

## 4) Node.js 侧（建议）

Node.js 推荐使用 `@grpc/grpc-js`：

- 暴露 `0.0.0.0:port`（或绑定到内网 ip）
- 注册到 Nacos（serviceName 由你的进程类型决定，例如 `civgenesis.battle`）
- 健康检查/心跳由 Nacos ephemeral instance 机制处理（也可额外加应用级 health）

## 5) 背压（最佳实践）

无论 Java->Node 还是 Node->Java：

- 对请求-响应：必须有并发上限（`maxInFlight`），超限快速失败（`BACKPRESSURE`/`SERVER_BUSY`）
- 对流式：要么使用 gRPC streaming 自带的流控，要么在应用层引入 credits（参见 `docs/IPC.md`）
