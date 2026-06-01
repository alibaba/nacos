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

# Nacos Observability Hooks Spec

This document defines the foundation observability model used by Nacos domains.
It expands the observability part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

## 1. Positioning

Observability hooks expose facts about Nacos runtime behavior so operators,
plugins, diagnostics APIs, logs, and monitoring systems can understand server
health, resource activity, request latency, queue pressure, and failure modes.

Observability is not a control path and not a source of domain truth. Metrics,
trace events, audit logs, server state, and diagnostics views must not redefine
Config, Naming, AI, security, or plugin resource semantics.

## 2. Signal Types

Nacos currently exposes these observability signal families:

| Signal | Primary implementation | Semantics |
| --- | --- | --- |
| Metrics | `NacosMeterRegistryCenter`, module `MetricsMonitor` classes, Micrometer | Numeric observations such as counters, gauges, timers, summaries, queue sizes, connection counts, and exception counts. |
| Trace events | `TraceEvent`, `NotifyCenter`, `NacosCombinedTraceSubscriber`, Trace plugin | Operation facts emitted by domains and optionally delivered to plugin subscribers. |
| Audit or trace logs | `ConfigTraceService`, `AiResourceTraceService`, module operation logs | Structured or line-oriented records for resource operations and diagnostics. |
| Health and readiness | `ModuleHealthCheckerHolder`, liveness/readiness endpoints | Process and module readiness facts for load balancers and orchestration. |
| Server state | `ModuleStateHolder`, server state APIs | Administrative state summary reported by modules. |
| Runtime diagnostics | loader metrics, Config listener metrics, Naming metrics, log-level APIs | Maintainer-facing inspection and adjustment surfaces. |
| External scrape adapters | Spring Boot Actuator/Micrometer registries, Prometheus module | Integration points for monitoring and service-discovery systems. |

## 3. Metrics Registry Model

`NacosMeterRegistryCenter` is the shared metrics registration facade. It creates
named `CompositeMeterRegistry` instances and attaches the Micrometer global
registry when available.

Current registry groups include:

| Registry | Intended scope |
| --- | --- |
| `CORE_STABLE_REGISTRY` | Core, remote, Raft, connection, and server executor metrics. |
| `CONFIG_STABLE_REGISTRY` | Config counters, timers, queue sizes, subscriber counts, and exceptions. |
| `NAMING_STABLE_REGISTRY` | Naming service, instance, subscriber, publisher, health check, push, and queue metrics. |
| `TOPN_CONFIG_CHANGE_REGISTRY` | Dynamic TopN Config change counters. |
| `TOPN_SERVICE_CHANGE_REGISTRY` | Dynamic TopN Naming service change counters. |
| `CONTROL_DENIED_REGISTRY` | Control plugin rejection metrics. |
| `LOCK_STABLE_REGISTRY` | Lock module metrics. |

Rules:

- stable registries should use low-cardinality tags and long-lived metric
  names;
- dynamic TopN registries may be cleared and rebuilt periodically and must not
  be treated as stable time series identity;
- metric tags must not contain secret content or full configuration payloads;
- high-cardinality resource labels must use TopN or bounded diagnostics instead
  of stable metric tags;
- metrics may describe queue size, retry latency, request latency, exception
  count, connection count, and resource count, but must not be used as the
  authoritative data source;
- modules that create high-volume task or event paths should expose queue,
  worker, retry, failure, or latency observations.

## 4. Domain Metrics

Core metrics cover Raft read and apply behavior, gRPC request timing, long
connections, per-module connection counts, and gRPC server executor status.
`GrpcServerThreadPoolMonitor` periodically samples SDK and cluster gRPC executor
state when `nacos.metric.grpc.server.executor.enabled` is true.

Config metrics cover query, publish, long-polling, notify task, client notify
task, dump task, fuzzy search, config count, subscriber count, read/write/notify
/dump latency, and Config-related exception counters. Config also maintains
TopN Config change counters.

Naming metrics cover service count, instance count, subscriber count, publisher
count, health-check counters, push count, failed push, empty push, push cost,
event queue size, pending push task count, and TopN service change counters.

Persistence, Control, Lock, and other modules may define additional metrics.
When a module exposes its own metrics, it must still follow the shared tag,
cardinality, and source-of-truth rules in this spec.

## 5. Trace And Audit

Trace and audit signals are operation facts.

Rules:

- trace payloads should include resource identity, operation type, timestamp,
  result, actor or source when available, and minimal diagnostic extension
  fields;
- trace payloads must not contain full Config content, secrets, tokens, or
  credentials;
- trace events are immutable observations and must not drive primary domain
  decisions;
- trace plugin subscribers must be isolated with a dedicated executor when they
  perform slow IO;
- trace plugin failures must not roll back or corrupt the domain operation that
  emitted the trace;
- domains should distinguish audit-grade logs from best-effort diagnostic trace
  events when persistence or compliance expectations differ.

Config currently writes line-oriented trace logs for persistence, notify, dump,
and pull operations. Naming emits `TraceEvent` subclasses through the local event
infrastructure and Trace plugin bridge. AI resource operations write
JSON-oriented trace logs for version, review, publish, label, visibility, and
lifecycle operations.

