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

# AI Vector Plugin Spec

This document defines the AI vector plugin contract. The plugin supplies
optional vector indexing and recall for Nacos AI discovery. It extends the
[Nacos Plugin Spec](plugin-spec.md) and does not change canonical AI resource
identity, lifecycle, visibility, or authorization.

## 1. Scope And Enablement

The AI module owns the vector SPI under `plugin/ai`. Implementations live
outside the canonical AI domain module. The default PostgreSQL implementation
is provided by `nacos-default-ai-vector-plugin`.

Vector indexing is optional. Disabling ARD or selecting no available vector
provider must not prevent Nacos startup, canonical AI resource writes, or
keyword discovery. The selected provider is configured by
`nacos.ai.ard.vector.provider`; provider-specific settings remain owned by the
implementation.

## 2. Provider Lifecycle

Each implementation provides a builder with a stable provider type and creates
an `AiResourceVectorIndex` instance. The router selects at most one provider,
reports plugin state through the unified plugin management model, and falls
back to a no-op implementation when no provider is configured or available.

`available()` reports whether the instance can currently serve vector
operations. It is not a signal that canonical resources or relational indexes
are unavailable. Implementations must release pools, clients, and executors
from `close()`.

## 3. Indexing Contract

The SPI supports resource-version replacement, document addition,
resource-level deletion, resource-version deletion, and nearest-neighbor
search. The following rules apply:

- replacement and deletion operations are idempotent;
- replacing a resource version leaves either the previous complete version or
  the new complete version visible within one provider, never a partial
  document set;
- document identity includes namespace, resource type, resource name, version,
  model, and chunk identity;
- search is namespace-scoped and may restrict resource types;
- returned hits identify the canonical resource and chunk and include a
  provider similarity score;
- protocol-specific DTOs, URLs, trust manifests, visibility decisions, and
  final ranking do not belong in the vector SPI.

The protocol-neutral AI discovery service combines vector hits with keyword
recall and applies lifecycle, visibility, final ranking, and pagination.

## 4. Schema Ownership

Each implementation owns its optional database objects and migration scripts.
The default PostgreSQL implementation owns `pg-ard-vector-schema.sql`, including
the pgvector extension and `ai_resource_ard_embedding_pg` table.

The main Nacos PostgreSQL datasource schema must not create the pgvector
extension or an embedding table. Consequently, a fresh deployment can use
PostgreSQL without pgvector, and a database user without extension-creation
permission can start Nacos while vector discovery is disabled.

Operators explicitly initialize the selected implementation's schema in its
configured datasource. The implementation must validate required extension,
table, dimension, and index compatibility before reporting itself available.

## 5. Consistency And Failure Handling

The relational ARD index and the selected vector index do not share a
distributed transaction. A durable, idempotent indexing consumer in the AI
module drives both indexes from canonical resource state. Vector failures keep
the task retryable and must not roll back an already committed canonical
resource write.

The consumer retries transient failures with bounded backoff. Periodic
reconciliation detects missing, partial, stale, or wrong-model vector data.
Changing the selected embedding model or vector provider requires rebuilding
affected documents. An implementation must expose enough health and indexed
identity information for reconciliation without exposing provider-specific
types to protocol adaptors.

## 6. Security And Operations

- Connection credentials and provider secrets are sensitive configuration and
  must not be returned by plugin detail APIs or written to logs.
- Embedding content is derived from canonical resources and must observe the
  same namespace and data-handling boundary as those resources.
- Implementations must bound batch size, query limit, connection use, and
  retry concurrency.
- Plugin unavailability and indexing lag must be observable independently from
  canonical resource write health.

## 7. Compatibility And Tests

Changes to the SPI must preserve Java 8 compatibility for plugin modules and
follow the Nacos plugin compatibility rules. A new optional method requires a
backward-compatible default or a coordinated compatibility change.

SPI contract tests cover provider selection, no-op fallback, idempotent
replace/delete, scoped search, and lifecycle cleanup. The default PostgreSQL
implementation additionally tests schema isolation, operation without
pgvector when disabled, transactional replacement inside the provider, and
reconciliation after simulated vector failures.

