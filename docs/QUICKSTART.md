# Quickstart（示例工程）

本仓库提供最小示例：`examples/echo-server/`（不包含任何玩法逻辑）。

## 1) 运行服务端

```bash
./gradlew :examples-echo-server:run
```

默认监听：

- WS：`ws://127.0.0.1:8888/ws`

## 2) 推荐交互流程（客户端）

示例流程（协议层）：

1. `ClientHello`（msgId=1）
2. `Resume`（msgId=3），token 传 `1` 或 `p:1`（示例鉴权实现会解析为 `playerId=1`）
3. 业务请求 `Echo`（msgId=1000）：
   - 请求/响应：`examples/echo-server/src/main/proto/echo.proto` 的 `EchoReq/EchoResp`
4. 触发全量快照 `Sync`（msgId=4）：
   - 服务端会 `PUSH(SyncSnapshotPush)`（可用于演示断线恢复与可靠推送）

> 注意：本仓库的 Unity/TypeScript SDK 是“协议层 SDK”；业务 Protobuf（如 `EchoReq/EchoResp`）需要你在客户端侧自行生成并在业务层解析。

