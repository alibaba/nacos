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

# Naming 运维规范

本文定义 Naming maintainer 和运维行为。

## 1. 运维范围

Naming 运维 API 是管理接口面。它可以检查或修改 service metadata、instance metadata、client
state、subscriber、switch、metrics 和 log level。除非某个操作明确属于应用运行时发现，否则不得
通过运行时 Client SDK 暴露。

## 2. Service 与 Instance 管理

Admin 和 Maintainer SDK 接口可以：

- 创建、更新、查询、列表和删除 service；
- 注册、注销、更新、局部更新和列表查询 instance；
- 批量更新或删除 instance metadata；
- 更新 cluster health checker metadata；
- 当 checker type 为 `NONE` 时，更新持久实例健康状态。

运行时实例注册仍可保留在 Client SDK 中，但大范围管理和诊断操作属于 Admin API、Console API 或
Maintainer SDK。

## 3. Client 与 Subscriber 诊断

Naming 可以暴露：

- client list 和 client detail；
- 指定 client 发布的 services；
- 指定 client 订阅的 services；
- 发布某个 service 的 clients；
- 订阅某个 service 的 clients；
- 某个 IP-port client 的负责节点；
- 某个 service 的 subscribers。

Subscriber aggregation 是跨 [server member](../design/foundation-cluster-membership-spec.md)
的诊断查询模式，不是 service 资源模型，也不得影响运行时订阅语义。

## 4. 开关、指标与日志

Naming 运维 API 可以暴露模块开关、指标和日志级别更新。开关更新必须保持管理属性，因为它可能改变
健康检查、心跳、清理、保护或推送等运行时行为。

指标可以包含 service 数、instance 数、subscription 数、client 数、push queue 和健康状态摘要。
指标是观测数据，不定义资源身份。共享指标、trace、日志和诊断规则由
[可观测钩子规范](../design/foundation-observability-hooks-spec.md)定义。

## 5. 清理诊断

Naming 清理行为包括空 service 清理和过期元数据清理。运维文档应将清理描述为生命周期维护，而不是
面向用户的 service 删除语义。显式删除仍遵循
[Naming 实例生命周期规范](naming-instance-lifecycle-spec.md)中的 service 生命周期规则。

## 6. 鉴权与错误

Naming 运维 API 必须遵循：

- [HTTP API 规范](../http-api/api-spec.md)；
- [响应与错误规范](../http-api/response-error-spec.md)；
- [鉴权与权限规范](../auth/auth-permission-spec.md)。

Naming 仍存在模块级 exception handler。新的 v3 运维 API 应收敛到通用 Nacos API 错误模型。

## 7. 相关规范

- [Naming 规范](naming-spec.md)
- [Naming 资源规范](naming-resource-spec.md)
- [Naming 健康检查与保护规范](naming-health-protection-spec.md)
- [可观测钩子规范](../design/foundation-observability-hooks-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
