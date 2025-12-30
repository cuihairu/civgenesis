# 协议与会话（草案）

本协议面向：Netty WebSocket Binary（默认）。目标：支持

- Req/Resp（客户端可超时重试）
- 服务器主动推送（**可靠推送**：可 ack + 断线续传）
- 断线重连：小差值续传，大差值全量 Sync
- 同连接切换账号：避免异步任务“串号”
- 心跳与空闲检测：PING/PONG + idle 超时

## 1. Frame 信封（WebSocket Binary，TLV）

采用 TLV 的目标是：**可扩展**（新增字段不破坏旧客户端/旧服务端），并且允许服务端/客户端快速跳过未知字段。

### 1.1 TLV 编码

一个 Frame 由一串 TLV 组成，直到 Buffer 结束：

- `T`：`uvarint`（tag id）
- `L`：`uvarint`（value 字节长度）
- `V`：`L` 字节的 value

约定：

- 未识别的 `T` 必须被跳过（按 `L` 跳过 `V`）
- 同一个 `T` 若出现多次：默认“最后一个覆盖前一个”（用于兼容/扩展）；服务端可选择直接拒绝
- 必须限制：最大 Frame 字节数、最大 TLV 个数、单个 `L` 最大值（防 DoS）

> 数值字段的 value 也使用 `uvarint` 编码存入 `V`（即：`V` 本身再是一个 varint 的 bytes），以便统一跳过逻辑；bytes 字段则直接存原始 bytes。

### 1.2 标准 tags（V1）

`msgId` 约束（定稿）：

- `msgId=0`：保留（不使用）
- `msgId 1..999`：系统保留（SDK/协议层）
- `msgId >= 1000`：业务消息（接入方自行分配）

必须字段：

- `1 type`：`uvarint`，枚举：`REQ=1, RESP=2, PUSH=3, ACK=4, PING=5, PONG=6`
- `2 msgId`：`uvarint`（业务消息号，自定义）
- `6 payload`：`bytes`（protobuf bytes；允许为空 bytes）

可选字段（按 type 决定是否出现）：

- `3 seq`：`uvarint`（仅 `REQ/RESP` 使用；`REQ` 必须 `>0`；`RESP` 原样带回）
- `4 pushId`：`uvarint`（仅 `PUSH/ACK` 使用；必须 `>0`）
- `5 flags`：`uvarint`（位标记：压缩/错误/保留）
- `7 ts`：`uvarint`（可选：服务器毫秒时间戳，用于客户端观测/调试）
- `8 ackPushId`：`uvarint`（可选：**捎带 ACK**，表示“我已处理到该 pushId（含）”；等价于单独发送 `ACK(pushId=ackPushId)`）

### 1.3 约束与推荐顺序

约束：

- `REQ`：必须含 `msgId>=1` 与 `seq>0`
- `RESP`：必须含 `msgId>=1` 与 `seq>0`（原样带回）
- `PUSH`：必须含 `msgId>=1` 与 `pushId>0`，且不应携带 `seq`
- `ACK`：必须含 `pushId>0`（`msgId/payload` 可省略）
- `PING/PONG`：`msgId/payload` 可省略或为空

推荐编码顺序（便于快速解析，非强制）：

1. `type`
2. `flags`
3. `msgId`
4. `seq`
5. `pushId`
6. `ts`
7. `payload`（最后，通常最大）
8. `ackPushId`（若使用捎带 ACK，建议放在 payload 之后或之前保持一致；V1 不强制）

### 1.4 flags 位定义（V1）

- `0x01 ERROR`：payload 为 `Error` protobuf（见“错误模型”）
- `0x02 COMPRESS`：payload 已压缩（算法/字典由上层协商，V1 不强制）
- `0x04 ENCRYPT`：payload 已加密（算法/密钥交换由上层协商，V1 不强制）
- `0x08 ACK_REQUIRED`：接收方需要尽快发送 ACK（主要用于 `PUSH`）

> 说明：`COMPRESS/ENCRYPT` 的具体算法由 `ClientHello` 协商决定；若未协商则默认不启用。

## 2. Req/Resp 语义（支持重试/超时）

### 2.1 基本规则

- 客户端发送 `REQ(seq>0)`。
- 服务端必须返回 `RESP(seq=同值)`。
- 客户端超时重试：**重发同一个 seq**（不要更换 seq）。

### 2.2 服务端去重（建议强制）

对每个 `PlayerSession` 维护一个小型缓存 `seq -> respFrame`（LRU/TTL）：

