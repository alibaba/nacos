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

# AgentDiscoveryService Java SDK Scenario Matrix

This matrix is the test-first contract and execution trace for the first
`AgentDiscoveryService` implementation. It was fixed before production code and is
maintained with the resulting tests. Every scenario is assigned to stable standalone
Java SDK IT, deterministic unit tests, or both.

The SDK owns subscription state through a transport-neutral Watch core. A negotiated
`SERVER_RAD_WATCH_V1` plus `SDK_RAD_WATCH_V1` connection uses gRPC fingerprint hints
followed by authoritative Discover. Explicit HTTP and AUTO without an available gRPC
Watch use one generation-based HTTP Batch Long Poll; bounded Discover polling remains
only as a compatibility fallback when the Watch binding is unavailable. Complete
snapshots, fingerprints, listener ordering, local-pending state, and capacity remain
transport-neutral.

## Test-Layer Legend

| Test layer | Meaning |
| --- | --- |
| IT | Runs through the public Java SDK against a standalone server. |
| Directed IT | Runs through public SDK clients while an external harness restarts the real standalone server. It is opt-in because ordinary shared-server IT must not stop the server. |
| UT | Uses deterministic transport, scheduler, reconnect, or failure injection. |
| IT + UT | The public workflow runs in IT and local invariants are verified by unit tests. |
| Deferred | Outside this phase; the reason is recorded and no production code is added. |

## Implemented Standalone Evidence

`AgentDiscoveryServiceJavaSdkITCase` executes the following stable public workflows:

