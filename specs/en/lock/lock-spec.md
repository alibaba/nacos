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

# Distributed Lock Spec

This document defines the Nacos Distributed Lock domain. Distributed Lock is an
experimental Nacos 3.0 capability. The current implementation and functional
surface are intentionally small. Future versions may introduce incompatible
changes, change the resource model, tighten security semantics, or remove the
module if the community no longer needs Nacos to provide this primitive.

## 1. Scope

Distributed Lock provides a simple distributed mutual exclusion primitive for
clients that need to coordinate short critical sections through a Nacos cluster.
The domain is implemented by the `lock` server module and the Java client
`LockService`.

Distributed Lock owns:

- lock identity, lock type, lock acquisition, lock release, and lease timeout
  semantics;
- server-side lock state replication through the
  [CP Consistency Spec](../design/foundation-cp-consistency-spec.md);
- optional Java SDK access through the
  [SDK Spec](../sdk/sdk-spec.md) and
  [Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md);
- lock request metrics through the
  [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).

Distributed Lock does not own:

- configuration, naming, AI registry, namespace, or plugin resource lifecycle;
- business transactions, database transactions, task scheduling, or workflow
  orchestration;
- fencing-token semantics, lock ownership tokens, lock renewal, lock query,
  waiter queues, fairness, or reentrant semantics;
- HTTP management APIs for broad lock listing, migration, or manual state
  mutation.

## 2. Experimental Status

The lock module must be treated as experimental until the community explicitly
promotes it to a stable capability.

The following compatibility guarantees are not provided yet:

- stable wire payloads beyond current client-server compatibility;
- stable lock resource identity beyond `lockType + key`;
- stable authorization behavior for every lock operation;
- stable lock extension SPI behavior;
- cross-language SDK parity.

Applications that require strong production-grade lock guarantees should verify
the current behavior against their failure model before relying on this module.
Experimental compatibility expectations follow the
[Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 3. Resource Model

The current lock resource identity is:

```text
lockType -> key
```

| Concept | Meaning |
| --- | --- |
| `lockType` | Lock implementation type. The built-in type is `NACOS_LOCK`. |
| `key` | User-defined lock name within the `lockType` scope. |
| `params` | Optional serializable extension parameters. The built-in mutex lock does not interpret them. |
| `expiredTime` | Requested lease duration in milliseconds. Despite the current field name, the server interprets it as a duration, not an absolute timestamp. |

The current model is global to the Nacos cluster and does not include
`namespaceId`, `groupName`, resource owner, or tenant identity. Adding those
dimensions would be a resource-model change and may be incompatible.

## 4. Lock Semantics

The built-in lock type is a simple mutex:

- acquisition succeeds when the lock is empty;
- acquisition succeeds when the existing lock has expired;
- acquisition fails when the lock is held and not expired;
- release attempts to move the lock from held to empty;
- an empty or expired lock may be cleared from the in-memory lock map;
- the result of acquire or release is a boolean success value.

The built-in implementation does not currently verify lock ownership during
release. A client that can send a release request for the same `lockType + key`
can release that lock. This is part of the experimental status and must not be
treated as the final security contract.

## 5. Lease And Expiration

The server computes the actual expiration timestamp from server time:

```text
endTime = serverCurrentTimeMillis + leaseDurationMillis
```

Rules:

- if the requested lease duration is negative, the server uses
  `nacos.lock.default_expire_time`;
- the default lease duration is currently `30000` milliseconds;
- the server caps the requested lease duration by `nacos.lock.max_expire_time`;
- the current maximum lease duration is `1800000` milliseconds;
- expiration is checked lazily when an acquire, release, cleanup, or snapshot
  related path touches the lock state.

Because expiration uses server time, clients must not assume that their local
clock determines the lock validity window.

## 6. Consistency And Recovery

Distributed Lock is a CP capability. Lock state transitions are applied through
the CP protocol group used by the lock module. A cluster must prefer correctness
over availability: when the CP path cannot commit a write, lock acquisition or
release must fail rather than produce divergent lock ownership.

The current implementation:

- registers a CP request processor for lock acquire and release;
- serializes lock operation requests into CP write requests;
- stores active lock state in the server-side lock manager;
- saves and loads lock state through the CP snapshot mechanism;
- uses the snapshot archive name `nacos_lock.zip`.

Lock state is process memory plus CP log/snapshot state. It is not a relational
database resource and is not governed by the
[Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md) in
the same way as Config or Naming domain data.

## 7. Client And Transport Boundary

Distributed Lock is exposed to clients through the runtime SDK, not as a broad
administrative API. The Java client uses the gRPC request path described by the
[gRPC API Spec](../grpc-api/api-spec.md). The client must check the
`SERVER_DISTRIBUTED_LOCK` ability before sending lock operations to a server,
following the
[Client Ability Negotiation Spec](../client/client-ability-negotiation-spec.md).

The public SDK boundary is:

- create a `LockService`;
- create a lock instance, usually through the Java client `NLockFactory`;
- acquire a lock;
- release a lock;
- close the client resources.

The SDK must not expose server internals such as CP group names, snapshot files,
lock manager maps, or low-level request processors as stable user contracts.

## 8. Extension Boundary

The server has a `LockFactory` SPI keyed by `lockType`. The built-in
implementation registers `NACOS_LOCK` and creates a mutex lock.

Extension rules:

- a lock type must define its own meaning for `params` before using them;
- a lock type must preserve the acquire/release boolean contract unless a
  future version introduces a separate typed contract;
- a lock type must be safe under CP write ordering;
- a lock type must not redefine Config, Naming, AI, or Core resource ownership;
- lock extension behavior is experimental and may change together with the lock
  module.

## 9. Security And Visibility

Lock acquire and release are write operations over lock resources and should be
authorized as `SignType.LOCK` with write action semantics. The Java client sends
security headers through the same client security proxy pattern used by other
runtime clients.

Current implementation status:

- the lock gRPC handler contains a `TODO Support auth` marker;
- the default auth implementation contains a historical `grpc/lock` operator
  point;
- lock requests do not yet have complete owner-token validation or release
  ownership checks.

Until the security contract is completed, deployments should treat lock as a
trusted-client experimental capability.

## 10. Observability

The lock module should expose low-cardinality metrics for operation count,
success count, and handler latency. The current implementation records:

- total acquire requests;
- successful acquire requests;
- total release requests;
- successful release requests;
- lock handler latency.

Metrics must not include raw lock keys, params, credentials, or user payloads as
labels.

## 11. Pending Issues

- Decide whether Distributed Lock should remain part of Nacos core capability,
  move to an extension module, or be removed.
- Define stable ownership semantics for release, including owner token, fencing
  token, connection binding, or another community-accepted mechanism.
- Decide whether lock identity must include `namespaceId`, tenant, or resource
  owner.
- Complete authorization for lock gRPC operations and align it with
  `SignType.LOCK`.
- Decide whether renewal, query, watch, fairness, or reentrant semantics belong
  to Nacos.
- Define cross-language SDK contracts only after the server semantics are
  stable.
- Revisit field naming such as `expiredTime`, which currently behaves as a
  lease duration.
