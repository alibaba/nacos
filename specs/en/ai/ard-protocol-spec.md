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

# ARD Protocol Spec

## 1. Scope

Nacos supports ARD v0.91 discovery for local Agent, Skill, Prompt and MCP resources.
Protocol terms and schemas follow the [upstream specification at f073b1ea][ard].
This document defines the Nacos integration and its support limits.

The [AI Registry Adaptor](ai-registry-adaptor-spec.md) owns HTTP publication and
protocol mapping. Resource identity, lifecycle, permissions, indexing and retrieval
belong to the [AI Registry](ai-registry-spec.md) and
[AI Resource Search](ai-resource-search-spec.md). Resource writes use Nacos APIs and SDKs.

## 2. Discovery and responses

ARD runs on the adaptor port when `nacos.ai.ard.enabled=true`. It requires base
resource search to be enabled. All routes use Open API authentication, including
explicit credential validation on well-known routes; anonymous access follows the
deployment policy. Protocol errors use `{"errorCode":"...","message":"..."}`
without a Nacos `Result<T>` wrapper. Rejected credentials return 401 / `UNAUTHENTICATED`.

| Method and path | Response |
| --- | --- |
| `GET /.well-known/ard.json` | Manifest with `entries` containing only the local Registry entry. |
| `GET /.well-known/ai-catalog.json` | Legacy host catalog with `specVersion=1.0` and host metadata. |
| `GET /v3/ai/ard/ai-catalog.json` | Legacy namespace catalog, enumerating all eligible entries across pages. |
| `POST /v3/ai/ard/search` | Latest online resources in `results`, with integer `score` in 0..100 and absolute Registry `source`. |
| `POST /v3/ai/ard/explore` | Facets over the complete visible, current matched set. |
| `GET /v3/ai/ard/agents` | Filtered, paginated resources in `items`. |
| `GET /v3/ai/ard/artifacts` | The exact resource version and representation referenced by an entry. |

Proxy the public domain root `/.well-known/ard.json` to the adaptor.
`nacos.ai.ard.catalog.base-url` is the public adaptor origin and optional proxy prefix;
it does not inherit the main server's context path. The Registry entry URL is a base
URL to which consumers append `/search` once.

Complete entries contain `identifier`, `displayName`, `type` and exactly one of
`url` or `data`; Nacos publishes by URL. The host manifest includes the official
base `@context`, without legacy `specVersion/host` or search `score/source` fields.
Catalog, Search and List retain their respective projections, including existing
`displayName`, `type`, `url` and serialization rules. Query context does not select
a response version. Identifiers use stable, collision-free encoding of Nacos URN segments.

Manifest entries preserve supplied `representativeQueries` and their order.
Counts outside the recommended 2–5 produce a warning without affecting visibility
or search eligibility. The Registry supplies two discovery examples. Legacy catalogs,
Search and List return at most the first five and omit the field when fewer than two
exist; canonical indexes and artifacts retain all examples.

## 3. Queries and context

Search requires nonblank query text; Explore permits an absent query or empty text.
Standard `query.filter` values are strings or string arrays. Values within one
original key use OR, and different keys use AND, including aliases of the same field.
Legacy `query.filters` retains its literal paths, value conversion and `values`
precedence. Only conditions with the same original key and resolved field merge by OR.

JSON-LD 1.1 resolution applies the bundled base context before the request `@context`
(URI, object or array). Full IRIs, compact IRIs and aliases resolve before path splitting;
`type` maps to the media-type IRI. Only `metadata`, `trustManifest` and `data` roots
admit literal JSON member suffixes, whose case is preserved. An array reset clears
request aliases and restores the base before subsequent definitions; a term defined
as null remains removed. Invalid contexts and paths return 400 / `INVALID_ARGUMENT`.

The following settings are Nacos deployment policy:

| Property under `nacos.ai.ard.context.` | Default and behavior |
| --- | --- |
| `allowed-uris` | Empty exact HTTP(S) URI allowlist; every external reference needs permission. The official base always resolves offline. No wildcards, user-info, fragments, redirects, HTTP Link traversal or forwarding of Nacos credentials. |
| `max-bytes` | 1048576 UTF-8 bytes per resolution across inline context and distinct remote documents, excluding the bundled base and cached repeats; must be positive. |
| `timeout-ms` | 3000 ms total remote-fetch budget per resolution; must be positive. |

External documents are cached only within one resolution. Denied, unavailable,
malformed or over-budget contexts return 400 without falling back to a context-free query.

Resolved terms map to existing resource type/version, tags, capabilities, display name,
Registry source and supported metadata predicates. Valid unsupported fields or missing
values yield no matches for nonempty constraints and empty facet buckets. Resource
`publisher`, `publisherId` and `trustManifest` dimensions instead return 400 for
filters and facets, including aliases, IRIs, legacy filters and List.

Explore uses the same field resolution and aggregates the full matched set, not a
Search page. Facet keys retain the requested trimmed spelling; aliases keep independent
options and repeated identical keys use the last request. Facet limit defaults to 20,
caps at 100, and minimum count is 1. `GET /agents` uses its own quote-aware filter and
`orderBy` grammar, including single-quoted equality joined by `AND` and ISO date/instant
comparisons; it has no POST-style context parameter.

<a id="token-contract"></a>

## 4. Pagination

