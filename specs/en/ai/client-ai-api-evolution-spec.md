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

# Nacos 3.3 Client AI API Amendment Proposal

| Item | Value |
| --- | --- |
| Status | Phase-one interfaces/transports implemented in primary specs; sections 3, 4 and 6 remain unimplemented proposals |
| Updated | 2026-09-11 |
| Scope | Resource facades, transport overrides, A2A/RAD capability discovery and routing, Agent/RAD Java model consolidation |

This proposal separates implemented phase-one contracts from future compatibility amendments;
unimplemented sections do not replace current behavior. See the detailed [API design](../../../Codex/design/nacos-3.3-client-ai-api/README.md),
[A2A routing](../../../Codex/design/nacos-3.3-client-ai-api/A2A_ROUTING.md), and
[IT matrix](../../../Codex/design/nacos-3.3-client-ai-api/COMPATIBILITY_IT.md).

## 0. Current Phase Boundary

The first phase now covers interface delegation, resource transports and their UT/Java SDK IT.
See the [phase-one plan](../../../Codex/design/nacos-3.3-client-ai-api/PHASE1_PLAN.md).
Legacy A2A keeps its existing gRPC path on both old and new servers regardless of global/Agent
grpc/http/auto settings; the Agent setting controls native Agent/RAD only in this phase. Accessors
do not start connections; actual A2A calls retain requireGrpcClient startup and reconnect demand.

Sections 3 and 4, including HTTP capabilities, complete compatibility flags, A2A-to-RAD conversion,
version owners, legacy fields and release adaptation, are deferred. They are not phase-one gates.
Phase one changes no server API, migration algorithm or legacy A2A business implementation.
Native HTTP unsupported responses retain existing behavior without new probes or normalization.

## 1. Amendments To Primary Specifications

| Primary specification | Proposed replacement or addition |
| --- | --- |
| [Java SDK Implementation](../sdk/sdk-java-impl-spec.md) §5.3 | Resource accessors and inheritance; default delegation for released flat APIs |
| [SDK](../sdk/sdk-spec.md) §5 | Shared mcp() naming and per-resource transport overrides |
| [Agent API](agent-api-spec.md) §2.1–2.2 | AgentService combines A2A and AgentDiscoveryService; new operations require RAD |
| [Client Ability Negotiation](../client/client-ability-negotiation-spec.md) | HTTP binding discovery; capability, reachability and business preconditions are independent |
| [A2A Compatibility](a2a-agent-spec.md) | New binding preserves all legacy semantics; migration authority stays server-side |
| [HTTP API Surface](../http-api/v3-api-surface.md) | Proposed Client capabilities route, not yet an implemented endpoint |

## 2. Interface And Transport Targets

`AiService` exposes `mcp()`, `agent()`, `skill()`, `agentSpec()`, and `prompt()`, while extending
`McpService`, `A2aService`, `SkillService`, `AgentSpecService`, and `PromptService`.
Resource interfaces extract existing methods; no additional business operations are introduced.

`AgentService extends A2aService, AgentDiscoveryService` and receives the existing `publishAgent`.
`AiService` extends neither Agent interface; unreleased 3.3 flat Agent calls move to `agent()`.
Released flat signatures remain deprecated bridges through resource accessors. Convenience
default overloads preserve dispatch to old core overrides. New accessors have compatibility
defaults so precompiled third-party implementations continue to load and execute old methods.

Keep the five-argument MCP createDraft default: false dispatches to the legacy four-argument
override; true remains unsupported for implementations without draft support. The official client
keeps a pure bridge override to mcp(); the resource delegate holds the only business implementation.

Keep the `grpc` default and `grpc/http/auto` values for `nacosAiTransportMode`. Add
`nacosAiMcpTransportMode`, `nacosAiAgentTransportMode`, `nacosAiSkillTransportMode`,
`nacosAiAgentSpecTransportMode`, and `nacosAiPromptTransportMode`.
Missing overrides inherit the global setting; validate every explicit value and freeze at construction.

