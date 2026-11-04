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

# ARD v0.91 Adaptation Design

Implementation status (2026-11-04): C02 dual-version offline schema validation and
C03 host manifest discovery are implemented; the remaining changes are pending. This does not promote the
current conformance baseline.

This document defines the target contract. The [AI Registry Adaptor Spec](ai-registry-adaptor-spec.md)
continues to describe current behavior. Maintainer discussion of the design
and spec changes precedes implementation; opening an issue is not design approval.

## 1. Baseline and delivery scope

- Local investigation: develop at a55137012.
- Current upstream baseline: 5fa2f5aef790b478319f6a3b43adf4661b0ed0e0, v0.9 Draft.
- Target upstream baseline: f073b1ea9b01a5f068d0b0986426f2ea49fa30b2, v0.91 Proposal.
- The document is dated 2026-08-26; the target commit is dated 2026-10-25.
  GitHub Releases was empty when checked on 2026-11-04. OpenAPI info.version
  remains 0.5.0. Legacy catalog specVersion=1.0 is not the ARD protocol version.
- Recheck upstream at implementation start and final acceptance. Review any
  new delta before explicitly changing the pin; builds must not follow main.

Upgrade the existing publication, Search, Explore, List and Artifact surfaces
for Agent, Skill, Prompt and MCP. Canonical resources, namespaces, authorization,
lifecycle and shared Search Core retain ownership. No ARD-specific persistent
search index is introduced.

The delivery is "ARD v0.91 adaptation for local resources". External manifest
and webpage ingestion, upstream registry management and full federation remain
outside the original adaptor scope. Do not claim complete ARD Registry
conformance without these capabilities. Web Ingestion is Required in both old
and new specifications, not a new optional v0.91 feature.

## 2. Differences and decisions

### 2.1 Difference index and protocol references

This index summarizes the target contract; sections 3–7 own the detailed semantics.
References are pinned. Inputs and assertions live in the
[unified test design](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md);
current-code evidence and migration live in the
[compatibility review](../../../test/openapi-test/ARD_V091_COMPATIBILITY_REVIEW.md).
P01/P03 remain unresolved policies, not permission to implement assumptions.

| Difference | ARD source | Target summary | Strength/verification |
| --- | --- | --- | --- |
| D01 / Baseline and version labels | [Appendix D.1–D.4][s01] | Pin original schemas, context and runner; distinguish protocol, OpenAPI and legacy catalog versions. | Contract sources; UT-C02-01 |
| D02 / Well-known discovery | [5.1][s02] | Add /.well-known/ard.json, retain the predecessor; documentation uses rel=ard. | Consumer MUST; legacy retention is a Nacos choice; IT-C03-01..03 |
| D03 / Independent Entry and Manifest | [4.2–4.3][s03], [OpenAPI results][results] | Require entries at the manifest root; validate complete entries separately from result projections. | Complete versus partial constraints; UT-C02-02..04 |
| D04 / Entry JSON-LD context | [4.1][s04], [base context][context] | Emit the base context on new manifest entries; preserve legacy envelopes and resource URNs. | Required base, optional wire context; UT-C04-01..04 |
| D05 / Query context and IRI matching | [5.3.1][s05], [QueryModel][query] | Use JSON-LD 1.1 processing and map resolved terms to existing domain predicates. | IRI/OR/AND; unsupported-path 400 is MAY; UT/IT-C05 |
| D06 / Explore term paths | [5.3.3][s06] | Share query resolution; aggregate the full matched set and preserve requested facet key spelling. | Complete-set aggregation and path syntax; UT/IT-C06 |
| D07 / Representative query constraints | [4.2][s03], [D.2][s07] | Missing examples never hide resources; distinguish new warnings from legacy schema limits. | 2..5 is a recommendation/warning; UT/IT-C07 |
| D08 / Open trust envelope | [4.5.1–4.5.2][s08] | Require identity when present; check local authority declarations without conflating relevance and trust. | Binding MUST, framework-defined verification; UT/IT-C08 and P03 |
| D09 / URLs, URNs and media types | [3.3][s09], [5.3][urls], [Appendix C][urn] | Preserve base URLs, artifact URLs and stable URNs; accept valid extension media types without inventing exporters. | Media types/URLs/URNs; UT-C09-02, IT-C09-03 |
| D10 / Search page-size limit | [5.3.2][s10], [5.3.4][list] | Raise the existing 50 cap to the documented 100; treat this as a pre-existing mismatch. | Prose defaults/caps; nonpositive behavior is Nacos policy; UT-C09-01 |
| D11 / Conformance tooling | [Appendix B][errors], [D.4][runner] | Combine dual fixtures, the official runner and Nacos semantic/live authorization tests. | Errors and tooling; UT/IT-C10 |
| D12 / Ingestion, federation and other gaps | [5.2][ingest], [5.4–5.5][federation] | Record delivery boundaries and follow-up gates explicitly. | Required ingestion, informative tracing; IT-C10 and scope statement |

