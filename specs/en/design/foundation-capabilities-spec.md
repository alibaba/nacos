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

# Nacos Foundation Capabilities Spec

This document defines the shared foundation capabilities used by Nacos domains.
The foundation layer provides infrastructure such as
[cluster membership](foundation-cluster-membership-spec.md),
[server lifecycle and environment configuration](foundation-server-lifecycle-env-spec.md),
[remote connection lifecycle](foundation-remote-connection-spec.md),
[request filtering and runtime context](foundation-request-context-spec.md),
[internal RPC and cluster requests](foundation-internal-rpc-spec.md),
[AP consistency](foundation-ap-consistency-spec.md),
[CP consistency](foundation-cp-consistency-spec.md),
[persistence and dump](foundation-persistence-dump-spec.md),
[task execution](foundation-task-execution-spec.md), and
[event dispatch](foundation-event-dispatch-spec.md), and
[observability hooks](foundation-observability-hooks-spec.md). It must support domain
semantics without owning Config, Naming, AI, or security resource meaning.

## 1. Positioning

Foundation capabilities sit below domain specs:

```text
Design intent
  -> Resource model
  -> Foundation capabilities
  -> Domain capabilities
  -> HTTP / gRPC / SDK interfaces
  -> Extension and security rules
```

A foundation capability may define state transfer, task scheduling, local
events, member discovery, or persistence behavior. A domain spec decides which
foundation capability to use for a resource and defines the resource lifecycle,
validation, authorization, and user-visible semantics.

## 2. Capability Inventory

| Capability | Primary modules | Responsibility | Domain contract |
| --- | --- | --- | --- |
| [Server lifecycle and environment configuration](foundation-server-lifecycle-env-spec.md) | `bootstrap`, `server`, `core.listener`, `sys.env` | Start process contexts, select deployment mode, inject environment, load pre-properties, refresh runtime server config, and expose module/server state. | Domains may read environment and lifecycle state, but must not define resource semantics through startup mechanics. |
| [Cluster membership](foundation-cluster-membership-spec.md) | `core.cluster`, addressing plugin | Discover members, keep member state, expose member-change notifications, and support member-based routing or aggregation. | Domains may depend on current member views, but must tolerate membership change and standalone mode. |
| [Remote connection lifecycle](foundation-remote-connection-spec.md) | `core.remote`, `common.remote` | Register gRPC connections, track connection metadata, process connection close/eject events, and expose request context. | Runtime domains may bind state to connection lifecycle, but transport heartbeat details remain hidden behind the connection layer. |
| [Request filtering and runtime context](foundation-request-context-spec.md) | `core.context`, `core.auth`, `core.paramcheck`, `core.control`, `core.remote` | Populate request context, execute HTTP/gRPC pre-handler filters, validate common parameters, and invoke auth/control/namespace guards. | Filters may enrich or reject requests, but domain handlers own resource lifecycle and operation semantics. |
| [Internal RPC and cluster requests](foundation-internal-rpc-spec.md) | `core.remote`, domain request handlers | Send server-to-server requests, cluster notifications, verification requests, and acknowledgements over the remote layer. | Internal RPC must not redefine public HTTP/gRPC API semantics. Domain handlers own payload meaning. |
| [AP consistency](foundation-ap-consistency-spec.md) | `core.distributed.distro`, Config notify path | Provide Distro runtime data sync and Config Notify-style eventual propagation for AP resources. | Domains must define resource type, owner, operation semantics, retry, verify, repair, and convergence tolerance. |
| [CP consistency](foundation-cp-consistency-spec.md) | `core.distributed.raft`, `consistency.cp` | Provide Raft/JRaft-backed strongly ordered writes, groups, processors, snapshots, and recovery for durable state. | Domains must define group ownership, request type, snapshot shape, read/write visibility, and unavailable behavior. |
| [Persistence and dump](foundation-persistence-dump-spec.md) | `persistence`, domain storage modules | Store durable data in embedded or external storage, load local dump data, and recover serving cache. | Domains must define schema, compatibility, data ownership, and whether dump is cache or source of truth. |
| [Task execution](foundation-task-execution-spec.md) | `common.task`, `common.executor`, domain task engines | Run immediate, delayed, retry, merge, batch, and scheduled tasks with bounded resources. | Tasks must be idempotent or explicitly guarded because retries, merge, and node changes can repeat work. |
| [Event dispatch](foundation-event-dispatch-spec.md) | `common.notify`, domain event publishers | Publish in-process events to subscribers, update indexes, trigger async work, and bridge trace events. | Events are local process facts unless a domain routes them through persistence or cluster protocols. |
| [Observability hooks](foundation-observability-hooks-spec.md) | `core.monitor`, `common.trace`, plugin trace/control | Report metrics, traces, queue depth, connection state, and operation events. | Observability must not change resource semantics or become a required control path. |

