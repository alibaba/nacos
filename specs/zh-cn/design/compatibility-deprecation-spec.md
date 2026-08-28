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

# 兼容与废弃策略规范

本文定义 Nacos API、SDK、存储字段、插件、适配器和实验能力共享的兼容与废弃规则。本文补充
[Nacos 设计规范](nacos-design-spec.md)、[资源模型规范](resource-model-spec.md)和公开接口规范。

## 1. 范围

本文负责：

- 历史行为如何被分类为标准、仅兼容、已废弃、待移除、实验性或已移除；
- 已废弃或仅兼容行为的文档要求；
- API、SDK interface、存储字段和插件扩展点的迁移期望；
- ability-gated fallback 的移除规则。

本文不定义固定的发布日历。每个废弃项仍需要领域维护者评审，并通过 release note 沟通。

## 2. 兼容状态

| 状态 | 含义 | 新开发规则 |
| --- | --- | --- |
| 标准 | 由规范定义、面向新使用场景的当前行为。 | 新代码和文档应使用它。 |
| 仅兼容 | 为避免破坏已有用户而保留，但不是目标模型。 | 除 bug fix 和迁移支持外不应继续扩展。 |
| 已废弃 | 仍可用，但用户应迁移到替代方案。 | 需要记录替代方案和迁移说明。 |
| 待移除 | 已废弃且移除条件已知。 | 只保留必要兼容测试和迁移说明。 |
| 实验性 | 尚未承诺稳定行为。 | 可以在清晰说明后引入不兼容变更或移除。 |
| 已移除 | 当前版本不再支持。 | 仅在需要时记录迁移历史。 |

新规范必须识别非标准行为。某个行为存在于历史代码、数据库 schema、配置或文档中，并不足以让它
成为标准行为。

## 3. 文档规则

当实现仍支持已废弃或仅兼容行为时，不应从文档中静默删除。它应记录在兼容或废弃章节中，并包含：

- 当前状态；
- 替代 API、字段、SDK 方法或插件模型；
- 迁移说明；
- 兼容风险，包括鉴权、可见性或响应形态差异；
- 已知的移除条件。

面向用户的主流程应优先描述标准行为。兼容章节应明确处于次级位置。

## 4. API 与 SDK 规则

Open API 承担最强的长期兼容预期。Admin、Console、Maintainer SDK 和插件自带 API 可以演进更快，
但当用户可能依赖已文档化行为时，不兼容变更仍需要迁移说明。

已废弃端点应留在兼容章节，而不是作为主 API 展示。新 API 定义不得因为旧形态已经存在就直接复制
旧形态。

SDK 应在合理情况下为已废弃 public 方法保持二进制兼容，尤其是 Java 的 client、api 和 plugin
模块。新增 SDK 能力应引导用户使用标准 interface，不应继续扩大已废弃写入或大范围查询面。

## 5. Ability-Gated Fallback

能力协商是混合版本场景的首选机制。Fallback 只有在所属领域规范记录以下信息时才允许使用：

- 控制标准行为的 ability key 或条件；
- 精确 fallback 行为；
- fallback 是否改变响应形态、一致性、安全性或性能；
- fallback 可以被移除的条件。

Fallback 移除应等到最低支持的服务端/客户端矩阵不再需要该 fallback，或社区明确接受该不兼容。

## 6. 存储与 Schema 规则

仅为兼容保留的存储字段必须记录为兼容字段或待移除字段。除非后续领域规范明确提升其语义，否则
它们不得成为新的领域语义。

Schema 清理应平衡正确性和运维成本。冗余字段可以为了避免用户频繁调整 schema 而临时保留，但新的
规范、API、SDK 和文档不得基于该字段构建新行为。

## 7. 插件与适配器

插件 SPI 兼容性归属于拥有该 SPI 的插件规范。插件可以保留历史配置 key 或扩展名作为兼容别名，
但标准插件查找和启用方式应单独文档化。

暴露外部社区协议的 adapter 是兼容面，不是 Nacos 标准 API 模型。Adapter 可以有意遵循外部响应
形态或路由约定，但必须记录为 adapter 行为；当它引入未鉴权端点或额外端口时，应默认要求用户
主动开启。

