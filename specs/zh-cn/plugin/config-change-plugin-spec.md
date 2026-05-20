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

# 配置变更插件规范

## 范围

配置变更插件用于在配置变更操作前后运行扩展逻辑。典型用途包括审计记录、格式校验、白名单
校验和 webhook 通知。

这是有序链式插件。同一个 pointcut 可以匹配多个插件，并按
`ConfigChangePluginService.getOrder()` 升序执行。
通用生命周期和状态规则由 [Nacos 插件化规范](plugin-spec.md) 定义。

该设计采用类似 AOP 的模型：配置变更操作是 pointcut，插件被织入到 pointcut 之前或之后。
该插件用于配置变更治理，不得重新定义配置身份或持久化语义。

## 概念

| 概念 | 含义 |
|------|------|
| Pointcut | 按操作和来源分类的配置变更点。 |
| Execute type | 插件在 pointcut 之前还是之后执行。 |
| Before plugin | 可以校验、拒绝或改写变更参数。 |
| After plugin | 可以观察已提交变更并执行尽力而为的副作用。 |
| Plugin properties | 通过 `ConfigChangeRequest` 传给插件的专属配置。 |

## SPI

插件实现 `ConfigChangePluginService`。

| 方法 | 要求 |
|------|------|
| `getServiceType()` | 稳定插件名称，用于插件管理和配置。 |
| `getOrder()` | 链式执行顺序，值越小越早执行。 |
| `executeType()` | `EXECUTE_BEFORE_TYPE` 或 `EXECUTE_AFTER_TYPE`。 |
| `pointcutMethodNames()` | 该插件处理的 pointcut。 |
| `execute(request, response)` | 插件逻辑。 |

该插件以 `config-change` 类型暴露给核心插件管理器。

## Pointcut

当前 pointcut 如下：

| Pointcut | 含义 |
|----------|------|
| `PUBLISH_BY_HTTP` | 通过 [HTTP API](../http-api/api-spec.md) 创建或更新配置。 |
| `PUBLISH_BY_RPC` | 通过 [gRPC API](../grpc-api/api-spec.md) 创建或更新配置。 |
| `REMOVE_BY_HTTP` | 通过 HTTP 删除单个配置。 |
| `REMOVE_BY_RPC` | 通过 gRPC 删除单个配置。 |
| `IMPORT_BY_HTTP` | 通过 HTTP 或控制台导入配置文件。 |
| `REMOVE_BATCH_HTTP` | 通过 HTTP 批量删除配置。 |

Pointcut 名称属于插件契约。新的配置变更路径必须复用相同语义 pointcut，或在第三方插件
依赖之前新增并记录 pointcut。

## Request 与 Response

`ConfigChangeRequest` 包含：

| 字段 | 含义 |
|------|------|
| `requestType` | 当前 pointcut。 |
| `requestArgs` | 操作参数，例如 namespace、group、dataId、content 或来源相关值。 |

`ConfigChangeResponse` 包含：

| 字段 | 含义 |
|------|------|
| `responseType` | pointcut 响应类型。 |
| `success` | before 插件设置为 false 时，变更会被拦截。 |
| `retVal` | 保留返回值。 |
| `msg` | 发生拦截时返回给调用方的失败信息。 |
| `args` | before 插件提供的替换参数。 |

Nacos 还会通过 request arguments 传递 `ConfigChangeConstants.ORIGINAL_ARGS` 和
`ConfigChangeConstants.PLUGIN_PROPERTIES`。

## 执行规则

前置插件可以通过 `ConfigChangeResponse.args` 检查或改写变更参数。如果前置插件设置
`success=false`，配置变更必须被拦截，并向调用方返回失败信息。

后置插件只在所属变更已经执行后运行，适合用于审计、通知或尽力而为的副作用。后置插件失败
不得破坏已提交的配置状态。

执行顺序在过滤禁用插件后计算。前置插件在变更前同步运行。后置插件通过 config executor
调度，应被视为异步执行。该调度遵循[任务执行规范](../design/foundation-task-execution-spec.md)。

前置插件替换参数时必须保持参数顺序和类型。后置插件不得假设自己的副作用可以回滚已经提交的
配置变更。

## 配置

插件自身属性使用前缀：

```properties
nacos.core.config.plugin.{pluginName}.*
```

插件包文档中的传统启用配置为：

```properties
nacos.core.config.plugin.{pluginName}.enabled=true
```

通过 `ConfigChangePluginManager.findPluginServiceImpl` 直接查找插件时，会遵守
`config-change:{pluginName}` 的统一插件状态。Pointcut 执行路径同时使用传统
`enabled` 属性，后续执行链路更新时应收敛到统一状态模型。

插件自定义属性使用小写 service type 读取：

```text
nacos.core.config.plugin.{serviceType}.{propertyKey}
```

## 参考实现

Nacos 服务端仓库定义 SPI 和 config aspect。参考实现可以位于外部插件仓库。官方示例曾包括：

| 示例 | 期望行为 |
|------|----------|
| `webhook` | 配置变更后发送通知。 |
| `whitelist` | 导入前校验配置名或后缀白名单。 |
| `fileformatcheck` | 导入前校验文件类型或内容。 |

这些示例只有在插件 JAR 加入服务端 classpath 并被启用后，才属于服务端运行时的一部分。