Skill/AgentSpec paths without a gRPC implementation receive the existing HTTP proxy directly,
preserving cache, polling, MD5 and listener semantics. In phase one, legacy A2A uses fixed old gRPC
on every server. Without RAD, new Search, Discover,
Watch, publication and publish operations cannot be emulated with legacy APIs. A network
failure in explicit GRPC mode is not a missing-binding fallback and does not enable HTTP.

Prompt direct queries and polling share a thin routing proxy. AUTO uses connection facts and
may fall back on connectivity failures; no new Prompt ability flag is introduced. Agent/MCP/Prompt
record independent AUTO state. Suspend initial reconnect only without required GRPC/A2A demand,
before any successful connection, after the existing failure threshold, and after every used AUTO
resource has succeeded over HTTP. First use of an unused resource resumes its necessary probe;
recovery after a formerly healthy connection remains unchanged.

Resource facades share the existing connection, namespace, authentication, HTTP liveness and
shutdown ownership. Resource modes do not override each other; one resource's HTTP success
must not suspend another required gRPC connection. Stateful publications retain protocol and
transport owners; an unknown write result must not cause cross-protocol or cross-transport replay.

## 3. HTTP Capability Discovery Target (Later Phase)

Propose `GET /v3/client/ai/capabilities` returning `Result<T>` with `schemaVersion=1` and a boolean
`capabilities` map. Initial keys are `radV1`, `radWatchV1`, and proposed `a2aCompatV1`. The response
describes the responding node's HTTP binding, not every cluster member or gRPC reachability.

`a2aCompatV1` means that all old A2A semantics, including migration phases, are implemented by
the new binding. gRPC may advertise corresponding `SERVER_A2A_COMPAT_V1`. Basic RAD alone
does not establish this contract. True/false mean SUPPORTED/NOT_SUPPORTED; missing fields
or an uninterpretable declaration mean UNKNOWN.

The endpoint is independent of resource existence and must not require Admin or individual
Agent permissions. Use an explicit Client identity policy compatible with supported anonymous
Client access. Discovery must not create a Client/Publisher, renew publications, scan data or grant
migration write permission.

404/405, gateway pages, network failures and 401/403 do not prove the absence of RAD. Existing
RAD HTTP servers without discovery can still execute an explicitly requested native Agent
operation; this is the intended request, not a write probe, and never falls back to legacy A2A.
Automatic conversion of an old A2A method requires evidence of the complete adapter contract;
otherwise retain its old path or fail clearly.

Capabilities belong to the selected target and transport. Refresh after gRPC reconnection; HTTP
caching is bounded and associated with the actual address, context path and identity. One
response does not establish uniform support behind an opaque load balancer. Deploy the new
binding to a uniform or explicitly sticky backend pool; otherwise controlled failures are allowed.
The node processing each operation remains responsible for authoritative validation.

## 4. Migration And Error Targets (Later Phase)

Basic support is a software contract declaration: `radV1` does not imply CANONICAL authority.
The complete A2A adapter delegates to existing `A2aCompatibilityOperationService` and Endpoint
compatibility paths; the Client does not implement another migration state machine:

- LEGACY/SYNCING: historical definitions remain authoritative for legacy calls; Runtime follows
  the corresponding legacy/mirror rules. Native RAD reads current standard facts and does not
  promise complete historical visibility before reconciliation finishes.
- QUIESCING: reject fenced historical definition writes while retaining existing read and Runtime
  behavior. Existing guards protect migration-owned resources; unrelated standard Agents are
  not globally disabled by that rule.
- CANONICAL: use server-side canonical compatibility while preserving old release, latest and
  version-specific publisher semantics.

Preserve machine-readable `AGENT_MIGRATION_IN_PROGRESS=50105`, distinct from
`SERVER_NOT_IMPLEMENTED=501` and connectivity errors. Current HTTP mapping does not
preserve every business detail; implementation must add only the necessary binding mappings.
Do not parse error prose, switch protocols on migration conflicts or introduce infinite retries.

