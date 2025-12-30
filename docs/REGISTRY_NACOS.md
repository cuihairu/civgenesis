# Nacos 注册中心（默认实现）

CivGenesis 默认提供基于 Nacos 的服务注册与发现（分布式注册）。

模块：

- `civgenesis-registry`：注册/发现 API 与元信息编码
- `civgenesis-registry-nacos`：Nacos 实现

> 说明：当前仓库不强制绑定某个“服务命名规范”；你可以按自己的进程拆分来定义 `serviceName`（例如 `civgenesis.gateway` / `civgenesis.game`）。

## 1) 元信息（metadata）

Nacos instance metadata（默认 key）：

- `cg.instanceId`：uint64（十进制字符串）
- `cg.transportCaps`：uint64（十进制字符串）
- `cg.endpoints`：端点列表（每项 URL-encode 后用 `,` 连接）

对应代码：`io.github.cuihairu.civgenesis.registry.RegistryMetadata`

## 2) 注册（register）

`NacosServiceRegistry.register(...)` 会把你的 `ServiceRegistration` 注册为 ephemeral instance，并写入上述 metadata。

## 3) 发现（discovery）

`NacosServiceRegistry.list(serviceName)` 返回 `ServiceInstance` 列表，包含：

- `instanceIdLong` / `transportCaps` / `endpoints`（从 metadata 解析）
- `ip/port`（Nacos instance 基础字段）

## 4) 选路（同机优先）

拿到 `ServiceInstance` 后，建议使用 `docs/IPC.md` 的策略选择端点：

- 同机：优先 `SHM` > `UDS` > `TCP loopback`
- 异机：TCP

## 5) Spring Boot 配置（可选）

若使用 `civgenesis-spring-boot-starter`，会自动创建 `NacosServiceRegistry`（可关闭）：

```properties
civgenesis.registry.nacos.enabled=true
civgenesis.registry.nacos.server-addr=127.0.0.1:8848
civgenesis.registry.nacos.namespace=
civgenesis.registry.nacos.group=DEFAULT_GROUP
```

> 注意：当前 starter 只负责创建 registry bean，不会自动注册实例；实例注册需要你在应用侧显式调用 `register/deregister`（避免误注册）。