## 3. Server Lifecycle And Environment Configuration

Server lifecycle and environment configuration define how a Nacos process starts,
loads runtime configuration, exposes application context, selects deployment
type, and reports module/server state.

Lifecycle rules:

- bootstrap must select deployment type before context-specific beans and module
  state builders depend on it;
- `EnvUtil` is the shared facade for server environment properties, Nacos home,
  port, context path, standalone mode, function mode, member list, and
  processor sizing;
- startup phases must prepare work directories, property sources, system
  properties, and custom environment hooks before serving traffic;
- runtime server configuration refresh is a local process mechanism and must
  publish `ServerConfigChangeEvent` for dynamic config subscribers;
- module state and server state are operational views and must not contain
  secrets or redefine domain resources.

Detailed rules are defined by the
[Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md).

## 4. Cluster Membership

Cluster membership defines the set of Nacos server members that can participate
in cluster routing, aggregation, and internal protocols.

Membership rules:

- standalone mode has one effective member and must not expose synthetic
  multi-node membership;
- member identity must be parseable by the cluster module and stable enough for
  routing, logging, and diagnostics;
- member lookup can be static, dynamic, or plugin-backed, but all lookup modes
  must publish equivalent member-change semantics;
- domains must handle member add, remove, health change, and temporary
  unreachable states;
- broad diagnostic aggregation over members is an operation behavior, not a
  domain resource model.

Detailed membership rules are defined by the
[Cluster Membership Spec](foundation-cluster-membership-spec.md). The
server-side member lookup extension is defined by the
[Addressing Plugin Spec](../plugin/addressing-plugin-spec.md).

## 5. Remote Connection Lifecycle

The remote layer owns gRPC connection setup, connection metadata, request
context, push, acknowledgement, connection ejection, and disconnect events.

Connection rules:

- a connection must be registered before ordinary unary requests and push flows
  depend on it;
- connection metadata such as connection id, remote address, client version,
  labels, namespace, and ability table belongs to the remote layer;
- domains may attach runtime state to a connection id, but must release or redo
  that state when connection lifecycle events indicate close, reconnect, or
  ejection;
- transport heartbeat and half-open detection are remote-layer concerns;
  domains should consume connection lifecycle events instead of duplicating
  transport heartbeat logic;
- push queues, blocked pushes, and over-limit connection ejection are
  protection mechanisms and must not redefine domain data ownership.

Detailed connection rules are defined by the
[Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md). The
public request envelope is defined by the
[gRPC API Spec](../grpc-api/api-spec.md). Domain specs may further define how
their runtime state reacts to connection lifecycle events.

Server-to-server request rules, cluster-source restrictions, handler
registration, server identity, and payload registration are defined by the
[Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md).

## 6. Request Filtering And Runtime Context

Request filtering and runtime context define the pre-handler layer for HTTP and
gRPC requests.

Filtering rules:

- HTTP and gRPC entry points must populate `RequestContext` with protocol,
  target, identity-related, app, user agent, and remote/source address metadata
  when available;
- request context is runtime-only and must be cleared after a reusable worker
  thread finishes request processing;
- auth, control, parameter checking, and namespace validation are cross-cutting
  guards, not domain resource implementations;
- filters may reject requests before handlers execute, but should use the
  transport's standard response and error model;
- common structural validation belongs in shared filters and extractors, while
  domain-specific validation remains in domain forms, requests, services, or
  handlers.

Detailed HTTP/gRPC filter, `RequestContext`, extractor, namespace validation,
auth, and control hook rules are defined by the
[Request Filtering And Runtime Context Spec](foundation-request-context-spec.md).

## 7. Consistency Protocols

### 7.1 Protocol Selection

Domains must select consistency behavior based on resource semantics:

| Resource characteristic | Recommended foundation |
| --- | --- |
| Runtime, high-frequency, client-owned, disposable, eventually convergent state. | AP consistency through Distro-style protocols. |
| Durable, management-owned, snapshot-recoverable, strongly ordered state. | CP consistency through Raft/JRaft-style protocols. |
| Durable database state with local serving cache. | [Persistence plus dump](foundation-persistence-dump-spec.md) and domain-defined cache invalidation. |

Protocol choice is a semantic decision. A domain must not use Distro or Raft
only because an implementation path is convenient.

### 7.2 AP Consistency

AP consistency provides eventual convergence for runtime state or cache/listener
visibility. Current AP-style implementations are Distro and Config Notify. The
shared resource selection and implementation rules are defined by the
[AP Consistency Spec](foundation-ap-consistency-spec.md).