Interface extraction, discovery and complete A2A adaptation may ship in stages. Minimal support
for actual gaps and owner lifecycles still require a finalized design. Until implemented, do not
advertise complete adapter support or claim the entire old A2A contract works over HTTP alone.

### 4.1 Prefer Client Conversion; Verified Limits

This proposal does not require a new compatibility RPC for every old method. See the
[client mapping analysis](../../../Codex/design/nacos-3.3-client-ai-api/CLIENT_MAPPING.md):

- Legacy Endpoint URI/TLS/path/query/transport and exact version map to existing RAD Endpoint
  and Batch models. A single registration is a complete one-element batch; batch registration
  replaces it. With isolated owners, the Client can clear the complete version intent on old deregistration.
- Multiple exact versions cannot coexist independently in one standard publisher: version is batch
  content, not identity. Changing only a local map key or merging batches is insufficient. Separate
  real connections/HTTP identities could solve this with additional lifecycle work; logical owners
  over a shared connection are another option. Do not claim all Client-only solutions are impossible.
- Unfiltered endpointSets preserve source order and empty sources, allowing recovery of the stored
  registrationType for converted legacy definitions. Withdraw the earlier missing-order claim.
  An exact query can obtain latestVersion with an additional read, but two reads are not one snapshot;
  define races and failure handling.
- Legacy Endpoint protocolVersion/tenant reside in reserved metadata that standard RAD neither
  accepts nor returns. New SDKs agreeing on ordinary metadata keys does not preserve bidirectional
  interoperability with real old SDKs. Minimal mapping/exposure support or the old path is required;
  definition-level protocolVersion and local caches cannot reconstruct another publisher's fields.
- Existing Client polling and listeners can adapt callbacks and absence/recovery without requiring
  native RAD Watch. Complete GET projection remains a prerequisite. Legacy reserved fields are
  excluded from RAD fingerprints, so native Watch alone cannot detect their isolated changes.
- Old release directly brings a version online, can retain latest, and treats an already-online A2A
  version as a no-op even when content differs. Ordinary publish cannot express the complete contract;
  retain the necessary server write semantics. DTO conversion cannot supply migration authority or mirrors.

A complete adapter flag declares the resulting contract, not where every conversion runs. This
clarification does not change general RAD version-range, metadata, publish or publisher semantics.
Finalize necessary extensions separately while retaining existing business implementations.

## 5. Validation Gates

Phase one uses P01–P16 for interfaces/default methods, old bytecode, mixed resource transports,
Skill/AgentSpec HTTP fallback, fixed A2A gRPC, shared connection/owner/listener lifecycles and
necessary old-wire regression. There is no new HTTP API or associated new OpenAPI scenario.
Update SDK scenario/coverage records and validate default and Jackson 3 configurations.

Later phases use the full A/D matrices for discovery, new A2A bindings and migration races.
Discovery requires OpenAPI IT when implemented. Proposed scenarios remain Pending and do
not increase implemented coverage.

## 6. Agent / RAD Java Model Consolidation Proposal

This section records the agreed contract implemented in the 2026-09-11 local trial: unified Agent
models, RAD definitions as the baseline and an abstract field-sharing layer. See the
[43-file inventory and M01–M15 validation plan](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_CONSOLIDATION.md).
The requested scope permits Java model changes without preserving 3.3.0-BETA aliases.
Released historical A2A contracts and existing wire/storage formats remain protected.

### 6.1 Unified Models And Abstract Bases

Move all current model.rad concrete models/enums into com.alibaba.nacos.api.ai.model.agent;
do not retain two parallel Agent model packages. Put classes used only for field sharing in
model.agent.base as public abstract classes named AbstractAgent…, with protected constructors.
Move Agent/MCP-shared ClientLivenessInfo to the common AI model package; existing RPC envelopes
remain in remote packages.

The shared bases are:

