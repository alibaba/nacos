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

# Naming 发现与订阅规范

本文定义查询、订阅、推送、模糊订阅、本地缓存和 failover 行为。

## 1. 服务查询

服务查询返回某个 service 的 `ServiceInfo` 视图。该视图包含 service name、groupName、clusters、
cache duration、last reference time、hosts 和保护阈值状态。

服务端查询可能应用：

- cluster 过滤；
- 运行时 Open API list 的 enabled-only 过滤；
- 请求指定的 health-only 过滤；
- 服务端内部过滤规则；
- 健康保护阈值。

Admin list API 可以返回 service summary、service detail page、subscriber、client 和 cluster
metadata 等管理视图。这些管理视图不得重新定义运行时发现语义。

## 2. 订阅

gRPC 订阅会在调用方连接下记录 subscriber，并返回当前 `ServiceInfo` 视图。后续 service 变化会推送
给已订阅客户端。取消订阅会移除 subscriber；当本地没有 listener 时，客户端应停止该 service 的
服务端推送。服务端变更事件遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。

Java SDK 会尽可能将同一 service 的多个本地 listener 映射成一个服务端订阅。本地 listener 选择由
客户端侧 `NamingSelector` wrapper 完成，规则见
[Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)。

HTTP Open API 不支持长轮询或推送订阅。自定义 HTTP 客户端应显式查询服务实例，或使用 gRPC 完成
订阅。

## 3. 推送与本地缓存

服务端推送会更新客户端 `ServiceInfo` 缓存。Java SDK 会：

1. 校验推送的服务视图；
2. 当 push-empty protection 启用时忽略空或非法推送；
3. 将服务视图存入内存；
4. 计算 instance diff；
5. 通知匹配的本地 listener；
6. 将服务视图写入磁盘缓存。

客户端可以在订阅建立、重连、缓存缺失或轮询兜底时重新查询服务端。本地磁盘缓存是恢复和 failover
辅助，不是服务端持久化数据源。

服务端推送 fan-out 和重试任务必须遵循
[运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)和
[任务执行规范](../design/foundation-task-execution-spec.md)。

## 4. 模糊订阅

模糊订阅允许客户端在一个 namespace 内通过 `serviceName` 和 `groupName` pattern 订阅 service key。
服务端维护：

- 每个 pattern 对应的 watched clients；
- 每个 pattern 匹配的 service keys；
- service key 进入或离开匹配集合时的 add/delete 通知；
- 初始化或 diff 同步批次。

模糊订阅存在服务端 pattern 数量和匹配 service 数量限制。当达到限制时，服务端可以拒绝订阅，或对
该 pattern 抑制额外匹配 service。客户端必须处理错误响应和同步响应。

## 5. SDK 选择

Java SDK 提供：

- `getAllInstances` 返回当前视图；
- `selectInstances` 按健康、enabled 状态和正权重过滤；
- `selectOneHealthyInstance` 按权重选择一个实例；
- `subscribe` 和 `unsubscribe` 接收 `NamingEvent` 或客户端 selector-based event；
- `fuzzyWatch` 和 `fuzzyWatchWithServiceKeys` 进行基于 pattern 的 service-key 订阅。

SDK selection 只作用在客户端进程内，不应视为服务端流量策略。

## 6. Failover

Java SDK 可以在 failover 模式启用时读取本地 failover 数据。如果存在合法 failover 视图，SDK 可以
优先返回该视图，而不是查询或订阅服务端。

Failover 数据是客户端应急视图。它不得改变服务端资源模型、service 元数据或 instance 生命周期。

## 7. 相关规范

- [Naming 资源规范](naming-resource-spec.md)
- [Naming 健康检查与保护规范](naming-health-protection-spec.md)
- [任务执行规范](../design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [SDK 规范](../sdk/sdk-spec.md)
