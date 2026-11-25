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
| Embedded storage | Derby through `LocalDataSourceServiceImpl`, or H2 when `h2` is selected in standalone mode; in cluster mode writes are ordered through the CP protocol. | Standalone Derby or standalone H2, or the `nacos_config` CP group plus local Derby state machine. |
| External storage | Hikari-backed JDBC datasource through `ExternalDataSourceServiceImpl`. | The configured external database selected by datasource properties and dialect rules. |

Selection rules:

- standalone mode uses embedded storage by default;
- cluster mode uses external storage by default;
- a configured non-empty datasource platform other than the embedded engines
  `derby` and `h2` selects external storage;
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
- local H2 initializes from `h2-schema.sql` when `h2` is selected; H2 is standalone only;
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

### 3.1 Embedded Derby Runtime Contract

The embedded storage runtime uses Derby 10.16.1.1 on the server's Java 17
baseline. Its split artifacts have the following responsibilities:

| Artifact | Responsibility in Nacos |
| --- | --- |
| `org.apache.derby:derby` | Embedded database engine, including `org.apache.derby.iapi.jdbc.AutoloadedDriver`. |
| `org.apache.derby:derbyshared` | Shared runtime classes, including `org.apache.derby.shared.common.stream.InfoStreams`. |
| `org.apache.derby:derbytools` | Provides `org.apache.derby.jdbc.EmbeddedDriver`, which Nacos explicitly loads. |

Runtime assembly rules:

- all three artifacts must use the root `derby.version`; the runtime must not
  mix Derby versions;
- the server distribution must include all three artifacts under `plugins/`;
  bundling only the engine JAR is insufficient for Nacos embedded storage;
- modules that directly reference `EmbeddedDriver` must make `derbytools`
  available in the corresponding compilation or test scope; a `provided`
  dependency still requires the distribution to supply it at runtime;
- GraalVM runtime hints, reflection metadata, and runtime initialization
  configuration must use the class locations in the selected Derby version.
  Internal Derby class names used by native-image integration are implementation
  details, not Nacos public APIs.

Embedded storage selection, database location, and logical schema follow the
datasource and domain contracts. Normal startup uses
`jdbc:derby:...;create=true` without `upgrade=true`. Database schema migrations
follow the corresponding domain specs.

See the [Derby 10.16.1.1 release notes](https://db.apache.org/derby/releases/release-10_16_1_1.cgi)
and [Derby module overview](https://db.apache.org/derby/docs/10.16/publishedapi/)
for upstream runtime requirements and artifact responsibilities.

### 3.2 Embedded H2 Runtime Contract

`h2` is an opt-in embedded engine for standalone servers. Only
`nacos.plugin.datasource-dialect.type=h2` selects it. Standalone and
`embeddedStorage=true` cluster deployments keep Derby as their default embedded
engine.

| Rule | Requirement |
| --- | --- |
| Artifact | `com.h2database:h2` 2.2.224, managed by the root POM through `h2.version`. The default plugin bundle does not include it. An H2 deployment places it under `plugins/` together with `nacos-datasource-plugin-h2`. |
| Selector | `nacos.plugin.datasource-dialect.type=h2` (or the legacy `spring.sql.init.platform=h2`), in lower case. The dialect and mapper lookups match the name exactly, so a server process that selects `H2` fails at startup, and the message names the property. |
| Mode | Standalone only. A server process that selects `h2` in cluster mode fails at startup and names the selector property. Cluster embedded storage keeps Derby. The startup checks run only in processes that host server persistence. A console-only process (`nacos.deployment.type=console`) reads configuration through a remote server, does not run these checks, and starts without the H2 driver. |
| Location | `${nacos.home}/data/h2-data/nacos.mv.db`, opened as `jdbc:h2:file:<dir>/nacos;WRITE_DELAY=0;TRACE_LEVEL_FILE=0`. |
| Durability | `WRITE_DELAY=0` is required, because H2's default write delay of 500 ms lets a committed change wait before it is flushed. A standalone server killed with SIGKILL right after a publish returned kept that publish after restart. Power-loss durability is not covered by these checks. |
| Shutdown | H2 closes the database in its own JVM shutdown hook (`DB_CLOSE_ON_EXIT`, left at its default), which runs while the server stops. A request or a background task that is in flight at that moment can fail with `Database is already closed`, and the server logs the failed statement. The client of a failed request gets an error and can send it again. Writes that the server acknowledged before the stop are kept. |
| Schema | `h2-schema.sql` from the H2 dialect plugin. Every statement is idempotent (`IF NOT EXISTS`), so the schema runs on each start without changing existing rows. A statement that fails stops initialization, and the error names the statement. A failed initialization is not kept: the connection pool that it opened is closed, and the embedded datasource is cached only after it initialized. A caller that catches the error and asks for the datasource again triggers a new initialization, which fails the same way while the schema is invalid. Derby, which re-runs its schema against existing objects, still logs and skips failing statements. |
| Derby-only operations | Derby ops query and import check that the active datasource is the embedded Derby engine. The CP snapshot reopen path (`cleanAndReopenDerby`, `restoreDerby`) requires Derby. Both reject `h2`. |
| Migration | No automatic Derby-to-H2 migration. A server that switches engines starts from a new H2 database that holds only the schema and the server's own startup records. Derby data is left untouched. |
| Backup | Stop the server, then copy `data/h2-data` as one directory. |
| Native Image | Not covered by this contract. The Native Image does not carry H2. |

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

Distributed embedded read requests must validate their declared result target
before JDBC execution. Mapper-based queries may use only row mapper class names
registered with the persistence row mapper registry. Scalar queries may use only
explicitly allowlisted basic result types, resolved from fixed class constants
instead of request-driven class loading. A missing, unknown, or target
incompatible with the query type must fail the read request.

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
- an unsafe derived identity must fail closed with a bounded, control-character-
  safe diagnostic. Processing one rejected identity must not prevent unrelated
  dump identities from running, and existing task retry or startup-failure
  semantics must remain unchanged;
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
