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
| Status | Resource interfaces, model consolidation and A2A/RAD adaptation implemented |
| Updated | 2026-09-20 |
| Scope | Resource facades, transport overrides, A2A/RAD capability discovery and routing, Agent/RAD Java model consolidation |

This specification records the implemented Client API evolution. The primary
[A2A compatibility contract](a2a-agent-spec.md), [RAD protocol](rad-protocol-spec.md),
and [SDK scenario matrix](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)
define behavior and its acceptance coverage.

## 0. Current Phase Boundary

Resource interfaces, transport overrides and model consolidation are implemented.
Initial capability resolution now routes old A2A operations to RAD when supported,
or retains legacy gRPC for old servers. The selected A2A mode remains fixed for
the client instance. Sections 3–5 cover capabilities, mapping and validation;
section 6 retains the model evolution record.

## 1. Amendments To Primary Specifications

| Primary specification | Proposed replacement or addition |
| --- | --- |
| [Java SDK Implementation](../sdk/sdk-java-impl-spec.md) §5.3 | Resource accessors and inheritance; default delegation for released flat APIs |
| [SDK](../sdk/sdk-spec.md) §5 | Shared mcp() naming and per-resource transport overrides |
| [Agent API](agent-api-spec.md) §2.1–2.2 | AgentService combines A2A and AgentDiscoveryService; new operations require RAD |
| [Client Ability Negotiation](../client/client-ability-negotiation-spec.md) | HTTP binding discovery; capability, reachability and business preconditions are independent |
| [A2A Compatibility](a2a-agent-spec.md) | New SDK uses explicit read/publication changes; old wire and server migration guards remain |
| [RAD](rad-protocol-spec.md), [Agent Storage](agent-storage-spec.md), [Agent Management](agent-management-spec.md) | O1: per-Endpoint effective bindings, Batch field defaults, writable enabled and removal of redundant state; Console derives labels |
| [HTTP API Surface](../http-api/v3-api-surface.md) | Client capabilities route with standard identity-only authentication |

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
may fall back on connectivity failures. Phase one introduced no Prompt gRPC ability flag;
the HTTP prompt declaration in section 3 does not change that fact. Agent/MCP/Prompt
record independent AUTO state. Suspend initial reconnect only without required GRPC/A2A demand,
before any successful connection, after the existing failure threshold, and after every used AUTO
resource has succeeded over HTTP. First use of an unused resource resumes its necessary probe;
recovery after a formerly healthy connection remains unchanged.

Resource facades share the existing connection, namespace, authentication, HTTP liveness and
shutdown ownership. Resource modes do not override each other; one resource's HTTP success
must not suspend another required gRPC connection. Stateful publications retain protocol and
transport owners; an unknown write result must not cause cross-protocol or cross-transport replay.

## 3. HTTP Capability Discovery

GET /v3/client/ai/capabilities returns Result with data.schemaVersion=1
and capabilities. The agreed resource set contains five boolean keys: radV1, mcp,
skill, prompt and agentSpec. Each describes the corresponding Client HTTP binding
on the responding node, not gRPC support/reachability, resource authority or management APIs.
Agent/MCP have HTTP and gRPC paths, as does Prompt query; the relevant Skill/AgentSpec
SDK paths currently use HTTP. Base declarations cover Client reads/search plus
Agent/MCP publication and Endpoint lifecycle, without implying every future enhancement.
See the capability design table for the exact resource mapping. Existing agent denotes
legacy A2A, not RAD. Do not advertise the new HTTP keys as supported gRPC bindings.
The endpoint takes no business selector and has no publication, heartbeat, Watch,
index or storage side effects. Declare implemented/installed bindings without querying
resources; an empty registry can report support. Do not add a mandatory probe before
existing MCP/Skill/Prompt/AgentSpec calls; preserve their existing paths on old servers.
The separate a2aCompatV1 proposal is withdrawn. Existing Watch negotiation selects
a Watch transport; it is not an additional A2A-to-RAD routing gate.

Use SERVER_RAD_V1 on the current gRPC connection and independent HTTP discovery
when gRPC is unavailable. Per-key true/false mean supported/not supported; a missing
or invalid value makes only that key UNKNOWN while preserving other valid keys.
An invalid root or unknown schema makes the response UNKNOWN. Ignore unknown optional
keys and never default absent keys to false. Authentication failures,
404/405, network failures and business not-found do not prove lack of RAD.
Cache by actual target, context path, transport and identity; invalidate on
connection/identity changes. A capability is neither resource authorization nor migration write permission.