- 收到 `REQ`：
  - 若 `seq` 已存在：直接重放缓存的 `RESP`
  - 否则：执行业务 -> 生成 `RESP` -> 写入缓存

收益：客户端重试不会重复扣资源/重复发奖/重复创建对象。

## 3. 可靠推送（PUSH + ACK）

### 3.1 推送序号

- 每个 `PlayerSession` 维护单调递增的 `pushId`。
- 所有 `PUSH` 都必须带 `pushId`。
- **只有当 `flags` 包含 `ACK_REQUIRED` 时，该 `PUSH` 才要求 ACK**；否则视为 best-effort 推送（可丢失、不重放）。

#### pushId 的生命周期（最佳实践）

- `pushId` **从 1 开始**，对同一个 `PlayerSession` 单调递增。
- 断线重连的“续传”依赖：服务端仍保留该 `PlayerSession` 的可靠推送缓存窗口（见下文 `N/T` 双阈值）。
- 当发生以下情况之一时，认为续传不成立，直接走全量 Sync（并在新的 `PlayerSession` 中把 `pushId` 重新从 1 开始）：
  - 服务端重启或进程切换导致可靠推送缓存丢失
  - 玩家离线超过服务端的缓存窗口（`N/T` 覆盖范围外）
  - 账号切换（同连接切换 playerId）导致旧 `PlayerSession` 被 detach

#### 是否需要持久化 pushId？

一般**不建议**把每条推送/每次 pushId 增量写入存储（成本高，容易拖慢游戏服）。

推荐语义：

- “可靠推送”只在内存窗口内可靠（至少一次 + 断线续传）
- 一旦窗口不满足，就全量 Sync（快照兜底）

如果你确实需要跨重启/长时间离线仍可续传，建议把它作为可选能力（复杂度显著上升）：

- 需要持久化：`lastAppliedPushId`（客户端）与服务端可重放的事件流（例如 Redis Stream/Kafka/DB 事件表）
- 并引入额外的会话标识（例如 `sessionId`/`serverEpoch`）用于判定“是否同一条事件流”

### 3.2 客户端 ACK

客户端维护 `lastAppliedPushId`，并周期性发送 `ACK(pushId=lastAppliedPushId)`：

- 可以独立定时 ack（例如 200ms~1s），或在处理到 `ACK_REQUIRED` 的 PUSH 后尽快 ack
- 也可以“捎带 ack”：在任意 outbound frame 中携带 TLV `ackPushId`（实现上可选，用于减少 ACK-only 帧）

### 3.3 服务端重放缓存

服务端仅对“可靠推送”维护推送缓存（ring buffer），并采用**双阈值组合**限制内存：

- `maxBufferedPushCount`：最多缓存 N 条可靠推送
- `maxBufferedPushAge`：最多缓存最近 T 时间窗口内的可靠推送

实现上建议两者同时生效：超过任意阈值都要淘汰更早的推送（通常按 `pushId` 从小到大淘汰）。

推荐默认值（可配置）：

- `N = 2000`（按玩家分片/房间事件量调整）
- `T = 60s`（移动网络环境可适当放大，但要评估内存）

- 连接在线：收到 `ACK` 后可释放更早的“可靠推送”缓存
- 断线重连：仅对“可靠推送”根据客户端提供的 `lastAppliedPushId` 重放缺失推送

可靠性语义（建议）：

- 网络语义：对“可靠推送”是 **至少一次**（可能重放重复 push）
- 客户端语义：对“可靠推送”通过 `pushId` 去重，可实现“看起来像 exactly-once”

## 4. 断线重连：续传 vs 全量 Sync（最佳实践）

### 4.1 Resume 请求（轻量）

客户端重连后发送 `REQ(msgId=Resume, payload含)`：

- `token/credential`
- `lastAppliedPushId`
- （可选）`clientStateRevision`：客户端认为的最新状态版本

### 4.2 服务端决策

服务端校验后：

- 若 `lastAppliedPushId` 在服务端可重放窗口内（同时满足 `N` 与 `T` 的阈值范围）：返回 `RESP(ResumeOk)` 并重放从 `lastAppliedPushId+1` 起的推送
- 若差值过大（超过 `N`、或推送过旧超过 `T`、或状态版本不兼容、或服务端重启丢缓存）：返回 `RESP(NeedFullSync)`，随后执行全量 Sync

判定建议（示意）：

- 服务器已缓存的可靠推送区间为 `[minPushId, maxPushId]`，且 `maxPushTs - minPushTs <= T`
- 当 `lastAppliedPushId < minPushId` 或 `lastAppliedPushId > maxPushId`：判定为不可续传（走全量）
- 当 `maxPushId - lastAppliedPushId > N`：判定为不可续传（走全量）

