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

# Naming Instance Lifecycle Spec

This document defines service and instance lifecycle behavior in the Naming
domain.

## 1. Service Lifecycle

Admin service creation creates service metadata and a service singleton. The
created service must choose one service type:

| Service type | Lifecycle rule |
| --- | --- |
| Ephemeral service | Runtime publications are owned by live clients and cleaned up by heartbeat or [connection lifecycle](../design/foundation-remote-connection-spec.md). |
| Persistent service | Instances are durable resources and are cleaned up only by explicit deregistration, delete, or persistent-state recovery rules. |

Later instance registration must match the service type. Registering a
persistent instance into an ephemeral service, or an ephemeral instance into a
persistent service, must be rejected.

Service deletion is allowed only when the service has no registered instances.
Deleting a service removes service metadata and lets service cleanup remove the
runtime singleton and derived cache state.

Runtime instance registration may create a service singleton implicitly. This is
allowed for service discovery convenience, but management metadata remains
separate from runtime instance publication.

## 2. Instance Registration

Instance registration must:

1. validate instance fields and heartbeat metadata;
2. fill default cluster name when omitted;
3. resolve the owning service type from an existing service singleton or create
   a singleton with the requested type when implicit creation is allowed;
4. ensure the input instance `ephemeral` value matches the service type;
5. derive or use the proper runtime client id;
6. register the instance through the ephemeral-service or persistent-service
   operation path;
7. update service indexes and service storage through Naming events;
8. publish trace events for audit and diagnostics.

Naming events follow the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

Ephemeral-service registration is supported through gRPC and HTTP Open/Admin
APIs. Persistent-service registration is supported through the persistent
request path and may fall back to HTTP compatibility when the server does not
advertise gRPC support for persistent instances.

## 3. Heartbeat

Heartbeat applies to HTTP and compatibility ephemeral IP-port clients. HTTP
Open API heartbeat reuses `POST /v3/client/ns/instance` with
`heartBeat=true`. gRPC ephemeral services are kept alive by
[connection lifecycle](../design/foundation-remote-connection-spec.md) events;
the transport-level heartbeat is defined outside Naming.

If heartbeat finds the client and service instance, it updates the last-updated
time and schedules beat processing. If heartbeat cannot find the instance and no
beat payload is provided, the server returns `INSTANCE_NOT_FOUND` so the caller
can register again.

Heartbeat interval, heartbeat timeout, and IP delete timeout may be controlled
by reserved instance metadata keys. Those values must satisfy the validation
rule in the [Naming Resource Spec](naming-resource-spec.md).

## 4. Deregistration

Deregistration removes the instance from the owning client and publishes service
change events. Deregistering a missing instance or a missing compatibility
client should be treated as a successful no-op for runtime caller idempotency.

When the last publisher disappears, the service index emits a delete-service
change event. Empty service cleanup may later remove the service singleton after
the configured expiration window.

## 5. Update And Partial Update

Admin instance update modifies operational instance metadata such as `enabled`,
`weight`, and extended metadata. Full update validates instance weight and
replaces the stored operational metadata. Partial update only changes fields
explicitly present in the request.

Operational instance metadata has higher priority than runtime registration
metadata in the served discovery view, as defined by the
[Naming Metadata And Selector Spec](naming-metadata-selector-spec.md).

Instance update does not change the service identity. Updating `ip`, `port`, or
`clusterName` is equivalent to operating on a different instance identity.

## 6. Batch Operations

Batch registration is an ephemeral-service gRPC capability. Batch input must
contain legal ephemeral instances whose service type is ephemeral. The server
stores batch publish information under the owning client and emits service
change events.

Java SDK batch deregistration is implemented by retaining the remaining
instances in the batch registration record and sending a new batch registration.
New APIs must document this as a batch-state replacement behavior, not as
independent persistent per-instance deletes.

## 7. Cleanup

Naming cleanup includes:

- heartbeat expiration for HTTP and compatibility ephemeral instances;
- client disconnect release for connection-based clients;
- empty service cleanup after the service has no publishers for the configured
  expiration time;
- expired metadata cleanup after service or instance metadata becomes detached.

Cleanup is part of the Naming lifecycle. It must publish the same resource
events needed by subscribers, indexes, metadata cleanup, and trace.

## 8. Related Specs

- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Health And Protection Spec](naming-health-protection-spec.md)
- [Naming Consistency And Client State Spec](naming-consistency-client-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
- [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
