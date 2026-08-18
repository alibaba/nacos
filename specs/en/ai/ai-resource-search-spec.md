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

# AI Resource Search Spec

This document defines the protocol-neutral AI resource search capability owned
by the `ai` module. It indexes canonical Nacos AI resources and exposes an
internal application service that protocol adaptors may use for search,
deterministic listing, pagination, and aggregation.

## 1. Scope And Activation

AI Resource Search is a reusable, protocol-neutral logical capability in the
`ai` module. RAD, ARD, generic AI Resource Search, and resource-specific Search
must share this capability rather than maintain separate indexes or search
engines. Their HTTP, Java SDK, Console, and external-protocol responses are
defined by the corresponding API or adaptor specifications.

The base relational search runtime is activated independently by
`nacos.ai.resource.search.enabled`, whose default is `true`.
`nacos.ai.ard.enabled` controls only the ARD Web Context and protocol endpoints;
it must not disable the search runtime required by RAD or other resource APIs.
The combination `nacos.ai.ard.enabled=true` and
`nacos.ai.resource.search.enabled=false` is invalid; server startup must fail
explicitly during configuration validation. It must not create a hidden
ARD-specific index. The search implementation must
not depend on ARD request, response, identifier, federation, media-type, or
artifact semantics.

## 2. Ownership Boundary

The AI module owns:

- canonical search document, chunk, and facet projections;
- the resource-type handler registry, Query Planner, and multi-channel result
  fusion;
- durable index tasks and reconciliation;
- keyword and optional vector recall;
- ranking and deterministic tie-breaking;
- visibility advice and per-resource visibility enforcement;
- latest-label and current online-version validation;
- typed predicates, opaque cursors, numbered pages, and complete-set
  aggregation;
- readiness for each resource projection generation.

Protocol adaptors or resource-specific API facades own request parsing,
protocol validation, type-specific filter translation, response DTOs, and
error mapping. Identifiers, media types, artifact URLs, and federation behavior
belong only to the corresponding protocol adaptor.

## 3. Search Model

The internal query model contains namespace, text, one or more canonical
resource types, typed predicates, time boundaries, sort order, and cursor or
numbered-page information. It must not contain protocol DTOs or
protocol-specific field names.

Canonical predicates support at least:

```text
field
operator = EXACT_ANY | EXACT_ALL | LITERAL_CONTAINS
values[]
caseSensitive
```

The operator defines ANY, ALL, or literal-contains behavior within one
predicate, and multiple predicates are combined with AND.
`metadata.<facetKey>` is a protocol-neutral facet path. Implementations treat
`%`, `_`, and the escape character as literal input and must not let database
LIKE wildcards change literal-contains semantics. A type-specific facade may
fix the resource type, permitted fields, case rules, and field weights, but it
must not change common visibility, enabled, or currentness rules. Existing
protocol filters may retain compatibility entry points, but are converted to
canonical predicates before entering Search Core.

Search results are application DTOs rather than persistence entities. A result
may expose the canonical resource key, current version, display and search
metadata, timestamps, and relevance score. Database row identifiers, index
status, and task state remain internal to the search implementation.

Visibility, enabled, and current-version validation occur before totals,
offsets, and page limits are applied. Cursors use stable resource anchors
rather than mutable list offsets. A numbered page returns `totalCount`,
`pageNumber`, `pagesAvailable`, and current-page items. An implementation may
stream past the offset, but must not truncate candidates before calculating the
total.

Generic Search provides unified recall and ranking across resource types.
Resource-specific Search fixes one resource type in the Query Planner and may
apply type-specific predicates and weights. When generic Search specifies only
one resource type, its eligibility, visibility, and currentness results match
the corresponding resource-specific Search. Cross-type Search must not be
implemented by concatenating already-paginated resource-specific results.

## 4. Aggregation

Aggregation runs over the complete eligible matched set, not one result page.
The service returns canonical bucket values, counts, the count outside the
requested bucket limit, and the total matched count when required by a
consumer.