### 3.1 Identity Only

Declare OPEN_API / AI / READ with ONLY_IDENTITY, without ALLOW_ANONYMOUS.
A valid caller with no resource grants succeeds; missing, invalid or expired
credentials fail. Later Agent operations still enforce their own authority and visibility.
Use ordinary Client credentials and ports, without an Admin/Console dependency.

Reuse validateIdentity followed by the existing authority-skip branch. A small
explicit non-resource parser should retain AI/action/tags without deriving a
namespace/group/Agent from caller parameters. DefaultResourceParser.class does
not override typed parser dispatch. Removing Secured is not identity enforcement.
The current outer HTTP filter returns 403 with Result ACCESS_DENIED; do not
confuse the plugin's internal 401 with that HTTP contract.

O6 is agreed (2026-09-17): use the standard Client auth flow. Default/true follows
the selected plugin's identity rules; explicit false bypasses authentication under
the Client switch. Do not add endpoint-specific forced identity validation.
Keep ONLY_IDENTITY, without resource authorization or ALLOW_ANONYMOUS. Admin/Console
switches do not replace the Client switch. Plugin enablement, missing/inactive plugins
and internal-identity branches follow the existing framework; add no capability-specific
checks or configuration errors. Distinguish validated identity from a standard-flow
bypass; the latter is not evidence that identity validation succeeded.

See the [A2A compatibility contract](a2a-agent-spec.md)
for the full response, parser, no-side-effect and identity matrix.

## 4. A2A / RAD Mapping

### 4.1 Routing

On initial confirmation of RAD support, old A2A and native Agent remote operations use RAD
and the Agent resource transport policy. Without RAD, old A2A forces legacy gRPC,
while AgentDiscoveryService/publishAgent remote operations are unsupported.
An AiService instance that initially selects legacy A2A retains that mode for all old methods
across reconnection, server upgrades and migration completion. Reevaluate on application
restart or a newly created instance. Existing applications retain old A2A operations, with
migration adaptation handled by the server. Do not add in-process handover to native RAD.
Underlying ability refresh and other resources keep their existing behavior.
Unreachable servers produce connection errors; UNKNOWN is not an old-version claim.
Inherited getAgentCard remains an old method through both facade and agent().
Local unsubscribe/shutdown remain available without remote capability.
Business errors do not trigger legacy fallback; ambiguous writes do not replay across owners.

### 4.2 Reads And Watch

These mappings apply to RAD mode. Legacy instances retain their original query,
Endpoint redo and polling lifecycles.

An omitted old version selects explicit label=latest and protocol=a2a.
Reconstruct Card from nativeDescriptor, project Runtime addresses for SERVICE,
and fall back to the declared Card when no runtime address exists. Preserve source
order for registrationType. latestVersion is true for latest reads and always
null for exact reads; do not perform an extra catalog/latest lookup.
Prefer the existing RAD Watch manager and the same Card projection; use RAD
Discover polling only when Watch is unavailable. Verify pending missing targets,
recovery, cross-entry listeners, cancellation, resubscription and shutdown.

O4 is agreed, pending implementation (2026-09-17): endpointSourceOrder expresses
default preference, not source enablement. Every definition must contain RUNTIME
and DECLARED exactly once, in either order. Remove single-source definitions and
their UI modes. Both sources are discoverable, but either can have no addresses;
definition publication does not require a runtime registration.
Without endpointSources, return both Sets in the recommended order, including empty
Sets; do not select only the first non-empty source on the server. Explicit
endpointSources=[RUNTIME] or [DECLARED] selects that source regardless of definition
preference. Return an empty Set when nothing matches, without adding the other source.
Selecting both sources preserves definition order; the Filter is a set, not a priority
override. Discover and Watch retain Version, identity, visibility and other filter rules.

A2A URL/SERVICE conversions already produce [DECLARED,RUNTIME]/[RUNTIME,DECLARED].
Keep them. An unspecified old query type follows the preference; an explicit type
selects the Card projection. Read the complete source order to recover the stored
registrationType in the response. SERVICE with no matching Runtime Endpoint retains
the old Card fallback; native single-source filtering does not inherit that fallback.
Network or permission failures remain errors. This decision supersedes the unaccepted
proposal to degrade SERVICE when a native definition excludes Runtime.

