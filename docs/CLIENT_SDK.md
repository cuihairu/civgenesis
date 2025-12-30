# 客户端 SDK（Unity / Cocos / LayaAir）

本仓库核心是服务端框架，但协议是 **WebSocket Binary + TLV**，因此需要客户端侧有“可复用的编解码与连接管理”。

本节提供：

- Unity（C#）参考实现：TLV 编解码 + 请求/响应（seq）+ 可靠推送 ACK（ACK_REQUIRED）
- TypeScript（Cocos Creator / LayaAir / 浏览器）参考实现：同上

> 说明：这里的 client SDK 不包含任何业务逻辑，只实现协议与连接管理。

## 1) WebSocket 二进制

- Unity / LayaBox 都必须使用 **binary**（ArrayBuffer / byte[]）发送与接收
- 生产环境建议使用 `wss://`（TLS）

## 2) 协议要点（客户端必须实现）

详见 `docs/PROTOCOL.md`，客户端侧需要重点实现：

- TLV Frame 编解码（uvarint tag/len）
- Req/Resp：`seq>0`，超时重试必须复用同一 `seq`
- 可靠推送：仅当 `flags.ACK_REQUIRED` 时才需要 ACK，并用 `pushId` 去重
- 断线重连：优先走 `Resume(lastAppliedPushId)`，窗口不满足则全量 SyncSnapshot

## 3) 参考代码位置

- Unity（C#）：`clients/unity/`
- TypeScript（Cocos/LayaAir）：`clients/ts/`

这些代码默认只依赖语言标准库（不绑定具体 WebSocket 插件/引擎），通过一个最小 `Transport` 接口接入。

## 4) TypeScript SDK 构建（可选）

`clients/ts/` 提供一个可发布的 npm 包骨架（仅 TypeScript 编译，无运行时依赖）：

```bash
cd clients/ts
npm i
npm run build
```

更多用法见 `clients/ts/README.md`。
