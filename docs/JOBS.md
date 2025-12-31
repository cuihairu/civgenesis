# 后台任务（本地 / 分布式）

游戏服务端常见“后台任务”分三类，它们的最佳实践不同：

1) **玩家内任务**（强一致/有序）：例如战斗回合计时、建筑完成、buff 到期  
2) **全局定时任务**（集群只跑一次）：例如每日 0 点刷新、排行榜汇总、批量结算  
3) **分布式异步任务**（可水平扩展）：例如发邮件、推送外部回调、写入分析日志

本项目提供本地任务能力，并为“分布式任务”预留扩展点（避免强绑某个中间件）。

## 1) 本地后台任务（推荐：分片线程 + 时间轮）

### 1.1 玩家内定时（推荐）

玩家相关的定时任务建议：

- 由 `playerId` 分片（同玩家串行）
- 使用时间轮（`civgenesis-scheduler`）触发，再回投到该玩家分片执行
- 必要时把“下一次触发时间/状态”持久化到玩家数据，避免进程重启丢失

> 这类任务不要做成“分布式 cron”，否则会破坏玩家状态的顺序性与一致性。

### 1.2 本地异步任务（阻塞 IO）

DB/HTTP/gRPC 等阻塞调用不要占用分片线程；用虚拟线程/阻塞池执行，完成后回投分片线程。

见：`civgenesis-core` 的 `BlockingExecutor`。

## 2) 集群只跑一次的定时任务（Leader-only）

全局 cron / 固定间隔任务建议单独做成 **jobs 进程**（一个或多个实例），并用“租约/主从切换”保证集群只执行一次：

- 能力要求：**带 TTL 的租约 + fencing token**（避免脑裂双写）
- 可靠性语义：至少一次（需要业务幂等/去重）

本仓库提供：

- `civgenesis-jobs`：`CivJob` + `JobRunner` + `LeaseProvider`（SPI）
- `JobMode.LEADER_ONLY`：只有拿到租约才会运行

但 **不默认提供** 分布式锁实现（因为 Redis/etcd/ZK 等选型差异很大）。

本仓库提供一个可选参考实现：

- `civgenesis-jobs-lease-redis`：`io.github.cuihairu.civgenesis.jobs.lease.redis.RedisLeaseProvider`

### 2.1 Spring Boot 启用 jobs（本地 runner）

```yaml
civgenesis:
  jobs:
    enabled: true
    thread-name: civgenesis-job-
```

说明：

- 这是一个“本地调度器 + JobRunner”骨架；如果你的 job 设置为 `LEADER_ONLY`，需要你提供 `LeaseProvider` Bean，否则会跳过执行并打印 warn。

## 3) 分布式异步任务（水平扩展）

建议优先用“队列/流”做任务分发（而不是分布式定时器）：

- Redis Streams / Kafka / RabbitMQ：天然支持 consumer group / 背压
- 任务幂等：用 `taskId` 或业务天然幂等键去重
- 玩家相关的异步任务：用 `playerId` 作为分区键，确保同玩家有序

本项目后续会把“任务队列 SPI”独立出来（与 IPC/背压模型对齐），当前先建议你使用成熟 MQ，并在 jobs 进程里消费后通过 gRPC/IPC 调用游戏服。

建议补充语义（框架层/接口层）：

- 任务消息携带 `taskId`（幂等键）与 `fencingToken`（来自 `LeaseProvider`）
- 消费端按 `taskId` 去重（或业务天然幂等），明确区分“可重试/不可重试”
- 通过 MQ 自带的 consumer group/backpressure 控制下游压力（而不是无限堆积到游戏服）