Implementation must update definition validation, Discover/Watch, management/internal
storage schemas and Artifact references, plus Console selectors, EndpointSourceMode,
bidirectional conversion, JSON validation, detail labels/source-disabled hints and i18n.
Management Runtime queries remain independent of preference. Remove only single-source
definition modes; keep EndpointSource enum values, single-source query filters and
declared-only address storage in Version content. Do not add BETA upgrade logic or
silently rewrite published digests. Revise single-source fixtures for the new contract.
See the [A2A compatibility contract](a2a-agent-spec.md) and
[SDK scenario matrix](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md).

### 4.3 Client Publication

Client APIs force ordinary auto-submit only when this invocation successfully creates
a Version from a state containing no Versions. Admin/Console draft creation and explicit
submission remain unchanged. Old setAsLatest maps to autoSubmit. Later new Versions and
existing editable drafts follow that invocation's flag: false keeps a draft, true submits.
An existing draft never qualifies as the first Version, even if it is the sole Version,
was created by Admin, or remains after a failed first publication submission.
Native publishAgent uses the same Client workflow. Fully replace an existing editable
DRAFT; return a successful no-op for REVIEWING/REVIEWED/ONLINE/OFFLINE without
overwriting content or restoring online state. Ordinary submit may await review;
latest changes only upon actual online transition. Authority and migration guards remain.
Old-server and old-wire release semantics remain unchanged.

Count all Version states when determining the first Version. An explicit new invocation after all Versions have been deleted uses the current
empty state without a historical-first marker. Enforce first-Version and editable-state
checks through the existing persistence workflow. Return conflicts or disappearance errors;
do not recreate resources or rerun publication after a failed operation.
Creation, update and submit failures propagate directly, without automatically repeating
writes or using a post-failure recoverEquivalent read to convert failure into success.
Remove that existing publication recovery behavior. Do not compensate by deleting saved
drafts; an exception does not guarantee that the server made no changes. Lost responses
remain unknown outcomes and must not trigger cross-transport publication replay.
A later explicit application invocation is independent: an existing draft follows its flag,
and a non-draft is a no-op. It does not automatically resume the first failed submission.
These publication rules do not change Endpoint redo or Watch reconnection.

Both A2A release and native Client publish completely replace an editable draft definition,
including the entire callInterfaces list. Do not merge protocols or nativeDescriptor fields;
omitted protocols are removed. Replacing [a2a, protocolB] with an A2A-only request leaves only
the new a2a definition. No merge mode or caller-origin marker is needed. Existing Agent
owner/scope governance properties are not part of this Version definition replacement.
Keep callInterfaces/basedOnVersion mutually exclusive; resolve the source Version into its
complete definition and apply the same replacement rule. Preserve existing creation/update
field rules for authorship and change descriptions. See
[A2A compatibility contract](a2a-agent-spec.md) and
the [SDK scenario matrix](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md).

### 4.4 Endpoints And A2A Fields

Cache original exact-Version intents in the A2A adapter, merge by Endpoint identity,
and submit a complete publication. Old deregistration removes the entire target
Version intent, retaining other Versions; remove the publication when empty.
Identical address/content can merge; different host/port/transport remain separate.

O1 is agreed, pending implementation. Effective Version bindings belong to each
Endpoint, reusing bindings/RuntimeVersionBinding. Keep Batch runtimeVersion and
versionRange as input defaults without adding a grouping model. Resolve each field
independently: runtimeVersion uses the Endpoint value, then the Batch value;
versionRange uses the Endpoint value, then the Batch value, then the exact range
of the effective runtimeVersion. Absent/null Endpoint bindings use the Batch defaults
to construct a binding. Empty lists, null list items and empty strings are invalid defaults.
Apply inheritance, then defaulting, then validation. Every effective runtimeVersion
must be valid and contained in its range; reject the whole batch without replacing
the existing registration when any Endpoint is invalid. Do not materialize an absent
Batch range as [Batch.runtimeVersion] before resolving Endpoint fields. Do not widen
or replace an inherited range to make validation pass. Batch range [1.0.0,2.0.0)
with Endpoint runtimeVersion 2.0.0 fails, whereas [1.0.0,2.0.0] succeeds.
Either field can be overridden independently. Common-Version batches remain simple;
Batch defaults are unnecessary when all Endpoints provide complete bindings.
Normalize independent copies, retain effective bindings in caches/deregistration/redo,
and apply the same contract to direct HTTP, SDK and gRPC requests.

