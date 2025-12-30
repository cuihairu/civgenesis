# CivGenesis Client (TypeScript)

该 SDK 仅实现 **WebSocket Binary + TLV** 协议与连接管理（Req/Resp + 可靠推送 ACK），不包含任何业务逻辑。

## 使用（Cocos Creator / LayaAir 等）

> 前提：运行环境有标准 `WebSocket`（浏览器/小游戏/Cocos Creator/LayaAir 通常都有）。

```ts
import { CivGenesisClient, WebSocketTransport, FrameType, ProtocolFlags } from '@civgenesis/client'

const transport = new WebSocketTransport()
const client = new CivGenesisClient({
  transport,
  autoReconnect: true,
  ackPiggyback: true,
  onPush: async (frame) => {
    if ((frame.flags ?? 0) & ProtocolFlags.ACK_REQUIRED) {
      // 这里建议：先应用业务逻辑，再让 SDK 发送 ACK（SDK 默认就是 after-handler ack）
    }
    // TODO: 根据 msgId 解码 payload（protobuf / json / 自定义）
  }
})

await client.connect('ws://127.0.0.1:8080/ws')

// Req/Resp
const resp = await client.request(1000, new Uint8Array([1, 2, 3]), { timeoutMs: 1000, retries: 1 })
if (resp.type !== FrameType.RESP) throw new Error('unexpected')
```

## LayaAir 备注

如果你的运行时没有直接暴露 `WebSocket`，但能拿到类似 `Laya.Browser.window.WebSocket`，可以在创建 transport 时传入自定义工厂：

```ts
import { WebSocketTransport } from '@civgenesis/client'

const transport = new WebSocketTransport((url) => new (Laya.Browser.window.WebSocket)(url))
```

