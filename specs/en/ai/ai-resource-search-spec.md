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

AI Resource Search is an internal reusable capability. It does not define a
Nacos HTTP API, Java SDK API, Console API, or external protocol response.

The ARD adaptor is the only consumer in this version. Consequently,
`nacos.ai.ard.enabled` activates both the ARD surface and its required search
runtime. There is no separate operator-facing search switch until another
consumer exists. The search implementation must not otherwise depend on ARD
request, response, identifier, federation, media-type, or artifact semantics.

## 2. Ownership Boundary

The AI module owns:

- canonical search documents and chunks;
- durable index tasks and reconciliation;
- keyword and optional vector recall;
- ranking and deterministic tie-breaking;
- visibility advice and per-resource visibility enforcement;
- latest-label and current online-version validation;
- canonical filters, opaque cursor pagination, and complete-set aggregation.

Protocol adaptors own request parsing, protocol validation, protocol-specific
filter translation, identifiers, media types, response DTOs, error bodies,
artifact URLs, and federation behavior.

## 3. Search Model

The internal query model contains namespace, text, canonical resource types,
canonical filters, time boundaries, sort order, page size, and opaque cursor.
It must not contain protocol DTOs or protocol-specific field names.

Search results are application DTOs rather than persistence entities. A result
may expose the canonical resource key, current version, display and search
metadata, timestamps, and relevance score. Database row identifiers, index
status, and task state remain internal to the search implementation.

Visibility and current-version validation occur before page limits are
applied. Cursors use stable resource anchors rather than mutable list offsets.

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
- `ai_resource_search_index_task`.

These relational objects are available in every supported main datasource.
The optional PostgreSQL vector implementation owns its separate pgvector
schema and `ai_resource_search_embedding_pg` table. Main datasource schemas
must not create the pgvector extension or an embedding table.

Canonical resource writes remain authoritative. Search documents and chunks
are derived state and may be rebuilt.

## 6. Consistency

Replacing one resource version's relational document and chunks is atomic.
Relational and vector indexes do not require a distributed transaction.
Instead, an idempotent durable task keyed by namespace, resource type, and
resource name re-reads canonical state and converges both indexes.

The same task row owns two durable stages:

- `base_index` converges deterministic relational chunks and the configured
  vector index;
- `llm_enhancement` replaces optional AI-generated chunks and then converges
  the complete resource-version vector index.

Each stage uses `pending`, `processing`, `retry`, and `completed` states.
Successful rows are retained as bounded, per-live-resource checkpoints rather
than task history. Resource lifecycle changes increment the task revision and
restart the row at `base_index`. A claimed revision may advance, retry, or
complete only while it still owns the row. A lease permits another node to
resume work after process failure.

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

When enhancement is disabled, base indexing may complete without the
enhancement stage. When it is enabled but incompletely configured, the
enhancement stage retains retry state instead of being treated as disabled.

Failures retain retry state. Periodic reconciliation detects missed, partial,
stale, and orphaned base index data. A missing or inconsistent index is rebuilt
through the normal indexing flow and requests enhancement when enhancement is
enabled at repair scheduling time. An already-consistent index is not repaired
merely because the existing resource lacks an enhancement checkpoint, so
enabling enhancement does not trigger a historical full refresh. Reconciliation
preserves the durable enhancement intent of an active task for the same resource.
Discovery reads only enabled documents whose configured indexes have converged.

## 7. Resource Bounds

List, aggregation, reconciliation, and durable-task polling scan relational
state in bounded database batches. List pagination retains only the requested
page and one look-ahead result in memory after visibility and current-version
validation.

Keyword and vector recall have a configurable per-channel candidate bound.
When a channel exceeds the bound, discovery fails explicitly instead of
returning a silently truncated result set. Operators may tune the bound with
`nacos.ai.resource.search.max-recall-candidates`.

## 8. Upgrade And Initialization

Deployments that created the ARD search schema before durable retry was added
must initialize all three protocol-neutral relational tables before enabling
`nacos.ai.ard.enabled`:

- `ai_resource_search_document`;
- `ai_resource_search_chunk`;
- `ai_resource_search_index_task`.

Use the matching current datasource schema for MySQL, PostgreSQL, Derby, or
Oracle. The task table is required even when existing document and chunk
tables already contain data. It stores stage, retry, lease, revision,
completion-checkpoint, and enhancement-fingerprint state and does not replace
either index table. Deployments upgrading an existing task table must add
`task_stage`, `enhancement_requested`, and `enhancement_fingerprint` before
enabling search. Existing task rows default to the `base_index` stage with
`enhancement_requested=false`; enabling enhancement therefore does not modify
consistent historical search data. Periodic reconciliation enhances a
historical resource only when its base or configured vector index actually
requires repair. Enhancing otherwise-consistent historical resources is a
separate explicit operator action.

PostgreSQL deployments that do not enable the default vector plugin need no
pgvector objects. Deployments that enable it must separately run the optional
schema owned by `nacos-default-ai-vector-plugin`.

## 9. Compatibility And Tests

Internal search names, persistence models, tables, configuration, and vector
SPI packages remain protocol-neutral even while ARD is the only consumer.

Tests cover keyword and vector recall, ranking, visibility, current-version
validation, cursor pagination, full-set aggregation beyond one page,
transactional replacement, both durable task stages, lease recovery,
superseded revisions, idempotent enhancement retry, configuration-fingerprint
recording without global rescheduling, lifecycle enhancement intent, and
repair-triggered reconciliation enhancement without historical full refresh.
Protocol adaptors separately test their request grammar and response
conformance.
