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

# Nacos Event Dispatch And NotifyCenter Spec

This document defines the local event dispatch and message bus model used by
Nacos domains. It expands the event dispatch part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

## 1. Positioning

Event dispatch is a local in-process foundation capability. It lets modules
publish immutable local facts and lets subscribers update derived indexes,
schedule tasks, refresh local views, bridge trace events, or react to lifecycle
changes.

Event dispatch is not a cross-node replication protocol, not a durable log, and
not a public API contract. Domains that require cross-node visibility must use
persistence, AP consistency, CP consistency, or internal cluster requests.

## 2. Concepts

| Concept | Current type | Semantics |
| --- | --- | --- |
| Event | `Event` | Serializable local fact with monotonic sequence and optional scope. |
| Slow event | `SlowEvent` | Event family sharing one publisher queue. Its sequence is always `0`. |
| Notify center | `NotifyCenter` | Global registry for publishers and subscribers. |
| Publisher | `EventPublisher` | Owns queueing and subscriber callback for an event family. |
| Shared publisher | `DefaultSharePublisher` | Shared publisher for `SlowEvent` subtypes. |
| Sharded publisher | `ShardedEventPublisher` | Publisher that can route multiple event types through one queue. |
| Subscriber | `Subscriber` | Callback for one event type, with optional executor and scope filtering. |
| Smart subscriber | `SmartSubscriber` | Subscriber for multiple event types. |
| Publisher factory | `EventPublisherFactory` | Builds specialized publishers for specific event families. |

## 3. Publisher Model

Default behavior:

- non-slow events use a per-event-type `DefaultPublisher`;
- `SlowEvent` subtypes use the shared `DefaultSharePublisher`;
- default non-slow publisher queue size is controlled by
  `nacos.core.notify.ring-buffer-size`, default `16384`;
- shared slow-event queue size is controlled by
  `nacos.core.notify.share-buffer-size`, default `1024`;
- `NotifyCenter` loads a custom `EventPublisher` through SPI when present,
  otherwise it uses `DefaultPublisher`;
- publisher instances are created lazily when subscribers register or when code
  explicitly calls `registerToPublisher`.

Default publisher rules:

- publisher threads wait for a subscriber for a limited startup window before
  consuming queued events;
- publishing to a full default queue falls back to synchronous delivery in the
  publishing thread;
- if a non-plugin event has no publisher, publication fails with a warning;
- if `Event.isPluginEvent()` is true and no publisher exists, the event may be
  dropped silently;
- publisher shutdown clears the queue.

Specialized publisher rules:

- a domain may register a custom publisher factory when the default per-type
  queue is not enough;
- Naming uses a sharded publisher factory so related member event classes can
  share one queue and preserve required ordering;
- Trace uses a dedicated publisher family so trace subscribers and plugin IO are
  isolated from generic event flow;
- specialized publishers must document queue size, ordering, overflow, and
  shutdown behavior.

## 4. Subscriber Model

Rules:

- `Subscriber.subscribeType()` identifies the single event type for a normal
  subscriber;
- `SmartSubscriber.subscribeTypes()` identifies all event types for a smart
  subscriber;
- `Subscriber.executor()` may return a dedicated executor for callback
  isolation;
- if no executor is returned, callbacks run in the publisher dispatch path;
- `scopeMatches(event)` may filter events by event scope;
- `ignoreExpireEvent()` may ignore events older than the publisher's last
  handled sequence for that subscriber;
- subscriber exceptions must be contained by the publisher or bridge and must
  not stop the process.

Subscribers that perform blocking IO, plugin callbacks, cross-node requests, or
large rebuilds must use a dedicated executor or schedule a task through the
[Task Execution Spec](foundation-task-execution-spec.md).

## 5. Event Semantics

An event is a local fact, not the fact's source of truth.

Rules:

- events should be published after the authoritative local state update they
  describe;
- event payloads should contain identity, operation type, timestamp, and minimal
  fields needed by subscribers;
- subscribers that need the latest state should reread authoritative state or a
  derived index instead of trusting the event as a complete snapshot;
- events may be duplicated, delayed, coalesced by domain logic, or missed when no
  publisher or subscriber exists;
- event ordering is guaranteed only within the publisher queue selected for the
  event family;
- event classes and payloads are internal contracts unless an interface spec
  explicitly exposes them.

Examples:

- `MembersChangeEvent` tells local components that the effective member view
  changed; subscribers reread member state when they need the latest view.
- Config `LocalDataChangeEvent` tells local listener/watch components that
  local serving cache changed; it is not a cross-node replication guarantee.
- Naming client, service, and metadata events rebuild indexes and trigger push;
  the client or persistent metadata state remains the authoritative source.
- Trace events are observable operation facts and must not drive primary domain
  decisions.

## 6. Relationship With Tasks And Consistency

Events and tasks are often chained:

```text
authoritative state update
  -> publish local event
  -> subscriber updates derived index or schedules task
  -> task performs async visibility, repair, notify, push, or trace work
```

Rules:

- local event publication alone is not AP consistency;
- AP consistency exists only when the domain defines remote propagation, retry,
  verify, and repair behavior;
- CP processors may publish domain events only after committed apply updates
  local state;
- persistence dump may publish local visibility events only after local cache has
  been updated;
- tasks scheduled by subscribers must follow the
  [Task Execution Spec](foundation-task-execution-spec.md).

## 7. Boundary Rules

- `NotifyCenter` is a local message bus, not a distributed event bus.
- Events are implementation contracts unless promoted by a domain or interface
  spec.
- Event payloads must not redefine resource identity, authorization, or
  persistence semantics.
- Slow subscribers must not block publisher threads; use `executor()` or
  schedule a task.
- Custom publishers must keep event dispatch observable through queue size,
  status, logs, or metrics.
- Plugin event loss tolerance must be explicit because plugin events can be
  dropped when no publisher exists.

## 8. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Observability Hooks Spec](foundation-observability-hooks-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
- [Naming Consistency And Client State Spec](../naming/naming-consistency-client-spec.md)