Runtime registration accepts enabled, like healthy, as a non-null boolean defaulting
to true. Disabled contributions remain visible to management and are excluded from
discovery; other publishers retain their contributions. Heartbeats do not change enabled.
Existing Naming operational metadata retains precedence over publication values.
Remove Endpoint.state/RuntimeEndpointState and redundant consistency validation.
Console derives its display from enabled/healthy: disabled first, then unhealthy,
otherwise available. Update frontend types and tests for all three display branches.
EndpointSet.sourceRevision/lastUpdatedTime remain server-generated; declared Endpoint
defaults and Version storage do not acquire runtime write semantics.

O2 is agreed, pending implementation: each Publisher registers one effective binding
per Endpoint; reads can still aggregate bindings from multiple Publishers. The new
SDK A2A adapter merges identical-address/content registrations into a continuous
closed range. Map runtimeVersion to the greatest currently referenced Version using
RAD SemVer. Intermediate definition Versions can match without individual registration
when the ordinary discovery prerequisites hold.
Deregistration removes the exact-Version registration reference without shrinking
the previously declared range while another reference remains. Registering 1.0.0
and 1.2.0 at the same address, then deregistering 1.0.0, retains [1.0.0,1.2.0].
Removing the highest Version updates runtimeVersion from remaining references but
still retains the range. Remove the Endpoint and its range record only after its
last reference disappears; subsequent registration starts a fresh range.
Out-of-range registrations extend the retained range. Duplicate registration does
not increment a reference count; replacing a Version list follows the same removal rules.
Cache effective bindings as well as active references; redo must not reconstruct a
narrower range from remaining Versions. Definite failures do not commit new cache state.
Native RAD still replaces the explicitly submitted complete Batch and can change
the range directly. Old-server/old-SDK wire behavior retains exact-Version isolation.
Do not add disjoint-set/union syntax; retain singular Version metadata per Naming Instance.
A live Runtime publication for one instance/Agent/protocol uses one write source, A2A or native
RAD. The first effective write selects it; reject another source before remote mutation. A
definite failure does not acquire it, an uncertain result retains the original owner, and
confirmed final deregistration releases it. Other scopes, reads and definition publication
are unaffected. Do not introduce a new generic owner framework.

Automatically inject A2A tenant and Endpoint protocolVersion into public metadata,
with shared keys `__nacos.agent.endpoint.tenant__` and `__nacos.agent.endpoint.protocolVersion__`; reverse-project on reads/Watch.
Missing protocolVersion retains the CallInterface fallback; do not mutate the definition.
Reuse these same historical Nacos-owned keys in Naming and public Endpoint metadata.
Allow only these two compatibility keys from the reserved prefix; other internal control
keys remain forbidden. Reject invalid input. Metadata-only changes participate in fingerprints.
A native RAD Util/Builder is deferred.

### 4.5 Migration And Specification Impact

Do not duplicate the migration state machine in the Client. Server guards decide
each request; SYNCING/QUIESCING do not mean absent RAD. Preserve machine-readable
details such as 50105. New RAD writes against unfinished historical authority/mirroring
still need directed validation; retain real old-SDK regression.
These agreed Client read/publication changes replace the earlier lossless-compatibility premise.

O5 current-code investigation, protection gaps still require implementation: complete migration projections
can be read through RAD before cutover, but historical definitions remain authoritative
and RAD reads can be absent or lag behind updates. The existing mutation guard covers
only definitions already marked as migration-owned. A same-name historical definition
that has not been projected is not fully protected by that guard; a new standard write
can conflict with subsequent reconciliation. Native RAD Endpoint registration writes
only standard Runtime state, bypassing the old A2A historical-primary/canonical-mirror
router. Old consumers may not see those new addresses, and general ranges must not be
expanded into old exact-Version shadows. The user explicitly requires supporting SDK-first
upgrades; withdraw the proposed upgrade-order prerequisite.

O5 now adopts legacy mode retention for this phase. After reliable initial evidence selects
legacy A2A, all old facade/subservice methods of that AiService instance keep the old gRPC
wire until application restart or re-instantiation. Reconnecting to a new node or completing
migration does not promote the instance to RAD. Retain old Endpoint redo/deregistration and
polling/listeners; do not convert them into O2 Batches or RAD Watch. Applications already
working with old servers use old A2A; native RAD methods are unsupported on those servers.
Retain that old interface path and server adaptation without adding mixed-API handover.
Connection ability refresh, HTTP caches and other resources keep their existing behavior.
A timeout, authentication failure, HTTP 404 or missing radV1 key alone must
not latch legacy mode; preserve UNKNOWN and real errors until reliable evidence exists, and
share one decision across concurrent first calls. An HTTP-only reachable target still causes
a legacy gRPC connection error for an instance retaining old mode.

