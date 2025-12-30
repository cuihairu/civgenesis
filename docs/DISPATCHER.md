# Dispatcher 与注解路由（草案）

目标：游戏消息全部走 Netty；业务侧通过自定义注解声明路由（类似 Controller），启动时扫描并构建 O(1) 路由表，运行期不走 Spring MVC/Tomcat。

本节覆盖：

- 如何组织 `msgId` 与 Protobuf 消息
- 如何写 handler（`@GameController/@GameRoute`）
- 如何自动扫描并注册路由（Spring Boot 自动装配）

## 1) 路由注解（建议）

- `@GameController`：标记一个“路由容器”（通常是 Spring bean，便于 DI）
- `@GameRoute(id=..., open=..., shardBy=..., codec=...)`：标记一个处理方法

推荐约束：

- `id`（msgId）必须唯一，且业务路由建议 `>= 1000`（系统保留段见 `docs/PROTOCOL.md`）
- `open=false` 的路由必须要求已登录（`playerId != 0`）
- 默认 `shardBy=PLAYER`，未登录时自动降级为 `CHANNEL`

## 2) Handler 形态（最佳实践：默认强类型，保留高性能扩展）

为了兼顾“接入易用/类型安全”与“极致性能/零拷贝”，建议支持两种 handler 形态：

### 2.1 强类型（默认）

SDK 在 IO 线程完成解码后，把“请求对象”投递到分片线程执行：

推荐同时支持两种写法，但以 `ctx.reply` 为主（更适合游戏服的异步/定时场景）：

- `void handle(RequestContext ctx, FooReq req)`：通过 `ctx.reply(...) / ctx.error(...) / ctx.push(...)` 输出（**推荐**）
- `FooResp handle(RequestContext ctx, FooReq req)`：纯同步场景直接返回响应（可选）

其中 `FooReq/FooResp` 为 Protobuf message（或其他 codec 的强类型对象）。

优点：

- 业务代码可读性高、边界清晰
- 更容易做鉴权/限流/观测（统一入口）

约束（建议强制）：

- “返回值模式”与 “显式回包模式”不可混用：
  - 若 handler 使用返回值模式：运行期禁止调用 `ctx.reply/ctx.error`（否则抛错或记录严重告警）
  - 若 handler 使用 `void + ctx.reply`：必须在处理链路中 **显式**完成响应：
    - 同步完成：直接 `ctx.reply/ctx.error`
    - 异步完成：先 `DeferredReply d = ctx.defer()`，在异步回调回投分片线程后通过 `d.reply/d.error` 完成
- 框架应提供“请求未响应”检测：
  - 若未 `defer` 且 handler 返回后仍未响应：自动回 `Error(code=TIMEOUT|NO_RESPONSE)` 并记录告警（防业务漏回包）

### 2.3 DeferredReply（异步响应句柄，最佳实践）

为了避免业务把 `ctx` 捕获到异步回调中导致：

- 切号后串号回包
- 断线后仍尝试回包
- 重复回包/多次完成

建议 `ctx.defer()` 返回一个一次性句柄 `DeferredReply`：

- `DeferredReply reply = ctx.defer()`
- `reply.reply(resp)`：完成响应（只能成功一次）
- `reply.error(code, message, retryable)`：完成错误响应（只能成功一次）
- `reply.cancel()`：取消（由框架在断线/切号时触发；业务可选调用）

语义建议：

- `DeferredReply` 内部捕获不可变上下文：`channelId/playerId/sessionEpoch/msgId/seq`
- 完成时必须校验：
  - channel 仍然活跃
  - `sessionEpoch` 仍匹配（防切号串号）
- `reply/reply.error` 只允许一次（内部 CAS/原子状态机）
- 若已 cancel 或校验不通过：完成操作变为 no-op，并记录 debug/metrics（避免业务回调抛异常）

框架建议提供语法糖：

- `ctx.blocking(callable).thenReply(mapper)`：阻塞任务在虚拟线程/阻塞池执行，完成后自动回投同一分片线程并完成 `DeferredReply`

### 2.2 原始 bytes（高级用法）

业务方法直接处理 payload bytes（例如 `ByteBuf` 或 `byte[]`），自行解码：

- `void handleRaw(RequestContext ctx, ByteBuf payload)`

适用：

- 极端高频、希望延迟更低/减少对象创建
- 自定义二进制协议（非 protobuf）

约束（最佳实践：默认 retain + 自动 release）：

- raw handler 属于“高性能高级接口”，SDK 默认以 **零拷贝优先**：
  - IO 线程拿到 frame 后，对 payload 使用 `retainedSlice()`（或等价方式）再投递到分片线程
  - 分片线程执行 `handleRaw` 时，在 `finally` 中自动 `release()`（无论成功/失败）
- 业务方不得在 `handleRaw` 返回后继续使用 `payload`，除非显式 `retain()` 并自行在合适时机 `release()`
- 若需要异步处理 raw payload：
  - 不建议把 `ByteBuf` 直接跨线程长期持有
  - 最佳实践是尽早解码为业务对象/byte[]，或在必须保留时业务方 `retain()` 并保证释放

可配置的安全模式（建议提供）：

- `COPY`：SDK 在投递前把 payload `copy` 到 heap（如 `byte[]` 或 heap `ByteBuf`），业务不再接触引用计数；更安全但有额外拷贝成本
- `RETAIN`：默认模式，性能更好但要求业务遵守引用计数规则

推荐做法：

