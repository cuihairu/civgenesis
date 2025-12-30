# Protobuf：消息定义与生成（Java / Unity / TypeScript）

在 CivGenesis 协议里，WebSocket 帧使用 **TLV** 作为信封；业务数据放在 `payload` 字段里，推荐使用 **Protobuf**（二进制、跨语言、成熟工具链）。

> 注意：本项目只提供“框架/协议层”能力；业务消息（`msgId >= 1000`）由接入方自己定义。

## 1) 如何定义消息（最佳实践）

### 1.1 文件与 package

建议每个接入方自己维护一个（或多个） `.proto`：

- `src/main/proto/biz/xxx.proto`（服务端工程）
- 客户端工程（Unity/TS）复用同一套 `.proto`（通过子模块或单独仓库同步）

建议 proto 结构：

- `proto3`
- `package` 固定（例如 `mygame.protocol`）
- 设置各语言 namespace（示例）

```proto
syntax = "proto3";
package mygame.protocol;

option java_multiple_files = true;
option java_package = "com.mygame.protocol";
option csharp_namespace = "MyGame.Protocol";

message EchoReq { string text = 1; }
message EchoResp { string text = 1; }
```

### 1.2 msgId 与消息的对应关系（定稿建议）

建议按「一个 msgId 对应一组 Req/Resp（以及可选 PUSH）」来组织：

- `REQ(msgId=X)` 的 `payload` 解码为 `XxxReq`
- `RESP(msgId=X)` 的 `payload` 解码为 `XxxResp`（或 `flags.ERROR` + `system.Error`）
- `PUSH(msgId=X)`（可选）按同一 msgId 解码为某个 `XxxPush`（若你希望复用 msgId，也可以为 push 单独分配 msgId）

msgId 分配（见 `docs/PROTOCOL.md`）：

- `1..999`：系统保留（SDK/协议层）
- `>= 1000`：业务消息（接入方自行分配）

### 1.3 如何定义/维护 msgId（推荐做法）

跨语言一致性最重要。推荐二选一（或两者组合）：

1) **在 proto 里定义 enum**

```proto
enum MsgId {
  MSG_ID_UNSPECIFIED = 0;
  ECHO = 1000;
}
```

2) **在服务端维护一个常量表（并生成到客户端）**

- Java：`public final class MsgIds { public static final int ECHO = 1000; }`
- Unity/TS：用脚本把同一份常量同步/生成（避免手写漂移）

> 不建议在多个文件/多个团队各自“随手加 id”，要集中管理 + 代码审查。

## 2) 生成 Protobuf 代码（命令）

### 2.1 本仓库：生成系统协议（system.proto）

系统协议 `.proto` 在 `civgenesis-protocol-system/src/main/proto/`，执行：

```bash
./gradlew :civgenesis-protocol-system:generateProto
```

生成目录（Gradle protobuf 默认）：

- `civgenesis-protocol-system/build/generated/source/proto/main/java`

### 2.2 你的服务端工程（Gradle）

在你的 Spring Boot/Gradle 工程里，推荐使用 Gradle Protobuf 插件：

```kotlin
plugins {
  id("com.google.protobuf") version "<your-version>"
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:<protobuf-version>" }
}
```

然后执行：

```bash
./gradlew generateProto
```

### 2.3 Unity（C#）生成命令（protoc）

把 `.proto` 放在某个目录（例如 `proto/`），执行：

```bash
protoc -I=proto --csharp_out=Assets/Scripts/Generated proto/*.proto
```

说明：

- 推荐在 `.proto` 里设置 `option csharp_namespace`
- Unity 里需要引入 `Google.Protobuf` 运行库（NuGetForUnity 或你自己的依赖管理方案）

### 2.4 TypeScript 生成命令（推荐工具链）

TS 的 Protobuf 生成生态较多，推荐优先选：

- `buf` + `protoc-gen-es`（现代、ESM 友好）
- 或 `ts-proto`（生成更“TS 风格”的类型）

本项目不强绑某一个工具链；你可以在客户端仓库按团队习惯选型并固化到 CI。

## 3) 与 CivGenesis Dispatcher 的对接

一条路由（msgId）通常包含：

- 一个 Protobuf `Req`
- 一个 Protobuf `Resp`
- 一个 handler：`(RequestContext, Req) -> Resp` 或 `(RequestContext, Req) -> void`

如何扫描注册 handler 见 `docs/DISPATCHER.md`。

