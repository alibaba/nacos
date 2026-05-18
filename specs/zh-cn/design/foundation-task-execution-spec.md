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

# Nacos 任务执行规范

本文定义 Nacos 各领域共用的基础任务执行模型。它是
[基础能力规范](foundation-capabilities-spec.md)中任务执行部分的展开。

## 1. 定位

任务执行是异步和定时工作的基础能力。它提供 delayed task、execute task、processor、按 key
调度、重试、合并、队列和诊断等通用原语。

任务执行不拥有领域语义。领域规范负责决定某个任务代表什么、用户可见成功何时成立、任务是否可以重试，
以及重启或故障转移后如何恢复状态。

典型使用场景包括：

- Config dump、变更通知、长轮询、容量检查和插件回调；
- Naming Distro sync/verify、push delay task、健康检查和 service 清理；
- persistence 健康检查和主数据源选择；
- metrics、trace 和其他周期性后台工作。

## 2. 任务类型

| 概念 | 当前类型 | 语义 |
| --- | --- | --- |
| Task | `NacosTask` | 通用契约。`shouldProcess()` 决定任务是否就绪。 |
| Delayed task | `AbstractDelayTask` | 带 interval、last process time 和 `merge` 行为的 keyed task。 |
| Execute task | `AbstractExecuteTask` | 立即就绪的 Runnable task。 |
| Processor | `NacosTaskProcessor` | 执行任务，并返回处理是否成功。 |
| Execute engine | `NacosTaskExecuteEngine` | 拥有 processor、任务插入、任务大小、关闭和诊断能力。 |
| Batch counter | `BatchTaskCounter` | 用于批量完成检查的辅助对象。 |

规则：

- task class 应是工作描述，而不是隐藏的持久状态；
- delayed task 必须显式定义 merge 行为；
- execute task 必须适合在选中的 worker 线程执行；
- processor 只有在希望 engine 重试该任务时才返回 `false`；
- task payload 必须包含足够的身份、时间戳、版本或操作类型，使重试和合并安全。

## 3. Delayed Task Engine

`NacosDelayTaskExecuteEngine` 将 delayed task 保存在 keyed map 中，并由单线程 scheduled
executor 周期性扫描。

模型如下：

```text
addTask(key, newTask)
  -> if an old task exists, newTask.merge(oldTask)
  -> tasks[key] = merged newTask
  -> scanner checks task.shouldProcess()
  -> remove ready task
  -> processor.process(task)
  -> if false or exception, update lastProcessTime and re-add task
```

规则：

- key 选择属于任务语义的一部分，必须对目标合并或按 key 替换行为保持稳定；
- merge 必须保留最强的待执行工作。例如全量 service push 应覆盖只针对部分 client 的 push；
- `shouldProcess()` 是就绪门槛，不是鉴权或领域正确性检查；
- delayed task 处理失败时，会更新 `lastProcessTime` 并重新加入队列；
- delayed task 必须具备幂等性，或由领域状态保护，因为重试会重复执行工作；
- engine shutdown 会清空待处理任务，因此需要重启恢复的领域必须把执行意图持久化到其他地方。

Config `TaskManager` 基于该模型处理 dump task，并补充 metrics、JMX task 信息和等待队列清空能力。
Naming `PushDelayTaskExecuteEngine` 基于该模型处理 service push delay task，并把就绪任务转发到
execute-task dispatcher。

## 4. Execute Task Engine

`NacosExecuteTaskExecuteEngine` 按 tag hash 将立即执行任务分发到多个 `TaskExecuteWorker`。

模型如下：

```text
addTask(tag, executeTask)
  -> if a processor is registered for tag, processor.process(task)
  -> otherwise choose worker by tag hash
  -> enqueue Runnable task
  -> worker thread runs task
```

规则：

- 对需要按资源保持顺序的操作，dispatch tag 必须稳定；
- execute task 会进入有界 worker queue；
- worker queue 满时，入队可能阻塞，因此不得在没有保护的低延迟关键路径上插入 execute-engine 任务；
- 任务运行超过慢任务阈值时，必须能通过日志或指标观察；
- execute task 抛出的异常由 worker 承接，但领域失败语义仍必须由任务实现自行处理。

Naming 通过 `NamingExecuteTaskDispatcher` 使用该模型，使 service 相关 push 工作按 service 身份分片。

## 5. 领域 Executor

除了通用 task engine，一些模块还使用专用 executor facade。

规则：

- `ConfigExecutor`、`PersistenceExecutor` 等模块 executor facade 应视为模块拥有的执行面；
- executor 选择必须匹配工作类型，例如 timer、async notify、long polling、capacity management、
  plugin callback 或 persistence health check；
- 定时任务必须定义后一次执行是否可以与前一次执行重叠；
- 长耗时或阻塞 IO 应使用专用 executor 或 task engine；
- 高吞吐路径应暴露 queue size、worker status 或等价诊断信息；
- shutdown 行为必须明确，因为内存 executor queue 不具备持久性。

## 6. 用户可见成功

任务完成和 API 成功是不同概念。

规则：

- 如果 API 在持久写成功后返回成功，notify、dump、push、trace 等后台任务是后续可见性或诊断工作，
  除非 API 规范另有说明；
- 如果 API 必须等待任务完成才返回成功，API 规范必须说明等待边界和超时行为；
- 异步修复、重试或漂移控制任务不得被描述为正常写入路径；
- 后台失败必须根据领域风险记录日志、上报指标、重试，或通过诊断能力暴露。

对于 Config，发布/删除成功由 Config 写路径定义；dump 和 notify task 更新本地服务缓存和 peer
可见性。对于 Naming，push task 更新 subscriber 视图，但不是 service 归属的事实来源。

## 7. 与事件的关系

任务和事件经常串联，但它们是不同抽象。

- 事件记录本地事实被观察到或状态发生转换。
- 任务代表现在或稍后应该执行的工作。
- 事件订阅者可以调度任务。
- 任务可以在更新本地状态后发布事件。

本地事件总线规则由
[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)定义。

## 8. 边界规则

- Task engine 是执行基础设施，不是持久工作流引擎。
- Task key、merge 行为、retry 行为和 processor 选择都属于任务契约。
- 除非领域持久化执行意图，否则内存中的待处理任务可能在 shutdown 时丢失。
- 可重试任务必须幂等，或由时间戳、版本、状态、CAS 等机制保护。
- 慢速 IO 不得运行在关键 task scanner 或 event publisher 线程上。
- 领域规范必须定义哪些任务失败影响资源正确性，哪些只影响可见性、诊断或修复延迟。

## 9. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [可观测钩子规范](foundation-observability-hooks-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [Config 持久化、Dump 与历史规范](../config/config-persistence-history-spec.md)
- [Naming 一致性与客户端状态规范](../naming/naming-consistency-client-spec.md)