| IT method | Scenario groups |
| --- | --- |
| `shouldInteroperateWithLegacyA2aSdk` | Legacy A2A SDK definition release, canonical Console and RAD reads, duplicate no-overwrite, legacy exact-Version Endpoint registration into the canonical Runtime Registry without Beta dual-write to the historical Naming service, Console Runtime Snapshot and legacy SERVICE query agreement, Version 2 pre-registration, omitted-selector multi-Version aggregation versus explicit-latest isolation, canonical Version publication, and legacy latest-subscription convergence. |
| `shouldSearchDiscoverAndIsolateNamespaces` | Default and custom namespaces; default, individual, combined, empty, and paged Search; latest/exact/label Discover; combined filters; caller immutability; explicit and mismatched namespace binding; namespace-isolated publication. |
| `shouldReplaceAndPartiallyDeregisterCompletePublications` | Complete register, identical idempotence, replacement convergence, canonical natural-key partial deregistration, unknown/repeated no-op, final deregistration, and protocol isolation. |
| `shouldAggregateIndependentSdkPublishers` | Two SDK identities contributing the same natural key and last-contributor removal. |
| `shouldDiscoverPreRegistrationAndPollUntilAgentAppears` | Pre-registration, missing Discover, subscribe-before-create, one typed `UNAVAILABLE` event, recovery with a complete `SNAPSHOT`, unsubscribe, and post-unsubscribe suppression. |
| `shouldWatchExistingAgentOnlyWhenCompleteFingerprintChanges` | Subscribe-existing current value, negotiated gRPC Hint followed by Discover, Runtime source-revision replacement event, and unchanged-fingerprint callback de-duplication. |
| `shouldShareCanonicalPollingIntentAndIsolateListeners` | Null/empty Filter canonical equivalence, one shared Wire intent, two recording listeners plus one throwing listener, complete replacement delivery, partial unsubscribe isolation, and shutdown callback suppression. |
| `shouldTrackVersionEvolutionAcrossRegistrationOrders` | Version 1 definition-first, Version 2 Endpoint-first, Version 3 definition-first, latest/exact/label subscriptions, catalog order, and offline/online latest recalculation. |
| `shouldSeparateDefaultRolloutPoolFromExplicitLatest` | Two independent publishers keep exact Version 1 and Version 2 Endpoints concurrently. The omitted selector uses latest Version metadata while aggregating every online Version's compatible Endpoints; explicit `label=latest` remains latest-only. The workflow verifies the interval before Version 2 Endpoint registration, the combined pool after registration, Version 1 removal after it goes offline, binding provenance, and polling callback de-duplication for both selectors. |
| `shouldApplyPublicationRangeAcrossOnlineVersions` | Inclusive Version ranges, replacement with a different range, matching exact Versions, and exclusion of nonmatching Versions. |
| `shouldDeregisterActiveHttpPublicationDuringIdempotentShutdown` | HTTP publication cleanup during active and repeated SDK shutdown. |
| `shouldKeepHttpAndGrpcDiscoverySemanticsEquivalent` | Search, Discover, HTTP publication, gRPC observation, and deregistration transport parity. |
| `shouldKeepComplexDiscoveryFingerprintsStableAcrossTransports` | Two online Versions, A2A plus MCP interfaces, declared and Runtime sources, independent gRPC/HTTP publishers, complete HTTP/gRPC snapshot fingerprint equality, and repeated-query fingerprint stability. |
| `shouldUseGrpcForAutoWhenInitialConnectionIsAvailable` | AUTO synchronous startup with an available negotiated gRPC connection, followed by Search, gRPC Watch callback, Endpoint Publication, Discover, and Deregister. |
| `shouldFallbackAutoToHttpWhenGrpcNeverLeavesStarting` | A deliberately unreachable gRPC port keeps the connection in STARTING while AUTO immediately completes Search, HTTP Watch callback, Publication, and Deregister through HTTP; explicit HTTP remains independent of gRPC and explicit GRPC fails without fallback. |
| `shouldBatchHttpWatchesAcrossTimeoutUnsubscribeResubscribeAndShutdown` | Two explicit-HTTP subscriptions share generation-based Batch Long Poll behavior across a silent timeout, independent Agent changes, partial unsubscribe, resubscribe, and shutdown callback suppression. |
| `shouldRejectOnlyLatestHttpWatchAndRetainExistingBatch` | A server HTTP Watch capacity rejection removes only the latest addition, preserves every previously admitted Watch, accepts a smaller generation, and reuses the released slot. |
| `shouldKeepSearchProjectionLifecyclePaginationAndTransportParity` | gRPC/HTTP Search parity over combined typed filters, case-sensitive names, stable first/middle/last/out-of-range numbered pages, Runtime Endpoint non-indexing, two-Version latest/catalog convergence, one-Version offline convergence, and exclusion after every Version is offline. The same eventual assertions are reusable for `AUTO`, `INDEX`, and `SCAN` server runs. |
| `shouldEnforceConfiguredLocalSubscriptionCapacityAndReuseSlot` | Workflow-configured polling-subscription capacity, idempotent duplicate admission, synchronous over-limit rejection before caching, and slot reuse after unsubscribe. |
| `shouldSurfaceServerWatchCapacityAndReuseSlot` | Workflow-configured authoritative gRPC Watch soft watermark, whole-operation crossing from below, remote over-limit exception mapping, rejected Watch cleanup, and capacity reuse after unsubscribe. |
| `shouldEnforceConfiguredLocalPublicationCapacityAndReuseSlot` | Workflow-configured SDK Publication soft watermark, whole-batch crossing from below, above-watermark idempotent replacement and new-identity rejection, and slot reuse after deregistration. |
| `shouldSurfaceServerPublicationCapacityAndStopRejectedRedo` | Workflow-configured authoritative Server Publication soft watermark, whole-batch crossing from below, remote over-limit exception mapping, rejected redo cleanup, and capacity reuse after deregistration. |
| `shouldRejectInvalidBoundariesBeforeRemoteMutation` | Nulls, page boundaries, duplicate filters/natural keys, namespace mismatch, reference ambiguity, invalid protocol/URI/transport/version/range, empty publication, server-owned health, invalid deregistration payload, unknown local no-op, and not-found mapping. |

