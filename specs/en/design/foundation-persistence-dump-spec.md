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

# Nacos Persistence And Dump Spec

This document defines the persistence, dump, local serving cache, and storage
mode foundation used by Nacos domains. It expands the persistence and dump part
of the [Foundation Capabilities Spec](foundation-capabilities-spec.md).

## 1. Positioning

Persistence and dump are foundation capabilities, not domain resources.

They provide:

- durable storage selection and lifecycle;
- JDBC datasource initialization, health, and transaction access;
- interaction abstractions for the persistence layer, including database
  operation, row mapping, transaction, and repository support;
- embedded storage replication through CP when enabled;
- local dump files and JVM serving cache for high-frequency reads;
- startup and repair flows that rebuild local serving state from durable state.

Domain specs own logical schema, resource identity, validation, authorization,
and user-visible behavior. The persistence foundation owns storage plumbing and
must not redefine Config, Naming, AI, or auth resource semantics.

Persisted data is usually domain semantic data. Therefore, concrete repository
interfaces and implementations may live in domain modules rather than in the
`persistence` module. For example, Config owns `ConfigInfoPersistService` and
its embedded and external implementations because those operations understand
Config records, history, gray release fields, capacity metadata, and Config
visibility rules.

## 2. Storage Modes

Nacos supports two storage mode families:

| Mode | Current implementation | Source of durable truth |
| --- | --- | --- |
| Embedded storage | Derby through `LocalDataSourceServiceImpl`; in cluster mode writes are ordered through the CP protocol. | Standalone Derby, or the `nacos_config` CP group plus local Derby state machine. |
| External storage | Hikari-backed JDBC datasource through `ExternalDataSourceServiceImpl`. | The configured external database selected by datasource properties and dialect rules. |

Selection rules:

- standalone mode uses embedded storage by default;
- cluster mode uses external storage by default;
- a configured non-empty datasource platform other than `derby` selects external
  storage;
- cluster mode can enable embedded distributed storage through the embedded
  storage property;
- once selected, `DynamicDataSource` must return the matching `DataSourceService`
  and initialize it once for the process.

Storage mode is an operational deployment choice. Domain specs must not expose
different resource identities just because the durable storage mode changes.

## 3. Datasource Contract

`DataSourceService` provides the storage runtime contract:

- `JdbcTemplate` for SQL execution;
- `TransactionTemplate` for transactional writes;
- datasource type and current JDBC URL for diagnostics;
- health state;
- master writable checks for external storage.

External storage rules:

- datasource properties build one or more Hikari datasources;
- when multiple datasources exist, master selection uses a write probe and
  updates the active `JdbcTemplate` and transaction manager;
- each datasource must be health checked periodically;
- external storage health can be `UP`, `WARN`, or `DOWN` depending on master and
  slave availability;
- datasource reload must replace old pools and detach old templates safely.

Embedded storage rules:

- local Derby initializes from `derby-schema.sql`;
- standalone embedded storage executes directly against local Derby;
- distributed embedded storage cleans and reopens local Derby on startup because
  the effective state must come from CP log replay and snapshot recovery;
- Derby health must become `DOWN` when the embedded CP state machine reports an
  unrecoverable apply error.

Datasource dialect plugins sit below repositories and above physical database
families. They may adapt SQL, mapper, pagination, generated-key, and function
behavior for a database platform. They must not change logical data meaning,
schema ownership, authorization, or resource identity. See the
[Datasource Dialect Plugin Spec](../plugin/datasource-dialect-plugin-spec.md).

## 4. Repository Contract

Repository services define domain storage semantics above datasource details.
They are the boundary where domain semantics meet persistence-layer
abstractions.

Rules:

- the `persistence` module may provide common primitives such as database
  operation, row mapper registration, storage constants, and async execution;
- domain modules may define concrete repository interfaces and implementations
  when stored data has domain meaning, such as Config's
  `ConfigInfoPersistService`;
- repository interfaces own logical operations such as add, update, remove,
  query, history, capacity, or metadata persistence;
- repository implementations may differ between embedded and external storage,
  but must preserve the same domain semantics;
- repository implementations may use datasource dialect mappers to build
  database-specific SQL, but must keep validation, resource identity, history,
  trace, and authorization semantics in the domain repository layer;
- controllers, request handlers, and domain services should depend on domain
  repository interfaces rather than direct mapper or dialect SPI calls;
- domain writes that are user-visible must record the required history, trace,
  capacity, and change metadata in the same domain operation when the domain
  spec requires it;
- broad listing and search operations must be paged or otherwise bounded;
- repository code must not use raw SQL fragments from public API input without
  validation and dialect-controlled construction;
- datasource dialect plugins and mappers must not become an alternate place to
  define domain behavior or compatibility policy;
- schema fields kept only for compatibility must not become new semantic
  contracts unless a domain spec explicitly promotes them.

For Config, repository interfaces are defined by the
[Config Persistence, Dump, And History Spec](../config/config-persistence-history-spec.md).

## 5. Distributed Embedded Storage

Distributed embedded storage uses the CP foundation for durable write ordering.
The current group for Config embedded storage is `nacos_config`.

The model is:

```text
Repository operation
  -> ModifyRequest list
  -> DatabaseOperate.update / blockUpdate
  -> CP WriteRequest(group = nacos_config)
  -> JRaft commit
  -> DistributedDatabaseOperateImpl.onApply
  -> local Derby transaction
  -> embedded apply hooks
```

Rules:

- distributed embedded writes must go through CP commit before local Derby apply
  is treated as durable;