- 默认使用强类型；只有在明确压测收益后才用 raw handler

## 3) 路由表构建（启动时）

最佳实践：**只扫描一次**，并把反射调用提前编译为 `MethodHandle`（避免运行期反射开销）。

建议流程：

1. 从 Spring `ApplicationContext` 获取 `@GameController` bean 列表
2. 扫描其 `@GameRoute` 方法
3. 校验：
   - msgId 唯一
   - 方法签名符合“强类型”或“raw”约定
   - `open=true` 的路由不得声明“必须登录”
4. 为每个 route 生成一个 `RouteInvoker`（基于 `MethodHandle`）
5. 构建 `RouteTable`

### 3.1 Spring Boot：自动扫描与注册（已实现）

`civgenesis-spring-boot-starter` 默认提供：

- 自动扫描 `@GameController` 的 Spring Bean
- 构建 `RouteTable`
- 构建 `Dispatcher`（默认 `DispatcherRuntime`）

最小配置示例：

```yaml
civgenesis:
  dispatcher:
    enabled: true
    shards: 64
    max-in-flight-per-connection: 64
    raw-payload-mode: RETAIN
    close-on-need-login: false
  ws:
    enabled: true
    port: 8080
    path: /ws
```

覆盖策略（最佳实践）：

- 若你自己提供 `Dispatcher` Bean：框架自动装配会让位
- 若你自己提供 `PayloadCodec/ShardExecutor/RouteTable`：框架默认实现会让位

### 3.2 纯 Java：手动扫描（不依赖 Spring）

```java
RouteTable routeTable = new RouteScanner().scan(List.of(new YourController(...)));
Dispatcher dispatcher = new DispatcherRuntime(routeTable, codec, shardExecutor, DispatcherConfig.defaults());
```

`RouteTable` 的数据结构（建议）：

- `Int2ObjectMap<RouteInvoker>`（避免 msgId 很大时数组浪费）
- 或者在 msgId 稠密时自动切换成数组 `RouteInvoker[]`

## 4) RequestContext（运行期上下文）

建议 `RequestContext` 至少包含：

- `long channelId`（连接 id）
- `long playerId`（未登录为 0）
- `long sessionEpoch`（用于“切号护栏”，见 `docs/PROTOCOL.md`）
- `int msgId`
- `int seq`（REQ/RESP）
- `long pushId`（仅 PUSH/ACK）
- `InetSocketAddress remoteAddress`
- `Map<String,Object> attrs`（轻量扩展）

输出能力：

- `reply(resp)`：发送 RESP（自动带回 `seq`）
- `push(msgId, payload, reliable)`：发送 PUSH（`reliable=true` 时设置 `ACK_REQUIRED` 并缓存）
- `error(code, message, retryable)`：统一错误封装（`flags.ERROR` + `Error` payload）

## 5) 分片执行（必须遵守）

最佳实践约束：

- 所有业务 handler 必须在 `ShardExecutor` 的分片线程内执行
- 业务代码不得阻塞分片线程（DB/HTTP/文件 IO 必须走 blocking 执行器）

推荐接口：

- `ctx.blocking(callable)`：在虚拟线程或专用阻塞池执行，完成后自动回投同一分片线程
- 回投前必须校验 `sessionEpoch`（防切号串号）

## 6) 拦截器（可选，但建议预留）

建议提供可插拔的 interceptor 链：

- `preHandle(ctx)`：鉴权/限流/灰度/黑名单
- `postHandle(ctx, result)`：metrics/审计
- `onError(ctx, ex)`：统一错误转换（不要把异常堆栈直接回给客户端）

## 7) in-flight 追踪与超时（最佳实践）

为了支持客户端超时重试与服务端背压，建议对每条连接追踪 in-flight 请求：

- in-flight 的 key：`seq`
- in-flight 的范围：仅 `REQ(seq>0)`；`PUSH` 不计入
- in-flight 的计数：**包含 deferred**（`DeferredReply` 尚未完成）

完成条件：

- 当写出 `RESP`（成功或 `flags.ERROR`）后，将该 `seq` 从 in-flight 移除
- 若 `DeferredReply.cancel()`（断线/切号导致），也应将该 `seq` 从 in-flight 移除

超时策略（可配置）：

- 若请求在 `requestTimeoutMillis` 内仍未完成响应：
  - 返回 `Error(code=SERVER_BUSY|TIMEOUT, retryable=true)`
  - 将该响应写入 `seq -> resp` 去重缓存（后续相同 seq 的重试直接重放该超时响应）

切号/断线策略（最佳实践，定稿）：

- 当连接断开或同连接切号（playerId detach）时：
  - 框架必须 **统一 cancel** 该连接的所有未完成 `DeferredReply`
  - 并从 in-flight 中移除对应 `seq`
- 对于已被 cancel 的 `seq`：
  - 不应重放旧的 deferred 结果（避免串号/越权）
  - 若客户端重试同一个 `seq`：
    - 服务端应返回 `Error(code=SESSION_EXPIRED, retryable=true)`，提示客户端走 `Login/Resume` 或重试流程

## 8) msgId / Protobuf 的建议组织方式（与扫描配套）

建议规则：

- `msgId 1..999`：系统保留；业务使用 `>= 1000`
- 每个业务 `msgId` 配套一组 Protobuf（`XxxReq/XxxResp`）
- `@GameRoute(id=...)` 直接使用常量（避免“魔法数字”）

Protobuf 定义与生成命令见 `docs/PROTOBUF.md`。
