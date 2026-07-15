<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Nacos Specs

This directory contains design-level specifications for Nacos behavior. Specs are
the source for implementation, tests, and user-facing documentation alignment.

本目录包含 Nacos 行为的设计级规范说明。规范用于对齐实现、测试和面向用户的文档。

Spec hierarchy:

```text
Nacos design
  -> Resource model
  -> Compatibility and deprecation
  -> Foundation capabilities
     -> Server lifecycle and environment configuration
     -> Cluster membership
     -> Remote connection lifecycle
     -> Request filtering and runtime context
     -> Internal RPC and cluster requests
     -> AP consistency
     -> CP consistency
     -> Persistence and dump
     -> Task execution
     -> Event dispatch and NotifyCenter
     -> Observability hooks
  -> Core capabilities and domain capabilities
     -> Config / Naming / AI Registry / Core Operations / Console / Distributed Lock
  -> HTTP / gRPC / SDK interface specs
     -> Client runtime
  -> Integration / Adapter model
  -> Extension model
  -> Security model
  -> Testing model
```

规范层次：

```text
Nacos 顶层设计
  -> 资源模型
  -> 兼容与废弃策略
  -> 基础能力
     -> 服务端生命周期与环境配置
     -> 集群成员
     -> 远程连接生命周期
     -> 请求过滤与运行时上下文
     -> 内部 RPC 与集群请求
     -> AP 一致性
     -> CP 一致性
     -> 持久化与 dump
     -> 任务执行
     -> 事件分发与 NotifyCenter
     -> 可观测钩子
  -> 核心功能与领域功能
     -> Config / Naming / AI Registry / Core 运维 / Console / 分布式锁
  -> HTTP / gRPC / SDK 接口规范
     -> 客户端运行时
  -> 集成 / 适配器模型
  -> 扩展模型
  -> 安全模型
  -> 测试模型
```

Available languages:

- [English](en/README.md)
- [简体中文](zh-cn/README.md)
