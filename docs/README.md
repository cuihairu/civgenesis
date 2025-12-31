---
home: true
title: CivGenesis
heroText: CivGenesis
tagline: Java 21 / Spring Boot / Netty 游戏服务器 SDK（框架代码）
actions:
  - text: 设计
    link: /DESIGN
  - text: 协议
    link: /PROTOCOL
  - text: Dispatcher
    link: /DISPATCHER
features:
  - title: Netty-only 消息链路
    details: 游戏消息全部走 Netty；Spring Boot 只负责装配与生命周期
  - title: TLV 协议 + Req/Resp
    details: 自定义 msgId + 可扩展 TLV；支持超时重试、去重与背压
  - title: 可靠推送与续传
    details: ACK_REQUIRED 才做可靠推送；断线小窗口续传/大窗口全量 Sync
---

## 文档

- [QUICKSTART](./QUICKSTART.md)
- [DESIGN](./DESIGN.md)
- [PROTOCOL](./PROTOCOL.md)
- [PROTOBUF](./PROTOBUF.md)
- [OBSERVABILITY](./OBSERVABILITY.md)
- [JOBS](./JOBS.md)
- [DISPATCHER](./DISPATCHER.md)
- [IPC](./IPC.md)
- [REGISTRY_NACOS](./REGISTRY_NACOS.md)
- [GRPC](./GRPC.md)
- [CLIENT_SDK](./CLIENT_SDK.md)
- [SCHEDULER](./SCHEDULER.md)
- [CONFIG](./CONFIG.md)
- [SECURITY](./SECURITY.md)
- [SYSTEM_MESSAGES](./SYSTEM_MESSAGES.md)
- [INTEGRATION](./INTEGRATION.md)
- [HOTFIX](./HOTFIX.md)