| Abstract class | Fields declared at this level | Reuse |
| --- | --- | --- |
| AbstractAgentMetadata | agentName/displayName/description/iconUrl/provider/tags | Management summary, RAD catalog entry, Admin metadata update; draft base extends it |
| AbstractAgentCallInterface | protocol/protocolVersion/descriptorMediaType/nativeDescriptor | Definition and discovery interfaces are concrete siblings |
| AbstractAgentSearchRequest | Five Search filter/pagination fields, no namespace | Client Search and complete RAD Search are concrete siblings |
| AbstractAgentEndpointRequest | agentName/protocol/endpoints | Deregistration models and registration base; operation validation stays separate |
| AbstractAgentEndpointRegistrationRequest | runtimeVersion/versionRange | Extends endpoint request base for Client registration and RAD RegistrationBatch |
| AbstractAgentDraftRequest | extensions/version/callInterfaces/author/changeDescription/basedOnVersion | Extends metadata base for Admin draft creation and Client publication |

Bases may reference stable value objects but not audience-specific Client/Admin requests.
They do not create Maven modules or own authorization, namespace defaulting, lifecycle,
transport, caching or redo. Concrete operations retain their validators; only identical shared
constraints may reuse validation helpers. Field sharing must not relax contextual rules.

Public SDK parameters/results, DTO members and collection elements must use concrete business
types, not AbstractAgent… types or abstract element lists. Do not introduce JsonTypeInfo,
discriminators or polymorphic construction factories. Deserializing a known concrete model must
naturally bind its inherited properties. AgentSummary, AgentVersionSummary and Endpoint remain
concrete because they have independent response/value-object meanings. Do not introduce generic
identity, version or namespace bases merely to share one or two fields.

### 6.2 Concrete Naming And Inheritance Direction

The initial trial retained AgentCatalogVersion and removed AgentVersionCatalogEntry.
Section 6.5 supersedes this binding with AgentVersionSummary; the remaining initial mappings are:
Management/storage catalog containers reuse the RAD-named type without changing their JSON or
validation rules. Rename the definition-side AgentCallInterface to AgentDefinitionCallInterface;
it and AgentDiscoveryCallInterface extend AbstractAgentCallInterface independently. Do not make
complete management details and discovery results extend one another.

Proposed Client request names are AgentSearchClientRequest,
AgentEndpointRegistrationClientRequest, AgentEndpointDeregistrationClientRequest and
AgentPublishClientRequest. Proposed Admin names are AgentDraftCreateAdminRequest,
AgentDraftUpdateAdminRequest, AgentUpdateAdminRequest, AgentLabelsUpdateAdminRequest and
AgentVersionAdminRequest. Preserve RAD AgentSearchRequest, DiscoveryRequest and Endpoint Batch
names. Do not mechanically create paired empty wrappers without an actual boundary.

Client and RAD Search requests independently extend AbstractAgentSearchRequest; only the
complete RAD request adds namespaceId. Registration/deregistration follow the same sibling
pattern. SDK methods accept concrete ClientRequest types, copy business fields, and inject the
instance namespace. They must not accept the abstract base or the complete RAD request instead.
Client publish and Admin draft creation independently extend the common draft base rather than
making Client depend on an Admin request. Maintainer namespace remains an explicit method
argument. HTTP Forms retain their string parsing and binding responsibilities.

### 6.3 Protocol, Domain And Validation Boundaries

RAD supplies shared concepts and discovery contracts, not the entire management lifecycle.
Admin operations may extend/compose shared objects without making all Admin APIs depend on
complete RAD root messages or online discovery views. Protocol-first design does not require a
separate Java class for every schema concept or rewriting existing versioned schemas.

Preserve JSON fields/nesting, optional/default values, enum values, RPC envelope types, errors
and Endpoint publication semantics. Management catalog labels require arrays, including empty
arrays; RAD permits omission. Shared types retain contextual validation. Construct actual
bounded management summaries rather than casting detail objects, and do not load AI Storage
content for Version lists. Preserve current namespace fields in discovery/publication results;
do not add JsonIgnore to shared models. Keep explicit storage projections, bytes, digests,
sourceRevision, Watch fingerprints and defensive copying. No A2A/MCP/Skill business, migration,
transport-routing, Watch or redo algorithm changes are part of this proposal.

