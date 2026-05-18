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

# Core Operations Spec

This document defines the Nacos Core Operations domain. Core Operations owns
server control-plane resources and operational actions that are shared by
Config, Naming, AI Registry, plugins, [Console](../console/console-spec.md),
and maintainer tooling.

## 1. Scope

Core Operations owns:

- namespace metadata and namespace lifecycle;
- cluster member views, member metadata updates, and member lookup mode control;
- server state, liveness, readiness, and module state aggregation;
- server loader metrics and connection rebalance operations;
- plugin inventory, plugin enable state, plugin configuration state, and
  cluster synchronization of plugin state;
- high-risk server operations such as CP/Raft maintenance commands, ID
  generator diagnostics, and runtime log-level changes.

Core Operations does not own:

- Config, Naming, or AI Registry resource semantics inside a namespace;
- plugin extension contracts, which are defined by the
  [Plugin Spec](../plugin/plugin-spec.md) and per-plugin specs;
- low-level foundation protocols such as member discovery, internal RPC,
  CP/AP consistency, persistence, request filtering, or event dispatch. Those
  capabilities are defined by the foundation specs and used by Core Operations.

Core Operations is administrative by nature. It should be exposed through Admin
API, Console API, or Maintainer SDK surfaces, not through runtime Client SDK
surfaces.

## 2. Namespace Operations

Namespace is the top-level isolation boundary for Nacos domain resources. Core
Operations owns namespace metadata:

```text
namespaceId -> namespaceName, namespaceDesc, namespaceType
```

Rules:

- the default namespace is a global namespace and always exists logically;
- custom namespaces are persisted as tenant metadata;
- creating a namespace allocates or validates `namespaceId`, stores display
  metadata, and makes the namespace available for domain-level resources;
- updating a namespace changes namespace display metadata only;
- deleting a namespace removes namespace metadata. It must not be treated as a
  guaranteed cascade delete for Config, Naming, AI Registry, auth, or plugin
  domain data unless the owning domain defines such behavior explicitly;
- namespace existence validation used by request filters is a read-side guard
  and must not create or mutate namespace metadata;
- namespace detail injection may enrich the returned namespace view, but must
  not change canonical namespace identity.

Namespace validation, request filtering, and runtime context behavior are
defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).

## 3. Cluster Member Operations

Cluster member operations expose and update the server member view owned by the
cluster membership foundation. Core Operations owns the administrative surface
over this view, not the membership protocol itself.

Rules:

- the self member is the current server identity and must be derived from the
  local `ServerMemberManager`;
- member listing is an operational view and may be filtered by address prefix
  or node state;
- member update accepts valid member records, marks them usable for the local
  view, resets failure counters, and ignores invalid member records;
- member lookup mode changes alter how cluster members are discovered and must
  be handled as cluster control operations;
- member operations must not be used as a replacement for CP group membership
  changes or domain data migration.

The detailed membership lifecycle is defined by the
[Cluster Membership Spec](../design/foundation-cluster-membership-spec.md).

## 4. Server State And Health

Server state is an operational diagnostic view. It is built from registered
module state builders and contains key-value runtime state suitable for
operators and console UI.

Rules:

- module state is derived runtime state and must not be treated as durable
  domain data;
- module state must not expose secrets, full user payloads, or high-cardinality
  runtime data;
- liveness answers whether the current process is alive enough for process
  management. It is not a domain health assertion;
- readiness answers whether registered modules can receive traffic. It is
  composed from module health checkers and should fail when any required module
  reports not ready;
- health probe endpoints may be intentionally exposed for infrastructure
  probes. Any public health surface must stay minimal and avoid sensitive state;
- detailed server state is for diagnostics and console/maintainer operations.

Server lifecycle, module state, and readiness foundations are defined by the
[Server Lifecycle And Environment Configuration Spec](../design/foundation-server-lifecycle-env-spec.md)
and the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).

## 5. Server Loader And Connection Rebalance

Server loader operations manage runtime gRPC SDK connections. They are
operational controls for connection distribution and should not be interpreted
as Naming, Config, or AI resource ownership changes.

