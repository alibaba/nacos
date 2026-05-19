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

# Nacos SDK 规范

本文档定义 Nacos SDK 的通用设计规范。不同语言 SDK 可以采用符合语言习惯的
命名、异步模型和包结构，但公开能力边界应遵循本文档。Java 基准实现由
[Java SDK 实现规范](./sdk-java-impl-spec.md)定义。

## 1. SDK 分类

Nacos SDK 分为两类：

- **Client SDK**：面向微服务应用、Agent Framework 以及其他运行时工作负载，
  用于在应用正常运行过程中消费 Nacos 能力。
- **Maintainer SDK**：面向运维工具、控制台、网关、管理平台以及其他需要管理
  Nacos 的应用，避免这些应用自行拼装和调用 Nacos Admin HTTP API。

两类 SDK 可以复用模型对象、鉴权参数、重试规则和连接基础设施，但不应混淆
目标用户和权限边界。

## 2. Client SDK 范围

Client SDK 面向应用运行时访问，应只暴露运行时应用通常需要的能力：

- 读取已知配置项，并订阅这些配置的变更；
- 注册和注销当前应用实例；
- 查询和订阅应用已知依赖的服务；
- 注册、解析和订阅运行时 AI 资源，例如 MCP endpoint、A2A agent endpoint、
  Prompt、Skill 和 AgentSpec；
- 在语言 SDK 支持时，提供[分布式锁](../lock/lock-spec.md)等可选运行时原语；
- 按[客户端运行时规范](../client/README.md)管理自身生命周期、本地缓存、监听器和连接。

Client SDK 应避免暴露大范围管理能力，包括：

- 集群控制、服务端状态变更、日志级别变更、连接或流量重载；
- 列举全部命名空间、全部配置、全部服务或全部客户端；
- 查询历史、审计类元数据、dump 数据或订阅者列表；
- 批量删除、跨命名空间管理以及其他高影响操作；
- 新增主要面向运维人员而非运行时应用的写入 API。

部分历史 Client SDK interface 已经包含写入或大范围查询能力，例如配置发布、
配置删除或服务列表查询。这些 API 属于兼容面。新的 SDK 设计不应继续扩大这类
能力；管理类场景应转向 Maintainer SDK 或 Admin API。

## 3. Maintainer SDK 范围

Maintainer SDK 面向管理接入，可以暴露 Client SDK 有意不包含的能力：

- 命名空间、集群、服务端状态、readiness/liveness、日志级别等维护能力；
- 配置的列表、搜索、发布、删除、历史、beta、dump 和元数据管理；
- 服务、实例、集群元数据、订阅者、客户端、健康检查等注册中心维护能力；
- MCP、A2A、Prompt、Skill、AgentSpec 和 Pipeline 等 AI 资源管理；
- 对大规模管理数据提供分页和过滤能力。

Maintainer SDK 应被视为 Nacos Admin API 能力面的类型化门面。只对管理、UI、
网关或运维工具有意义的能力，应归入 Maintainer SDK，而不是 Client SDK。

## 4. 安全规则

SDK 能力设计必须遵循最小权限原则：

- Client SDK 凭据应限制在运行时资源范围内，不应要求大范围读写权限。
- Maintainer SDK 凭据权限更高，文档和示例必须与 Client SDK 凭据明确区分。
- 大范围读取 API 必须提供显式过滤和分页，不应静默执行无边界的全量集群读取。
- 跨命名空间操作属于 Maintainer SDK，并应要求显式传入 namespace。
- 当 API 可以列举或导出大量配置、服务、客户端或元数据时，SDK 文档应明确说明
  可能的数据泄露风险。

## 5. 传输和 API 对齐

SDK 契约是语义契约，而不是传输契约：

- Client SDK 可以使用 [gRPC](../grpc-api/api-spec.md)、
  [HTTP Open API](../http-api/api-spec.md)、本地缓存文件或多种传输组合，只要
  公开 SDK 行为保持稳定。
- Client SDK 的连接、server list、能力协商、本地缓存和 redo 行为由
  [客户端运行时规范](../client/client-runtime-spec.md)定义。
- Maintainer SDK 应与 Nacos Admin API 的语义和结果模型对齐，即使实现细节未来
  更换传输方式。
- SDK 模型对象应与 HTTP 和 gRPC 的语义对象对齐，避免同一个业务含义在不同
  传输中被重复定义。
- SDK 错误应将 Nacos 错误码和校验失败映射为符合语言习惯的异常或结果类型，
  同时保留服务端语义。

## 6. 多语言 SDK 对齐

Java 目前是定义共享 SDK 语义的基准实现。其他语言 SDK 应对齐相同的能力分类：

- 初始化、命名空间绑定、鉴权和生命周期关闭；
- Client SDK 的配置、注册中心、AI 以及可选分布式锁运行时能力；
- Maintainer SDK 的 Core、配置、注册中心和 AI 管理能力；
- namespace、group、dataId、service name、cluster、version、label 等一致的
  数据标识规则；
- 在语言运行时支持时，按照[客户端本地缓存与 Redo 规范](../client/client-local-cache-redo-spec.md)
  保持一致的监听、订阅、重试、超时和本地缓存行为。

语言 SDK 可以按照语言习惯暴露 future、promise、stream、coroutine、callback
或 context cancellation。这些差异应记录在语言实现规范中，而不是改变共享 SDK
范围。