M15 adds structural checks for abstract bases, protected constructors, concrete API/DTO types,
and concrete JSON deserialization without a discriminator. M01–M14 retain inherited-property,
namespace, old-JSON, catalog, bounded-summary, storage-vector, default/Jackson 3 and real
Client/Maintainer/OpenAPI scenarios. New items stay Pending until executed. Update coverage and
scenario registries with implementation; do not expand live fault-injection scope. On adoption
and implementation, update the Java binding mappings in both languages of the primary Java SDK
implementation, Agent API, Agent Management and RAD specifications.

### 6.4 Follow-up Review: Three-Level Discovery Models (Pending)

Sections 6.1–6.3 describe the existing local trial. This section records the subsequently agreed
simplification target; it does not change current wire or runtime behavior. Consolidated resource
information uses AgentSummary, with version metadata organized as
`AgentSummary.versionInfo: AgentVersionInfo → onlineVersions[]: AgentVersionSummary`.
Each query retains its response-field restrictions, and user-constructed Client inputs remain
namespace-free.

The public discovery hierarchy is
`AgentDiscoveryResult → callInterfaces[]: AgentCallInterface → endpoints[]: Endpoint`.
Do not insert a versions[] or EndpointSet navigation level. Declared and runtime addresses share
Endpoint, with a source property identifying DECLARED/RUNTIME. Separate query entry points do not
justify separate public CallInterface or Endpoint types. Evaluate VersionDetail with declared
addresses only first, and assess runtime retrieval separately. Do not require a new management
aggregation query or include runtime addresses in version storage or contentDigest.

The representation of source order, empty sources, sourceRevision and internal wire mappings
remains to be designed; flattening the public structure must not silently discard these contracts.
Before implementation, specify whether wire adapters retain the existing schemas or schemas change
as well, and update the corresponding bilingual specifications and SDK/OpenAPI scenarios. The
target is not yet the implemented HTTP/gRPC structure.

The default Discover restriction to latest's protocol definitions, source order and declared
addresses is recorded as MODEL-D01 in the
[relationship review, section 12](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_RELATIONSHIPS.md).
Coverage of all online versions' endpoints, descriptor ownership, deduplication and Watch
dependencies will be addressed separately. Model simplification does not also change cross-version
discovery algorithms or introduce extra result levels in anticipation of that follow-up.

### 6.5 Current Step: Resource Summary And Version Metadata

This section supersedes the initial resource/version type split described earlier in this chapter. Constraints for the other request and protocol models remain applicable.

This step consolidates Agent/version metadata only; CallInterface, Endpoint and MODEL-D01 stay
unchanged. Remove public Agent, AgentCatalogEntry, AgentVersionCatalog and AgentCatalogVersion.
Use AgentSummary with optional extensions, AgentVersionInfo for the version collection, and
AgentVersionSummary with protocols/labels for individual entries. AgentVersionDetail retains
protocol content.

Public JSON contains versionInfo with editingVersion, reviewingVersion, labels and onlineVersions.
Derive latest from labels["latest"] and the online count from onlineVersions rather than keeping
separate public facts. Search also returns AgentSummary, omitting namespace, management fields,
extensions and editing/reviewing; its label map contains only online targets. List projections
omit extensions while detail/update responses retain their previous extension behavior.

Update the associated Search/Admin/Console JSON shapes and Java generics without BETA model
aliases. Persistence retains explicit projections to the original version_info and ext.versionCatalog
schema, including schemaVersion, field formats and version-content bytes. Validate stored-field
consistency before assembling the new model. Discovery selection, addresses, Watch, A2A, transports
and publication algorithms do not change. UT/IT must verify the new response shapes, field boundaries,
complete labels, old-storage reads and derived catalog consistency; record fresh validation separately.
