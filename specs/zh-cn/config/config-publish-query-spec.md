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

# Config 发布与查询规范

本文定义 Config 发布、删除、查询、列表、导入、导出和克隆语义。

## 1. 发布

发布正式 Config 会写入由 `namespaceId + group + dataId` 标识的资源。

发布行为：

- 校验身份、内容、tag、元数据、namespace 和容量限制；
- 将空 namespace 归一化为默认 namespace id；
- 将非法配置 `type` 归一化为 `text`；
- 当 dataId 匹配加密插件且请求未提供 `encryptedDataKey` 时，对内容进行加密；
- 根据请求字段写入正式配置或灰度配置版本；
- 记录持久化 trace 数据；
- 写入成功后发布 Config 变更事件。

Config 变更事件遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。

CAS 发布必须比较请求携带的 `casMd5` 和已存储 md5。md5 不匹配表示资源冲突，不能覆盖已存储配置。

## 2. 删除

删除正式 Config 会移除由 `namespaceId + group + dataId` 标识的资源，记录删除 trace，并发布
Config 变更事件。删除灰度配置只会移除指定灰度版本。

兼容接口允许删除不存在的配置表现为成功，但新的管理流程不应把这种结果展示为该资源曾经存在。

## 3. 运行时查询链

运行时查询使用 Config 查询链。默认链路为：

```text
entry/cache lock
  -> content type wrapper
  -> gray rule match
  -> special tag not-found handling
  -> formal config fallback
```

有效语义顺序为：

1. 归一化 namespace，并按 `dataId + group + namespace` 查找缓存项。
2. 如果缓存项不存在，返回 config-not-found。
3. 如果缓存项正在 dump 或被写锁修改，返回 query conflict，由客户端重试。
4. 用请求标签匹配灰度规则，例如客户端 IP 和 tag。
5. 如果命中灰度规则，返回命中的灰度内容、md5、`encryptedDataKey`、最后修改时间、配置类型和灰度元数据。
6. 如果请求显式指定了特殊 tag 但没有命中 tag 灰度，返回 tag-specific not-found。
7. 否则返回正式配置内容、md5、`encryptedDataKey`、最后修改时间和配置类型。
8. 根据配置类型解析响应 content type，默认使用 text。

运行时查询必须使用缓存和 dump 内容，而不是大范围持久化查询。共享 dump 与 cache 边界由
[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)定义。

## 4. 管理查询

Admin 查询面向管理用户返回配置详情。当存储的配置为加密配置时，Admin 查询会先解密内容，再返回
详情对象。

运行时查询响应可能包含加密后的内容和 `encryptedDataKey`。客户端侧解密属于
[配置加密插件规范](../plugin/config-encryption-plugin-spec.md)描述的客户端配置 filter 和加密插件链路。

## 5. 列表与搜索

Config 列表和搜索是管理操作。它们根据 API 支持的范围，对 `dataId`、`groupName`、
`namespaceId`、`appName`、`configTags`、`type` 和内容详情进行精确或模糊检索。

搜索必须受队列、线程数、容量和等待时间控制，避免大范围管理查询影响运行时查询和监听链路。
Executor 和队列边界由[任务执行规范](../design/foundation-task-execution-spec.md)定义。

## 6. 导入、导出与克隆

导入、导出和克隆是管理操作：

- 导出打包配置内容和元数据；
- 导入要求元数据和内容互相匹配，应用请求指定的同名配置策略，并为成功写入发布变更事件；
- 克隆将选中的配置复制到目标 namespace，并可选择目标 group 或目标 dataId；
- 导入和导出必须保留配置类型、描述、应用名、group、dataId、内容和加密语义。

## 7. 接口规则

| 接口面 | 规则 |
| --- | --- |
| HTTP Open API | 通过 `/v3/client/cs/config` 查询单个已知配置，不提供 HTTP 监听或大范围管理行为。 |
| HTTP Admin API | CRUD、元数据、列表/搜索、导入、导出、克隆、beta 查询/删除、监听、容量、指标和运维使用 `/v3/admin/cs/*`。 |
| gRPC API | 查询、兼容发布、兼容删除、监听、模糊订阅和推送消息必须保持同一套 Config 身份和 md5 语义。 |
| Client SDK | 应用应优先使用运行时查询和监听 API；大范围管理 API 属于 Maintainer SDK。 |
