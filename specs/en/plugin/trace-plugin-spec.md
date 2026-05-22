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

# Trace Plugin Spec

## Scope

The trace plugin type lets Nacos publish domain operation events to subscribers.
It is intended for Nacos operation tracing, auditing, and diagnostics, not for
distributed tracing between application services.

This is a subscriber or broadcast plugin. Multiple subscribers may observe the
same event. Trace plugins must not own the primary business decision. Common
plugin lifecycle and state rules are defined by the
[Nacos Plugin Spec](plugin-spec.md).
Trace event dispatch runs on Nacos local event infrastructure and must also
follow the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).
Shared trace, audit, metrics, and diagnostic boundaries are defined by the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).

Unlike generic distributed tracing, Nacos trace events describe Nacos resource
operations, such as instance registration, service removal, service push, and
health state changes. They are not spans for application-to-application calls.

## Concepts

| Concept | Meaning |
|---------|---------|
| `TraceEvent` | Base immutable event with type, event time, namespace, group, and name. |
| Domain trace event | A subclass that adds domain-specific fields. |
| Combined subscriber | Core bridge that maps emitted domain events to interested plugin subscribers. |
| Subscriber executor | Optional executor used to isolate plugin IO or slow callbacks. |

## SPI

Plugins implement `NacosTraceSubscriber`.

| Method | Requirement |
|--------|-------------|
| `getName()` | Stable subscriber name. Later duplicate names replace earlier ones. |
| `subscribeTypes()` | Trace event classes this subscriber wants to receive. |
| `onEvent(event)` | Subscriber callback. |
| `executor()` | Optional executor for asynchronous callback execution. |

The plugin is exposed to the core plugin manager as type `trace`.

## Event Rules

Trace events carry Nacos [resource information](../design/resource-model-spec.md)
such as event type, event time, namespace, group, and resource name. Domain
events may add extra fields.
Common field names, sanitization rules, and metric label boundaries are defined
by the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).
This plugin spec does not require every domain to emit identical business
fields.

Subscribers must treat events as immutable facts. They must not mutate Nacos
resources from the trace callback unless the owning domain explicitly documents
that side effect.

Current naming trace event types include:

| Event class | Event type | Meaning |
|-------------|------------|---------|
| `RegisterInstanceTraceEvent` | `REGISTER_INSTANCE_TRACE_EVENT` | Instance registration. |
| `BatchRegisterInstanceTraceEvent` | `BATCH_REGISTER_INSTANCE_TRACE_EVENT` | Batch instance registration. |
| `DeregisterInstanceTraceEvent` | `DEREGISTER_INSTANCE_TRACE_EVENT` | Instance deregistration. |
| `RegisterServiceTraceEvent` | `REGISTER_SERVICE_TRACE_EVENT` | Empty service creation. |
| `DeregisterServiceTraceEvent` | `DEREGISTER_SERVICE_TRACE_EVENT` | Empty service removal. |
| `UpdateInstanceTraceEvent` | `UPDATE_INSTANCE_TRACE_EVENT` | Instance metadata or state update. |
| `UpdateServiceTraceEvent` | `UPDATE_SERVICE_TRACE_EVENT` | Service metadata update. |
| `SubscribeServiceTraceEvent` | `SUBSCRIBE_SERVICE_TRACE_EVENT` | Service subscription. |
| `UnsubscribeServiceTraceEvent` | `UNSUBSCRIBE_SERVICE_TRACE_EVENT` | Service unsubscription. |
| `PushServiceTraceEvent` | `PUSH_SERVICE_TRACE_EVENT` | Service push to subscribers. |
| `HealthStateChangeTraceEvent` | `HEALTH_STATE_CHANGE_TRACE_EVENT` | Instance health state change. |

`DeregisterInstanceTraceEvent` carries a reason. Current reasons are
`REQUEST`, `NATIVE_DISCONNECTED`, `SYNCED_DISCONNECTED`, and
`HEARTBEAT_EXPIRE`.

Current AI resource trace event types include:

| Event class | Event type | Meaning |
|-------------|------------|---------|
| `AiResourceTraceEvent` | `AI_RESOURCE_TRACE_EVENT` | AI resource lifecycle operation, such as draft creation, review, publish, online/offline, deletion, label update, scope update, or audit-compatible default log output. |

`AiResourceTraceEvent` carries the operator, resource type, resource id,
optional version, operation, status, client IP, and optional extension text.

## Execution

`NacosCombinedTraceSubscriber` registers a domain event publisher and dispatches
only matching event classes to each plugin subscriber. If `executor()` returns
`null`, the callback runs in the event dispatch path. Plugins that write to
remote systems, files, databases, or other slow sinks should return a dedicated
executor.

The trace publisher is allowed to degrade by dropping trace events under queue
pressure, as defined by the local event degradation rules.

Trace subscribers are loaded by SPI. Duplicate names in the same type are not
stable for production use; plugin packages should use unique names.

## Degradation

Trace plugins are observability extensions. Their failure must not break Nacos
core data changes or request handling. Plugins that perform blocking IO should
return a dedicated executor. If the trace queue is overloaded, events may be
dropped to preserve server stability.

The current core bridge catches subscriber callback exceptions. Plugin
implementations must still log enough information for operators to diagnose
their own sink failures.

## Implementation Note

The Nacos server repository defines the trace SPI and event model. Reference
subscriber implementations may live in external plugin repositories and should
follow this spec.

For compatibility with the existing AI resource audit log, Nacos ships a
default `AiResourceTraceEvent` file-log subscriber in `plugin-default-impl` and
packages it with the default plugins. It is a normal trace subscriber and
writes the existing JSON line format to `ai-resource-trace.log`.
