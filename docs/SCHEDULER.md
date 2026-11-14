# 时间轮定时系统（草案）

目标：用时间轮实现高效定时，同时保持“少锁化”的分片线程模型。

## 核心原则

时间轮线程只负责“到期触发”；**业务回调必须回投到 `ShardExecutor`** 执行。

否则会出现：

- 定时回调跨线程改玩家/房间状态 -> 加锁/竞态/死锁风险
- 定时与消息处理乱序 -> 状态不一致

## API 建议

- `schedule(shardKey, delay, task)` — 已实现（`ShardScheduler.java:6`，`Delay` 为 `java.time.Duration`，内部经 `HashedWheelTimer` 到期触发）
- `cancel(handle)` — 已实现：`schedule(...)` 返回 `ScheduledTask`，其 `cancel()` 即取消（`ScheduledTask.java:3`）
- `scheduleAt(shardKey, deadlineMonoNanos, task)` — 建议接口，**尚未实现**：当前仅 `schedule(shardKey, Duration, task)` 一个重载（`ShardScheduler.java:6`）

其中 `shardKey` 默认：

- 已登录：`playerId`
- 未登录：`connectionId`（运行时实际字段名，`RequestContext.java:46`；框架内无 `channelId` 概念）

## Tick（房间/战斗/行军）最佳实践

不建议“全局固定频率扫所有对象”，更推荐“对象自调度”：

- 每个房间/行军/建筑实例维护 `nextDeadline`
- 到期后执行一次逻辑，并计算下一次 `nextDeadline` 再 schedule

收益：

- 空闲对象不占 tick 成本
- 大规模对象时更平滑（不会在同一帧集中爆发）

## 时间基准

内部 deadline 建议使用 `System.nanoTime()`（单调不回拨）。

