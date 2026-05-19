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

# Config 监听与订阅规范

本文定义精确 Config 监听、服务端推送和模糊订阅语义。

## 1. 精确监听

精确监听面向一个具体 Config 资源：

```text
namespaceId -> groupName -> dataId
```

客户端为每个监听配置发送当前 md5。服务端记录：

- group key 到 connection id 的映射；
- connection id 到 group key 和 md5 的映射；
- 该连接是否需要 namespace 兼容转换。

客户端注册监听时，服务端会比较客户端 md5 和服务端状态。如果客户端不是最新状态，响应中会包含
发生变化的配置身份，客户端随后查询内容。

## 2. 变更推送

发布、删除、元数据更新或灰度状态变化成功后，Config 领域发布本地变更事件。gRPC notifier 根据
变化的 group key 找到监听连接，并推送 `ConfigChangeNotifyRequest`。Config 变更事件的跨节点刷新可见性
由[AP 一致性规范](../design/foundation-ap-consistency-spec.md)定义。本地事件分发边界由
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义。

变更推送规则：

- 推送携带变化的 Config 身份，不携带权威配置内容；
- 客户端收到变更通知后必须查询 Config；
- 推送使用重试、任务执行和 Control 点；
- 当推送重试超过配置次数时，服务端可以注销失效连接；
- 断开的客户端会丢失内存中的监听状态，重连后必须重新注册。

推送重试和异步 notifier 执行必须遵循[任务执行规范](../design/foundation-task-execution-spec.md)。

## 3. 模糊订阅

模糊订阅监听模式，而不是单个精确 Config。模式生成格式为：

```text
namespaceId >> groupPattern >> dataIdPattern
```

模式使用 `*` 匹配：

| 模式形式 | 含义 |
| --- | --- |
| `*` | 匹配该段所有值。 |
| `prefix*` | 前缀匹配。 |
| `*suffix` | 后缀匹配。 |
| `*text*` | 包含匹配。 |
| `literal` | 精确匹配。 |

模糊订阅维护每个模式命中的 group key 集合。初始化时，服务端对比命中集合和客户端上报的集合，
按批次推送 `ADD_CONFIG` 或 `DELETE_CONFIG` 状态。初始化后，Config 变化会更新命中集合，并向
匹配客户端推送变化事件。

## 4. 模糊订阅限制

模糊订阅必须有边界：

- `nacos.config.fuzzy.watch.max.pattern.count` 限制跟踪模式数量，默认 `20`。
- `nacos.config.fuzzy.watch.max.pattern.match.config.count` 限制每个模式命中的配置数，默认 `500`。
- `nacos.config.push.batchSize` 控制初始化 diff 批大小，默认 `20`。

当某个模式达到命中配置上限时，服务端可以抑制后续新增命中项，并在命中集合不完整时保护删除通知。

## 5. 诊断

Admin listener 和 metric API 可以按配置身份、客户端 IP 和集群节点查询监听状态。这些 API 属于
管理诊断能力，不应通过运行时 Client SDK 暴露。

## 6. 运行时恢复

Config listener reconnect、连接生命周期和 push retry 行为遵循
[运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)。Config 本地
failover、snapshot、listener resync 和 fuzzy watch recovery 由
[客户端本地缓存与 Redo 规范](../client/client-local-cache-redo-spec.md)定义。基础层服务端
连接边界仍由[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)定义。
