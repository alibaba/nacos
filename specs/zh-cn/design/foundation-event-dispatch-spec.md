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

# Nacos 事件分发与 NotifyCenter 规范

本文定义 Nacos 各领域共用的本地事件分发和消息总线模型。它是
[基础能力规范](foundation-capabilities-spec.md)中事件分发部分的展开。

## 1. 定位

事件分发是本地进程内基础能力。模块可以发布不可变的本地事实，订阅者可以据此更新派生索引、调度任务、
刷新本地视图、桥接 trace 事件，或响应生命周期变化。

事件分发不是跨节点复制协议，不是持久日志，也不是公开 API 契约。领域如果需要跨节点可见性，必须使用
持久化、AP 一致性、CP 一致性或内部集群请求。

## 2. 概念

| 概念 | 当前类型 | 语义 |
| --- | --- | --- |
| Event | `Event` | 可序列化的本地事实，带单调 sequence 和可选 scope。 |
| Slow event | `SlowEvent` | 共享同一 publisher queue 的事件族。其 sequence 始终为 `0`。 |
| Notify center | `NotifyCenter` | publisher 和 subscriber 的全局注册中心。 |
| Publisher | `EventPublisher` | 拥有某个事件族的排队和 subscriber 回调。 |
| Shared publisher | `DefaultSharePublisher` | `SlowEvent` 子类共用的 publisher。 |
| Sharded publisher | `ShardedEventPublisher` | 可以让多种事件类型共用一个队列的 publisher。 |
| Subscriber | `Subscriber` | 单一事件类型的回调，支持可选 executor 和 scope 过滤。 |
| Smart subscriber | `SmartSubscriber` | 订阅多种事件类型的 subscriber。 |
| Publisher factory | `EventPublisherFactory` | 为特定事件族构造专用 publisher。 |

## 3. Publisher 模型

默认行为：

- 非 slow event 使用按事件类型区分的 `DefaultPublisher`；
- `SlowEvent` 子类使用共享的 `DefaultSharePublisher`；
- 默认非 slow publisher queue size 由 `nacos.core.notify.ring-buffer-size` 控制，默认
  `16384`；
- 共享 slow-event queue size 由 `nacos.core.notify.share-buffer-size` 控制，默认 `1024`；
- `NotifyCenter` 会通过 SPI 加载自定义 `EventPublisher`，没有实现时使用 `DefaultPublisher`；
- publisher 在 subscriber 注册或代码显式调用 `registerToPublisher` 时懒加载创建。

默认 publisher 规则：

- publisher 线程在启动阶段会有限等待 subscriber 注册，然后才消费队列事件；
- 默认队列满时，publish 会回退为在发布线程同步投递；
- 非 plugin event 没有 publisher 时，发布失败并打印 warning；
- `Event.isPluginEvent()` 为 true 且没有 publisher 时，事件可以静默丢弃；
- publisher shutdown 会清空队列。

专用 publisher 规则：

- 当默认按类型队列不足以表达领域需求时，领域可以注册自定义 publisher factory；
- Naming 使用 sharded publisher factory，使相关 member event class 可以共享同一队列并保持需要的顺序；
- Trace 使用专用 publisher family，使 trace subscriber 和插件 IO 与通用事件流隔离；
- 专用 publisher 必须说明 queue size、ordering、overflow 和 shutdown 行为。

## 4. Subscriber 模型

规则：

- `Subscriber.subscribeType()` 表示普通 subscriber 订阅的单一事件类型；
- `SmartSubscriber.subscribeTypes()` 表示 smart subscriber 订阅的全部事件类型；
- `Subscriber.executor()` 可以返回专用 executor，用于隔离回调；
- 如果没有返回 executor，回调会在 publisher dispatch 路径执行；
- `scopeMatches(event)` 可以按 event scope 过滤；
- `ignoreExpireEvent()` 可以让 subscriber 忽略早于 publisher 已处理 sequence 的事件；
- subscriber 异常必须由 publisher 或桥接层承接，不得停止进程。

执行阻塞 IO、插件回调、跨节点请求或大范围重建的 subscriber，必须使用专用 executor，或通过
[任务执行规范](foundation-task-execution-spec.md)调度任务。

## 5. 事件语义

事件是本地事实，不是该事实的权威来源。

规则：

- 事件应在它描述的权威本地状态更新之后发布；
- event payload 应包含身份、操作类型、时间戳和 subscriber 所需的最小字段；
- subscriber 需要最新状态时，应重新读取权威状态或派生索引，而不是把 event 当作完整快照；
- 当没有 publisher 或 subscriber 时，事件可能重复、延迟、被领域逻辑合并，或被错过；
- 事件顺序只在该事件族选中的 publisher queue 内成立；
- event class 和 payload 是内部契约，除非接口规范显式暴露。

示例：

- `MembersChangeEvent` 告诉本地组件有效 member 视图已变化；subscriber 需要最新视图时应重新读取
  member 状态。
- Config `LocalDataChangeEvent` 告诉本地 listener/watch 组件本地服务缓存已变化；它不是跨节点复制保证。
- Naming client、service 和 metadata event 用于重建索引和触发 push；client 或持久 metadata 状态仍是
  权威来源。
- Trace event 是可观测的操作事实，不得驱动主要领域决策。

## 6. 与任务和一致性的关系

事件和任务经常串联：

```text
authoritative state update
  -> publish local event
  -> subscriber updates derived index or schedules task
  -> task performs async visibility, repair, notify, push, or trace work
```

规则：

- 本地事件发布本身不是 AP 一致性；
- 只有领域定义了远端传播、重试、verify 和 repair 行为时，才形成 AP 一致性；
- CP processor 只能在 committed apply 更新本地状态之后发布领域事件；
- persistence dump 只能在本地 cache 已更新之后发布本地可见性事件；
- subscriber 调度的任务必须遵循[任务执行规范](foundation-task-execution-spec.md)。

## 7. 边界规则

- `NotifyCenter` 是本地消息总线，不是分布式事件总线。
- 除非领域规范或接口规范提升语义，否则事件只是实现内部契约。
- Event payload 不得重新定义资源身份、鉴权或持久化语义。
- 慢 subscriber 不得阻塞 publisher 线程；应使用 `executor()` 或调度任务。
- 自定义 publisher 必须通过 queue size、status、日志或指标保持可观测。
- plugin event 的丢失容忍度必须明确，因为没有 publisher 时 plugin event 可以被丢弃。

## 8. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [可观测钩子规范](foundation-observability-hooks-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [Trace 插件规范](../plugin/trace-plugin-spec.md)
- [Naming 一致性与客户端状态规范](../naming/naming-consistency-client-spec.md)
