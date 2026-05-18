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

# A2A Agent 规范

本文档定义 A2A AgentCard 资源在 AI Registry 中的领域契约。

## 1. 身份

A2A Agent 标准身份为：

```text
namespaceId -> a2a -> agentName
```

`agentName` 是公开资源名。当前存储可能会先编码 agent name 再写入 Config 数据。
编码是实现细节，不应改变公开身份。

## 2. 领域模型

A2A Agent 资源包含 AgentCard 元数据和版本化 AgentCard 详情，可以包含：

- agent name、description、provider、capabilities、skills、authentication 和协议字段；
- registration type；
- version list 和 latest published version；
- 通过 agent interface 表达的 service-style endpoints。

当前 AgentCard 模型以 Google A2A 1.0.0 协议字段为准。Nacos 为兼容已有客户端，
仍保留若干 0.x 兼容字段，例如根级 `url`、`protocolVersion`、
`preferredTransport`、`additionalInterfaces` 和
`supportsAuthenticatedExtendedCard`。这些字段只作为兼容输入存在；当 1.0.0
模型成为唯一支持契约后，legacy 字段可以被统一删除。

未指定版本时，运行时查询解析 latest 版本。

## 3. Endpoint 模型

A2A endpoint 可以由运行时客户端注册，并通过 A2A endpoint group 下的 Naming
service 表达。

Endpoint 解析规则：

- endpoint 绑定到具体 agent 版本；
- endpoint metadata 应在可用时包含 transport、protocol binding、protocol version、
  path、query、TLS support 和 tenant 数据；
- 当存在多个兼容 endpoint 时，当前实现随机选择一个；这不是稳定策略契约，后续应由
  endpoint-selection 规范细化。

Naming 是 endpoint 基础设施。A2A Agent 仍然是 AI Registry 资源。

## 4. API 和 SDK 行为

- Admin API 可以注册、查询、更新、删除、列表和操作 A2A AgentCard 版本。
- Client API 和 SDK 可以查询 AgentCard、在支持时发布 AgentCard、注册/注销
  endpoint、批量替换当前客户端拥有的 endpoint，并订阅 AgentCard 变更。
- gRPC payload 包含 AgentCard 查询/发布和 endpoint 注册请求，详见
  [gRPC API 规范](../grpc-api/api-spec.md)。

## 5. 当前兼容存储

当前 A2A 实现通过 Config 形态记录保存 AgentCard 元数据和版本，并通过 Naming
service 表达 endpoint。这是兼容存储。标准模型应为
`ai_resource + ai_resource_version`。

## 6. 待迁移问题

- 将 AgentCard 元数据和版本行迁移到标准 AI 资源模型。
- 定义从编码 Config dataId 到 resourceName 身份的迁移。
- 定义 endpoint 所有权、连接清理和确定性 endpoint 选择。
- 让 latest-version 行为与共享 label 模型对齐。
- 在 A2A 之上引入协议无关抽象，例如 `RemoteAgent` 或 `AgentService`。Nacos
  是统一注册中心，不应将 AI Registry 模型绑定到某一个特定 agent 协议，除非该协议
  已经成为明确的社区标准。A2A 应作为更高层 remote-agent 资源模型下的一种协议绑定。

## 7. 演进说明

A2A 协议版本、AgentCard 字段、安全方案和 endpoint 表达都可能快速演进。当前基线为
A2A 1.0.0，0.x 兼容字段只为避免破坏已有客户端而保留。当上游 A2A 模型变化，或
Nacos 引入协议无关的 remote-agent 抽象时，本规范可能需要不兼容调整；调整必须定义
迁移和兼容行为。