Protocol-derived values are mapped by the adaptor. For example, canonical
resource-type buckets may be translated to ARD media types without moving ARD
media-type knowledge into the AI module.

## 5. Index And Schema

The relational search index uses protocol-neutral objects:

- `ai_resource_search_document`;
- `ai_resource_search_chunk`;
- `ai_resource_task`.

These relational objects are available in every supported main datasource.
`ai_resource_task` is reusable for durable asynchronous work within the AI
resource domain; it is not a global Nacos workflow engine. Each task type owns
the schema of its versioned JSON input and result. The logical JSON values are
stored as text or CLOB rather than datasource-specific native JSON types.
The optional PostgreSQL vector implementation owns its separate pgvector
schema and `ai_resource_search_embedding_pg` table. Main datasource schemas
must not create the pgvector extension or an embedding table.

Canonical resource writes remain authoritative. Search documents and chunks
are derived state and may be rebuilt.

A logical `IndexProjection` consists of one document, zero or more chunks, and
a facet set:

- the document stores resource identity, display information, state, current
  version, source digest, and stable ordering fields;
- facets store exact-filter properties. The first generation may keep generic
  key/value or array facets in document metadata without immediately adding a
  physical table;
- chunks contain only text used for keyword or vector recall. Facets do not
  become standalone chunks and are not embedded;
- structured document/facet and keyword indexes are mandatory for base Search,
  while the vector index is an optional recall channel; and
- a resource such as Agent populates only facets it owns and must not require
  Skill, Prompt, MCP, or AgentSpec to add Agent-specific columns.

One Query Planner/Fusion coordinates candidate generation, structured
filtering, de-duplication, score fusion, visibility, currentness, and pagination
across all recall channels. When a vector provider cannot push down facets,
the planner uses a bounded candidate strategy whose completeness can be
demonstrated. It must not apply one post-filter to a fixed top-K result and
claim complete totals or pages.

### 5.1 Resource-Type Handlers

Every resource type declared searchable registers a protocol-neutral type
handler with at least these semantics:

```text
resourceType()
project(namespaceId, resourceName) -> Optional<IndexProjection>
scan(namespaceId, cursor, batchSize) -> SourcePage
isCurrent(document) -> boolean
exists(namespaceId, resourceName) -> boolean
```

An empty `project` result means that the resource is absent, undiscoverable, or
has no valid current version, and the index service deletes that logical
resource's derived document. `scan` is used only by Backfill/Reconciliation
and scans by stable resource key in bounded batches. `isCurrent` enforces the
resource's enabled, visibility, current-version, and source-digest rules. A
handler belongs to the `ai` module and must not reference ARD DTOs, URLs, or
media types.

The searchable types declared by this specification are Agent, AgentSpec,
Skill, Prompt, and MCP. If a future AI Resource does not participate in generic
Search, its resource specification states that exclusion explicitly; an
unimplemented handler must not silently omit a declared type.

### 5.2 Agent Projection

At most one enabled document exists per `(namespaceId, agentName)`. Its
`resourceVersion` is the exact online Version referenced by common `latest`.
An Agent document projects at least:

- directory fields such as display name, description, business tags, provider,
  icon, and scope;
- an ordered compact catalog of all online Versions;
- `metadata.protocols`, the ordered de-duplicated union of protocols across all
  online Versions;
- `metadata.artifactKinds`, the set of complete representation keys exportable
  from the exact common-latest Version; and
- `metadata.projectionVersion` plus a `sourceDigest` over stable business facts.

`protocols` describes invocation protocols, while `artifactKinds` describes
complete version artifacts that can be returned; they are not interchangeable.
Agent name, description, tags, capabilities, and examples may produce chunks.
Scope, owner, status, protocols, and artifact kinds remain structured fields.
Runtime Endpoints, health, Publishers, heartbeats, and Runtime revisions never
enter the durable search index.

