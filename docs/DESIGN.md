# CivGenesis 设计（草案）

目标：提供一个适合游戏服的 **SDK/运行时**，游戏消息全部走 Netty；Spring Boot 只负责装配、配置、生命周期。

## 技术基线

- Java 21
- Spring Boot 3.x（仅用于配置与生命周期，不承担每条消息处理）
- Netty 4.1.x（默认 WebSocket Binary，可选 TCP）
- 协议：自定义 `msgId` + Protobuf payload（`msgId` 不依赖 protobuf 生成常量）

## SDK 模块建议

> 这里是“SDK 形态”的推荐分层；最终是否拆成多模块由仓库实现阶段决定。

- `civgenesis-core`
  - `Dispatcher`：`msgId -> handler` O(1) 路由
  - `Session`：连接态与登录态分离，支持同连接切换账号
  - `ShardExecutor`：按 `playerId` 分片串行执行（少锁化）
  - 统一的 `RequestContext`/`ResponseWriter`
- `civgenesis-transport-netty`
  - WebSocket server/client（可选）
  - pipeline：握手、编解码、限流、心跳、背压、连接管理
- `civgenesis-codec-protobuf`
  - payload 的 Protobuf 编解码
  - `msgId` 与 Protobuf `MessageLite` 的映射（注册表）
- `civgenesis-scheduler`
  - 时间轮定时器（与 `ShardExecutor` 绑定）
- `civgenesis-hotfix-arthas`（可选依赖）
  - Arthas HTTP API 的封装：`redefine` / `jad`

## 消息处理链路（核心）

1. Netty IO 线程：只做解码、轻量校验、限流、鉴权前置
2. 投递到业务执行：`ShardExecutor.execute(shardKey, task)`
3. 业务 handler 执行：读取/修改内存态，必要时发起阻塞任务（DB/HTTP）
4. 阻塞任务返回：回投同一 `shardKey` 执行（避免跨线程改状态）
5. 写回：统一 encoder，批量 flush（可合包/限速）

## 登录服务（最佳实践）

本仓库定位为“游戏服 SDK/运行时”，不承担账号体系与三方渠道接入。最佳实践是将登录鉴权拆为独立服务：

- `auth/login`（HTTP 或独立 TCP）：账号/渠道登录、风控、选服，签发 `accessToken`（可选一次性 `wsTicket`）
- `game-server`（Netty WS）：只做 token 校验并绑定 `PlayerSession`，之后所有业务消息走 WS

这样游戏服可以更专注于高并发长连接与状态管理，同时登录服务可独立扩容与迭代。

## 线程模型（最佳实践）

- IO 与业务隔离：避免在 Netty eventloop 内做任何阻塞/耗时逻辑
- 分片串行：默认按 `playerId` 分片；未登录按 `channelId`
- 账号切换：同连接允许更换 `playerId`，但必须引入 `sessionEpoch` 防止异步回调串号（见 `docs/PROTOCOL.md`）

异步响应建议使用一次性句柄（避免捕获可变 `ctx`）：

- `DeferredReply d = ctx.defer()`，在异步回投分片线程后通过 `d.reply/d.error` 完成响应（详见 `docs/DISPATCHER.md`）

## Java 21 虚拟线程（协程）的使用原则

最佳实践是“**两池分工**”：

- 分片执行器（`ShardExecutor`）：使用平台线程（固定单线程分片），承载**有序/状态机/CPU**逻辑
- 阻塞执行器（`BlockingExecutor`）：优先使用虚拟线程（virtual threads），承载**阻塞 I/O**（DB/HTTP/gRPC 调用等）

原因：

- 虚拟线程适合大量阻塞任务，能显著降低线程占用与提升并发
- 但游戏核心逻辑通常依赖“同玩家/同房间串行与顺序”，用平台线程分片更可控，避免在高频路径引入额外调度抖动

## 自定义 Controller/注解扫描（不走 Spring MVC）

推荐注解（仅作为 SDK 约定，不绑定 Web 框架）：

- `@GameController`：标记容器 bean
- `@GameRoute(id=..., open=..., shardBy=...)`：标记处理方法

启动时构建路由表：

- 从 Spring `ApplicationContext` 获取 `@GameController` bean
- 反射扫描方法上的 `@GameRoute`
- 生成 `RouteTable`（建议用数组或 `int -> invoker` 表），运行期不反射