### 4.3 全量 Sync（安全兜底）

全量 Sync 推荐使用：

- `RESP(SyncSnapshot)`：包含关键状态快照 + `serverStateRevision`
- 之后继续正常 `PUSH(pushId=...)`

原则：

- PUSH 用于“增量通知/事件流”
- SyncSnapshot 用于“最终一致兜底”（任何丢包/乱序都能恢复）

## 5. 同连接切换账号（必须的护栏）

你允许“同连接切换账号”后，必须防止异步任务回调写错玩家：

- `PlayerSession` 引入 `sessionEpoch: i64`（每次 attach/detach 自增）
- 每个请求上下文捕获 `(playerId, sessionEpoch)`
- 任何异步结果回投分片线程执行前校验 `epoch`：
  - 不匹配：丢弃结果或返回“会话已失效”

同时建议：

- 同一 `playerId` 只允许一个活跃连接：新登录踢旧连接（可配置）

## 6. 错误模型（统一错误信封）

所有 `RESP` 统一可携带错误：

- 若成功：`flags` 不含 `ERROR`，payload 为业务响应 protobuf
- 若失败：`flags` 包含 `ERROR`，payload 为 `Error` protobuf

`Error` 建议字段：

- `code: int32`（错误码）
- `message: string`（可读消息，便于排查）
- `retryable: bool`（客户端是否可重试）
- `detail: map<string,string>`（可选，便于定位）

## 7. 控制消息（系统 msgId 保留段，最佳实践）

## 8. 心跳与空闲连接（最佳实践）

协议层提供 `PING/PONG` frame type：

- 任意一方都可以发送 `PING`
- 收到 `PING` 必须尽快回 `PONG`

服务端建议策略（可配置）：

- 使用 Netty `IdleStateHandler` 做空闲检测
- `idleTimeoutSeconds=30`（更激进，节省连接资源）
- 空闲超时前先发一次 `PING` 探测：
  - 若在 `pingTimeoutMillis` 内收到 `PONG`：认为连接仍存活，不断开
  - 否则断开连接

为避免业务与“连接/会话/同步/鉴权”类协议冲突，建议 **预留系统 msgId 段**，并强制业务 msgId 从较大值开始分配。

推荐：

- `msgId 1..999`：系统保留（SDK/协议层）
- `msgId >= 1000`：业务消息（接入方自行分配）

登录门禁（定稿）：

- 未登录状态下：
  - 仅允许系统消息（`msgId 1..999`）与 `PING/PONG/ACK`
  - 任何业务消息（`msgId >= 1000`）一律拒绝：返回 `Error(code=NEED_LOGIN, retryable=true)`，并可配置是否断开连接

### 7.1 系统消息建议表（V1）

以下仅定义“语义与流转”，具体 protobuf schema 后续在 `civgenesis-protocol` 中给出最小实现。

- `msgId=1 ClientHello`
  - 用途：能力协商（压缩/加密/最大包大小/客户端版本等）
  - 建议：`REQ/RESP`
  - 协商项（建议，均可配置）：
    - `clientVersion`：客户端版本/构建号（用于灰度/兼容性判断）
    - `maxFrameBytes`：客户端可接受的最大 frame（服务端会取 min 并做上限保护）
    - `supportedCompressions`：客户端支持的压缩算法列表（建议枚举：`NONE, ZSTD, LZ4, GZIP`）
    - `supportedCiphers`：客户端支持的加密算法列表（建议枚举：`NONE, TLS, CHACHA20_POLY1305, AES_256_GCM`）
    - `requireEncrypt`：是否强制启用加密（若服务端无法满足则拒绝连接/拒绝登录）
    - `requireAckPush`：是否要求“可靠推送”（服务端可按玩法/场景启用；不要求则只发 best-effort 推送）
  - 服务端选择（RESP）：
    - `selectedCompression`：选定的压缩算法（或 `NONE`）
    - `selectedCipher`：选定的加密算法（或 `NONE/TLS`）
    - `serverLimits`：如 `maxFrameBytes/maxInFlightReq/maxBufferedPushCount/maxBufferedPushAge` 等
  - 生效规则（最佳实践）：
    - 压缩与加密顺序：发送端“先压缩再加密”，接收端“先解密再解压”
    - 若连接已在 TLS 上：`selectedCipher` 可返回 `TLS`（表示“由传输层保障”，应用层不再重复加密）
    - `flags.ENCRYPT` 只表示“应用层加密”；若使用 `TLS`，建议不设置 `flags.ENCRYPT`

