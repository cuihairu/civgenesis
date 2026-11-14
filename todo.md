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

## 语言与文档站（P2）

- [ ] 浏览器语言自动识别跳转（等用户拍板范围；基线=手动切换+显式链接）——当前文档站为 VuePress 单 locale（`zh-CN`，`docs/.vuepress/config.ts:6`），无 `locales` 路由、无 `/zh/`，README 为英文单文件，两者之间无互链；自动识别属增强项，需先产出双语内容再谈跳转（见 `docs/审计-文档一致性.md` G2-2）
- [ ] 是否把文档站迁到 root=EN + `/zh/` 的 locale 契约（与全舰队一致）——结构迁移，非缺页补齐，待拍板

## 文档与示例（不含业务逻辑）

- [x] Quickstart：提供一个最小 `examples/`（echo + resume/snapshot 演示），不包含任何玩法逻辑
- [x] 文档：补充“如何接入你自己的 admin/网关/登录服务”的集成说明（只写接口与流程）

## 覆盖率 98% 门槛（2026-11-09）

- [x] 覆盖率巡检（2026-10-20 巡检单）：codecov 报 48.24% → 核实为**测量缺失**而非真实缺口：`examples/echo-server`（application 模块）从未被 jacoco 插桩，其类全部按 missed 计入。修复根 `build.gradle.kts` 插桩触发器（`java-library` → `java`）后，插桩面即覆盖全部 java 模块。
- [x] 对真实缺口补测试（`db5c4e0` 插桩修复 + `f398452` aeron、`a75ee60` tcp/ws loopback、`f1acb02` tlv、`5890ec7` executors、`2a661de` nacos、`bb62c6b` grpc/selector、`54ee9ce` echo-server 与错误路径）。
- [x] 复测：`./gradlew clean test jacocoRootReport` 全绿，根聚合 **LINE 99.07%**（covered 2450 / missed 23，共 2473），高于 98% 门槛（49 行内）。
- [x] 剩余 23 行未覆盖均非关键路径，逐条记录（不凑数、不改生产行为）：
  - `ipc/routing/EndpointSelector.java` 95/103/104（3 行）——防御分支，isLoopbackTcp 仅对 TCP 类端点调用，blank-host 分支已被 `tcp:///x:9` 用例经过
  - `dispatcher/runtime/GzipPayloads.java` 24-26（3 行）——compress 中 GZIPOutputStream 抛 IOException 的兜底（标准 gzip close 不会抛，需重度 mock 才可达）
  - `dispatcher/route/RouteScanner.java` 35（1 行）——非 public 方法防御检查，`getMethods()` 永远只返回 public，实际不可达
  - `ipc/uds/UdsIpcConnection.java` 62/81/106-109/124（7 行）——队列满回滚、半包截断、写循环异常兜底等防御路径
  - `ipc/uds/UdsIpcServer.java` 56/68/79/85（4 行）——accept 线程 lambda 的 jacoco 行表错位（实际字节码落在 52 行），仅运行期 IO 异常可触发
  - `spring/observability/CivgenesisObservabilityAutoConfiguration.java` 87/90/99/102（4 行）——@ConditionalOnProperty 空标记类的隐式构造器，实例化测试纯属凑数
  - `ipc/aeron/AeronIpcEndpoint.java` 27（1 行）——URI 有 scheme 时 ssp 不可能为 null，防御分支不可达