The Trace plugin surface is defined by the
[Trace Plugin Spec](../plugin/trace-plugin-spec.md). Local trace event dispatch
must also follow the
[Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

### 5.1 Field Guidance

Trace and audit payloads should keep a small stable base field set:

| Field category | Examples | Rule |
| --- | --- | --- |
| Signal identity | `eventType`, `signalType`, `module`, `domain` | Identify what happened without encoding business payload. |
| Resource identity | `resourceType`, `namespaceId`, `groupName`, `resourceName`, `version` | Use canonical resource names when available. |
| Operation context | `action`, `operation`, `phase`, `requestId`, `traceId` | Describe the operation and its phase. |
| Actor and source | `user`, `sourceIp`, `clientId`, `connectionId`, `member` | Include only when available and safe. |
| Result | `success`, `errorCode`, `exceptionClass`, `reason`, `latency` | Distinguish success, failure, and cost. |
| Extension | `labels`, `metadata`, `ext` | Keep bounded and sanitized. |

Metrics should use low-cardinality labels such as `module`, `operation`,
`protocol`, `result`, `errorCode`, `exceptionClass`, `registry`, `queue`,
`task`, `connectionType`, or `memberRole`. Stable metrics must not use raw
`dataId`, `serviceName`, `instanceIp`, `clientId`, Config content, AI artifact
body, tokens, or credentials as labels. High-cardinality facts should use TopN
registries, trace/audit logs, or diagnostic APIs instead.

Domain-owned examples:

- Config may include Config identity, publish/query/listen/dump/notify phase,
  and result fields, but not Config content.
- Naming may include service identity, instance operation reason, push phase,
  and health-check phase, but not arbitrary instance metadata payloads.
- AI may include AI resource identity, version, status, review result,
  visibility result, and pipeline stage, but not artifact bodies or model
  credentials.
- Core and foundation modules may include member identity, request type, raft
  group, task name, queue name, connection type, and lifecycle phase.

## 6. Health, Readiness, And Server State

Liveness answers whether the process is running. Readiness answers whether
Nacos should receive ordinary traffic. Module readiness checks are registered
through `AbstractModuleHealthChecker` and aggregated by
`ModuleHealthCheckerHolder`.

Server state is built from `ModuleStateHolder` and module `ModuleStateBuilder`
implementations. It is an administrative status view, not a resource model.

Rules:

- liveness and readiness endpoints may be intentionally public when a deployment
  or API spec marks them as health probes;
- readiness failure should identify the failing module at a coarse level;
- module state fields should be safe for operators to view and must not expose
  secrets;
- server state and readiness must not replace domain validation or
  authorization checks.

## 7. Diagnostic APIs And Logs

Diagnostic APIs are management or operation surfaces.

Examples include:

- server loader metrics and connection reload operations;
- Config client cache and snapshot metrics by client IP or Config identity;
- Naming metrics, switches, subscriber/client diagnostics, and log-level
  updates;
- module log-level updates;
- Prometheus service-discovery responses when the Prometheus module is enabled;
- memory, performance, Distro, queue, task worker, and response latency logs.

Rules:

- diagnostic APIs must be classified as Admin API, Console API, internal API, or
  explicitly public health probes;
- broad metrics and diagnostics must not be exposed through runtime Client SDK
  surfaces;
- diagnostics may aggregate across cluster members, but aggregation is an
  operation behavior and must tolerate partial failure or timeout;
- log-level updates are administrative controls and must require write
  permission;
- Prometheus or external scrape adapters must document their enablement,
  authentication, and payload scope.

## 8. Relationship With Other Foundation Capabilities

Observability hooks usually attach to other foundation paths:

- server state and readiness reporting depend on the
  [Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md);
- request metrics, auth context, and request diagnostics consume fields from the
  [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md);
- task engines expose queue and execution status according to the
  [Task Execution Spec](foundation-task-execution-spec.md);
- event publishers and trace bridges follow the
  [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md);
- internal cluster diagnostics use member routing from the
  [Cluster Membership Spec](foundation-cluster-membership-spec.md) and request
  semantics from the
  [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md);
- connection metrics and loader diagnostics depend on the
  [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md);
- Control plugin metrics and denials follow the
  [Control Plugin Spec](../plugin/control-plugin-spec.md).

## 9. Boundary Rules

- Observability must not change resource ownership, resource identity,
  persistence semantics, consistency behavior, or authorization decisions.
- Metrics and logs may be delayed, sampled, reset, dropped, or incomplete.
- Observable success does not imply domain success unless the owning domain API
  defines that relationship.
- Diagnostic payloads must avoid secrets and full opaque Config content.
- High-cardinality metrics must be bounded by TopN, sampling, or explicit
  diagnostics APIs.
- Plugin-provided observability must fail open for core data changes unless a
  separate governance spec explicitly defines a blocking policy.

## 10. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md)
- [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
- [Config Capacity And Ops Spec](../config/config-capacity-ops-spec.md)
- [Naming Ops Spec](../naming/naming-ops-spec.md)