## 8. 当前已知兼容项

以下是当前的兼容或废弃示例：

- v1/v2 HTTP API。它们已经从主服务端发行包中移除，并迁移到外部
  [nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter)；
- spec 出现前发布的 v3 兼容端点；
- AI Prompt legacy endpoints 和旧 Pipeline REST 风格端点；
- 默认关闭的旧 MCP Console 导入端点；迁移到统一 AI 资源导入端点后，计划在 Nacos
  3.4.0 移除；
- 历史 MCP `mcpId` 输入与输出；标准管理使用 Namespace 限定的 `mcpName`，
  这些 ID 字段继续作为兼容别名；
- 旧 A2A AgentCard Java、gRPC、Admin、Maintainer 和 Console facade；
- Naming API 定义的 service selector 字段和请求参数；
- Config 聚合配置字段及相关数据库列；
- 历史插件配置 key；
- 仍保留在历史路径下的 OIDC browser endpoints；
- 分布式锁在提升为稳定能力前属于实验性能力。

此清单不穷尽所有项目。每个领域规范仍负责精确领域行为和迁移细节。

在 Nacos 3.3 版本线中，Config 默认 namespace 在 legacy 空 tenant 与 `public` 之间的存储迁移，
以及 Config beta/tag 旧表向 `config_info_gray` 的迁移，视为已移除兼容行为。从 3.0 之前版本
升级时，如果使用过默认 namespace 或 beta 灰度发布，运维侧必须先完成相关数据迁移再升级。

## 9. 废弃 V3 API 门禁

以下少量待移除的废弃 v3 API 默认关闭：

| 废弃 API | 标准替代 API |
| --- | --- |
| `GET /v3/admin/ai/pipelines` | `GET /v3/admin/ai/pipelines/list` |
| `GET /v3/admin/ai/pipelines/{pipelineId}` | `GET /v3/admin/ai/pipelines/detail?pipelineId={pipelineId}` |
| `GET /v3/console/ai/pipelines` | `GET /v3/console/ai/pipelines/list` |
| `GET /v3/console/ai/pipelines/{pipelineId}` | `GET /v3/console/ai/pipelines/detail?pipelineId={pipelineId}` |
| `POST /v3/console/ai/mcp/import/validate` | `POST /v3/console/ai/import/validate` |
| `POST /v3/console/ai/mcp/import/execute` | `POST /v3/console/ai/import/execute` |

关闭时，端点返回 HTTP `410 Gone` 和 `API_DEPRECATED` 结果码，并说明对应的标准替代 API。
运维人员可以在迁移窗口内通过以下配置临时重新开启全部这些端点：

```properties
nacos.core.api.compatibility.enabled=true
```

该开关有意设计为共享开关，只作用于显式接入 v3 兼容门禁的 API，不替代
`nacos-api-legacy-adapter` 按 API 受众维护的兼容开关。重新开启端点后，原有认证和鉴权仍然生效。

旧的 `nacos.ai.resource.import.legacy-mcp-api-enabled` 参数不再识别。旧 MCP 直接 URL 导入还必须
额外设置 `nacos.ai.resource.import.allow-user-url=true`；运维侧应优先使用受管 source 配置。

## 10. Legacy HTTP API Adapter

从 Nacos 3.2.0 版本线开始，legacy v1 和 v2 HTTP API 不再属于默认 Nacos server 发行包。它们是由
[nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter)提供的独立兼容面。

规则：

- v3 HTTP API 和当前 SDK 是标准迁移目标。
- legacy adapter 是临时迁移辅助，不是重新承诺的 API 契约。
- adapter 必须显式安装，例如将 jar 放入 Nacos `plugins` 目录，或在 embedded/custom application
  中添加依赖。
- adapter version 必须与目标 Nacos server version 匹配。
- adapter 不保证在未来 Nacos 版本中继续支持，也不应用于定义新的 v1/v2 行为。

领域规范只应在迁移上下文中提及 legacy v1/v2 行为，或在当前兼容路径依赖它时进行说明。

## 11. 旧 A2A Agent Facade

标准 Agent 模型使用 `type=agent`、协议无关 Version 和 RAD 发现。历史 A2A AgentCard
表面仅用于兼容，并按照 [A2A Agent 规范](../ai/a2a-agent-spec.md)在服务端边界适配。

