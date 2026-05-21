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

# Nacos Task Execution Spec

This document defines the foundation task execution model used by Nacos domains.
It expands the task execution part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

## 1. Positioning

Task execution is a foundation capability for asynchronous and scheduled work.
It provides common primitives for delayed tasks, immediate execute tasks,
processors, keyed dispatch, retry, merge, queueing, and diagnostics.

Task execution does not own domain semantics. Domain specs decide what a task
means, when user-visible success happens, whether a task may be retried, and how
state is recovered after restart or failover.

Typical users include:

- Config dump, change notification, long polling, capacity checks, and plugin
  callbacks;
- Naming Distro sync and verify tasks, push delay tasks, health checks, and
  service cleanup;
- persistence health checks and master datasource selection;
- metrics, trace, and other periodic background work.

## 2. Task Types

| Concept | Current type | Semantics |
| --- | --- | --- |
| Task | `NacosTask` | Common contract. `shouldProcess()` decides whether the task is ready. |
| Delayed task | `AbstractDelayTask` | Keyed task with interval, last process time, and `merge` behavior. |
| Execute task | `AbstractExecuteTask` | Runnable task that is ready immediately. |
| Processor | `NacosTaskProcessor` | Executes a task and returns whether processing succeeded. |
| Execute engine | `NacosTaskExecuteEngine` | Owns processors, task insertion, task size, shutdown, and diagnostics. |
| Batch counter | `BatchTaskCounter` | Helper for batch completion checks. |

Rules:

- task classes must be small descriptions of work, not hidden durable state;
- delayed tasks must define merge behavior explicitly;
- execute tasks must be safe to run on the selected worker thread;
- processors must return `false` only when the task should be retried by the
  engine;
- task payloads must contain enough identity, timestamp, version, or operation
  type to make retries and merges safe.

## 3. Delayed Task Engine

`NacosDelayTaskExecuteEngine` stores delayed tasks in a keyed map and scans them
periodically with a single scheduled executor.

Model:

```text
addTask(key, newTask)
  -> if an old task exists, newTask.merge(oldTask)
  -> tasks[key] = merged newTask
  -> scanner checks task.shouldProcess()
  -> remove ready task
  -> processor.process(task)
  -> if false or exception, update lastProcessTime and re-add task
```

Rules:

- key choice is part of task semantics and must be stable for the intended
  merge or replace-by-key behavior;
- merge must preserve the strongest required work. For example, a full-service
  push must dominate a subset-client push;
- `shouldProcess()` is the readiness gate, not an authorization or domain
  correctness check;
- failed delayed task processing is retried by re-adding the task after updating
  `lastProcessTime`;
- delayed task processing must be idempotent or guarded by domain state because
  retry can repeat work;
- the engine shutdown clears pending tasks, so domains that need restart
  recovery must persist the intent elsewhere.

Config `TaskManager` extends this model for dump tasks, adds metrics, exposes
JMX task information, and can wait until the queue becomes empty. Naming
`PushDelayTaskExecuteEngine` extends this model for service push delay tasks and
dispatches ready work into the execute-task dispatcher.

## 4. Execute Task Engine

`NacosExecuteTaskExecuteEngine` dispatches immediate tasks to sharded
`TaskExecuteWorker` instances by tag hash.

Model:

```text
addTask(tag, executeTask)
  -> if a processor is registered for tag, processor.process(task)
  -> otherwise choose worker by tag hash
  -> enqueue Runnable task
  -> worker thread runs task
```

Rules:

- dispatch tags must be stable for operations that require per-resource
  ordering;
- execute tasks are queued in bounded worker queues;
- enqueueing may block when the worker queue is full, so callers must avoid
  placing execute-engine insertion on latency-critical paths without protection;
- a task running longer than the slow-task threshold must be observable in logs
  or metrics;
- exceptions thrown by execute tasks are contained by the worker, but domain
  failure semantics must still be handled by the task implementation.

Naming uses this model through `NamingExecuteTaskDispatcher` so service-related
push work is sharded by service identity.

## 5. Domain Executors

Some modules use dedicated executor facades in addition to common task engines.

Rules:

- module executor facades, such as `ConfigExecutor` or `PersistenceExecutor`,
  must be treated as module-owned execution surfaces;
- executor choice must match work type, such as timer, async notify, long
  polling, capacity management, plugin callback, or persistence health check;
- scheduled tasks must define whether a later run can overlap an earlier run;
- long-running or blocking IO should use a dedicated executor or task engine;
- domain code must expose queue size, worker status, or equivalent diagnostics
  for high-volume paths;
- shutdown behavior must be explicit because in-memory executor queues are not
  durable.

## 6. User-Visible Success

Task completion and API success are different concepts.

Rules:

- if an API returns success after a durable write, background tasks such as
  notify, dump, push, and trace are follow-up visibility or diagnostic work
  unless the API spec says otherwise;
- if an API returns success only after task completion, the API spec must state
  the wait boundary and timeout behavior;
- asynchronous repair, retry, or drift-control tasks must not be presented as
  the normal write path;
- background failure must be logged, metered, retried, or surfaced through
  diagnostics according to domain risk.

For Config, durable publish/delete success is defined by the Config write path;
dump and notify tasks update local serving cache and peer visibility. For
Naming, push tasks update subscriber views and are not the source of service
ownership.

## 7. Relation To Events

Tasks and events are often chained but remain different abstractions.

- An event records that a local fact was observed or a state transition
  happened.
- A task represents work that should be executed now or later.
- Event subscribers may schedule tasks.
- Tasks may publish events after they update local state.

The local event bus rules are defined by the
[Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

## 8. Boundary Rules

- Task engines are execution infrastructure, not durable workflow engines.
- Task keys, merge behavior, retry behavior, and processor selection are part of
  the task contract.
- In-memory pending tasks may be lost on shutdown unless the domain persists the
  intent.
- Retryable tasks must be idempotent or guarded by timestamp, version, state, or
  compare-and-set style checks.
- Slow IO must not run on critical task scanner or event publisher threads.
- Domain specs must define which task failures affect resource correctness and
  which only affect visibility, diagnostics, or repair latency.

## 9. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Observability Hooks Spec](foundation-observability-hooks-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [Config Persistence, Dump, And History Spec](../config/config-persistence-history-spec.md)
- [Naming Consistency And Client State Spec](../naming/naming-consistency-client-spec.md)
