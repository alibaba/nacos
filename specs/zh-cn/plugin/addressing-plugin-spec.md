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

# 寻址扩展规范

## 范围

寻址决定 Nacos 运行时组件如何发现 Nacos Server 地址。它同时包含服务端集群成员发现面和
Java 客户端 server list 发现面。

公开文档描述过寻址扩展点。当前服务端代码主要使用内置 `MemberLookup` 实现，并未将寻址
注册到统一的 `PluginType` 注册表。当前 Java 客户端代码通过 SPI 加载
`ServerListProvider` 实现。

本文档记录当前成员发现行为，以及寻址类扩展应遵守的兼容性预期。

寻址是扩展相邻机制，而不是当前统一插件类型。它仍放在插件规范树中，是因为官方扩展文档
历史上将基于 address-server 的 lookup 描述为扩展点。共享扩展规则由
[Nacos 插件化规范](plugin-spec.md) 定义，集群成员关系仍属于
[集群成员规范](../design/foundation-cluster-membership-spec.md)。

## 概念

| 概念 | 含义 |
|------|------|
| Member | 集群中的一个 Nacos 服务端节点。 |
| Member lookup | 服务端发现并刷新集群 member list 的服务。 |
| Server list provider | Java 客户端侧返回 SDK 请求 server list 的 SPI。 |
| Address server | 返回当前 server list 的外部 HTTP 端点。 |
| Lookup mode | 被选中的服务端成员发现策略。 |
| Address source | 诊断客户端 server list 来源的值。 |

## Java 客户端寻址

Java Client SDK 在 `AbstractServerListManager` 中通过 SPI 加载 `ServerListProvider`
实现。Config 和 Naming 客户端分别通过 `ConfigServerListManager` 和
`NamingServerListManager` 使用选中的 provider；gRPC client 再通过 `ServerListFactory`
消费同一份 server list。

被选中的 provider 是满足 `match(...)` 且 `getOrder()` 最高的实现。

内置 provider：

| Provider | 触发条件 | 行为 |
|----------|----------|------|
| `PropertiesListProvider` | 配置了 `serverAddr`。 | 使用客户端配置中的固定 server address list。 |
| `EndpointServerListProvider` | 配置了 `endpoint`。 | 从 address endpoint 拉取 server 地址，周期刷新，并在列表变化时发布 `ServerListChangeEvent`。 |

客户端寻址配置包括：

| 配置项 | 目的 |
|--------|------|
| `serverAddr` | 固定 server address list。 |
| `endpoint` | 动态 server address endpoint host。 |
| `endpointPort` | endpoint 端口，Java 客户端实现默认 `8080`。 |
| `endpointContextPath` | 构造 endpoint URL 时使用的 context path。 |
| `endpointClusterName` | endpoint path 使用的 server list 名称。 |
| `endpointQueryParams` | 追加到 endpoint URL 的 query string。 |
| `endpointRefreshIntervalSeconds` | endpoint 模式刷新周期。 |
| `isUseEndpointParsingRule` | 客户端是否应用 endpoint 解析规则。 |

客户端寻址扩展必须：

- 返回 Nacos HTTP 和 gRPC client 可解析的 server 地址；
- 保持 server list 刷新与请求 payload 语义解耦；
- 动态列表变化时发布 `ServerListChangeEvent`；
- 在 `shutdown()` 中释放后台刷新资源；
- 保留 `NacosClientProperties` 传入的 namespace、context path 和 module name 语义。

客户端寻址扩展属于 Java Client SDK 扩展，不是服务端插件管理器条目，不由服务端 Admin
插件 API 列出或启停。

## 当前服务端 Lookup 模式

`LookupFactory` 会选择一个 `MemberLookup`。

| 模式 | 名称 | 行为 |
|------|------|------|
| 文件配置 | `file` | 读取 `cluster.conf` 或配置的 member list，并监听本地配置变化。 |
| 地址服务器 | `address-server` | 从地址服务器 URL 拉取 member list，并周期刷新。 |
| 单机 | 内部模式 | 服务端以 standalone 模式运行时使用。 |

选择由以下配置控制：

```properties
nacos.core.member.lookup.type=file
nacos.core.member.lookup.type=address-server
```

如果未配置模式，服务端在存在本地集群成员配置时使用文件配置模式，否则使用地址服务器模式。

文件模式拥有本地静态成员发现。地址服务器模式拥有远端动态成员发现。单机模式不得发布多节点
成员关系。

## 地址服务器模式

地址服务器模式使用：

| 配置或环境变量 | 目的 |
|----------------|------|
| `address.server.domain` / `address_server_domain` | 地址服务器主机。 |
| `address.server.port` / `address_server_port` | 地址服务器端口。 |
| `address.server.url` / `address_server_url` | 返回 server list 的路径。 |
| `nacos.core.address-server.retry` | 启动拉取重试次数。 |
| `maxHealthCheckFailCount` | 地址服务器被标记为不健康前的失败次数。 |

返回的 server list 必须能解析为 Nacos 集群 member 地址。

地址服务器模式必须按 `nacos.core.address-server.retry` 进行启动拉取重试。运行时健康检查在
连续失败次数达到 `maxHealthCheckFailCount` 后将地址服务器标记为不健康，但不得在地址
服务器不可用时凭空构造新 member。

## 兼容性预期

寻址扩展必须保持[集群成员规范](../design/foundation-cluster-membership-spec.md)定义的 member
身份格式和更新语义，包括 listener 通知行为和关闭行为。扩展不得绕过集群成员校验，也不得注入地址
含义不明确的成员。

如果某个部署使用外部寻址 SPI，它应表现为单个被选择的 member lookup 服务，并必须记录自身
配置 key。

如果未来将寻址迁移到统一 `PluginType`，必须保持：

- `file` 和 `address-server` lookup 名称；
- 集群模块接受的 member 地址格式；
- member 变化时的 listener 通知行为；
- 未显式配置 lookup type 时的启动 fallback 行为。
