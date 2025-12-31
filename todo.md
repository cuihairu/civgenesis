# TODO

> 说明：这是 CivGenesis 的“框架层”任务清单（不包含任何业务/玩法逻辑）。完成一项就打勾。

## 已完成（Done）

- [x] 多模块 Gradle（Java 21）工程骨架
- [x] 协议：WebSocket Binary + TLV（uvarint tag/len）编解码
- [x] 传输：Netty WS Server（idle/ping-before-close）
- [x] Dispatcher：`@GameController/@GameRoute` 扫描 + 路由表 + MethodHandle 调用
- [x] 执行模型：`ShardExecutor`（按 player/channel 分片）
- [x] 调度：时间轮（HashedWheelTimer -> shard 回投）
- [x] Protobuf：payload codec + `system.proto` 模块
- [x] Registry：SPI + Nacos 默认实现
- [x] gRPC：基础 helper（endpoint 解析/通道创建）
- [x] 可观测性（可选）：Prometheus `/metrics` + OpenTelemetry span（手动埋点）
- [x] Jobs：本地 runner + leader-only 租约 SPI（不强绑中间件）
- [x] 客户端 SDK（协议层）：Unity(C#) + TypeScript（Cocos/LayaAir）
- [x] 文档站：VuePress + GitHub Pages CI

## P0（先做，决定是否能线上跑）

- [x] 系统消息处理器：`ClientHello/Resume/Sync/Snapshot/Error` 的服务端最小可用实现
- [x] 可靠推送（ACK_REQUIRED）服务端落地：pushId 分配、ring buffer、N/T 双阈值淘汰、断线重放
- [x] Req/Resp 去重缓存（`seq -> RESP` 的 TTL/LRU）+ 超时兜底（服务端侧）
- [x] 会话与门禁：未登录拒绝业务 msgId（>=1000），支持“踢旧连接”策略（可配置）
- [x] 连接恢复最佳实践落地：小差值 Resume 续传；大差值触发全量 SyncSnapshot

## P1（稳定性/性能）

- [x] Dispatcher：in-flight 的“按连接/按 shard”更细粒度限流 + 更明确的错误码/错误语义
- [x] Dispatcher：对 handler 执行耗时的分位数统计与慢请求日志（可配置阈值）
- [x] Netty：WS pipeline 的参数可配置化增强（backlog/recvBuf/allocator 等）
- [x] 传输：可插拔 transport（tcp/ws 可切换，ws 默认）
- [x] Protocol：`ENCRYPT/COMPRESS` 协商与实现（内置 `GZIP`；加密推荐 `TLS`）

## P2（分布式与工程化）

- [x] Jobs：提供至少一个 `LeaseProvider` 参考实现（Redis/etcd/ZK 任选其一，作为可选模块）
- [x] 分布式任务建议：补充 MQ/Stream 的任务分发最佳实践与示例（仅框架/接口，不写业务）
- [x] Registry：元信息协议完善（transportCaps/endpoints/instanceId）与灰度/权重字段预留
- [x] IPC：Unix socket / shared-memory 的实现模块（含背压）

## 文档与示例（不含业务逻辑）

- [x] Quickstart：提供一个最小 `examples/`（echo + resume/snapshot 演示），不包含任何玩法逻辑
- [x] 文档：补充“如何接入你自己的 admin/网关/登录服务”的集成说明（只写接口与流程）