The Agent source digest is canonical-JSON SHA-256 over Agent metadata that
affects catalog or search projection, the complete version catalog, common
latest, the latest Version `contentDigest`, artifact kinds, and projection
version. A semantically irrelevant modification timestamp does not by itself
change the digest.

Canonical resource identity fields and task-control fields use exact,
case-sensitive comparison consistent with canonical resource storage. Keyword
matching remains case-insensitive through locale-stable query normalization;
it must not depend on a datasource-specific case-insensitive table collation.

## 6. Consistency

Replacing one logical resource's relational document, chunks, and embedded
facets is atomic.
Relational and vector indexes do not require a distributed transaction.
Instead, an idempotent durable `search_index` task re-reads canonical state and
converges both indexes. Its task key is SHA-256 over task type, namespace, and
the logical subject composed of resource type and resource name. Including the
task type permits other AI resource workflows to use the same table without
colliding with search-index tasks.

After a canonical resource lifecycle transaction commits successfully, one
coalesced task is scheduled by `(namespaceId, resourceType, resourceName)`.
Scheduling failure does not roll back the authoritative write; metrics, alerts,
and reconciliation repair it. Agent creation, directory metadata or governance
changes, Version publish/online/offline/delete, common-latest or custom-label
changes, canonical definition changes made through the legacy A2A facade, and
Agent deletion all schedule the task. Endpoint registration, deregistration,
heartbeat, health changes, and Runtime revisions do not schedule a catalog
index task.

The same task row owns two durable stages:

- `base_index` converges deterministic relational chunks and the configured
  vector index;
- `llm_enhancement` replaces optional AI-generated chunks and then converges
  the complete resource-version vector index.

Each stage uses `pending`, `processing`, and `completed` states. Initial and
retryable work both use `pending`; `retry_count`, `next_execute_at`, and
`last_error` distinguish a delayed retry from a new task. Successful rows are
retained as bounded, per-live-resource checkpoints rather than task history.
Resource lifecycle changes increment the task revision and restart the row at
`base_index`. A claimed revision may advance, retry, or complete only while it
still owns the row. Revision represents scheduled task content and is not
incremented by claiming. Each successful claim increments an independent,
monotonic `lease_token`. Lease renewal, state transitions, and superseded-work
release compare this token so an expired worker cannot mutate or release a
newer worker's lease. Lifecycle scheduling preserves an unexpired lease across
any number of coalesced updates; the replacement revision becomes claimable
only after the current token holder releases the lease or the lease expires. A
lease permits another node to resume work after process failure. Returning an
enhancement task to `base_index` is also revision- and token-fenced, so a stale
worker cannot overwrite a newer lifecycle revision or claim. Base and
enhancement stages both renew leases on an executor independent from polling;
enhancement tasks are not claimed beyond configured worker concurrency.

The search-index task input is stored in `task_payload` with a mandatory
`schemaVersion`, a `subject` containing resource type and resource name, and
`options.enhancementRequested`. The payload is replaced atomically when a new
revision is scheduled and remains immutable while that revision executes.
Enhancement completion metadata is stored in versioned `task_result`; the
current result contains the completed enhancement fingerprint. Scheduler
metadata used by polling, claiming, retry, lease recovery, revision fencing,
and lease-token fencing remains in dedicated relational columns.

A malformed or unsupported task payload is quarantined as a completed
checkpoint with its decode error. It must not fail or starve other due tasks;
reconciliation may reopen the row later with a current payload if the
corresponding base index is inconsistent.

Scheduling deadlines use Unix Epoch milliseconds in the `next_execute_at` and
`lease_expire_at` BIGINT columns. Polling, claiming, retry, and lease renewal
must derive their comparisons and deadlines from the same injected application
clock; they must not mix JDBC timestamps produced by the JVM with datasource
`CURRENT_TIMESTAMP`. Nacos cluster nodes are expected to keep their system
clocks synchronized.