The same twenty-three stable workflows pass with both the default JSON adapter and
`jackson3`. Existing `AiServiceJavaSdkITCase` runs with them as a compatibility
regression. The opt-in
`shouldRestoreGrpcAndHttpPublicationsAndWatchesAfterRealServerRestart` workflow also passed
against a real standalone process stopped and restarted by an external harness.
It retains independent gRPC and HTTP publishers, a negotiated gRPC Watch, and an
HTTP Batch Long Poll subscription so the restarted server must recover gRPC Watch
resubscription and publication redo together with HTTP `HTTP_CLIENT_NOT_FOUND`
re-registration and HTTP Watch recovery.
Scheduler, listener-failure, transport-error, ability, heartbeat/50404, rollback,
and redo races remain deterministic UT responsibilities; the shared-server CI run
is never stopped.

Every default-gRPC workflow first completes a side-effect-free RAD Search readiness
probe rather than relying only on the shared MCP connection probe. Search count
assertions are scoped to a per-test random Agent-name stem so asynchronous removal
from an earlier standalone-server workflow cannot change another workflow's exact
expected count.

## Factory, Namespace, And Lifecycle

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Create `AiService` through `AiFactory` with no namespace | Agent operations bind to `public`. | IT |
| Create services bound to `public` and a custom namespace | Search, Discover, subscriptions, and publications remain namespace-isolated. | IT + UT |
| Request or Batch omits namespace | The SDK copies the value and injects its bound namespace without mutating caller input. | IT + UT |
| Request or Batch carries the same namespace | The request succeeds and caller input remains unchanged. | UT |
| Request or Batch carries another non-empty namespace | The SDK rejects it locally before transport invocation. | IT + UT |
| Reuse caller lists, maps, references, filters, and endpoints after a call | SDK state and wire payload remain isolated from later caller mutations. | UT |
| Shutdown with no subscription or publication | Polling, heartbeat, HTTP resources, and gRPC resources stop cleanly. | IT + UT |
| Shutdown with active subscriptions and publications | The SDK cancels polling and heartbeat and best-effort deregisters every complete publication. | IT + UT |
| Repeated shutdown | No duplicate callback, uncontrolled exception, or leaked task is produced. | UT |
| `grpc` Agent transport mode | Initial gRPC connection is attempted synchronously; an unavailable connection keeps retrying and Agent operations never fall back to HTTP. | IT + UT |
| `http` Agent transport mode | Agent operations do not depend on initial gRPC startup and remain usable when the configured gRPC port is unreachable. | IT + UT |
| `auto` with available negotiated gRPC | Agent operations prefer gRPC. | IT + UT |
| `auto` with gRPC remaining `STARTING` | The public request does not wait for the background probe, succeeds through HTTP, and settles on HTTP only after the retry budget plus a successful HTTP operation. | IT + UT |
| Invalid, padded, or unknown transport mode | Factory creation fails locally with a controlled invalid-parameter exception. | UT |

