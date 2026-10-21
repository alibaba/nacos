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

# Agent SDK core regression acceptance matrix

This matrix organizes the 3.3 SDK changes by observable behavior, independently of
temporary implementation phase numbers. The contract is defined by the
[Agent API spec](../../specs/en/ai/agent-api-spec.md#21-java-sdk-contract),
[A2A compatibility spec](../../specs/en/ai/a2a-agent-spec.md), and
[SDK testing spec](../../specs/en/testing/java-sdk-integration-test-spec.md).

`Covered` means the stated assertions have execution evidence at the specified
layer; `Partial` means some required assertions remain; `Pending` means the new
acceptance workflow has not run. UT evidence is explicitly different from real
server IT evidence. Existing evidence refers to the 2026-10-20 validation of
production commit `cfff3cd1b`; new upgrade evidence is recorded below after execution.
JSON adapters are a secondary compatibility dimension, never additional functional
scenarios. No finite matrix establishes correctness for every possible interleaving.

Abbreviations: **D** = `AgentDiscoveryServiceJavaSdkITCase`, **R** =
`A2aRadRoutingJavaSdkITCase`, **M** = `AgentWatchManagerTest`, **H** =
`HttpAgentWatchTransportTest`. Names below are executable method names, not plans
inferred solely from line coverage. Grouped assertions in a row must all pass.

## Existing A2A against 3.2.3

| ID | Required assertions | Layer / executable evidence | Status |
| --- | --- | --- | --- |
| OLD-01 | All 18 old overloads through AiService and agent(), with grpc/http/auto configuration; old wire remains usable | R `allSignaturesOnReleasedOldServer` (6 configurations) | Covered |
| OLD-02 | First release becomes readable/latest; later release, duplicate release and explicit version read retain old semantics | Same method; these are legacy semantics, not native draft semantics | Covered |
| OLD-03 | Single/batch endpoint registration, complete replacement, whole exact-Version deregistration, SERVICE projection | Same method; UPG-01/03 additionally compare multi-address whole-Version removal before/after upgrade. Native RAD partial removal has a different contract | Covered |
| OLD-04 | Both subscription signatures deliver initial Cards; cancellation and repeated shutdown work | Same method; ongoing old-server update delivery also verified by UPG-01 | Covered |
| OLD-05 | Capability becomes NOT_SUPPORTED after negotiation; native publish/search/discover/watch/register reject with 501; local cleanup remains possible | Same method, `verifyUnsupportedNative` | Covered |
| OLD-06 | Old application bytecode + replacement SDK, old third-party implementation defaults, released SDK on new server | `AiServiceBinaryCompatibilityJavaSdkITCase` three current-server cases plus old-server smoke; released binary is 3.2.4, not mislabeled 3.2.3 | Covered |

## RAD Discover and publication

| ID | Required assertions | Layer / executable evidence | Status |
| --- | --- | --- | --- |
| DISC-01 | Default/custom namespace, exact version, label/latest, combined filters; input objects unchanged | D `shouldSearchDiscoverAndIsolateNamespaces` | Covered |
| DISC-02 | Omitted selector rollout pool differs from explicit latest; online-version ranges applied | D `shouldSeparateDefaultRolloutPoolFromExplicitLatest`, `shouldApplyPublicationRangeAcrossOnlineVersions` | Covered |
| DISC-03 | Runtime/Declared preference and explicit filtering; no hidden-source resurrection | D `shouldRespectSourcePreferenceForDiscoveryAndWatch` | Covered |
| DISC-04 | Disabled contributions excluded; unhealthy/empty endpoints remain valid snapshots | D `shouldRetainDisabledContributionsAndEnableByReplacement`, `shouldWatchPublicAgentHealthAndPartialDeregistrationWithUnifiedModels`; M `emptyAndUnhealthyEndpointsRemainCompleteSnapshotsAndRepeatedAbsenceIsDeduplicated` | Covered |
| DISC-05 | Complete replacement, idempotence, partial deregistration, independent publishers and version bindings | D `shouldReplaceAndPartiallyDeregisterCompletePublications`, `shouldAggregateIndependentSdkPublishers`, `shouldPreservePerEndpointBindingsAcrossReplacementAggregationAndPartialRemoval` | Covered |
| DISC-06 | Missing/invalid references and filters fail predictably before mutation; first version/draft/no-op publication semantics | D `shouldRejectInvalidBoundariesBeforeRemoteMutation`; `AgentPublishJavaSdkITCase` | Covered |
| DISC-07 | Distinguish confirmed Agent absence from missing namespace, raw HTTP 404 and null/malformed success; access failures never become target-absence events | `AiGrpcClientAgentTest.discoveryPreservesTargetAbsenceButDoesNotMisclassifyNamespaceOrRaw404`, `AiHttpClientProxyAgentTest.discoveryDistinguishesConfirmedAbsenceFromRawHttp404AndEmptySuccess`, M `activationAndInvalidResultFailuresCleanRejectedState` (controlled UT) | Covered |
| DISC-08 | Explicit converter preserves complete descriptor/security data and server-selected source; missing/invalid descriptor or mismatched name/version fails distinctly; bad conversion does not end Watch | D `shouldConvertRealDiscoverySnapshotsExplicitly`, `shouldConvertOnlyServerVisibleRuntimeContributions`, `shouldKeepConversionUnderListenerControl`; `AgentConvertersTest.rejectsDescriptorIdentityMismatch` and malformed-input UT | Covered |

## Watch lifecycle, data events and recovery

| ID | Required assertions | Layer / executable evidence | Status |
| --- | --- | --- | --- |
| WATCH-01 | Initial result only via Listener; each new Listener initialized once, repeated registration idempotent | D `shouldInitializeEachListenerOnceBeforeSubsequentChanges`; M initial-delivery tests | Covered |
| WATCH-02 | A read before subscription, B before activation: fingerprint recheck delivers B; no B then stale initial A | M `dirtyHintInsideInitialTransportActivationSchedulesRefreshAfterAck`, `lateEnqueuedSnapshotCannotOverwriteNewerInitialDelivery`; transport ACK/generation UT | Covered |
| WATCH-03 | Missing Agent/version/label retained; one absence transition; creation/deletion/identical recreation observed | D `shouldKeepMissingVersionAndLabelSubscriptionsUntilTargetsResolve`, `shouldNotifyDeletionAndIdenticalRecreationWithoutPushingEmptyLegacyCard`, `shouldDiscoverPreRegistrationAndPollUntilAgentAppears` | Covered |
| WATCH-04 | Description/tags/latest/Runtime changes update complete snapshot; identical fingerprints suppress duplicates | D `shouldWatchCurrentMetadataForAnExactOldVersion`, `shouldWatchExistingAgentOnlyWhenCompleteFingerprintChanges` | Covered |
| WATCH-05 | Initial auth/local capacity/gRPC server capacity failures reject only new intent; slots reusable | D `shouldEnforceConfiguredLocalSubscriptionCapacityAndReuseSlot`, `shouldSurfaceServerWatchCapacityAndReuseSlot`; initial-rejection UT | Covered |
| WATCH-06 | Existing subscription survives real permission revoke/restore with no access-error UNAVAILABLE and no resubscribe | D `shouldRecoverSameGrpcAndHttpWatchesAfterPermissionIsRestored` | Covered |
| WATCH-07 | Existing subscription survives 403/409/50105/parameter/capacity errors, cleanup failure and failed binding activation | M `allWireFailuresPreserveDataAndCoalesceRecoveryWithoutCallbacks`, `backgroundRejectionsDuringRefreshRetryWithoutErrorEvents`, `pendingDiscoverRejectionsRetainIntentAndRecover`; transport UT. Real post-acceptance migration rollback is not claimed | Covered |
| WATCH-08 | Temporary Discover failure retries; one failing Agent does not delay another; new Hint during refresh is not lost | M `transientFailureRetainsSnapshotAndDirtyUntilBoundedRetry`, `failedAgentRefreshDoesNotDelayAnotherAcceptedAgent`, `concurrentHintDuringRefreshSchedulesOneFollowingRefresh` | Covered |
| WATCH-09 | Background executor rejection retains work; independent retry resumes; canceled/previous-activation work cannot publish stale state | M `refreshSubmissionRepeatedRejectionsEventuallyDeliverChangedData`, `refreshFromPreviousActivationCannotOverwriteRecoveredState`; `AgentWatchTaskSchedulerTest` | Covered |
| WATCH-10 | Real server restart with same client restores both transports and publication redo; later updates delivered | D `shouldRestoreGrpcAndHttpPublicationsAndWatchesAfterRealServerRestart` | Covered |
| WATCH-11 | gRPC reconnect/late keys/ACK errors; HTTP timeouts/late generations/batch changes; AUTO and polling fallback ownership | `GrpcAgentWatchTransportTest`, H, `AgentWatchTransportRouterTest`, `PollingAgentWatchTransportTest`; D `shouldBatchHttpWatchesAcrossTimeoutUnsubscribeResubscribeAndShutdown`, AUTO IT | Covered |
| WATCH-12 | Unsubscribe/resubscribe/shutdown stop local work; one listener cancellation does not stop others | D `shouldShareCanonicalPollingIntentAndIsolateListeners`, HTTP batch lifecycle IT; M cancellation races | Covered |
| WATCH-13 | Legacy A2A suppresses null Card and resumes later complete Card without lifecycle callbacks | D `shouldNotifyDeletionAndIdenticalRecreationWithoutPushingEmptyLegacyCard`; `A2aRadWatchAdapterTest` | Covered |

## HTTP growth quota

| ID | Required assertions | Layer / executable evidence | Status |
| --- | --- | --- | --- |
| QUOTA-01 | Failed growth retains additions, rolls back to acknowledged set, old watch updates; no quota data event | D `shouldRetainHttpQuotaAdditionsAndRecoverAfterCancelingOldWatch`; H `quotaRollbackRetainsAllAdditionsAndOnlyGrowthSuccessClearsRestriction` | Covered |
| QUOTA-02 | Known exhaustion synchronously rejects further growth; duplicate intent remains allowed | Same IT/UT; existing-set success alone cannot clear growth rejection | Covered |
| QUOTA-03 | Cancel old acknowledged item advances pending retry without resubscribe | Same IT; H `cancelAcknowledgedItemAdvancesPendingGrowthWithoutResubscribe` | Covered |
| QUOTA-04 | All pending canceled: cooldown allows one real new probe; no empty request | H `cancelAllPendingAllowsOnlyOneRealGrowthProbeAfterCooldown` | Covered |
| QUOTA-05 | Never acknowledged: retry entire accepted set; old responses/canceled timers cannot acknowledge unsent items or reset current work | H `neverAcknowledgedQuotaRetriesWholeCurrentSetAndNeverEmptyRequest`, generation/timer race tests | Covered |

## Listener delivery and blocking I/O

| ID | Required assertions | Layer / executable evidence | Status |
| --- | --- | --- | --- |
| LISTEN-01 | Blocked callback retains latest state only; observation and independent Listener continue; release delivers latest without another change | D `shouldCoalesceSlowListenerWithoutBlockingWatchObservation` on HTTP/gRPC; M `blockedCallbackCoalescesThousandsOfChangesAndConvergesIndependently` (2,048 changes) | Covered |
| LISTEN-02 | Default pool saturation does not block Discover/network work; pending memory bounded by subscriptions/listeners, not number of changes | M `realDefaultCallbackPoolSaturationDoesNotBlockDiscover`, latest-state invariant test; no heap benchmark or unlimited executor isolation claimed | Covered |
| LISTEN-03 | getExecutor throws or execute rejects: retain initial/latest state and recover without extra Discover/change | D `shouldRecoverListenerSubmissionWithoutAnotherAgentChange`; M `failedInitialDeliveryRecoversWithoutDiscoverOrAnotherChange` | Covered |
| LISTEN-04 | Follow-up rejection, executor enqueue-then-throw and inline execution cannot duplicate or strand latest delivery | M `rejectedFollowupAfterRunningCallbackRetainsLatestState`, `staleTaskFromExecutorThatEnqueuesThenThrowsCannotDuplicateDelivery`, `lateExecutorFailureCannotResetFollowupAlreadyQueuedByCallback` | Covered |
| LISTEN-05 | onEvent throws: log once, no automatic replay, future changes still delivered | D `shouldObserveLaterChangesAfterListenerThrows`; M `invokedListenerFailureIsNotReplayedButFutureChangesStillArrive` | Covered |
| LISTEN-06 | Listener result copies isolated; absence/recreation may coalesce; cancel/close invalidates queued and retry work | M listener-isolation tests, `queuedAbsenceAndIdenticalRecreationCanCoalesce`, `cancelOrShutdownDuringRetrySubmissionLeavesNoDelivery` | Covered |
| LISTEN-07 | Rate-limited contextual diagnostics; diagnostic/copy failures do not break observation or lose delivery | M `diagnosticPathsPreserveAbsenceAndCleanupSemantics`, `snapshotPreparationFailureRetainsDeliveryWithoutRequery`; transport rejection/log UT | Covered |

## Real 3.2.3 to 3.3.0-RC upgrade acceptance

The client is the **current 3.3 SDK using old A2A methods before the server upgrade**.
A released 3.2.x binary cannot acquire RAD support merely by restarting. Start a
fresh disposable 3.2.3 standalone, preserve its Derby data while stopped, then start
the 3.3.0-RC server on the same address with that data. Keep the test JVM and original
AiService objects alive through the server transition. Never replace the database
with a fresh empty server or re-publish historical definitions to simulate migration.

| ID | Required assertions | Layer / executable evidence | Status |
| --- | --- | --- | --- |
| UPG-01 | Before upgrade: publish v1/v2, latest/exact reads, endpoint writes and changed Card delivery on original subscriptions; NOT_SUPPORTED | New `A2aServerUpgradeJavaSdkITCase` | Covered |
| UPG-02 | After real binary/data upgrade: historical v1/v2 still readable before any write; original clients and subscriptions survive; endpoint redo visible | Same external-process workflow | Covered |
| UPG-03 | Original client observes capability SUPPORTED but old API remains old wire; publish v3, endpoint register/remove, original polling Listener receives v3 | Public exact-read latestVersion shape distinguishes old wire from RAD adapter; no private reflection | Covered |
| UPG-04 | Close/recreate AiService: old A2A API now uses RAD; initial v3, publish v4, callback v4, endpoint replace/remove all work | Client lifecycle restart in same test JVM, not an OS/JVM process restart | Covered |
| UPG-05 | Native capability/discover/subscribe/publish v5/register/replace/deregister; initial and subsequent full snapshots; no error data events | Same workflow, grpc/http/auto configurations | Covered |
| UPG-06 | Cancel listeners and close clients; subsequent writes do not invoke removed listeners; cleanup isolated data/processes | Same workflow and external harness finally block | Covered |

## Cluster evidence remains separate

These are **three distinct tests**, previously reported as six skips because each
was selected with two JSON adapters. Their opt-in system properties were false in
the standalone run. This is a legitimate environment limitation, not a pass.

| ID | Test / required environment | Required assertion | Status |
| --- | --- | --- | --- |
| CLUSTER-01 | D `shouldConvergePinnedNodeDefinitionAndRuntimeChanges`; distinct A/B members | A-pinned HTTP/gRPC listeners observe metadata/definition/runtime mutations on A and B; cross-node fingerprints converge | Covered |
| CLUSTER-02 | D `shouldConvergeGrpcAndHttpWatchesAcrossRollingClusterRestart`; externally controlled cluster with quorum while a node is down | Original watches/readers/publishers converge while one member stops and returns. A synchronous connection/server error may precede retained publication redo; the test does not register again and still requires original Listener convergence within the reconnect deadline | Covered |
| CLUSTER-03 | D `shouldKeepPinnedWatchesReadableAndRecoverAfterPeerRestart`; distinct A/B and external B restart | A-pinned watches survive peer outage; after quorum recovery, later B changes arrive without resubscribe | Covered |

## Reporting and execution order

1. Audit this table against existing UT/IT execution reports. Historical execution
   is reusable when production and relevant tests have not changed; name new runs.
2. Execute the real upgrade workflow first, including writes at every stage.
3. Run added/changed SDK tests and required regression on the actual release SDK.
   Run both HTTP and gRPC behavior; list serializer parity only as a secondary check.
4. Report each group with assertion outcomes and evidence layer. Pending cluster
   and environment cases remain in the denominator of the complete target matrix.
   Strict coverage = Covered / total; effective = (Covered + 0.5 * Partial) / total.
   Do not turn 100% changed-line UT coverage into a 100% functional coverage claim.
5. SDK usage documentation remains deferred to its own repository. This document
   is the executable test acceptance record, not an external usage guide.

Execution on 2026-10-21: all 48 behavior groups are Covered (strict and effective
coverage 48/48 = 100% within this UT + IT acceptance scope). UPG-01..06 pass in one external-process workflow containing
all three grpc/http/auto configurations, with zero failure/error/skip. The
3.2.3 Derby data was migrated to a permanent CANONICAL 3.3.0-RC state with the
original clients alive. Publication, read, endpoint redo/removal and callback
assertions passed before and after client recreation. All three cluster workflows also pass in a separate three-node embedded-storage
fixture; the previous standalone skips remain historical evidence. The final
rolling/peer rerun restarted B once each without needing the bounded startup retry.
Earlier evidence retains one embeddedDumpService startup failure caused by a
missing nacos_config Raft Leader, and the initial synchronous registration failure.
This does not establish a general server-startup reliability guarantee.

### Reproduce the real upgrade

Install the current release SDK first (`-Prelease-nacos,!dev`), and supply the
unpacked 3.3.0-RC server distribution. From the repository root:

```bash
python3 test/java-sdk-test/run-a2a-server-upgrade.py \
  --distribution distribution/target/nacos-server-3.3.0-RC/nacos \
  --output target/agent-sdk-upgrade-validation
```

The output directory must be new, so failed evidence cannot be overwritten.
Defaults use main 18848, gRPC 19848, console 18080 and registry 19080; all are
checked before starting. The harness creates its own named Docker container,
copies stopped Derby data, retains the Maven/JUnit process, and cleans up only its
own container and server. It records server versions, binary/data hashes, migration
marker, test command, reports and server logs. This focused upgrade fixture uses
auth disabled and historical Naming shadow enabled. Auth revoke/restore is covered
by the separate authenticated Watch IT; shadow-disabled full migration remains
outside this simple upgrade acceptance workflow.