A new instance reevaluates capabilities without changing the original instance. Do not
automatically transfer registrations or subscriptions; use normal shutdown and registration
lifecycles. RAD requests do not fall back to legacy on business errors; existing owner and
ambiguous-write constraints remain. Final review confirmed one node-level admission gate:
while effective A2A authority is historical, reject new RAD business operations uniformly,
including unrelated standard Agents. Do not scan historical names for selective admission.
Search, Discover, Publish, Register/full replacement, initial Watch and subsequent Watch
business reads preserve AGENT_MIGRATION_IN_PROGRESS (50105) through the SDK. Reject before
business mutation, without misleading successful empty results. No client migration waiting,
dual write or old-protocol fallback is required.

Capabilities remains available under normal auth; radV1 still describes implemented support.
Retain whole-publication deregistration/local cancellation/shutdown and valid-owner lease
renewal under normal ownership checks. Partial SDK removal that registers the remaining
snapshot is still rejected with 50105, preserving the original registration/cache; do not
automatically escalate it to whole-publication deletion. Heartbeats or cleanup cannot create a new Client/Publisher or redo
registration, and Agent admission must not break shared MCP liveness. Preserve HTTP/gRPC
auth ordering. Reuse effective compatibility mode and terminal precedence: explicit LEGACY,
AUTO without a plan and nonterminal AUTO reject; fresh CANONICAL and terminal authority admit
requests to normal domain checks. Missing markers or a null resolveConfigured() result alone
are insufficient to decide admission.

Place the gate at native RAD external bindings and subsequent authorized Watch reads, without
blocking legacy wire, Admin/Console, internal migration or indexing paths. Existing guards
still apply. The same admission policy applies to each native RAD business entry. No migration-ready capability or switch timer is needed, and old write barriers
still apply; not every legacy request must succeed during migration.

Retain legacy A2A public interfaces and wire compatibility in this phase. Reassess removal
after adoption across several future major versions, without committing to a removal release.
The temporary historical-storage migration component's removal plan is separate from removing
public A2A interfaces or the canonical AgentCard adapter.
Initial capability resolution selects legacy A2A or RAD for the client instance.
See the [A2A compatibility contract](a2a-agent-spec.md).

Affected contracts include Client publish, new-SDK A2A mapping, RAD registration
and public metadata, Agent Storage/Management binding and state rules, capability
negotiation and HTTP identity policy. The primary specs, migration guard contracts, RAD/management and internal storage schemas,
Artifact references and contract tests define the implemented behavior.
See [A2A compatibility contract](a2a-agent-spec.md).

## 5. Validation Gates

The [Java SDK scenarios](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md),
[Client HTTP scenarios](../../../test/openapi-test/CLIENT_API_TEST_SCENARIOS.md), and
[migration scenarios](../../../test/java-sdk-test/A2A_MIGRATION_SDK_IT_SCENARIOS.md)
cover all 18 A2A and 10 native Agent signatures, additional AI regressions, and
8 affected HTTP operations. Exercise HTTP, gRPC, AUTO-to-gRPC, AUTO-to-HTTP,
both facade/resource entries, both JSON adapters, and real non-RAD servers.

Validate capability identity without resource grants, invalid/missing credentials,
anonymous-AI isolation, no lease renewal, and credential-cache isolation.
Admin/Console draft creation must not auto-submit. Released bytecode, old SDK
processes, real restart, cross-node Watch and migration need their own enabled
fixtures; conditional skips are not passes. Keep existing unrelated exclusions
and the Derby cluster Search cutover limitation explicit in the coverage records.

Behavior changes must update tests and the primary specs/schemas together, pass
stage validation, then run the complete affected matrix. Public RAD, Watch,
management and Artifact schemas use contract version 0.5.0 at stable paths;
payload schemaVersion keeps its independent meaning.

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
validation rules. The later endpoint consolidation supersedes the two CallInterface siblings: both use
AgentCallInterface → EndpointSet → Endpoint, with field constraints by query context.
Complete management details and discovery results do not extend one another.

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

### 6.6 Request Package Refinement Proposal (2026-09-15)