Enhancement writes are idempotent. AI-generated chunk types are replaced
transactionally rather than appended, and vector documents are replaced for
the complete resource version. The task is completed only after both writes
succeed. An enhancement configuration fingerprint covers the provider
endpoint, model, prompt revision, output schema revision, and relevant output
limits, but never secrets. The fingerprint records the configuration that
produced a completed enhancement for audit and diagnosis; it is not a
convergence target. Configuration changes do not reschedule completed
resources. A resource lifecycle task records whether enhancement was enabled
when the task was scheduled. Only a task with that durable request may advance
from `base_index` to `llm_enhancement`, and it uses the effective configuration
when the enhancement stage runs.

When enhancement is disabled, a task that is still in `base_index` may complete
without the enhancement stage. A task that has entered `llm_enhancement` must
not complete directly. It creates a new revision with
`options.enhancementRequested=false` and returns to `base_index` so that any
partially written enhancement chunks are removed and the base vector index
converges before the base-index checkpoint completes. Enabling enhancement
later does not reschedule that completed resource. When enhancement is enabled
but incompletely configured, the enhancement stage returns to `pending` with
retry metadata instead of being treated as disabled.

Failures return the current stage to `pending`, increment `retry_count`, and
set `next_execute_at` using exponential backoff. Periodic reconciliation
detects missed, partial, stale, and orphaned base index data. A missing or
inconsistent index is rebuilt through the normal indexing flow and requests
enhancement when enhancement is enabled at repair scheduling time. An
already-consistent index is not repaired merely because the existing resource
lacks an enhancement checkpoint, so enabling enhancement does not trigger a
historical full refresh. Reconciliation preserves the durable enhancement
intent, revision, stage, retry delay, and lease of an active task for the same
resource. Vector reconciliation compares the relational document identity as
well as model and chunk count. Discovery reads only enabled documents whose
configured indexes have converged. Each type handler's reconciliation detects
missing, partial, stale, and orphaned projections. Agent reconciliation also
compares projection version, source digest, common latest, version catalog,
and optional vector state. Deleting the derived document is the correct
converged result for a resource with no online Version or one that is disabled
or deleted; it is not a permanent retry condition.

## 7. Resource Bounds

List, aggregation, reconciliation, and durable-task polling scan relational
state in bounded database batches. Source scans and numbered lists use a stable
resource-key keyset, with an immutable row key breaking ordering ties. After
predicate, visibility, and current-version validation, list pagination retains
only the requested page and one look-ahead result in memory. A numbered page
retains only an additional counter, not all matching items. Reconciliation
does not retain every canonical resource name in memory. Its cluster-wide scan
lease records an owner and expiry, renews through CAS while scanning, and
releases only while the same owner still holds it.

Keyword and vector recall have a configurable per-channel candidate bound.
When a channel exceeds the bound, discovery fails explicitly instead of
returning a silently truncated result set. Operators may tune the bound with
`nacos.ai.resource.search.max-recall-candidates`.

## 8. Readiness And Read Modes

A resource type that moves from a legacy scan path to the index maintains
durable, cluster-shared readiness by `(resourceType, projectionVersion)`. A
Backfill scan lease identifies only the current scan owner and does not replace
readiness.

A projection generation is marked `READY` through CAS only after all of these
conditions hold:

1. every valid namespace was enumerated successfully, without using a `public`
   fallback to hide enumeration failure;
2. one bounded source scan for the resource type completed and every detected
   difference was scheduled successfully;
3. a subsequent verification pass found no unrepaired missing, stale, or
   orphaned document;
4. no pending, processing, or retry task from that pass remains; and
5. the readiness record names the current projection version.

`READY` is sticky for one generation. Ordinary lifecycle work that is briefly
pending does not return that generation to unready; query currentness checks
exclude stale documents until the task converges. A projection contract change
increments the projection version and creates a new readiness generation.

RAD Search selects its read path with
`nacos.ai.rad.search.mode=AUTO|INDEX|SCAN`, defaulting to `AUTO`:

