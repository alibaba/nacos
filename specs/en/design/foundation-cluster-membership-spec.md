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

# Nacos Cluster Membership Spec

This document defines the foundation-level cluster membership model used by
Nacos server-side modules. It refines the membership part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

Cluster membership is an infrastructure view of Nacos server members. It is not
a Config, Naming, AI, or security resource model, and it must not redefine
domain ownership.

## 1. Scope

The cluster membership capability owns:

- server member identity and member state;
- member lookup and lookup-mode switching;
- local member readiness;
- member metadata and ability refresh;
- member health observation through report tasks;
- member-change event publication;
- member views used by cluster RPC, consistency protocols, and operation APIs.

It does not own:

- domain sharding semantics beyond exposing a member view;
- Config, Naming, AI, or auth resource lifecycle;
- public HTTP or gRPC API contracts;
- datasource persistence semantics other than compatibility persistence of the
  cluster member list.

## 2. Member Model

A Nacos server member is represented by `Member`, which extends the public
`NacosMember` response model and adds server-side runtime fields.

| Field | Meaning |
| --- | --- |
| `address` | Stable member address in `ip:port` form. The port is the main Nacos server port. |
| `ip` / `port` | Parsed member endpoint used for routing and diagnostics. |
| `state` | Lifecycle state such as `STARTING`, `UP`, `SUSPICIOUS`, `DOWN`, or `ISOLATION`. |
| `extendInfo` | Server metadata such as version, raft port, site, weight, upgrade compatibility, and last refresh time. |
| `abilities` | Server ability table reported by the member, including remote, config, and naming abilities. |
| `failAccessCnt` | Local transient failure counter used to move a member from suspicious to down. |
| `grpcReportEnabled` | Compatibility flag for mixed-version member reporting. It is not a new semantic capability. |

Member identity rules:

- `address` is the key of the membership map and must be parseable as an
  internet address plus port.
- If a lookup result omits the local member, the local member must be added back
  to the effective member set.
- `allMembers()` returns the current member view including self.
- `allMembersWithoutSelf()` returns the current member view excluding self.
- `memberAddressInfos` is the current healthy-address view and only contains
  members in the effective `UP` view.

## 3. Lookup Modes

`MemberLookup` is the common lookup interface. Every lookup implementation must
feed the same membership model by calling `afterLookup(Collection<Member>)`,
which delegates to `NacosMemberManager.memberChange`.

Supported lookup modes:

| Mode | Implementation | Selection and behavior |
| --- | --- | --- |
| Standalone | `StandaloneMemberLookup` | Used when Nacos runs in standalone mode. The effective member set contains only the local member. |
| File config | `FileConfigMemberLookup` | Selected when `nacos.core.member.lookup.type=file`, `cluster.conf` exists, or a member list property is configured. It reads `cluster.conf` and watches the config directory for changes. |
| Address server | `AddressServerMemberLookup` | Selected when `nacos.core.member.lookup.type=address-server` or no file/member-list source is present. It synchronously pulls the member list at startup, then refreshes it periodically. |

Lookup selection rules:

- standalone mode always uses standalone lookup;
- an explicit `nacos.core.member.lookup.type` has priority in cluster mode;
- file/member-list configuration has priority over address-server fallback;
- switching lookup mode must destroy the old lookup and inject the same
  `ServerMemberManager` into the new lookup;
- lookup implementations may expose diagnostic `info()`, but that data is not a
  domain resource contract.

The external server-side member lookup extension is described by the
[Addressing Plugin Spec](../plugin/addressing-plugin-spec.md). This document
defines the membership model that addressing implementations must feed.

## 4. Member Change Application

`ServerMemberManager.memberChange` applies a complete member-list view. It is
synchronized so the effective list update, compatibility file sync, and event
publication stay ordered.

Change rules:

- an empty lookup result must not replace the current member view;
- the local member must remain in the effective view;
- existing member metadata and abilities are preserved when the same address is
  still present in the new list;
- `memberJoin` and `memberLeave` are convenience operations that resolve to a
  full member-list change;
- topology changes are written to `cluster.conf` for compatibility by
  `MemberUtil.syncToFile`;
- `MembersChangeEvent` is published only when the effective topology changes or
  basic member metadata changes.

`ServerMemberManager.update(Member)` applies metadata/state refresh for an
existing member. It must not add an unknown member. If the update changes basic
metadata, it publishes a `MembersChangeEvent` with the changed member as the
trigger.

## 5. Member Readiness And Health Observation

Local readiness is set by `ServerMemberManager.setSelfReady`. It marks the local
member as `UP`, publishes the local address into the environment, and starts
member report tasks in cluster mode.

Member health observation is based on best-effort reporting:

- `MemberInfoReportTask` periodically reports the local member metadata to one
  peer at a time;
- `UnhealthyMemberInfoReportTask` separately retries reporting to members that
  are not `UP`;
- reporting uses gRPC when the peer supports remote reporting, with HTTP kept as
  mixed-version compatibility;
- a successful report refreshes peer state and reported metadata;
- a failed report marks the peer `SUSPICIOUS`, increments failure count, and may
  mark the peer `DOWN` after the configured threshold or a connection-refused
  error;
- state changes must publish `MembersChangeEvent`.

Member health is a local observation of a server peer. It is not a substitute
for AP/CP protocol membership decisions, request-level failures, or domain
resource health.

## 6. Member Change Events

`MembersChangeEvent` is the local in-process event that carries the effective
member view and optional trigger members. `MemberChangeListener` subscribes to
that event and ignores expired events by default. General local event semantics
are defined by the
[Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

Known consumers include:

- `ProtocolManager`, which propagates member changes to AP and CP protocols;
- `ClusterRpcClientProxy`, which creates or destroys server-to-server gRPC
  clients;
- Naming runtime components that maintain server and service indexes;
- operation and diagnostic services that aggregate by member.

Event rules:

- the event is a local process notification, not a cross-node replication
  guarantee;
- subscribers must reread the member view when they need the latest state;
- subscribers must tolerate duplicate, delayed, or coalesced observations;
- member-change events must not be used as public API contracts.

## 7. Consumer Rules

Consumers of cluster membership must follow these rules:

- tolerate standalone mode and a single-member effective view;
- tolerate member add, remove, state change, and temporary unreachable states;
- prefer `allMembersWithoutSelf()` for remote fan-out;
- filter by state or ability when a behavior requires only ready or capable
  members;
- avoid long-lived cached member lists unless refreshed through
  `MembersChangeEvent`;
- do not treat member ordering as a domain sharding rule unless the domain spec
  explicitly defines that behavior;
- do not write domain ownership into `Member.extendInfo`.

## 8. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Addressing Plugin Spec](../plugin/addressing-plugin-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