This is a proposal following the request usage audit; Java classes have not yet moved or been
renamed. Implementation will replace the corresponding names in section 6.2 and update the Agent
API, Java SDK implementation specs and affected IT scenario/coverage records. See the
[request package audit](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_PACKAGES.md)
for callers, exceptions, validation ownership and implementation stages.

Keep the nacos-api module. Retain RAD protocol objects, shared values and response models in
model.agent, and abstract shared classes in model.agent.base. Express audience through packages:

- model.agent.admin: AgentDraftCreateRequest, AgentDraftUpdateRequest, AgentUpdateRequest,
  AgentLabelsUpdateRequest and AgentVersionRequest.
- model.agent.client: AgentPublishRequest, AgentSearchRequest, AgentEndpointRegistrationRequest
  and AgentEndpointDeregistrationRequest.

These replace the corresponding existing AdminRequest/ClientRequest names without adding paired
wrappers. Root AgentSearchRequest remains the complete namespaced RAD request; client.AgentSearchRequest
remains namespace-free. They are concrete siblings of the same abstract base, explicitly distinguished
by qualified type names where both occur. Admin requests remain shared by Maintainer, Console and
the server; internal A2A definition conversion may reuse the draft input. SDK requests do not all map
directly to HTTP Forms: partial endpoint removal still computes and registers the remaining complete set.

Prefer existing bases: move extensions from its three direct declaring subclasses to
AbstractAgentMetadata, and identical Admin creation/Client publication validation to
AbstractAgentDraftRequest. Do not add identity/version bases or widen concrete operation fields.
Resolve package-private AgentAdminRequestUtils access during relocation rather than making that
helper public to work around package boundaries.

AgentEndpointDeregistrationBatch is a namespaced SDK-internal removal intent, not a server request.
Moving it into the client implementation module is a separate follow-up with its dedicated validation;
api must not acquire a dependency on client.

The proposal changes Java type ownership and shared declarations only. Preserve HTTP JSON, RPC
envelope names, namespace binding, lifecycle, partial removal, storage digests and RAD revisions.
Java callers need updated imports and recompilation. The agreed scope does not require BETA aliases;
released historical A2A contracts remain protected. Validation stays Pending until actually executed.

### 6.7 Request Consolidation And Namespace Context (Current Implementation)

This section supersedes the initial request hierarchy in sections 6.1, 6.2 and 6.6;
earlier text records design evolution.

Java models use `com.alibaba.nacos.api.ai.model.agent` as the root. Shared RAD models,
Search and RegistrationBatch stay in that package. `agent.admin` contains
`AgentDraftCreateRequest`, `AgentDraftUpdateRequest`, `AgentUpdateRequest`,
`AgentLabelsUpdateRequest` and `AgentVersionRequest`; `agent.client` contains `AgentPublishRequest`.
`agent.base` contains only `AbstractAgentMetadata` and `AbstractAgentDraftRequest`, both
abstract with protected constructors. Metadata shares metadata fields and extensions;
Draft shares version-definition fields and draft validation. Client publication and Admin
draft creation are sibling concrete subclasses; public APIs use concrete types.
Shared validation lives in `com.alibaba.nacos.api.ai.utils.AgentValidationUtils`, outside model.
Forms perform HTTP string parsing. Admin models remain shared by the Maintainer SDK,
Console and server; namespace comes from the Form or an explicit method argument.
JSON conversion uses `JsonUtils`/`NacosTypeReference`.

Search and complete registration use root-package `AgentSearchRequest` and
`AgentEndpointRegistrationBatch`, containing business fields without namespace accessors.
Partial deregistration uses
`deregisterAgentEndpoints(String agentName, String protocol, List<Endpoint> endpoints)`;
there is no deregistration Java Request/Batch. The SDK defensively copies caller content
and supplies its instance namespace through HTTP parameters or the RPC envelope to query
and registration services. Publication keys and redo data retain namespace separately.
Partial deregistration registers the complete nonempty remainder or deregisters the whole
publication when empty, without mutating caller objects or collections. HTTP fields,
authorization, replacement and error semantics remain unchanged. Search/Register RPC
namespace is on the envelope rather than nested in the business request.
No 3.3 BETA Java compatibility wrappers are retained; historical A2A contracts are unchanged.

Logical RAD schemas still require namespace; the Java model and its context together
form the complete request. Execution results are recorded separately; pending matrix
entries are not evidence of passing tests.