<a id="compatibility-policy"></a>

### 2.2 Compatibility investment

Prioritize the latest protocol and retain compatibility where the cost is low.
[5.1][s02] makes predecessor discovery optional, while [4.1][s04] explicitly
supports context-free entries; follow each clause rather than infer a universal
guarantee from Proposal status. Reuse legacy catalog routes/projections, common
Search/List fields and legacy filters. Do not build a second protocol engine,
version negotiation or token translation layer. Necessary protocol changes take
priority with documented migration and error expectations; optional result fields
are not a reason to remove existing fields.

Same-version token correctness, cross-version limits and query restart follow
[4.5](#token-contract). Authorization, namespaces, resource identity, artifacts and
main-server/SDK contracts retain Nacos regression coverage. Required UT/IT remain required.

<a id="response-contract"></a>

## 3. Discovery and response models

| Path | Target behavior |
| --- | --- |
| GET /.well-known/ard.json | Standard host manifest containing only the local Registry entry. |
| GET /.well-known/ai-catalog.json | Legacy host catalog with specVersion=1.0 and host metadata. |
| GET /v3/ai/ard/ai-catalog.json | Legacy namespace catalog. |
| POST /v3/ai/ard/search, POST /v3/ai/ard/explore, GET /v3/ai/ard/agents | Existing paths with updated query semantics. |
| GET /v3/ai/ard/artifacts | Existing version, digest, representation and authorization contract. |

ARD section 5.1 does not require a Nacos namespace-specific ard.json route.
Remove that proposed extra route from mandatory scope; retain the existing
namespace catalog.

New routes use the existing adaptor port, enablement, ArdApi errors and Open
API authentication. Register operations in the adaptor ControllerMethodsCache;
well-known routes must still validate explicitly supplied credentials.
Document proxying the domain-root well-known path to the adaptor. No HTML
hosting or DNS management is introduced. Return documents directly without
redirects or a new protocol-version negotiation parameter.

Suggested models are ArdEntryFields, complete ArdEntry and ArdManifest;
legacy ArdCatalog/ArdHostInfo retain their envelope. Search and List retain
separate response DTOs. Shared fields may be reused, but score/source must not
leak into manifests. Renaming every internal class is not a delivery objective.

An internal model change does not replace every HTTP response contract:

| API | Wire contract and compatibility |
| --- | --- |
| New GET /.well-known/ard.json | New ArdManifest and complete Entry contract, including new publication context/extensions where supported. |
| Existing GET /.well-known/ai-catalog.json and GET /v3/ai/ard/ai-catalog.json | Retain ArdCatalog/ArdHostInfo, legacy Entry projection, specVersion=1.0 and existing field semantics. |
| Existing POST /v3/ai/ard/search and GET /v3/ai/ard/agents | Retain results/items, pagination and existing Entry fields; Search retains score/source. Responses satisfy both versions; relaxed required fields do not justify dropping existing fields. |
| Existing POST /v3/ai/ard/explore and GET /v3/ai/ard/artifacts | Retain aggregation/artifact formats; do not wrap them in ArdManifest. |

Keep displayName, type, url and other existing fields with their types, semantics
and null/omission/empty-collection serialization rules. Optional result fields in
the new schema do not require smaller responses. Shared DTO reuse must not leak
new manifest context, trust extensions or relaxed example counts into legacy
catalogs or shared Search/List responses; retain their compatibility projections.
The common response contract needs no new Search/List endpoint or negotiation
parameter. Do not choose a response version from a preceding discovery request
or the presence of query @context.

UT-C02-03, UT-C03-01, IT-C03-03, IT-C05-05 and IT-C09-02 must validate these
boundaries. Validate live shared responses against both schema versions and
compare existing fields/serialization with the baseline. New-schema validation
or successful old-DTO deserialization alone is insufficient. If a necessary future
protocol change conflicts with the common contract, document field changes,
client migration and tests; do not assume version negotiation is mandatory.
Request-validation and page-size changes retain D05/D10 review. Response
compatibility does not imply that every behavior remains unchanged.

A complete entry requires identifier, displayName, type and exactly one of
url/data. Nacos continues publishing by URL; no inline-data publishing API is
needed. Search requires identifier, integer score in 0..100 and an absolute
source URI; list entries require identifier. Continue emitting existing
selection fields, including type/url/displayName, for old consumers.
List uses items and Search uses results.

New manifest entries may carry the explicit official base @context. Do not
emit @id initially; any future @id must denote the same resource as identifier.
Context-less predecessor entries remain interpretable with the base context.
Do not flatten arbitrary internal metadata into top-level JSON-LD extensions
or expose index internals, management data or Runtime state.

## 4. Query processing and Search Core boundary

### 4.1 Context processing

Add @context to ArdSearchQuery with a JSON representation preserving its
string/object/array shape. Apply the pinned base expandContext first, followed
by the request context. Use JSON-LD URI and namespace rules, not lowercasing or
colon substitution. Base type maps to ns#mediaType, not JSON-LD @type.
When a core spelling is redefined, use its effective IRI without falling back
to the original literal field.

An adaptor-internal ArdContextResolver wraps a JSON-LD 1.1 processor.
Titanium JSON-LD is the candidate. Pin an exact dependency only after JDK 17,
license, transitive dependency, offline execution and required expansion
checks. Do not add an RDF database, signing stack or dependencies to Java 8
client modules.

P01: determine loader network policy, local mappings/cache, resource budgets and
the exact dependency before implementation. ARD sections 4.1/5.3.1 allow
URI/object/array contexts; they prescribe neither numeric limits nor rejection
of every non-official URI. Bundle the pinned base context for offline resolution.
Design both success for supported contexts and controlled deployment-limit
failures. The earlier 64 KiB/16-document/depth-8 assumptions are not requirements.

A null or numeric query.@context does not satisfy the OpenAPI QueryModel type.
A null/reset inside a context array needs separate JSON-LD and ARD base-context
analysis; do not reject it indiscriminately. Actual parsing/cycle failures must
be controlled without silently ignoring context. Production loader policy is
gated on P01 and the corresponding UT-C04/HTTP test design.

### 4.2 Paths and filters

ArdTermPathResolver recognizes a full IRI, compact IRI or defined term before
splitting a path; dots inside an HTTPS IRI are not JSON path separators.
Only roots resolving to metadata, trustManifest or data permit a literal JSON
path suffix. Root aliases such as m.location work. Preserve JSON member case;
array paths use any-element matching.

ArdQueryMapper converts resolved terms to existing Search Core inputs:

- type/mediaType to resourceType and Agent artifactKinds, selecting before pagination;
- version to resourceVersion and metadata.resourceType to resourceType;
- tags, capabilities and displayName to existing domain predicates;
- publisher, publisherId, source and trustManifest.* to adaptor-derived values;
- supported metadata.* to existing domain facets, without an ARD IRI index.

This work adds IRI semantics to searchable fields without arbitrary vocabulary
storage. Valid unsupported fields/IRIs and absent fields have no values: nonempty
constraints match no entry. Withdraw the earlier blanket unsupported-IRI 400
policy: section 5.3.1 makes rejection MAY, so retain empty-match compatibility.
Malformed contexts/paths still fail; empty arrays retain field-specific behavior.
Keep legacy query.filters; standard requests use query.filter.

Values within one original key use OR; different keys use AND. Distinct keys
resolving to the same IRI must not be unioned into a broader OR. Intersect
constraints for single-valued type when appropriate, but retain separate
predicate groups for multivalued tags: intersecting their values would wrongly
exclude a resource possessing both tags. Standard query.filter accepts only
strings/string arrays; non-string array elements, including null, now fail with
400. This tightens historical tolerance for inputs already outside the old
schema, so document client migration to string values. Preserve legacy
query.filters conversions, values precedence and same-key merging (BC03/04).
Do not enable whole-request schema rejection for Nacos compatibility extensions.

Pagination binding, failure and restart are defined together in [4.5](#token-contract).

### 4.3 Explore

Use the same resolver for query.filter and resultType.facets.field.
Internal aggregation may share a canonical field; response facet keys preserve
the requested spelling. Different aliases may produce equal statistics.
Preserve existing behavior for exactly repeated original facet keys. Neither
ARD section 5.3.3 nor OpenAPI requires a new duplicate-key 400 response.
Valid unsupported fields/IRIs and absent fields yield empty buckets; do not
introduce blanket rejection. Aggregate the full visible, current matched set, never one
Search page. GET /agents keeps its EBNF filter/orderBy grammar and has no
new POST-style context parameter.

### 4.4 Request and upgrade compatibility gates

The [compatibility review](../../../test/openapi-test/ARD_V091_COMPATIBILITY_REVIEW.md)
records BC01..20 with before/after behavior, protocol strength, migration and UT/IT.
Preserve endpoint-specific text rules, namespace query/body binding, federation
normalization, Explore limits/duplicate keys and GET filter/orderBy tolerance.
Do not import other facades' validation limits. Capture actual HTTP baselines
for numeric strings, floats and unknown members instead of inferring 400 from
Java field types. Full pagination equality requires frozen data; concurrent data,
permission and index changes do not provide snapshot semantics.
<a id="token-contract"></a>

### 4.5 Invalid tokens and query restart

Pass mapped Query objects to existing Core pagination, reusing its codec without
invalidating all old tokens just to mark a version boundary. Add no ARD token
format or universal query binding. Basic Search/List tokens contain only an
anchor: changing a query may still permit continuation when that anchor remains
valid. Enhanced tokens additionally bind a relevanceV1 query/config fingerprint.
Test these separately under BC15..18; preserve adaptor empty-match short circuits.
Reuse current predicate/value order and hash inputs without adding compatibility
canonicalization or translation. If necessary semantics change a fingerprint,
apply the failure/restart rules below; no cross-version ABI is promised.
Equivalent prefixes, context
declaration order or URI/inline forms can continue when the mapped Query is
identical. Reordering filter keys/values may still change the enhanced hash.
Page size is excluded from that fingerprint and can change on continuation.
Do not add TTL, signatures or silent first-page resets.

[5.3.2][s10]/[5.3.4][list] do not define cross-version token ABIs, TTLs or snapshots.
These are Nacos adaptor decisions using the existing error envelope and the 400
category in [Appendix B][errors]. After authentication, binding and syntax checks,
a request reaching pagination validation fails with HTTP 400 when decoding fails,
the anchor is invalid/invisible, the enhanced fingerprint mismatches, or basic and
enhanced tokens are mixed:

```json
{"errorCode":"INVALID_ARGUMENT","message":"Invalid discovery cursor"}
```

The example message is from the current implementation. Require a nonempty message
identifying an invalid cursor/token, without freezing its exact wording. Do not add
TOKEN_EXPIRED, a recovery flag, TTL, version negotiation, persistent snapshots or
legacy token converters. Errors contain neither successful results nor a new
pageToken. The server must not silently restart or turn an invalid token into a
successful empty response. Existing protocol prefilters may still return 200 with
an empty result and no token before pagination validation. Preserve authentication
and other request-error precedence rather than relabel them as token failures.

Client upgrade guidance and the explicit recovery workflow in IT:

1. Treat pageToken as opaque. On an established pagination-state failure, or a
   deliberate version/configuration transition, discard the token and all results
   and counts accumulated for that traversal, then start a new query.
2. Preserve query (including context/filter), namespace, federation, List
   filter/orderBy and chosen pageSize. Use the current valid identity and omit
   pageToken. Do not relax filters/permissions or append the new traversal to old results.
3. Continue only with newly returned tokens. Token presence determines completion;
   a short or empty page is not necessarily the end. Restart uses current data and
   permissions and does not preserve the old snapshot.
4. INVALID_ARGUMENT also covers other request errors. Do not retry every 400 or
   rely solely on message matching. No public SDK auto-recovery is added. If a
   caller chooses automatic recovery, allow at most one restart; stop and expose
   any restart failure or repeated continuation failure, then inspect the request,
   routing and configuration instead of looping.

UT/IT-C05-14 cover valid controls, deterministic invalidation, restart with the
original conditions and no blanket retry of other 400s. Same-version regressions
must not be excused by cross-version invalidation policy. Old tokens may naturally
continue to work but have no cross-version guarantee. Old servers retain their
actual historical error behavior; no released binary must be retrofitted.

Cross-version token survival is not a delivery gate. Real old-to-new, new-to-old
and mixed-node runs are optional migration exercises (LX32); unavailable old
environments do not block C10. Current-version continuation, deterministic failure
and restart tests remain mandatory. Enable context only after target nodes upgrade
or with an explicitly upgraded pool. Before rollback, stop new semantics, discard
pagination state and restart on the target version; never silently strip context
and send a changed query to old nodes.

## 5. Publication quality, identity and compatibility

### 5.1 Representative queries

ARD section 4.2/Appendix D.2 recommends 2..5 examples; absence and counts such
as one or six remain structurally valid. New complete-entry rendering preserves
real examples with diagnostics instead of truncating to satisfy a nonexistent
hard limit. Legacy catalogs select at most five and omit fewer than two to
satisfy the old schema; Search/List retain that compatibility projection.
This is a Nacos compatibility choice. Do not change the shared index content or
fabricate examples. The Registry entry may have two real discovery examples.
Missing examples do not change visibility, online state or search eligibility.

### 5.2 Publisher and trust

Preserve URN encoding and catalog.host.identifier semantics. Never infer or
rewrite identity from the request Host header. The default nacos value remains
a legacy local identifier. Domain-anchored deployment claims require an
explicit organization-controlled FQDN. Changing publisher changes URNs and
requires an explicit identity migration notice, not automatic substitution.

Omit trustManifest when identity is unconfigured. ARD section 4.5.1 requires authority binding while section 4.5.2 delegates
verification to the declared framework. P03 must determine how an actual
conflict and an unverifiable identity are handled, with a compatibility plan.
ARD specifies neither an HTTPS/SPIFFE/did:web allowlist nor startup failure
for an unknown DID. Remove those earlier commitments. A known conflict cannot
be called conformant; unknown verification cannot be called verified.
Concrete HTTP/startup/export policy needs a Nacos design and positive/negative
tests, not an inference from the ARD error-code table. String-domain matching
does not prove certificates, signatures, ownership or attestations.
Schema/semantic tests may proceed; new production rejection rules wait for P03.

Keep lowercase trustManifest, require identity and allow framework-defined
members, without automatically exporting internal configuration.
No trustSchema/attestation management API or cryptographic verification framework
is added here. Future verification follows the declared trustSchema framework.
Score remains semantic relevance only.

The pinned upstream EntryFields schema spells its property TrustManifest while
the prose and context use trustManifest. Vendor the original unchanged; add
independent semantic assertions for the lowercase field. Do not silently patch
the fixture to obtain a passing result. The official runner does not prove
authority binding or signature verification.

### 5.3 URLs, types and pagination

Registry entry url remains a base URL to which clients append /search.
A future referral URL is a complete search endpoint and must not be appended
again. Source stays an absolute serving-registry URI. Artifacts remain owned
by the adaptor with exact versions, digests, representations and offline expiry.

Retain MCP Server Card, A2A Card, Skill ZIP and Nacos vendor media types.
A valid media type with no supported local representation yields no results;
an upstream recognized type does not imply a corresponding Nacos exporter.
Keep multisegment URNs and collision-free Unicode segment encoding.

Search defaults to 10 with a maximum of 100; List defaults to 20 with a maximum
of 100. Preserve existing normalization: missing/nonpositive sizes select the
default and oversized values clamp to the maximum. Handle actual parse failures
as controlled errors; baseline existing numeric-string/float coercions under
BC14 instead of adding blanket 400s. Document the 51..100 Search page-size
change; clients needing the previous 50-item cap can explicitly request 50.
Short or empty pages may carry continuation tokens and are not an end signal.

## 6. Acceptance matrix

Before changing production code for each feature, design three layers from
protocol and existing behavior: committed correctness UT/IT with success and
expected errors, local-only cross tests, and existing before/after regression.
See the [ARD v0.91 Test Design](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md)
for fixtures, assertions, case IDs and reusable classes. Do not invent failure
expectations for unresolved P01/P03 policies. Track design, implementation and
execution separately.

Each HTTP behavior commit updates standalone IT, scenario documentation and the
coverage registry. Do not postpone all IT to the final commit or substitute
mock-controller assertions for live HTTP evidence.

| Scenario | Minimum acceptance |
| --- | --- |
| A01 Baseline | Original dual-fixture hashes, offline reference resolution and separate version labels; no runtime download of main. |
| A02 Publication | New host and legacy host/namespace routes; host contains Registry only; namespace enumeration beyond 100 entries. |
| A03 Authentication | Separate port, ARD-only enablement, explicit invalid credentials, anonymous policy and identity-specific visibility. |
| A04 Entries | Complete versus partial constraints, exclusive url/data, no search-field leakage, extensions and lowercase trust semantics. |
| A05 Context | Absent/URI/object/array, term/prefix aliases, full IRIs, redefinition, errors and limits. |
| A06 Paths | Aliased metadata/trust roots, literal suffixes, arrays, case, dots in IRIs, absent versus unsupported fields. |
| A07 Boolean | Scalar/array values, OR/AND grouping and alias collisions on multivalued tags versus single-valued type. |
| A08 Cursor | Basic anchors/enhanced hashes; same-version continuation, deterministic 400 and original-query restart; equivalent mappings, page-size changes and empty pages. Cross-version survival is not a gate; mixed runs are optional. |
| A09 Explore | Aliased keys, duplicate/unknown fields, empty buckets and complete-set/base Search agreement. |
| A10 Examples | 0/1/2/5/6 examples, deduplication, old/new projection, eligibility and Registry examples. |
| A11 Trust | Missing identity, authority match/conflict/unknown and framework openness; runtime errors depend on P03, without a scheme allowlist. |
| A12 URLs/types | Base versus endpoint URLs, extensions, multisegment/Unicode URNs and stable identifiers. |
| A13 Pagination | Search 10/50/51/100/101, zero/negative/type errors, List boundaries and namespace filters. |
| A14 Artifacts | Four nonempty resource kinds, pure/multiprotocol/older-A2A Agent cases, ZIP, version/digest, offline and Runtime exclusion. |
| A15 Regression | Legacy requests/catalog schema, federation values, error envelope and RAD with ARD disabled. |
| A16 Tooling | Pinned manifest/publisher/registry runner modes and their blind spots; strict JSON Schema cannot silently skip. |

Publisher mode requires an accessible HTTPS domain or a controlled equivalent
resolver fixture. Running only manifest/registry against localhost:9080 is not
publisher-mode evidence. External request helpers never inherit main-server
administrator credentials. Authentication IT uses an explicit adaptor target
and dedicated identities. If the existing helper rejects all Authorization
headers, add a target-bound opt-in helper while retaining rejection tests for
the general external helper; do not relax its global protection.

No public Java SDK contract changes are planned. Reassess Java SDK IT if later
work changes public write models, SDK methods or exception contracts.

## 7. Delivery gates and later capabilities

- DAUTH-F03 currently disables private cross-type shared-index IT. Reproduce
  on the implementation baseline, independently repair canonical background
  projection versus foreground visibility, and remove Disabled only after live
  verification. Without closure, report partial delivery, not full acceptance.
- Both upstream versions require the same relevance cutoff for Search and
  Explore. Optional Search-only Jev filtering is an existing deviation.
  Strict base-mode conformance runs with its default disabled setting.
  Including enhancement in the same claim needs a separate full-set filtering
  and cost design; facets cannot be calculated from a Search page.
- External Web Ingestion covers manifests and webpage JSON-LD. Source management,
  fetch limits, provenance, lifecycle and access policy require a separate
  design before complete Registry conformance can be claimed.
- Federation needs upstream configuration, auto merge/deduplication/pagination,
  referrals, loop/hop control, shared deadlines, partial-failure policy and
  credential isolation. With no upstream, retain local execution and empty referrals.
- Persistent custom-vocabulary projections and complete trust frameworks are
  separate work, not implied by an open DTO.
- W3C Trace Context is informative upstream guidance and is not a release gate.

## 8. References

- [Upstream specification](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md)
- [Entry schema](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard-entry.schema.json)
- [OpenAPI](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml)
- [Base context](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.context.jsonld)
- [Conformance tooling](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/conformance/README.md)
- [AI Resource Search Spec](ai-resource-search-spec.md)
- [API Integration Test Spec](../testing/api-integration-test-spec.md)
- [Compatibility Policy](../design/compatibility-deprecation-spec.md)
- [Candidate Titanium JSON-LD processor](https://github.com/filip26/titanium-json-ld)

[s01]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L546-L588
[s02]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L206-L218
[s03]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L96-L124
[s04]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L88-L94
[s05]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L231-L268
[s06]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L347-L410
[s07]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L565-L570
[s08]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L183-L197
[s09]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L43-L59
[s10]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L270-L301
[urls]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L227-L229
[urn]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L534-L544
[list]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L412-L423
[errors]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L524-L532
[runner]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L579-L588
[ingest]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L220-L224
[federation]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L431-L497
[context]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.context.jsonld
[query]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L126-L188
[results]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L264-L349
