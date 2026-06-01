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

# Naming Persistent CP Consistency Spec

This document defines Naming domain rules for persistent service CP
consistency. It refines the shared
[CP Consistency Spec](../design/foundation-cp-consistency-spec.md) and the
[Naming Consistency And Client State Spec](naming-consistency-client-spec.md).

## 1. Scope

This spec owns:

- persistent instance write, apply, snapshot, and recovery semantics;
- service metadata and instance metadata CP group boundaries;
- visibility of persistent instance and metadata changes to derived Naming
  serving state;
- unsupported or pending persistent-service operations.

It does not define ephemeral Distro synchronization, active health-check
algorithms, or client SDK redo behavior.

## 2. CP Groups

Naming uses separate CP groups for persistent service data and metadata:

| Group constant | Responsibility |
| --- | --- |
| `Constants.NAMING_PERSISTENT_SERVICE_GROUP_V2` | Persistent instance publish state. |
| `Constants.SERVICE_METADATA` | Service and cluster metadata. |
| `Constants.INSTANCE_METADATA` | Instance metadata. |

These groups are independent consistency domains. New behavior must not assume
that a write committed in one group is atomically committed in another group.

## 3. Persistent Instance Writes

Persistent instance register, update, and deregister operations are serialized
as instance store requests and submitted to
`Constants.NAMING_PERSISTENT_SERVICE_GROUP_V2`.

Persistent instance operations must reject an existing service whose service
type is ephemeral. A service identity must not be both persistent and ephemeral
in the same namespace and groupName scope.

Applying a persistent instance write must update the persistent IP-port client
state and publish local Naming events so publisher indexes, service storage,
metadata overlays, and push views can be rebuilt.

Persistent subscription state is not supported. Subscriptions remain
connection-based runtime state.

## 4. Metadata Writes

Service metadata and cluster metadata are written through
`Constants.SERVICE_METADATA`. Instance metadata is written through
`Constants.INSTANCE_METADATA`.

Service metadata updates must preserve the service type field when applying a
change over existing metadata. Metadata writes may create or connect service
singletons as needed so subsequent Naming views can resolve the service
identity.

Instance metadata add or change must publish service change events so discovery
views can be refreshed. Instance metadata delete removes the operational
metadata overlay for that instance.

Operational metadata has higher priority than runtime registration metadata in
the final instance view, as defined by the
[Naming Metadata And Selector Spec](naming-metadata-selector-spec.md).

## 5. Snapshot And Recovery

Persistent instance state must provide CP snapshot operations. The current
implementation stores persistent instance snapshot data as a checked snapshot
file and restores it into the persistent client manager.

Loading a persistent instance snapshot must:

1. update existing persistent clients from snapshot data;
2. create missing persistent clients from snapshot data;
3. remove clients that no longer exist in the snapshot;
4. emit the local Naming events required to repair derived indexes and service
   storage.

Metadata groups must also provide snapshots. Loading metadata snapshots must
rebuild in-memory metadata maps and keep service identity attached to metadata.

## 6. Visibility

A successful CP write means the operation was accepted by the corresponding
CP group. Runtime query and push visibility still depends on local apply and
derived serving state:

- the apply path updates client or metadata state;
- local events update indexes and service storage;
- discovery query reads the derived service view;
- subscriber push follows service change events.

Range queries over persistent services and metadata should document whether
they read local derived state or CP read state. Until a query explicitly routes
through CP read semantics, it should be treated as a local serving-state view.

## 7. Failure And Unsupported Behavior

- If the CP group has no available leader or cannot commit, the write must
  fail instead of falling back to Distro.
- Persistent batch registration is not a completed capability and must not be
  documented as standard behavior until implemented.
- Persistent client verify and sync-client ownership are not Distro behaviors.
- Persistent subscriptions are unsupported.

## 8. Related Specs

- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md)
- [Naming Health And Protection Spec](naming-health-protection-spec.md)
- [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md)
- [Naming Consistency And Client State Spec](naming-consistency-client-spec.md)
- [Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md)
- [CP Consistency Spec](../design/foundation-cp-consistency-spec.md)
- [Task Execution Spec](../design/foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
