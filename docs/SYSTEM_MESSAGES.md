# 系统消息（protobuf）草案

本文件给出系统保留 `msgId 1..999` 的最小 protobuf schema 建议，用于落地：

- `ClientHello`：能力协商（压缩/加密/限制/可靠推送）
- `Login`：鉴权与会话绑定（支持同连接切号）
- `Resume`：断线重连（续传 vs 全量）
- `Sync/Snapshot`：全量同步兜底
- `Error`：统一错误载荷

说明：

- 字段号（tag）一旦落地应尽量保持兼容，后续只增不改。
- 这里只描述系统层协议；业务层消息（`msgId>=1000`）由接入方定义。

## 1) 公共枚举

- `CompressionAlgorithm`：`NONE=0, ZSTD=1, LZ4=2, GZIP=3`
- `CipherAlgorithm`：
  - `NONE=0`
  - `TLS=1`（表示连接使用 `wss://`，应用层不加密）
  - `CHACHA20_POLY1305=2`（应用层 AEAD）
  - `AES_256_GCM=3`（应用层 AEAD）

## 2) msgId=1 ClientHello（REQ/RESP）

### ClientHelloReq

- `uint32 protocolVersion`：协议版本（初始 `1`）
- `string clientVersion`：客户端版本/构建号
- `uint32 maxFrameBytes`：客户端可接受最大 frame
- `repeated CompressionAlgorithm supportedCompressions`
- `repeated CipherAlgorithm supportedCiphers`
- `bool requireEncrypt`：是否强制加密（TLS 或应用层）
- `bool requireAckPush`：是否要求支持可靠推送（ACK_REQUIRED）
- `bytes clientKeyShare`：仅应用层加密需要（建议 X25519 公钥，32 bytes）
- `bytes clientNonce`：随机数（建议 12~32 bytes，用于 HKDF salt/context）
- `map<string,string> ext`：扩展（可选）

### ClientHelloResp

- `uint32 protocolVersion`
- `CompressionAlgorithm selectedCompression`
- `CipherAlgorithm selectedCipher`
- `uint32 maxFrameBytes`：服务端最终限制（<= 客户端声明，且 <= 服务端上限）
- `uint32 maxInFlightReq`：服务端建议的最大并发未完成请求数（客户端用来控速）
- `uint32 maxBufferedPushCount`：可靠推送窗口 N
- `uint32 maxBufferedPushAgeMillis`：可靠推送窗口 T
- `bytes serverKeyShare`：仅应用层加密需要（X25519 公钥）
- `bytes serverNonce`：仅应用层加密需要
- `string serverEpoch`：服务端进程/实例 epoch（用于判定“是否可续传”，可选）

应用层加密派生（建议）：

- Key exchange：X25519 ECDH 得到 `sharedSecret`
- Key derivation：`HKDF(sharedSecret, salt=clientNonce||serverNonce, info="civgenesis-v1")`
- 派生方向性 key：`c2sKey`、`s2cKey`（并约定每个 frame 的 nonce 生成方式）

## 3) msgId=2 Login（REQ/RESP）

> 说明：该消息建议默认关闭（见 `docs/CONFIG.md`），外置登录服务架构下推荐统一使用 `Resume`。

### LoginReq

- `string token`：鉴权 token（建议为 `accessToken` 或一次性 `wsTicket`）
- `bool kickExistingSession`：是否踢掉同 playerId 的旧连接（默认建议 true，由服务端策略决定）

### LoginResp

- `uint64 playerId`
- `uint64 sessionEpoch`：会话 epoch（切号/重绑时递增）
- `bool needSync`：是否需要全量 Sync（初次登录通常 true）
- `uint64 serverTimeMillis`

## 4) msgId=3 Resume（REQ/RESP）

### ResumeReq

- `string token`（建议为 `accessToken` 或一次性 `wsTicket`）
- `uint64 lastAppliedPushId`：客户端已处理到的可靠推送 id（含）
- `uint64 clientStateRevision`：客户端状态版本（可选）
- `string serverEpoch`：客户端记住的服务端 epoch（可选，用于检测服务端重启/迁移）

### ResumeResp

- `enum Result { OK=0; NEED_FULL_SYNC=1; INVALID=2; }`
- `Result result`
- `uint64 serverStateRevision`（可选）
- `uint64 minBufferedPushId`（可选，仅用于调试/观测）
- `uint64 maxBufferedPushId`（可选，仅用于调试/观测）

> 若 `result=OK`：服务端会重放缺失的“可靠推送”（PUSH + ACK_REQUIRED）。
> 若 `result=NEED_FULL_SYNC`：客户端应触发 `Sync` 或等待服务端 `SyncSnapshot`。

## 5) msgId=4 Sync（REQ/RESP）

### SyncReq

- `uint64 clientStateRevision`（可选）

### SyncResp

- 可为空；建议由服务端直接推送 `SyncSnapshot`（见下）

## 6) msgId=5 SyncSnapshot（PUSH，建议 ACK_REQUIRED）

### SyncSnapshotPush

- `uint64 serverStateRevision`
- `bytes snapshot`：业务快照（开源 SDK 可先保留为 bytes，由接入方定义其结构/二次解码）

## 7) Error（RESP payload，flags.ERROR）

### Error

- `int32 code`
- `string message`
- `bool retryable`
- `map<string,string> detail`

#### 建议系统错误码（V1）

- `1001 INVALID_FRAME`
- `1002 UNSUPPORTED_PROTOCOL`
- `1003 UNAUTHORIZED`
- `1004 NEED_LOGIN`
- `1005 BACKPRESSURE`（连接 in-flight 超限、或目标分片队列满）
- `1006 SERVER_BUSY`（更通用的繁忙）
- `1007 SESSION_EXPIRED`（epoch 不匹配/切号导致）

## 8) 服务端落地位置（已实现）

系统 protobuf 定义：

- `.proto`：`civgenesis-protocol-system/src/main/proto/civgenesis/system.proto`
- msgId 常量：`civgenesis-protocol-system/src/main/java/io/github/cuihairu/civgenesis/protocol/system/SystemMsgIds.java`

服务端系统处理器（框架代码，非业务）：

- `civgenesis-system/src/main/java/io/github/cuihairu/civgenesis/system/controller/SystemClientHelloController.java`
- `civgenesis-system/src/main/java/io/github/cuihairu/civgenesis/system/controller/SystemResumeController.java`
- `civgenesis-system/src/main/java/io/github/cuihairu/civgenesis/system/controller/SystemSyncController.java`

Spring Boot 自动装配：

- `civgenesis-spring-boot-starter/src/main/java/io/github/cuihairu/civgenesis/spring/system/CivgenesisSystemAutoConfiguration.java`

## 9) 需要你提供的 SPI（不包含业务逻辑）

为了避免框架绑定你的账号体系/存储模型，以下能力通过 SPI 注入：

- 鉴权（Resume/Login）：`civgenesis-system/src/main/java/io/github/cuihairu/civgenesis/system/auth/TokenAuthenticator.java`
- 快照（SyncSnapshot）：`civgenesis-system/src/main/java/io/github/cuihairu/civgenesis/system/snapshot/SnapshotProvider.java`

如果你没有提供实现：

- `TokenAuthenticator` 默认是拒绝所有 token（用于提醒你接入自己的鉴权）
- `SnapshotProvider` 默认不支持（只回 `SyncResp`，不推送快照）
