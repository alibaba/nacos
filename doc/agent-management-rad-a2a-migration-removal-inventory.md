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

# Historical A2A Migration 4.0 Removal Inventory

This inventory tracks temporary compatibility code used only to migrate Nacos
3.0-3.2 A2A Config/Naming data. The authoritative behavioral contract is the
[Historical A2A Upgrade Migration Spec](../specs/en/ai/a2a-upgrade-migration-spec.md).

Every production class in the migration package carries the uniform
`TODO(remove in 4.0)` lifecycle declaration. Existing main-flow integration
points carry the searchable two-line comment required by that spec.

| Module/package/class/method | Integration point | Removal action in 4.0 | Retained dependency | Tests to remove/retain |
| --- | --- | --- | --- | --- |
| `ai/.../a2a/migration/A2aMigrationState.java` | Migration-only state machine. | Delete with the migration package. | Canonical Agent lifecycle and public compatibility facade. | Remove migration state tests; retain explicit CANONICAL facade tests. |
| `ai/.../a2a/migration/A2aMigrationMarker.java` | Internal Config marker DTO. | Delete marker parsing and transitions; an existing marker may remain as inert Config. | Canonical Agent/Version/Storage rows. | Remove marker tests; retain canonical restart/read tests. |
| `ai/.../a2a/migration/A2aMigrationLeaseRecord.java` | Internal renewable reconciliation lease DTO. | Delete with reconciliation orchestration. | Shared Config CAS capability. | Remove migration lease tests; retain Config CAS tests. |
| `ai/.../a2a/migration/A2aMigrationProgress.java` | Bounded diagnostic progress DTO. | Delete migration progress writes and diagnostics. | General observability infrastructure. | Remove migration progress tests. |
| `ai/.../a2a/migration/A2aMigrationMetrics.java` | Temporary low-cardinality state, reconciliation, cutover, Runtime mirror/shadow, retry, and latency metrics. | Delete migration-only gauges, counters, and timers with the migration package. | General meter registry infrastructure and long-lived RAD Watch metrics. | Remove migration metric tests; retain shared meter-registry and RAD Watch metric tests. |
| `ai/.../a2a/migration/A2aMigrationControlStore.java` | Reads/writes three internal Config control objects. | Delete the class and its internal data-id references. | Config query/publish services. | Remove control-store tests; retain Config service tests. |
| `ai/.../a2a/migration/A2aMigrationStateService.java` | AUTO authority, terminal latch, policy checks, marker/lease/progress orchestration. | Delete the class and restore the compatibility resolver to direct static-mode resolution. | Explicit CANONICAL behavior. | Remove AUTO migration tests; retain static mode and canonical regression tests. |
| `ai/.../a2a/migration/A2aMigrationLease.java` | Lease renewal, verification, loss, and release handle. | Delete with the migration package. | None. | Remove migration lease-owner tests. |
| `ai/.../a2a/migration/A2aHistoricalDefinitionSnapshot.java` | Immutable legacy summary/Version source fence. | Delete with historical source scanning. | None. | Remove snapshot/fingerprint tests. |
| `ai/.../a2a/migration/A2aHistoricalDefinitionScanner.java` | Paged legacy Config scan, strict validation, and source fingerprinting. | Delete the scanner and its direct legacy Config reads. | Generic Config query service. | Remove historical scanner tests; retain legacy facade read tests while the facade remains supported. |
| `ai/.../a2a/migration/A2aMigrationDefinition.java` | Migration-only normalized Agent target value. | Delete with the definition reconciler. | Canonical Agent API models. | Remove migration definition tests. |
| `ai/.../a2a/migration/A2aHistoricalDefinitionReconciler.java` | Converts a fenced legacy snapshot to one complete canonical Agent definition. | Delete the migration orchestrator. | Retain `A2aCanonicalDefinitionConverter` for the non-migration legacy facade. | Remove historical reconciler tests; retain converter/facade tests. |
| `ai/.../a2a/migration/A2aMigrationStorageVerifier.java` | Consistency-aware write/read-back verification for migration Storage writes. | Delete the migration verifier. | Retain generic `AgentVersionStorageService` consistency/listener delegation. | Remove migration storage-verifier tests; retain Agent Storage tests. |
| `ai/.../a2a/migration/A2aMigrationTargetStore.java` | Version-first/Resource-last idempotent target reconciliation and guarded orphan cleanup. | Delete migration-owned repair, conflict, and cleanup logic. | Canonical Agent repositories and Storage services. | Remove target-store migration tests; retain canonical repository/storage tests. |
| `ai/.../a2a/migration/A2aMigrationReconciliationTask.java` | Leased namespace/page scan, progress, retry, and two-pass orphan confirmation. | Delete the scheduled reconciliation task and its properties. | Generic executor and namespace services. | Remove reconciliation-task tests. |
| `ai/.../a2a/migration/A2aMigrationDefinitionHintReconciler.java` | Bounded, coalesced, best-effort write-after reconciliation hints while historical definitions remain authoritative. | Delete the queue, executor, and hint submission path. | Periodic canonical maintenance outside the migration window. | Remove hint-queue tests; retain generic managed-executor tests. |
| `ai/.../a2a/migration/A2aMigrationDefinitionWriteAfterHook.java` | Isolates successful historical definition responses from best-effort hint failure. | Delete the hook and restore direct legacy facade calls. | Historical facade behavior that remains separately supported. | Remove write-after isolation tests; retain legacy mutation response tests. |
| `ai/.../a2a/migration/A2aMigrationAgentMutationGuard.java` | Rejects generic Agent mutations of migration-owned projections before permanent cutover. | Delete the guard and its Agent-operation calls. | Canonical Agent visibility and mutation authorization. | Remove migration-owned guard tests; retain normal Agent mutation tests. |
| `ai/.../a2a/migration/A2aMigrationEndpointRouter.java` | Routes one old A2A logical publication to legacy-primary/canonical-mirror or canonical-primary/optional legacy-shadow physical layouts. | Delete the router, retry queue, connection-local desired state, and logical reservation calls. | Retain canonical Runtime registration, `A2aEndpointChildPublisherManager`, both explicit static compatibility branches, and the generic capacity gate. | Remove migration router tests; retain static handler, child publisher, Runtime capacity, and canonical Runtime tests. |
| `ai/.../a2a/migration/A2aRuntimeSnapshotComparator.java` | Normalizes historical and canonical exact-Version Runtime snapshots for the cutover gate. | Delete with Runtime equivalence and cutover orchestration. | Retain the pure canonical legacy Endpoint adapter used by the compatibility facade. | Remove migration comparator tests; retain Runtime mapper tests. |
| `ai/.../a2a/migration/A2aMigrationMemberView.java` | Immutable fingerprint of the exact ready cluster member set. | Delete with quiescing member gates. | Generic cluster membership. | Remove migration member-view tests; retain cluster membership tests. |
| `ai/.../a2a/migration/A2aMigrationQuiescingGeneration.java` | Binds an opaque quiescing generation to the accepted member view. | Delete with the migration state machine. | None. | Remove quiescing-generation tests. |
| `ai/.../a2a/migration/A2aMigrationDefinitionReadinessValidator.java` | Performs complete read-only Resource/Version/Storage verification rounds. | Delete the migration-specific validator. | Canonical Agent repositories and Storage reads. | Remove migration readiness tests; retain canonical read tests. |
| `ai/.../a2a/migration/A2aMigrationSearchReadinessGate.java` | Requires current shared Search projections before one-time cutover. | Delete the migration gate. | Shared AI Resource Search readiness and type handlers. | Remove migration gate tests; retain Search readiness tests. |
| `ai/.../a2a/migration/A2aMigrationRuntimeReadinessGate.java` | Scans active historical exact-Version services and required mirror retries before cutover. | Delete the migration gate. | Canonical RAD Runtime and Naming storage. | Remove migration Runtime-gate tests; retain RAD/Naming tests. |
| `ai/.../a2a/migration/A2aMigrationCutoverCoordinator.java` | Runs two complete zero-difference rounds under one lease and CASes into quiescing. | Delete with cutover orchestration. | Generic Config CAS and cluster membership. | Remove cutover coordinator tests. |
| `ai/.../a2a/migration/A2aMigrationQuiescingTask.java` | Installs local ACKs, enforces timeout/member rollback, and runs the final cutover gates. | Delete the scheduler plus quiescing-timeout and migration-lease-duration properties. | Canonical Agent/RAD services. | Remove quiescing/lease-takeover tests; retain canonical restart tests. |
| `ai/.../a2a/migration/A2aMigrationLegacyMutationGuard.java` | Rejects historical A2A definition mutations while quiescing. | Delete the guard and its compatibility-facade calls. | Static legacy/canonical mutation routing. | Remove write-fence tests; retain normal facade mutation tests. |
| `ai/.../a2a/A2aCompatibilityModeResolver.java#resolve()` | Single temporary migration-package call from the legacy facade router. | Delete the marked call/branch; resolve the remaining supported static mode directly. | Public A2A facade routing until separately deprecated. | Remove AUTO migration assertions; retain supported facade route tests. |
| `ai/.../a2a/A2aCompatibilityOperationService.java#notifyLegacyMutation()` | Main-flow write-after hook after successful LEGACY definition mutations. | Delete the marked field, constructor parameter, calls, and helper. | Static legacy/canonical facade selection. | Remove migration hint assertions; retain static route tests. |
| `ai/.../agent/AgentOperationService.java#checkMigrationMutable()` | Main-flow guard before modifying an existing migration-owned Agent projection. | Delete the marked field, optional setter, helper, and calls. | Existing visibility checks and canonical Agent mutations. | Remove migration guard assertions; retain all ordinary Agent lifecycle tests. |
| `ai/.../remote/handler/a2a/AgentEndpointRequestHandler.java` | Temporary single Endpoint migration-router branch before explicit LEGACY/CANONICAL routing. | Delete the marked state resolution and migration call; retain static legacy/canonical routing. | `LegacyA2aEndpointOperationService` and `CanonicalA2aEndpointOperationService`. | Remove migration branch assertions; retain single Endpoint facade tests. |
| `ai/.../remote/handler/a2a/BatchAgentEndpointRequestHandler.java` | Temporary Batch Endpoint migration-router branch before explicit LEGACY/CANONICAL routing. | Delete the marked state resolution and migration call; retain static legacy/canonical routing. | `LegacyA2aEndpointOperationService` and `CanonicalA2aEndpointOperationService`. | Remove migration branch assertions; retain Batch Endpoint facade tests. |
| `api/.../model/v2/ErrorCode.java#AGENT_MIGRATION_IN_PROGRESS` | Temporary detail code `50105` for migration-owned Agent mutation rejection. | Remove when no supported server can enter the historical migration write fence. | Standard Agent conflict and lifecycle error contracts. | Remove the enum lookup assertion with migration API tests. |
| `core/.../cluster/MemberMetaDataConstants.java` | Temporary migration ability, policy, and ACK keys in member basic metadata. | Delete the three constants and remove them from `BASIC_META_KEYS`. | Generic member metadata propagation. | Remove migration metadata tests; retain member propagation tests. |

Later implementation commits must append one row for every new migration class
or marked main-flow integration point before that commit is considered
complete. Removing a row before its code is removed is not allowed.