- `ModifyRequest` execution must be deterministic after sorting by execute
  number when the implementation requires ordering;
- SQL limiters must reject unsupported query or modify forms before executing
  them;
- apply hooks must run after committed apply and must not block the state machine
  with slow work;
- `BadSqlGrammarException` and `DataIntegrityViolationException` are returned as
  operation failures instead of stopping the Raft state machine;
- serious datasource access failures may surface as consistency errors and must
  update observable storage health.

Reads use the CP read path when distributed embedded storage is active. Startup
dump may set a blocking read context so the node waits until readable data
exists before rebuilding serving cache.

Snapshot rules are defined by the
[CP Consistency Spec](foundation-cp-consistency-spec.md). Embedded storage must
provide enough snapshot data to rebuild local Derby state after restart or
membership changes.

## 6. Dump And Local Serving Cache

Dump is a local serving-state rebuild and refresh mechanism. It is not a second
durable source of truth.

The Config dump model is:

```text
Durable write or cluster change notification
  -> ConfigDataChangeEvent or ConfigChangeClusterSyncRequest
  -> DumpRequest
  -> DumpTask keyed by groupKey or groupKey + "+gray+" + grayName
  -> repository reload
  -> ConfigDumpEvent
  -> ConfigCacheService
  -> local disk dump and JVM cache
  -> LocalDataChangeEvent
  -> listener/watch push
```

Startup dump rules:

- startup must clear old formal and gray dump files before rebuilding local dump
  data from persistence;
- startup must dump formal and gray records into local disk and JVM cache;
- embedded cluster startup must wait for the CP group to have readable data
  before startup dump completes;
- external storage startup may dump directly from the configured datasource;
- startup dump failure is a fatal server startup failure.

Runtime dump rules:

- after a successful durable write, the domain must schedule dump refresh for the
  affected resource;
- cluster change notifications may ask peer nodes to reload local dump from
  persistence; the notification payload is not authoritative content;
- full dump tasks are repair and drift-control mechanisms;
- change dump tasks should be keyed so repeated changes to the same resource can
  be merged or replaced according to the
  [task execution](foundation-task-execution-spec.md) rules;
- dump apply must publish local change events only after local serving state has
  been updated, following the
  [event dispatch](foundation-event-dispatch-spec.md) rules.

For the current Config implementation, runtime client reads are served from local
cache and dump files. The persistence layer remains the durable source of truth.

## 7. Local Dump Storage

Local dump storage is selected by `ConfigDiskServiceFactory`.

Rules:

- `rawdisk` is the default `config_disk_type`; unknown values also fall back to
  raw disk behavior;
- `rocksdb` may be selected as an alternative local dump backend;
- local dump storage must implement save, remove, read, clear formal records,
  and clear gray records;
- raw disk storage keeps formal and gray content under the Nacos home data
  directories, separated by namespace-aware and no-namespace paths;
- dump storage keys must be derived from validated and encoded Config identity
  fields;
- changing the local dump backend must not change Config resource identity,
  md5 semantics, authorization, or the durable persistence source.

Local dump storage is part of the serving cache path. It is not the repository
contract and must not be used as an independent durable database.

## 8. Cache Update Rules

Local serving cache must preserve monotonic visibility for each resource key.

Rules:

- a dump with an older `lastModified` timestamp must be ignored;
- if content md5 changes, dump must update both local disk content and JVM cache
  md5 state;
- if md5 is unchanged but timestamp is newer, dump may update timestamp without
  rewriting content;
- remove dump must delete local disk data and remove JVM cache state;
- gray dump must also update gray rule, encrypted data key, and gray ordering
  when those fields change;
- local read and write locks must protect cache and dump mutation for the same
  resource key;
- `LocalDataChangeEvent` is a local visibility event and must not be treated as a
  cross-node replication guarantee; see the
  [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

If local disk is full or cannot safely store formal dump content, the server must
treat the condition as fatal because the runtime query path depends on local
serving state.

## 9. Maintenance And Cleanup

Persistence and dump maintenance operations are administrative capabilities.

Rules:

- history cleanup must run only on the storage-mode owner node:
  - embedded standalone: the only node;
  - embedded cluster: the CP leader for the Config storage group;
  - external storage: the first Nacos member selected by member order;
- local-cache full dump is a repair operation and must not replace the normal
  publish or delete path;
- Derby import and query operations are maintainer-only operations and must be
  guarded by explicit ops settings and permissions;
- large import and dump operations must be batched and bounded;
- maintenance APIs must report failure instead of silently treating partial
  storage repair as success.

## 10. Boundary Rules

- Persistence is durable storage plumbing; domain specs define resource meaning.
- Dump files and JVM cache are derived serving state unless a domain spec states
  otherwise.
- Embedded distributed storage durability comes from CP commit and snapshot
  recovery, not from local Derby writes alone.
- External storage durability comes from the configured external database, not
  from Nacos cluster replication.
- Datasource dialect plugins adapt SQL, not domain semantics.
- Local events, dump tasks, and cache updates are implementation internals unless
  an interface spec exposes them explicitly.
- Schema compatibility fields must be documented as compatibility or pending
  removal when they no longer represent domain semantics.

## 11. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Config Persistence, Dump, And History Spec](../config/config-persistence-history-spec.md)
- [Config Listener And Watch Spec](../config/config-listener-watch-spec.md)
- [Datasource Dialect Plugin Spec](../plugin/datasource-dialect-plugin-spec.md)
- [Config Encryption Plugin Spec](../plugin/config-encryption-plugin-spec.md)