Search defaults to 10 entries and List to 20; both cap `pageSize` at 100.
Missing or nonpositive sizes use the default; larger sizes are clamped.
Follow `pageToken` to completion: short and empty pages may still have a continuation token.
Page size may change during traversal. Each page uses current data and permissions,
without snapshot guarantees.

Tokens are opaque and reuse shared Search Core pagination. Basic tokens identify an
anchor; enhanced tokens also bind the query/configuration fingerprint. Invalid decoding,
an invalid or invisible anchor, mismatched enhanced fingerprints, or mixed token modes
return 400 / `INVALID_ARGUMENT` when pagination validation is reached. Authentication
and request validation retain precedence; protocol empty-match shortcuts may return
before token validation. The server does not silently restart pagination.

On a pagination-state failure, discard the token and accumulated results, then restart
the original query with the same filters, context, namespace and options and a valid
identity. Continue with newly returned tokens. Other 400 responses are not automatic
retry signals; any automatic recovery is limited to one restart. Tokens have no
cross-version validity guarantee.

## 5. Registry identity and trust

`nacos.ai.ard.registry.publisher` identifies only the Registry entry's publisher.
An explicit value must be an ASCII FQDN (punycode supported), normalized to lowercase
without a final root dot. `nacos.ai.ard.catalog.trust.identity` requires this explicit
publisher; absent or blank identity omits `trustManifest`. `identity-type` is a hint.

Recognized identities receive offline authority consistency checks: HTTPS host and
`did:web` DNS authority must match the publisher, while SPIFFE requires a lowercase
trust domain. HTTPS rejects user-info and invalid host/port; SPIFFE also rejects port,
query and fragment. `did:web` accepts a DNS name, optional `%3A` port and path segments,
not an IP address. HTTPS/did:web comparison ignores case, one final root dot and valid
ports; SPIFFE preserves trust-domain dots. Unknown schemes remain unverified declarations.
These checks do not establish domain ownership, delegation or signature validity.

Invalid publisher or malformed/conflicting identity makes the manifest and both
Registry-containing catalogs return 500 / `INTERNAL_ERROR`; authentication has
precedence. Startup and ordinary Search/List/Explore/Artifact access remain available.
Correcting configuration and restarting restores Registry documents. Manifests preserve
custom identity-type hints; legacy projections use `spiffe`, `did`, `https` or `other`.

Resources do not inherit Registry publisher or trust. Existing resource identifiers,
including `nacos.ai.ard.catalog.host.identifier`, remain compatibility identifiers;
changing that prefix changes resource IDs. Without publisher or trust, the Registry
also retains its legacy identifier. Resource owner, author, provider, request Host
and artifact URL do not establish publisher identity. Search score measures relevance only.

## 6. Artifacts

Artifact URLs point to the adaptor's `/v3/ai/ard/artifacts` and carry the exact version,
content digest and representation key. Offline versions, digest mismatches and unavailable
representations return 404 / `NOT_FOUND`. Main server and adaptor may use separate ports.
Skill artifacts are complete `application/agent-skills+zip` archives; Prompt and MCP use
their native representations.
For MCP, `resourceName` is the canonical name within the namespace, not the compatibility
UUID. A nonblank optional `mcpName` must match it exactly; conflicts return 400 /
`INVALID_ARGUMENT`. Downloads still check the current caller's read access.

Agent supports `application/a2a-agent-card+json` when the common latest version contains
a valid complete A2A card, and `application/vnd.nacos.ai-agent+json` for the versioned
Nacos definition. The latter follows the [Agent artifact schema][agent-artifact] and
excludes Runtime endpoints, health observations, Publishers, heartbeats and management
fields. Declared address defaults do not imply live health checks.

One logical Agent produces at most one entry per request. Without a type filter,
pure A2A latest prefers its A2A card and multi/custom-protocol latest prefers the Nacos
definition. Type filters select an available representation in deterministic primary
order using `resourceType=agent` and `artifactKinds`, before totals and pagination.
Representation selection changes the artifact URL, not the logical identifier.

## 7. Support limits

Discovery uses the current shared index and may be incomplete before convergence.
MCP artifact lifecycle enforcement remains incomplete. External manifest/webpage ingestion,
full upstream federation, arbitrary vocabulary storage and
resource publisher/trust management are unsupported; this is not full Registry conformance.
Federation values `auto` (default), `referrals` and `none` currently execute locally,
with empty referrals.

Optional Jev relevance filtering applies only to Search, so enhanced Search and Explore
do not share a relevance cutoff. `X-Nacos-ARD-Enhancement` reports `disabled`, `applied`,
`partial` or `bypassed`; original scores remain unchanged. The default is disabled.
See [AI Resource Search](ai-resource-search-spec.md) for the shared runtime contract.

## 8. Compatibility

The previous adaptor was based on ARD v0.9; this update supports v0.91 within the scope
above. Existing catalog routes and resource identifiers remain available. Legacy catalog
`specVersion=1.0` is not the ARD protocol version.

Clients must send strings in standard filter arrays, explicitly request `pageSize=50`
if they require the previous cap, and configure `registry.publisher` when declaring
Registry trust. Send context-aware queries only to upgraded nodes and restart pagination
after version changes. Detailed scenarios and migration evidence belong in the
[test design](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md) and
[compatibility review](../../../test/openapi-test/ARD_V091_COMPATIBILITY_REVIEW.md).

[ard]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md
[agent-artifact]: ../../schemas/ai/agent/agent-artifact.schema.json#/$defs/NacosAgentArtifact