不同受众采用不同兼容窗口：

- Java `A2aService` 和旧 A2A gRPC Payload 当前不设删除版本；
- Admin `/v3/admin/ai/a2a` 和 `A2aMaintainerService` 兼容到 4.0.x 窗口；
- Console `/v3/console/ai/a2a` 兼容到 3.4.x 窗口，内置 UI 完成迁移后可以移除。

不得只向这些 facade 增加新能力。新增开发以 Agent Management 和 RAD 契约为目标。
历史数据与混合 Server 滚动升级使用独立迁移方案，本身不延长 API 兼容窗口。

## 12. 旧 MCP 标识符

标准 MCP 管理使用 `namespaceId + type=mcp + mcpName` 定位 Resource。UUID 形态的
`mcpId` 作为公开资源标识符已废弃，但继续作为内部物理存储别名和旧 Wire 字段。

不同字段具有不同兼容状态：

| 接口面 | 状态 | 规则 |
| --- | --- | --- |
| 新 Admin、Console 和 Maintainer Lifecycle API | 标准 | 接受 `mcpName` 和可选 Version，不增加 `mcpId`。 |
| 现有 Admin、Console 和 Maintainer ID-only 输入 | 已废弃兼容 | 在请求 Namespace 中唯一匹配 `AiResource.ext.mcpId`，随后按标准名称鉴权和操作。 |
| 现有 Model、Event、Create/Release Response 和嵌套 `McpServerBasicInfo.id` 字段 | Active Compatibility | 物理 Config 坐标和当前消费者仍依赖时，保持 Wire Shape 和原值。 |
| MCP gRPC Request 顶层 `AbstractMcpRequest.mcpId` | Ignored 且 Deprecated | 保留 Field Number，不实现 ID 查询，并保持各 Handler 当前 Name 必填规则。 |

旧 ID 查询不得使用最终一致的 Search、历史 Manifest/Config 身份查询或 MCP 专用内存 Index。
该已废弃路径不新增表或字段。移除它需要为 Config 坐标、直读消费者、SDK Model 和 Wire Response
制定独立迁移；首期生命周期托管不定义移除版本。精确行为由
[MCP Server 规范](../ai/mcp-server-spec.md)定义。

### 12.1 旧 MCP Maintainer 方法

`McpMaintainerService` 的旧 Detail 和 Direct-online Create/Update 方法自 Nacos 3.3.0
起废弃，计划在 Nacos 4.0.0 删除。在兼容窗口内，其运行时行为继续保持兼容。调用方应按下表迁移：

| 已废弃操作 | 标准替代方式 |
| --- | --- |
| Serving 投影详情 | 先通过 `listLifecycleVersions` 选择精确 Version，再使用 `getLifecycleVersion`。 |
| Local、Remote 或通用 Direct-online Create | 使用 `createLifecycleDraft`，随后调用 `submitLifecycleVersion`；启用审核时，在审核通过后显式调用 `publishLifecycleVersion`。 |
| Direct-online Update | 新 Version 使用 `createLifecycleDraft`，已有 Draft 使用 `updateLifecycleDraft`，随后 Submit，并在需要时 Publish。 |

旧的跨 Resource List/Search，以及 Published Version 或完整 Resource Delete 方法不在本次废弃
范围内，因为 Typed Lifecycle 接口尚未提供语义等价的替代方法。它们必须在补齐独立 API 设计并完成
废弃评审后，才能设定删除版本。

## 13. 相关规范

- [HTTP API 规范](../http-api/api-spec.md)
- [V3 API 范围](../http-api/v3-api-surface.md)
- [SDK 规范](../sdk/sdk-spec.md)
- [客户端能力协商规范](../client/client-ability-negotiation-spec.md)
- [资源模型规范](resource-model-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [集成与适配器规范](../integration/integration-adapter-spec.md)
- [插件规范](../plugin/README.md)
- [Agent 管理规范](../ai/agent-management-spec.md)
- [RAD 协议规范](../ai/rad-protocol-spec.md)
- [MCP Server 规范](../ai/mcp-server-spec.md)