## Search

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Search with default page inputs inside the workflow's unique Agent-name scope | Enabled visible Agents with an online latest Version are returned in stable pages without depending on unrelated shared-server data. | IT |
| Search by literal `agentNameContains` | Only case-sensitive literal contains matches are returned; `%`, `_`, and `\` are not interpreted as datastore wildcards. | OpenAPI IT + Java SDK IT |
| Search by multiple `tagsAll` | Every returned Agent contains all requested tags. | IT |
| Search by multiple `protocolsAny` | Every returned Agent contains at least one requested protocol. | IT |
| Combine name, tags, protocols, and pagination | Filters compose with AND semantics except `protocolsAny`, and page metadata is correct. | IT |
| Search an empty namespace or unmatched filter | A successful empty `Page` is returned. | IT |
| Null request, invalid page, duplicate filter values, or invalid protocol | A controlled local invalid-parameter exception is raised and no request is sent. | UT |
| gRPC ability is `SUPPORTED` | Exactly one Search RPC is sent and the typed page is returned. | UT |
| gRPC ability is `NOT_SUPPORTED` or `UNKNOWN` | A local unsupported error is raised without legacy or HTTP fallback. | UT |
| HTTP transport | The documented GET query, auth resource, and stable HTTP Client id are used. | IT + UT |
| AUTO read and gRPC is not currently available | HTTP is selected immediately without waiting for asynchronous reconnect. | IT + UT |
| AUTO read encounters a gRPC connection-class failure | The read may be repeated through HTTP; a definite business error is returned without fallback. | UT |

## Discover

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Discover with no Version or label | Definition metadata resolves from latest while Runtime Endpoints aggregate bindings compatible with every online Version. | IT + UT |
| Discover with explicit `label=latest` | Definition metadata and Runtime Endpoints are both restricted to the current latest Version. | IT + UT |
| Discover exact Version and custom label references | Each reference resolves definition metadata and Runtime Endpoints for only that exact resolved Version. | IT |
| Discover with no Filter | All permitted call interfaces and Endpoint sources are retained. | IT |
| Filter by protocol and protocolVersion | Matching interfaces remain; no match returns `callInterfaces=[]`. | IT |
| Filter by Endpoint source | Matching interface remains; no matching source returns `endpointSets=[]`. | IT |
| Filter by transport or metadata | Matching Endpoint set remains; no matching Endpoint returns `endpoints=[]`. | IT |
| Combine all Filter dimensions | The complete typed result is filtered at the documented levels. | IT |
| Runtime Endpoint is unhealthy | Discover retains it with `healthy=false`; no implicit selection occurs. | IT |
| Target does not exist, is disabled, or has no visible online Version | A controlled not-found exception is returned. | IT |
| Version and label are both set, or reference/filter is invalid | The SDK rejects the call locally without mutating the input. | IT + UT |
| gRPC ability is missing or unknown | A local unsupported error is raised and no remote request is sent. | UT |
| HTTP and gRPC transport parity | Both transports return equivalent typed snapshots for the same server state. | IT |
| AUTO with available or never-connected gRPC | The same typed snapshot is returned through gRPC or immediate HTTP routing respectively. | IT + UT |

## Definition And Version Evolution

The matrix covers operation-order equivalence classes rather than a parameter
Cartesian product. For one publisher, Register is a complete replacement, so
the meaningful orderings are Endpoint-before-definition, definition-before-
Endpoint, and replacement across an already-online Version.

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Publish Version 1, then register its exact Endpoint Batch | Omitted, explicit-latest, and exact-Version Discover all return Version 1 and the Runtime Endpoint. | IT |
| Register an exact Version 2 Batch before Version 2 exists | Registration succeeds; latest Version 1 remains discoverable but the Version 2-only Endpoint is filtered out. | IT |
| Publish Version 2 before registering its Endpoint | Search and both selector modes use Version 2 metadata. Omitted Discover retains the Version 1 Endpoint, while explicit latest returns an empty Runtime set. | IT + UT |
| Register Version 2 from a publisher independent of Version 1 | Omitted Discover returns Version 1 and Version 2 Endpoints with their bindings; explicit latest returns only Version 2. | IT + UT |
| Take Version 1 offline while Version 2 remains online | Omitted Discover removes the Version 1-only Endpoint; explicit latest is unchanged and does not emit a duplicate callback. | IT + UT |
| Keep a custom label on Version 1 while latest advances | Label Discover remains on Version 1 and does not follow latest implicitly. | IT |
| Move the custom label from Version 1 to Version 2 | Label Discover and the matching subscription atomically move to Version 2. | IT |
| Publish Version 3 before registering its exact Batch | Explicit-latest Discover changes to Version 3 with an empty Runtime set; omitted Discover retains Endpoints compatible with older online Versions. The later Register changes the relevant Runtime source revision. | IT |
| Exact Version 1 subscription while latest moves through later Versions | It does not receive a Version-change callback; it changes only if the Version 1 content or matching source revision changes. | IT + UT |
| Omitted-selector subscription through a latest change | It receives latest metadata immediately but keeps older online-version Endpoints until their Versions go offline. | IT + UT |
| Explicit-latest subscription through Endpoint replacement and Version changes | It switches strictly to the new latest pool, including an intentionally empty interval, and suppresses unchanged polls. | IT + UT |
| Search after multiple online Versions | One catalog entry lists all online Versions in descending SemVer order and reports the current latest. | IT |
| Register or deregister Runtime Endpoints without changing definitions | Search catalog fields and Version membership remain unchanged because Runtime Endpoints are not indexed. | OpenAPI IT + Java SDK IT |
| Take one of two online Versions offline, then take the final Version offline | The catalog first converges to the remaining Version, then the Agent leaves Search entirely. | OpenAPI IT + Java SDK IT |
| Publication range spans multiple Versions | Every matching exact/latest Discover sees the shared Endpoint; a nonmatching Version does not. | IT + UT |
| Offline and then bring the current latest online while another Version remains online | Search/Discover follow the server-managed recalculated latest without changing publisher intent. | IT |

## Agent Subscription And Watch

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Subscribe to an existing target with both Watch abilities | The method returns the current snapshot, installs one connection-scoped gRPC Watch, and refreshes only through Hint followed by Discover. | IT + UT |
| Subscribe through explicit HTTP or AUTO without negotiated Watch ability | The method returns the same snapshot and installs the intent into one generation-based HTTP Batch Long Poll. | IT + UT |
| HTTP Batch Long Poll reaches its timeout without a change | No listener callback is emitted; the same current Watch generation is sent again. | IT + UT |
| Add or remove one HTTP Watch while an older request is in flight | The client cancels the superseded request, advances generation, sends the complete current list, and ignores a late older response. | IT + UT |
| Subscribe before the target exists | The method returns `null`, emits at most one typed `UNAVAILABLE` event, retains a bounded local-pending intent, and delivers a complete `SNAPSHOT` after the target appears. | IT + UT |
| Repeated not-found polls | No duplicate `UNAVAILABLE` or empty snapshot is delivered and the subscription remains active. | UT |
| Poll returns the same Version, digest, and source revisions | No duplicate listener event is delivered. | IT + UT |
| Resolved Version changes | One complete replacement event is delivered. | IT + UT |
| `contentDigest` changes | One complete replacement event is delivered. | UT |
| Any `sourceRevision` changes | One complete replacement event is delivered. | IT + UT |
| A Runtime binding changes while its Endpoint payload and health stay equal | The v2 Runtime revision changes because discovery-visible binding provenance is part of the snapshot. | UT |
| A Filter produces a typed empty result | That result is cached and can replace an earlier non-empty snapshot. | UT |
| Same reference/filter and two listener instances | Canonically equivalent intents share one Wire intent while listener identities remain isolated and both receive changes. | IT + UT |
| Repeat subscribe with the same listener identity | Only one Wire intent and one callback per change are retained. | UT |
| Reach the configured local subscription limit | The last admitted subscription remains active; because the current API installs one subscription per call, the next distinct key throws `CLIENT_OVER_THRESHOLD` before Discover, cache insertion, or scheduling. A future batched Wire Watch operation must admit or reject the whole batch from the pre-operation watermark without partial caching. | IT + UT |
| Unsubscribe at the configured limit, then subscribe another key | The released slot is immediately reusable and the new subscription is scheduled normally. | IT + UT |
| Unsubscribe with the same reference/filter/listener | Only that listener stops; other listeners continue. | IT + UT |
| Unsubscribe an absent or different listener | The operation is an idempotent no-op. | UT |
| Listener throws | Per-listener ordered delivery isolates the failure so refresh and other listeners continue. | IT + UT |
| gRPC Hint is duplicate, stale, or carries the already-materialized fingerprint | It is acknowledged only after dirty state is durable; stale/unknown connection-scoped keys are rejected and an unchanged authoritative result produces no callback. | UT |
| gRPC Watch admission exceeds the configured server watermark | The SDK surfaces `OVER_THRESHOLD`, removes the rejected route and Watch state, and can reuse capacity after unsubscribe. | IT + UT |
| gRPC connection is replaced | The old wire key is discarded, current intents are resubscribed on the new connection, and the first required refresh cannot be lost between Subscribe and ACK. | Directed IT + UT |
| HTTP Watch uses the stable HTTP Client id | The binding remains attributable to one SDK client without renewing Publisher liveness. | IT + UT |
| HTTP Watch binding is unsupported or persistently unavailable | Existing intents move once to bounded Discover polling; business rejections do not silently fall back. | UT |
| HTTP Watch capacity rejects a generation after one addition | Only that latest addition receives a terminal `UNAVAILABLE`; the previous accepted batch stays active and a released slot is reusable. | IT + UT |
| Shutdown during Watch or polling | Pending long-poll, refresh, retry, and polling tasks are cancelled and no callback is delivered after shutdown. | IT + UT |

## Complete Endpoint Publication

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Register before Agent definition exists | Publication succeeds but Discover stays not found until the Agent becomes discoverable. | IT |
| Register one or many Endpoints | The complete Batch becomes visible in the matching Runtime source. | IT |
| Repeat an identical complete Batch | The call succeeds idempotently and produces no duplicate Endpoint. | IT |
| Replace a Batch with changed Endpoint payload | The new complete Batch replaces old endpoints and binding values. | IT + UT |
| Replace a Batch while omitting a previous Endpoint | The omitted Endpoint is removed. | IT |
| Publish two protocols | Each `(namespace, agent, protocol)` state is independent and Discover filters correctly. | IT + UT |
| Publish from two SDK instances | Contributions aggregate by natural key and one publisher deregistration does not remove the other. | IT |
| Cross the configured local publication watermark with one complete Batch | When the pre-operation Endpoint-entry count is below the watermark, the whole Batch is admitted and cached even if the resulting count exceeds it; no partial Batch is retained. | IT + UT |
| Grow while already at or above the local publication watermark | Equal-size or shrinking replacement remains allowed, while a new identity or growing replacement throws `CLIENT_OVER_THRESHOLD` without entering either publication or gRPC redo cache. | IT + UT |
| Server rejects publication growth at its per-Client watermark | The SDK surfaces `OVER_THRESHOLD`, removes the rejected identity from all redo/heartbeat state, and accepts it after enough Endpoint entries are deregistered. | OpenAPI IT + Java SDK IT + UT |
| Missing range | The server receives a valid Batch whose range defaults to exact runtime Version semantics. | IT + UT |
| Duplicate natural key, invalid URI/transport/version/range, empty Batch, or `healthy` input | The SDK rejects the Batch locally and does not retain invalid redo intent. | IT + UT |
| Caller changes Batch or Endpoint objects after registration | Stored expected state and redo payload remain the original canonical copy. | UT |
| gRPC endpoint ability is unsupported or unknown | The SDK fails locally without HTTP or legacy fallback. | UT |
| HTTP registration | Stable Client id and required module header are sent; returned liveness intervals schedule heartbeat. | IT + UT |

## Partial And Complete Deregistration

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| Remove one natural key from a multi-Endpoint Batch | SDK sends one complete replacement registration containing only the remainder. | IT + UT |
| Remove the final natural key | SDK sends one whole-publication deregistration and removes local expected state. | IT + UT |
| Deregister an unknown local publication or natural key | The call succeeds as a no-op and sends no remote mutation. | IT + UT |
| Deregister the same key repeatedly | Every later call remains a no-op. | IT |
| Deregistration URI differs only in path, query, or host spelling | Matching follows canonical host, effective port, and transport natural identity. | UT |
| Deregistration attempts to include priority, weight, metadata, or health | The SDK rejects the request locally. | UT |
| Deregister one protocol while another exists | The other protocol remains registered and discoverable. | IT |
| Replacement registration fails after local removal | The reduced desired Batch remains available for a later reconnect redo. | UT |
| Whole deregistration fails after the last local removal | A deregistration intent remains until completion or shutdown. | UT |

## Heartbeat, Retry, Reconnect, And Redo

| Scenario | Expected result | Coverage |
| --- | --- | --- |
| HTTP heartbeat succeeds | The same Client id is used and the next heartbeat follows returned effective intervals. | UT |
| HTTP query occurs between heartbeats | It does not extend Publisher liveness. | IT |
| HTTP operation returns `HTTP_CLIENT_NOT_FOUND (50404)` | All publications for that HTTP SDK instance are marked unregistered and replayed as complete Batches. | Directed IT + UT |
| Transient HTTP heartbeat failure other than 50404 | Expected publication state is retained and the later heartbeat can recover without creating a new Client id. | UT |
| gRPC disconnect then reconnect | Every expected publication is marked unregistered and replayed once on the new connection. | UT |
| gRPC ability changes after reconnect | Ability is checked again; unsupported or unknown prevents replay on that connection. | UT |
| Write timeout with unknown result | The SDK retains intent but does not blindly switch transport. | UT |
| A retained write later receives a parameter or authorization error | Redo stops, the prior confirmed publication is restored, or the first invalid intent is discarded. | UT |
| HTTP and gRPC services publish the same logical Agent | Transport-owned redo records and deregistration remain isolated. | UT |
| Shutdown with several protocol publications | Each whole publication is best-effort deregistered before transport shutdown. | UT |

## Directed Real-Server Lifecycle

The opt-in reconnect case uses the same production distribution and public
`AiService`/`AgentMaintainerService` APIs as normal IT. The JUnit process never
starts or stops Nacos. It writes a ready marker, an external harness stops and
restarts the standalone process with the same data directory, and the test
continues only after the harness writes a restarted marker.

| Phase | Operations and assertions | Coverage |
| --- | --- | --- |
| Initial server | Create and publish a legacy-compatible Version 1; pre-register legacy exact-Version Endpoints for Versions 1 and 2 into the canonical Runtime Service; register independent protocol-neutral gRPC and HTTP Endpoint Batches on the same SDK connection; Search, legacy SERVICE query, latest/exact Discover, Runtime bindings, and gRPC/HTTP Watch subscriptions agree without publication overwrite. | Directed IT |
| Server unavailable | Keep the same gRPC and HTTP SDK instances and their local subscription/publication intent; both transports observe connection unavailability, but the client process stays alive and no local intent is deleted. | Directed IT + UT |
| Same server restarted | The gRPC SDK reconnects, redoes its protocol-neutral complete Batch plus both legacy exact-Version publications, negotiates Watch again, and resubscribes with a new connection-scoped key. The HTTP heartbeat receives `HTTP_CLIENT_NOT_FOUND`, retains the same external HTTP client id, creates fresh server state, and re-registers its complete Batch. Legacy child publishers are rebuilt on the new connection, Runtime bindings recover independently, the gRPC Watch resumes, and HTTP Batch Long Poll converges. | Directed IT + UT |
| Endpoint-first upgrade after reconnect | Create and publish Version 2 after its legacy Endpoint was already registered and recovered; legacy SERVICE query, RAD Discover, and both subscriptions immediately resolve that canonical Runtime Endpoint. Later protocol-neutral gRPC and HTTP publishers add their independent contributions without replacing it. | Directed IT |
| Exact and label checks after reconnect | Exact Version 1 remains resolvable, exact/latest Version 2 agree through both transports, and a moved custom label resolves Version 2. | Directed IT |
| Cleanup | Unsubscribe both listeners, deregister both protocol-neutral final publications and the legacy Version 2 publication, verify the exact Runtime pool becomes empty, delete the Agent, and shut down clients while the restarted server remains usable for later IT. | Directed IT |

The directed case has a bounded wait for both markers and every server/client
convergence assertion. Missing harness coordination produces a skipped or
failed targeted run rather than a sleeping normal CI test.

## Compound Standalone Workflows

| Workflow | Cross-checks | Coverage |
| --- | --- | --- |
| Maintainer creates and publishes an Agent, then SDK Search and Discover | Admin write is visible through both Client read operations. | IT |
| Legacy A2A SDK releases an AgentCard and old exact-Version Endpoints for Versions 1 and 2, then Console Runtime Snapshot, RAD, legacy SERVICE, and direct Naming reads inspect them; Maintainer publishes Version 2, then omitted/default and explicit-latest discovery compare pools | Legacy and protocol-neutral surfaces share the canonical Agent definition and Runtime Service; duplicate release does not overwrite an online Version; pre-registration does not create a definition; exact bindings survive independently; the historical Version-specific Naming service remains empty in Beta; omitted selection aggregates both online Versions while explicit latest contains only Version 2. | IT |
| SDK pre-registers Endpoint, Maintainer creates/publishes Agent, SDK Discover | Pre-registration becomes visible without implicit definition creation. | IT |
| Maintainer publishes Agent, SDK registers/replaces/partially deregisters/finally deregisters | Discover observes full replacement, remainder, and empty Runtime source in order. | IT |
| Two SDK publishers register the same Endpoint, one deregisters, then the other deregisters | Aggregated visibility remains until the last contribution is removed. | IT |
| Two protocols are published and filtered independently | Search protocol catalog and Discover protocol filter agree. | IT |
| Subscription starts before Agent creation, then definition and Runtime Endpoint appear | Listener receives complete snapshots only when the public fingerprint changes. | IT |
| Subscription is removed, then definition or Runtime Endpoint changes | No post-unsubscribe callback occurs. | IT |
| Same workflow through gRPC and explicit HTTP clients | Query shapes and publication semantics are transport-equivalent. | IT |
| Maintainer publishes three Agents, then gRPC and HTTP SDKs immediately query the current snapshot and subsequently page/filter the converged catalog while one Agent gains a Version, receives a Runtime Endpoint, and is taken offline Version by Version | Both transports remain available without a readiness error, then return the same stable order and complete catalog after convergence; typed filters compose identically, Endpoint publication does not alter Search, and lifecycle projection converges without stale Versions. | IT |
| Public and custom namespace workflows run together | Search, Discover, subscription, and publication never cross namespaces. | IT |
| Version 1 online + Version 2 Endpoint pre-registration + Version 2 publish | Search, latest/exact/label Discover, and subscriptions agree at every transition. | IT |
| Version 1 online + Version 2 publish before Endpoint registration + independent Version 2 publisher | Omitted selection preserves the rollout pool across the transition, explicit latest observes only Version 2, and both subscriptions converge without duplicate callbacks. | IT |
| Real standalone restart with active gRPC/HTTP publications and both Watch transports | The same live SDK process restores both transport-owned complete Batches, resubscribes the gRPC Watch, resumes HTTP Batch Long Poll, then observes a later Version and both Endpoints without duplicate callbacks. | Directed IT |

## Deferred In This Phase

| Item | Reason |
| --- | --- |
| Public `getAll` / `selectOneHealthy` helper API shape | The design states local selection semantics but does not yet specify a stable Java type and method signature. It does not block Search, Discover, polling, or publication and is recorded rather than invented in this phase. |
| Agent management-metadata change notification | `AgentDiscoveryResult` intentionally excludes display name, description, tags, provider, and other management metadata. Its polling fingerprint contains only resolved Version, Version `contentDigest`, and Endpoint `sourceRevision` values. A future requirement to subscribe to forced updates of published Agent metadata needs a Search/catalog subscription or an explicit RAD contract extension; it is not inferred by the current Discover subscription. |
| Packet loss at individual frames and unknown gRPC write-result ambiguity | Covered with deterministic unit fault injection; a real single-node process restart is covered separately, while frame-level fault injection is not stable standalone IT. |
