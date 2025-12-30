# CivGenesis

[![CI](https://github.com/cuihairu/civgenesis/actions/workflows/ci.yml/badge.svg)](https://github.com/cuihairu/civgenesis/actions/workflows/ci.yml)
[![Docs](https://github.com/cuihairu/civgenesis/actions/workflows/docs.yml/badge.svg)](https://github.com/cuihairu/civgenesis/actions/workflows/docs.yml)
[![codecov](https://codecov.io/gh/cuihairu/civgenesis/branch/main/graph/badge.svg)](https://codecov.io/gh/cuihairu/civgenesis)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.2.1-02303A?logo=gradle&logoColor=white)
[![License](https://img.shields.io/github/license/cuihairu/civgenesis)](LICENSE)

Java 21 / Spring Boot / Netty 的游戏服务器 SDK（偏“框架/运行时”，业务逻辑由接入方提供）。

许可证：Apache-2.0（见 `LICENSE`）。

文档入口：

- 本地：`docs/README.md`
- GitHub Pages（CI 自动发布）：`https://cuihairu.github.io/civgenesis/`

客户端 SDK（协议侧，非业务逻辑）：

- Unity（C#）：`clients/unity/`
- TypeScript（Cocos/LayaAir）：`clients/ts/`

可观测性（Prometheus + OpenTelemetry）：

- 文档：`docs/OBSERVABILITY.md`

后台任务（本地 / 分布式 jobs 进程）：

- 文档：`docs/JOBS.md`

## 构建

```bash
./gradlew build
```

## 文档站点（VuePress）

本仓库使用 VuePress 生成静态文档，并通过 GitHub Pages 发布。

本地预览：

```bash
npm ci
npm run docs:dev
```

构建静态站点：

```bash
npm ci
npm run docs:build
```
