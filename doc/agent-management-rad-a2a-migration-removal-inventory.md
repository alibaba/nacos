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
| `ai/.../a2a/A2aCompatibilityModeResolver.java#resolve()` | Single temporary migration-package call from the legacy facade router. | Delete the marked call/branch; resolve the remaining supported static mode directly. | Public A2A facade routing until separately deprecated. | Remove AUTO migration assertions; retain supported facade route tests. |
| `core/.../cluster/MemberMetaDataConstants.java` | Temporary migration ability, policy, and ACK keys in member basic metadata. | Delete the three constants and remove them from `BASIC_META_KEYS`. | Generic member metadata propagation. | Remove migration metadata tests; retain member propagation tests. |

Later implementation commits must append one row for every new migration class
or marked main-flow integration point before that commit is considered
complete. Removing a row before its code is removed is not allowed.
