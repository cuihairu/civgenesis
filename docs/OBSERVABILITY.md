# 可观测性：Prometheus Metrics + OpenTelemetry Tracing

本项目不走 Spring MVC，但仍建议按“生产最佳实践”提供：

- **Metrics**：用于容量评估、背压告警、性能回归
- **Tracing**：用于跨进程/跨线程定位卡顿与异常（尤其是 gRPC/外部服务调用）

## 1) 启用 Prometheus（内置 /metrics）

开启后会启动一个独立的 Netty HTTP 端口用于 Prometheus 抓取（默认 `0.0.0.0:9090/metrics`）。

```yaml
civgenesis:
  observability:
    prometheus:
      enabled: true
      host: 0.0.0.0
      port: 9090
      path: /metrics
```

> 说明：开启 `prometheus.enabled` 会自动启用内部 metrics 采集。

验证：

```bash
curl http://127.0.0.1:9090/metrics
```

### 1.1 内置指标（当前版本）

- `civgenesis_transport_connections{transport="ws"}`：当前连接数（gauge）
- `civgenesis_transport_connections_total{event="open|close"}`：连接开关次数（counter）
- `civgenesis_transport_frames_total{direction="in|out",type="REQ|RESP|PUSH|ACK|PING|PONG"}`：收发帧数（counter）
- `civgenesis_transport_frame_bytes{direction=...,type=...}`：收发帧大小（summary）
- `civgenesis_dispatch_in_flight`：当前 in-flight 请求数（gauge）
- `civgenesis_dispatch_requests_total{msg_id=...,status="ok|error",error_code=...}`：请求完成数（counter）
- `civgenesis_dispatch_request_seconds{msg_id=...,status=...,error_code=...}`：请求耗时（timer/histogram）
- `civgenesis_job_total{job=...,status="ok|error"}`：后台任务执行次数（counter）
- `civgenesis_job_seconds{job=...,status="ok|error"}`：后台任务耗时（timer/histogram）

> `msg_id` 有一定基数，建议按“模块/玩法”规划 msgId 段，避免无限增长导致指标基数过大。

## 2) 启用 OpenTelemetry（Tracing）

本项目提供“业务消息处理”的手动埋点（span）。推荐使用 **OpenTelemetry Java Agent** 来负责导出与采样配置。

```yaml
civgenesis:
  observability:
    tracing:
      enabled: true
      instrumentation-name: civgenesis
```

### 2.1 推荐运行方式（Java Agent）

启动参数示例：

```bash
java \
  -javaagent:/path/opentelemetry-javaagent.jar \
  -Dotel.service.name=civgenesis-gateway \
  -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
  -Dotel.traces.exporter=otlp \
  -jar app.jar
```

该 span 会带以下 attributes（便于排查）：

- `cg.msg_id` / `cg.seq`
- `cg.connection_id`
- `cg.player_id`
- `cg.session_epoch`

## 3) 与协议/Dispatcher 的关系

- 业务消息 `msgId >= 1000`（见 `docs/PROTOCOL.md`）
- 路由扫描与 handler 注册见 `docs/DISPATCHER.md`
