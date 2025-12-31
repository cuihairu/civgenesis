# 集成指南（不含业务逻辑）

本页说明 CivGenesis 如何与“你已有的 admin / 网关 / 登录服务”协作；CivGenesis 仓库只提供**游戏服务器框架层**与 SPI，不包含任何业务逻辑。

## 1) 登录服务（推荐独立进程）

最佳实践：

- 登录/鉴权：独立 HTTP（或独立 TCP）进程
- 游戏消息：Netty WS/TCP（CivGenesis）

推荐交互流程：

1. 客户端向登录服务请求 `token`（例如 OAuth/JWT/自定义签名）
2. 客户端连接 CivGenesis WS（或 TCP），发送 `ClientHello`（msgId=1）
3. 客户端发送 `Resume`（msgId=3）携带 `token` 与 `lastAppliedPushId`
4. 服务端 `TokenAuthenticator` 验证 token 并返回 `playerId`，随后业务消息才允许处理（`msgId>=1000` 默认都要求已登录）

你需要实现的 SPI：

- `io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator`

## 2) 网关（Gateway / Ingress / LB）

常见部署：

- `wss://` 在边缘网关/Ingress 终止 TLS
- 网关按路径/域名转发到 CivGenesis WS（明文 `ws://` 或内网 `wss://`）

建议：

- 外网必须 `wss://`（TLS）
- 网关与 CivGenesis 之间至少内网隔离与访问控制
- 网关层可以做：限流、IP 黑白名单、跨域、握手鉴权、地域/线路选择

## 3) Admin（运营后台）

本仓库不提供 admin。推荐做法：

- Admin 通过你自己的 RPC/HTTP 接口调用游戏服
- 或者走内部 gRPC/IPC（见 `docs/GRPC.md` / `docs/IPC.md`）

## 4) “踢旧连接”策略

同一 `playerId` 多连接处理建议：

- 默认策略：踢旧连接（避免双写状态）
- 可选：拒绝新连接

实现位置：

- `TokenAuthenticator` 的返回值 `AuthResult.kickExistingSession`
- Dispatcher 侧会基于 `playerId` 做单会话约束

## 5) 监控与追踪（可选）

- Prometheus：`civgenesis.observability.prometheus.enabled=true`
- OpenTelemetry：`civgenesis.observability.tracing.enabled=true`

更多见：`docs/OBSERVABILITY.md`

