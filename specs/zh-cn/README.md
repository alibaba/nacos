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

# Nacos 规范说明

当前规范按照从顶层设计到外部接口，再到扩展机制和横切安全规则的顺序组织。

## 设计基础

- [Nacos 设计规范](design/nacos-design-spec.md)
- [资源模型规范](design/resource-model-spec.md)
- [兼容与废弃策略规范](design/compatibility-deprecation-spec.md)
- [基础能力规范](design/foundation-capabilities-spec.md)
- [服务端生命周期与环境配置规范](design/foundation-server-lifecycle-env-spec.md)
- [集群成员规范](design/foundation-cluster-membership-spec.md)
- [远程连接生命周期规范](design/foundation-remote-connection-spec.md)
- [请求过滤与运行时上下文规范](design/foundation-request-context-spec.md)
- [内部 RPC 与集群请求规范](design/foundation-internal-rpc-spec.md)
- [AP 一致性规范](design/foundation-ap-consistency-spec.md)
- [CP 一致性规范](design/foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](design/foundation-persistence-dump-spec.md)
- [任务执行规范](design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](design/foundation-event-dispatch-spec.md)
- [可观测钩子规范](design/foundation-observability-hooks-spec.md)
- [核心功能规范](design/core-capabilities-spec.md)

## 接口模型

- [HTTP API 规范](http-api/api-spec.md)
- [gRPC API 规范](grpc-api/api-spec.md)
- [SDK 规范](sdk/sdk-spec.md)
- [Java SDK 实现规范](sdk/sdk-java-impl-spec.md)
- [客户端运行时规范](client/README.md)

## 领域模型

- [Config 规范](config/README.md)
- [Naming 规范](naming/README.md)
- [AI Registry 规范](ai/README.md)
- [Core 运维规范](core/core-operations-spec.md)
- [Console 规范](console/console-spec.md)
- [分布式锁规范](lock/lock-spec.md)

## 扩展模型

- [集成规范](integration/README.md)
- [插件规范](plugin/README.md)

## 安全模型

- [鉴权与权限规范](auth/auth-permission-spec.md)
- [鉴权插件规范](auth/auth-plugin-spec.md)
- [RAM 鉴权插件规范](auth/ram-auth-plugin-spec.md)
- [OIDC 鉴权插件规范](auth/oidc-auth-plugin-spec.md)
- [可见性插件规范](auth/visibility-plugin-spec.md)
- [默认鉴权插件实现规范](auth/default-auth-plugin-spec.md)

## 测试模型

- [API 集成测试规范](testing/api-integration-test-spec.md)
- [Java SDK 集成测试规范](testing/java-sdk-integration-test-spec.md)

[AGENTS.md](../../AGENTS.md) 等 Agent 指南文件应只摘要这些规范以便本地执行。
当人、AI agent、模板或校验工具使用 API 指南时，规范仍然是规则来源。
