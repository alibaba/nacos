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

Failures retain retry state. Periodic reconciliation detects missed,
partial, stale, wrong-model, and orphaned index data. Discovery reads only
enabled documents whose configured indexes have converged.

## 7. Compatibility And Tests

Internal search names, persistence models, tables, configuration, and vector
SPI packages remain protocol-neutral even while ARD is the only consumer.

Tests cover keyword and vector recall, ranking, visibility, current-version
validation, cursor pagination, full-set aggregation beyond one page,
transactional replacement, durable retry, and reconciliation. Protocol
adaptors separately test their request grammar and response conformance.
