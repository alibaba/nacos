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

# Naming 资源规范

本文定义 Naming 资源身份、字段、校验和元数据。

## 1. Service 身份

Naming service 由以下字段唯一标识：

```text
namespaceId -> groupName -> serviceName
```

| 字段 | 含义 | 说明 |
| --- | --- | --- |
| `namespaceId` | 服务所属 namespace。 | 当接口支持默认值处理时，空值或缺省值会被处理为默认 namespace id。 |
| `groupName` | namespace 内的业务分组。 | 新公开规范和 v3 接口使用 `groupName`；兼容 key 中仍可能使用 `group@@serviceName`。 |
| `serviceName` | 服务资源名。 | `serviceName` 是 Naming service 的 `resourceName`。 |

身份字段是稳定的。修改 `namespaceId`、`groupName` 或 `serviceName` 表示新的 service 资源，不是
普通元数据更新。

## 2. Service 类型

Service 类型是 service 资源自身的属性：

| 服务类型 | `ephemeral` 值 | 资源含义 |
| --- | --- | --- |
| 临时服务 | `true` | 非持久化服务，实例是由存活 client 持有的运行时发布信息。 |
| 持久服务 | `false` | 持久化服务，实例通过持久路径管理。 |

一个 service 内不得混用临时和持久实例语义。注册输入、存储路由、健康检查、清理和一致性逻辑都必须
由 service 类型派生。

## 3. Service 字段

| 字段 | 含义 | 是否身份字段 |
| --- | --- | --- |
| `protectThreshold` | 健康实例比例过低时使用的保护阈值。 | 否 |
| `selector` | 遗留 API 定义的 service selector。新行为不应依赖此字段，参见 [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)。 | 否 |
| `metadata` / `extendData` | 服务元数据 key-value map。 | 否 |
| `ephemeral` | 服务类型。`true` 表示临时服务，`false` 表示持久服务。 | 不参与 equality，但会改变运行时语义。 |
| `clusters` | 以 cluster name 为 key 的 cluster 元数据 map。 | 否 |

v2 模型中 service equality 使用 `namespace`、`group` 和 `name`。但 service 创建会记录该 service 是
临时还是持久，后续实例注册必须与 service 类型匹配。

## 4. Cluster 资源

Cluster 是 service 下的从属资源：

```text
namespaceId -> groupName -> serviceName -> clusterName
```

| 字段 | 含义 |
| --- | --- |
| `clusterName` | Cluster 名称。实例输入省略时默认为 `DEFAULT`。 |
| `healthChecker` | 健康检查器定义，如 TCP、HTTP、MySQL 或 NONE。 |
| `healthyCheckType` | 序列化后的健康检查类型。 |
| `healthyCheckPort` | 不使用实例端口时，主动健康检查使用的端口。 |
| `useInstancePortForCheck` | 主动健康检查是否使用每个实例自身端口。 |
| `metadata` / `extendData` | Cluster 元数据 key-value map。 |

Cluster 元数据不会创建顶层 service。它始终属于所属 service。

## 5. Instance 资源

Instance 从属于 service 和 cluster：

```text
namespaceId -> groupName -> serviceName -> clusterName -> ip:port
```

| 字段 | 含义 |
| --- | --- |
| `ip` | 实例地址。必填。 |
| `port` | 实例端口。必填，范围 `0..65535`。 |
| `clusterName` | 实例所属 cluster。默认为 `DEFAULT`。 |
| `weight` | 实例流量权重。默认为 `1.0`。 |
| `healthy` | 运行时健康状态。默认为 `true`，但可被心跳或主动健康检查改变。 |
| `enabled` | 实例是否可被运行时消费者发现。默认为 `true`。 |
| `ephemeral` | 兼容和路由字段，必须与所属 service 类型匹配。默认 `true`，除非服务端默认配置改变 HTTP form 默认值。 |
| `metadata` | 实例元数据 key-value map。 |
| `instanceId` | 可选的生成或用户指定运行时标识。 |

公开身份应使用 service scope 加 `clusterName`、`ip` 和 `port`。`instanceId` 是运行时标识，不应在
新 API 中替代规范化身份字段。

## 6. Client、Publisher 和 Subscriber

Naming 维护运行时 client 视图：

| 运行时对象 | 含义 |
| --- | --- |
| Client | 绑定到一个 gRPC 连接或一个 IP-port 兼容身份的服务端运行时状态对象。 |
| Publisher | 为某个 service 注册实例的 client。 |
| Subscriber | 订阅 service 变化的 client。 |

Client id 是实现相关状态。gRPC Client 使用连接 id；HTTP/IP-port 兼容 Client 使用从 IP-port 派生的
client id。除 maintainer 诊断外，公开运行时 API 不应要求用户构造内部 client id。Client state 和
索引模型见 [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)。

## 7. 校验规则

- service 操作必须包含非空 `serviceName` 和 `groupName`。
- instance 操作必须包含非空 `ip`。
- `port` 必须在 `0..65535` 范围内。
- `clusterName` 如果提供，必须符合 Naming cluster-name 字符规则。
- 实例注册必须与所属 service 类型匹配。持久实例不得注册到临时服务，临时实例也不得注册到持久服务。
- 心跳超时和删除超时必须大于等于心跳间隔。
- 批量注册是面向临时服务的能力。当前 Java SDK 会拒绝持久实例，服务端 gRPC handler 也通过临时
  operation service 处理批量注册。

## 8. 内部 Key

实现代码可以使用：

- grouped service name：`group@@serviceName`；
- service key：`namespace@@group@@serviceName`；
- service info key：`group@@serviceName@@clusters`；
- metadata id：由 instance `ip`、`port` 和 `clusterName` 生成。

这些 key 是实现和兼容 key。新 API 和 SDK 契约应优先使用显式 `namespaceId`、`groupName`、
`serviceName`、`clusterName`、`ip` 和 `port` 字段。

## 9. 相关规范

- [资源模型规范](../design/resource-model-spec.md)
- [Naming 实例生命周期规范](naming-instance-lifecycle-spec.md)
- [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)
- [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)
