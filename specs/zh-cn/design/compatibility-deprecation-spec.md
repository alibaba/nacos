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
- Naming API 定义的 service selector 字段和请求参数；
- Config 聚合配置字段及相关数据库列；
- 历史插件配置 key；
- 仍保留在历史路径下的 OIDC browser endpoints；
- 分布式锁在提升为稳定能力前属于实验性能力。

此清单不穷尽所有项目。每个领域规范仍负责精确领域行为和迁移细节。

在 Nacos 3.3 版本线中，Config 默认 namespace 在 legacy 空 tenant 与 `public` 之间的存储迁移，
以及 Config beta/tag 旧表向 `config_info_gray` 的迁移，视为已移除兼容行为。从 3.0 之前版本
升级时，如果使用过默认 namespace 或 beta 灰度发布，运维侧必须先完成相关数据迁移再升级。

## 9. Legacy HTTP API Adapter

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

## 10. 相关规范

- [HTTP API 规范](../http-api/api-spec.md)
- [V3 API 范围](../http-api/v3-api-surface.md)
- [SDK 规范](../sdk/sdk-spec.md)
- [客户端能力协商规范](../client/client-ability-negotiation-spec.md)
- [资源模型规范](resource-model-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [集成与适配器规范](../integration/integration-adapter-spec.md)
- [插件规范](../plugin/README.md)