### 7.3 协商算法（最佳实践）

服务端在处理 `ClientHello` 时：

- 计算交集：`clientSupported ∩ serverAllowed`
- 按服务端偏好顺序选择（可配置）：`preferred -> fallback...`
- 若客户端 `requireEncrypt=true` 且最终选到 `NONE`：返回错误并可选断开连接
- 若客户端 `requireAckPush=true`：服务端应在 `serverLimits` 中明确打开“可靠推送”，并在需要可靠性的 `PUSH` 上设置 `flags.ACK_REQUIRED`

服务端建议默认偏好（可配置）：

- 压缩：`ZSTD`（性能/压缩比综合较好）-> `LZ4`（极低延迟）-> `GZIP`（兼容）-> `NONE`
- 加密：优先 `TLS`（`wss://`）；若必须应用层加密，则 `CHACHA20_POLY1305`（移动端友好）-> `AES_256_GCM` -> `NONE`

### 7.4 ClientHello 是否强制？

最佳实践：

- 允许客户端不发送 `ClientHello`（简化接入）：服务端使用默认策略（`compression=NONE`，`cipher` 取决于是否运行在 TLS 上，`maxFrameBytes` 使用服务端默认上限）。
- 生产环境若需要强制某些能力（例如必须加密/必须可靠推送窗口对齐），可配置为“强制 ClientHello”，否则拒绝 `Login`。
- `msgId=2 Login`
  - 用途：鉴权并绑定 `playerId`（允许同连接重复 Login 实现切号）
  - 建议：`REQ/RESP`
  - 备注：**默认建议关闭**（由配置开启），优先使用 `Resume` 作为统一入口（首次/重连/切号），更贴合“外置登录服务”的架构（见下文）。
- `msgId=3 Resume`
  - 用途：断线重连续传判定（携带 `lastAppliedPushId`）
  - 建议：`REQ/RESP`
- `msgId=4 Sync`
  - 用途：客户端主动请求全量同步（或服务端要求后触发）
  - 建议：`REQ/RESP` 或由服务端直接 `PUSH` 快照
- `msgId=5 SyncSnapshot`
  - 用途：服务端下发全量状态快照（兜底恢复）
  - 建议：`PUSH` 且 `flags` 包含 `ACK_REQUIRED`（确保送达）

> 说明：`PING/PONG/ACK` 由 frame `type` 实现，不占用 `msgId`。

### 7.2 推荐时序（V1）

- 首次连接：
  1. （可选）`REQ(ClientHello)`
  2. 推荐统一入口：`REQ(Resume)`（携带 token + `lastAppliedPushId=0`）-> `RESP(ResumeOk|NeedFullSync)`
  3. 若 `NeedFullSync`：服务端 `PUSH(SyncSnapshot, ACK_REQUIRED)` 或客户端 `REQ(Sync)` 再由服务端下发快照
- 断线重连：
  1. `REQ(Resume)`（包含 token + lastAppliedPushId）
  2. `RESP(ResumeOk)` 并重放缺失的可靠推送；或 `RESP(NeedFullSync)` 然后走全量 `SyncSnapshot`
- 同连接切号：
  1. `REQ(Resume(newToken, lastAppliedPushId=0))`（或 `REQ(Login(newToken))`）
  2. 服务端 detach 旧 `PlayerSession`（epoch++，pushId 重新从 1 开始）
  3. `RESP(LoginOk)`，随后进入 Resume/Sync 流程

## 9. 外置登录服务（最佳实践）

通常会将账号登录与选服拆成独立服务（HTTP 或独立 TCP），游戏服只负责 token 校验与会话绑定。

### 9.1 推荐链路

1) 客户端 -> 登录服务（HTTP）

- 完成账号/渠道登录、风控、选服
- 返回：
  - `accessToken`（短期）
  - （可选）`wsTicket`（一次性票据，用于降低 token 泄露风险）
  - `wsEndpoint`（推荐为 `wss://`）

2) 客户端 -> 游戏服（WS）

- 连接 `wss://wsEndpoint`
- （可选）发送 `ClientHello` 协商能力
- 发送 `Resume(token=accessToken或wsTicket, lastAppliedPushId=0)`
- 服务端完成鉴权并 attach `PlayerSession`，然后走续传/快照流程

### 9.2 token 校验（建议）

- 优先离线校验（JWT + 公钥/JWK），减少对登录服务的实时依赖
- 若必须在线校验，可在游戏服侧加缓存（例如短 TTL）以降低压力
