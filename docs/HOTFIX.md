# 热更新（兼容 Arthas redefine）草案

本项目不内置 admin；热更新由你现有的管理项目触发。本 SDK 只提供“兼容约定/封装”。

## 1. 目标能力

- 上传 `.class` 到目标进程本地目录（例如 `hotfix/class/`）
- 通过 Arthas HTTP API 执行：
  - `redefine hotfix/class/xxx.class`
  - `jad com.xxx.YourClass`（查看当前运行字节码对应的反编译）

## 2. 目录约定

- `hotfix/class/`：待 redefine 的 class 文件（相对 `user.dir`）

## 3. 限制（必须写进开源文档）

JVM redefine 的限制：

- 不能新增/删除方法、字段
- 只能修改方法体实现

因此它适合“线上紧急修复”，不适合作为常规迭代手段。

## 4. 安全与审计（建议强制）

热更新属于高危操作，建议至少具备：

- 强鉴权（签名/短期 token）
- IP 白名单
- 操作审计（谁/何时/对哪个实例/更新了哪些类）
- 灰度策略（先单实例再扩）

## 5. 推荐的 SDK 封装（无 admin）

提供一个小型客户端封装（供你的管理项目使用）：

- `ArthasHotfixClient.redefine(target, classFiles...)`
- `ArthasHotfixClient.jad(target, className)`

其中 `target` 至少包含：

- `ip`
- `arthasHttpPort`
- （可选）鉴权信息