Rules:

- the current-client view is derived from the local connection manager;
- cluster loader metrics are best-effort aggregated from member responses and
  may be incomplete when a member does not respond in time;
- reloading a single connection asks the client connection to reconnect,
  optionally toward a redirect address;
- reloading by count keeps at most the requested number of local connections and
  redirects extra connections;
- smart reload compares cluster connection counts and redirects connections
  from overloaded members to underloaded members;
- connection rebalance must not drop domain data. Reconnected clients are
  responsible for rebuilding runtime subscriptions, watches, or registrations
  through their own domain protocols;
- loader controls are write operations and require administrative permission.

Remote connection lifecycle rules are defined by the
[Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md).

## 6. Plugin State Operations

Core Operations owns the operational state of discovered plugins. Plugin
contracts remain in the extension model, but enable state and mutable plugin
configuration are control-plane state.

Rules:

- plugins are discovered from `PluginProvider` implementations and identified
  by `pluginType:pluginName`;
- exclusive plugin types may enable only the configured implementation by
  default. Auth and datasource dialect plugins are exclusive in current code;
- non-exclusive plugins are enabled by default unless persisted state overrides
  them;
- critical plugins must not be disabled through normal operations;
- only configurable plugins may accept configuration updates, and updates must
  satisfy the plugin's declared config definitions;
- cluster-mode plugin state and config changes must be replicated through the
  CP `plugin_state` group and restored through CP snapshots;
- standalone plugin state and config changes are persisted locally;
- `localOnly` operations are emergency local-node changes. They bypass cluster
  synchronization and must be treated as temporary operational overrides.

Plugin type semantics are defined by the [Plugin Spec](../plugin/plugin-spec.md).
The CP replication boundary is defined by the
[CP Consistency Spec](../design/foundation-cp-consistency-spec.md).

## 7. Core Maintenance Operations

Core maintenance operations cover high-risk controls that affect server runtime
or consistency infrastructure.

Rules:

- CP/Raft maintenance commands are operator-only controls and must be scoped to
  explicit groups and supported commands;
- Raft maintenance changes may affect leadership, snapshots, peers, or group
  recovery and must not be exposed to runtime clients;
- ID generator diagnostics are read-side operational state and do not allocate
  domain IDs by themselves;
- runtime log-level updates are local process controls unless a separate
  synchronization rule is defined;
- maintenance operations should emit logs and should be audited when an audit
  mechanism is available.

CP protocol behavior is defined by the
[CP Consistency Spec](../design/foundation-cp-consistency-spec.md).

## 8. Console And Maintainer Surfaces

Console APIs and Maintainer SDKs may expose Core Operations through UI-friendly
or typed wrappers. These surfaces must preserve the same operation boundaries:

- Console operations may aggregate local and remote server state, but must not
  invent new resource lifecycle semantics;
- console-only content such as announcement or UI guide information is UI
  presentation data, not canonical Core Operations state;
- Maintainer SDK operations should match Admin API semantics and permission
  requirements.

## 9. Security And Compatibility Rules

- Mutating Core Operations require administrative write permission.
- Diagnostic read operations require administrative read permission unless they
  are intentionally minimal public health probes.
- Public health probes must not expose sensitive module state.
- Core Operations must follow the HTTP API response and error rules except when
  a compatibility or health-probe surface explicitly defines another shape.
- Runtime Client SDKs must not expose broad Core Operations.

## 10. Pending Issues

- Namespace id validation is still partly implemented in the controller path and
  should be moved into shared parameter validation.
- Namespace deletion should define explicit compatibility behavior for domains
  that still keep data under a deleted namespace.
- Server loader inputs should define stricter validation for connection count,
  redirect target, and smart reload factor.
- Public versus authenticated server state surfaces should remain documented so
  future module state fields do not leak sensitive data.
- `localOnly` plugin operations need clear audit, visibility, and recovery
  guidance.
- Raft maintenance commands should keep an explicit allowlist and operation
  safety guidance.