### 7.3 CP Consistency

CP consistency provides strongly ordered durable state. Current built-in CP
behavior is backed by Raft/JRaft through `JRaftProtocol`. The shared group,
processor, read/write, snapshot, and recovery rules are defined by the
[CP Consistency Spec](foundation-cp-consistency-spec.md).

## 8. Persistence, Dump, And Local Cache

Persistence stores durable data in embedded or external storage. Dump and local
cache provide serving acceleration or failover, but they are not automatically
the source of truth.

Persistence rules:

- domain specs own logical schema, compatibility, and migration expectations;
- datasource dialect plugins may adapt SQL and database behavior, but must not
  change logical data meaning;
- dump files and local cache must declare whether they are recoverable cache,
  failover data, or authoritative embedded storage;
- after a durable write, domains should define when local cache and listener
  views become visible;
- schema remnants kept for compatibility should be documented as compatibility
  fields or pending removal.

Detailed datasource, repository, embedded storage, dump, cache update, and
maintenance rules are defined by the
[Persistence And Dump Spec](foundation-persistence-dump-spec.md).

## 9. Task Execution

Nacos uses task engines and executors for delayed sync, retries, dump, health
checks, push, metrics collection, and other async work.

Task rules:

- tasks should be keyed when merge or replace-by-key behavior is expected;
- retryable tasks must be idempotent or guarded by version, timestamp, state, or
  compare-and-set checks;
- delayed tasks must define whether a later task replaces, merges, or coexists
  with earlier tasks;
- task queues must be bounded or protected by control, timeout, backpressure, or
  discard rules;
- user-visible success should be tied to domain semantics, not to background
  task completion unless the API explicitly says it waits for the task;
- task engines should expose metrics or diagnostics for queue length, retry
  count, failures, and execution latency.

Detailed delayed task, execute task, processor, queue, retry, merge, and
domain-executor rules are defined by the
[Task Execution Spec](foundation-task-execution-spec.md).

## 10. Event Dispatch And Message Bus

`NotifyCenter` and domain event publishers provide an in-process event bus. They
are used to update derived indexes, schedule async tasks, notify push
components, and bridge trace events.

Event rules:

- events are immutable facts about a local state transition or observation;
- local event publication is not a cross-node replication guarantee;
- subscribers must not block critical write or connection paths with slow
  remote IO;
- event subscribers that need isolation should use a dedicated executor;
- derived indexes must be rebuildable from authoritative state and events;
- trace plugins may observe events, but must not own the primary business
  decision.

When a domain requires cross-node event visibility, it must explicitly route the
state change through persistence, AP consistency, CP consistency, or an
[internal cluster request](foundation-internal-rpc-spec.md).

Detailed `NotifyCenter`, publisher, subscriber, slow-event, custom publisher,
and local event semantics are defined by the
[Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

## 11. Observability Hooks

Observability hooks expose runtime facts for metrics, trace, audit logs, health,
server state, queue status, worker status, and diagnostic APIs.

Observability rules:

- metrics and logs may be delayed, sampled, reset, dropped, or incomplete;
- stable metrics must use bounded names and low-cardinality tags;
- high-cardinality observations should use TopN, sampling, or explicit
  diagnostics APIs;
- trace and audit payloads must not contain secrets or full opaque Config
  content;
- liveness/readiness and server state are operational views and must not replace
  domain validation or authorization;
- plugin-provided observability must fail open for core data changes unless a
  governance spec explicitly defines a blocking policy.

Detailed metric registry, trace, audit, health, server state, diagnostics, and
external scrape rules are defined by the
[Observability Hooks Spec](foundation-observability-hooks-spec.md).

## 12. Foundation Boundary Rules

- Foundation capabilities do not own Config `dataId`, Naming `serviceName`, AI
  resource names, or auth permission meaning.
- Domain specs must choose and constrain foundation behavior instead of
  inheriting all implementation details.
- Internal requests, events, tasks, and protocol messages are not public API
  contracts unless an interface spec explicitly exposes them.
- Compatibility behavior should be recorded at the domain level when it affects
  user-visible data or API behavior.
- Security, visibility, trace, and control remain cross-cutting concerns and
  must follow their own specs even when they hook into foundation paths.

## 13. Related Specs

- [Nacos Core Capabilities Spec](core-capabilities-spec.md)
- [Resource Model Spec](resource-model-spec.md)
- [Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md)
- [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Observability Hooks Spec](foundation-observability-hooks-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
- [Plugin Spec](../plugin/plugin-spec.md)
- [Addressing Plugin Spec](../plugin/addressing-plugin-spec.md)
- [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