| Mode | Not READY | READY | Index-call failure |
|---|---|---|---|
| `AUTO` | Use the complete legacy scan path | Switch stickily in-process to the index | Fail explicitly; no per-request fallback |
| `INDEX` | Return service unavailable explicitly | Use the index | Fail explicitly |
| `SCAN` | Use legacy scan | Always use legacy scan | Not applicable |

One request never combines partial index and partial legacy-scan results. The
switch must not change RAD name, tag, protocol, case, ordering, total,
pagination, visibility, or version-catalog contracts. Another consumer that
needs a compatibility read mode defines it in its protocol or API spec but
still reuses this readiness.

## 9. Upgrade And Initialization

Deployments that created the ARD search schema before durable retry was added
must initialize all three protocol-neutral relational tables before enabling
`nacos.ai.resource.search.enabled`:

- `ai_resource_search_document`;
- `ai_resource_search_chunk`;
- `ai_resource_task`.

Use the matching current datasource schema for MySQL, PostgreSQL, Derby, or
Oracle. The task table is required even when existing document and chunk
tables already contain data. It stores the task type, versioned payload and
result, stage, retry, lease, revision, and completion checkpoint and does not
replace either index table.

Deployments that already created `ai_resource_search_index_task` must migrate
to `ai_resource_task` before enabling search. Existing `resource_type`,
`resource_name`, and `enhancement_requested` values move into the version-1
`task_payload`; `enhancement_fingerprint` moves into version-1 `task_result`;
`attempt_count` becomes `retry_count`; and `next_retry_time` becomes
the Unix Epoch millisecond `next_execute_at`. Existing `retry` rows become
`pending`. An intermediate `ai_resource_task` schema that used
`next_execute_time` and `lease_until` timestamps must convert them to
`next_execute_at` and `lease_expire_at` Epoch milliseconds. Existing
`ai_resource_task` tables must also add the non-null `lease_token` BIGINT column
with default `0`. Migrated rows use `task_type=search_index`, and their task
keys are regenerated with the task type included. Existing task intent must be
retained during a live upgrade.
For an unreleased development deployment where task state may be discarded,
the old task table may instead be dropped and the current schema recreated;
reconciliation then repairs inconsistent base indexes.

Enabling enhancement does not modify consistent historical search data.
Periodic reconciliation enhances a historical resource only when its base or
configured vector index actually requires repair. Enhancing
otherwise-consistent historical resources is a separate explicit operator
action.

PostgreSQL deployments that do not enable the default vector plugin need no
pgvector objects. Deployments that enable it must separately run the optional
schema owned by `nacos-default-ai-vector-plugin`.

Before `AUTO` or `INDEX` uses a newly added resource type, that type completes
Backfill and readiness for its projection generation. The index remains
rebuildable derived state; it does not change the authoritative canonical
resource or Runtime Endpoint stores and does not migrate Runtime state into the
relational search tables.

## 10. Compatibility And Tests

Internal search names, persistence models, tables, configuration, and vector
SPI packages remain protocol-neutral. Existing ARD Skill, Prompt, and MCP
request, cursor, ordering, and artifact behavior remains compatible while the
shared core is extended.

Tests cover keyword and vector recall, structured facets, typed predicates,
ranking, visibility, current-version validation, cursor and numbered pages,
full-set aggregation beyond one page, equivalence between generic single-type
and resource-specific Search, transactional replacement, both durable task
stages, lease recovery,
superseded revisions, versioned task payload and result, task-type isolation,
timezone-independent Epoch scheduling, deterministic-clock lease and retry
boundaries, consecutive lifecycle coalescing that preserves an active lease,
stale-worker release fencing by lease token, idempotent enhancement retry,
configuration-fingerprint recording without global rescheduling, lifecycle
enhancement intent, repair-triggered reconciliation enhancement without
historical full refresh, Agent lifecycle scheduling versus Runtime Endpoint
non-scheduling, readiness CAS/restart/new generations, and the
`AUTO/INDEX/SCAN` matrix. Protocol adaptors and resource APIs separately test
their request grammar, response conformance, and single-type cross-results.
