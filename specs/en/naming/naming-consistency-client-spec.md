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

# Naming Consistency And Client State Spec

This document defines Naming consistency paths, client identity, indexes, and
snapshots. Service type decides which consistency path owns runtime instance
state.

## 1. Common Client State

Naming uses Client state to connect publishers and subscribers to services.
The Naming Client is a server-side abstraction for data associated with one
runtime communication actor. It is not the same concept as an end-user SDK
object or application process.

For gRPC communication, one live connection maps to one connection-based
Client. All publish and subscribe requests sent through that connection update
the same Client object. This makes Naming runtime data stateful by connection,
instead of being stateless per HTTP request.

For HTTP and compatibility clients, Naming may create IP-port-based Client
objects to emulate equivalent publisher state. Those Clients are kept alive by
heartbeat and expiration checks because separate HTTP requests may reach
different Nacos server nodes.

| Client type | Typical source | Lifecycle |
| --- | --- | --- |
| Connection-based client | gRPC SDK connection. | Released when the connection is closed. |
| Ephemeral IP-port client | HTTP or compatibility ephemeral registration. | Kept alive by heartbeat and expiration checks. |
| Persistent IP-port client | Persistent instance registration. | Stored through the persistent path and may survive process restart. |

Client id is internal state. Public runtime APIs should identify instances by
service scope, cluster, IP, port, and service type.

Client state is the source for publisher and subscriber indexes. Indexes are
derived serving state and may be rebuilt from client state, metadata snapshots,
and events. Local Naming event behavior follows the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

The main publish-to-push flow is:

1. a register, deregister, subscribe, or unsubscribe request updates the
   connection-bound or IP-port-based Client;
2. the Client update emits Naming events;
3. service-to-publisher and service-to-subscriber indexes are updated from the
   event;
4. service data is aggregated from publisher Clients through the indexes;
5. subscriber Clients for the same service are selected through the indexes;
6. gRPC push sends the aggregated `ServiceInfo` view through each subscriber
   connection.

New storage or push behavior must preserve this boundary: Client owns runtime
publisher/subscriber state, while indexes are derived acceleration structures
for service-level aggregation and push.

## 2. Ephemeral Service Consistency

Ephemeral services own AP-oriented runtime instance state. Their instances are
registered under ephemeral clients, synchronized across the cluster through the
Distro client data path defined by the
[Naming Ephemeral Distro Consistency Spec](naming-ephemeral-distro-consistency-spec.md),
and removed when heartbeat or
[connection lifecycle](../design/foundation-remote-connection-spec.md) indicates
that the client disappeared.

Ephemeral state should favor fast runtime availability and eventual convergence.
It must not be treated as durable metadata.

Batch registration and gRPC connection redo are ephemeral-service behaviors.
They restore runtime intent from the client process and do not create durable
server-side instance metadata.

## 3. Persistent Service Consistency

Persistent services own CP-oriented instance state. Register, deregister, and
update operations are written through the persistent service group and applied
by the persistent client operation service. The shared CP foundation is defined
by the [CP Consistency Spec](../design/foundation-cp-consistency-spec.md), and
Naming-specific persistent behavior is defined by the
[Naming Persistent CP Consistency Spec](naming-persistent-cp-consistency-spec.md).
Persistent instance snapshots are used for state recovery.

Persistent clients do not support subscriber state. Subscriptions are ephemeral
client behavior.

## 4. Metadata Consistency

Service metadata, cluster metadata, and instance metadata are written through CP
metadata groups. Loading metadata snapshots must recreate service singletons as
needed so service identity and metadata stay connected after recovery.

Metadata consistency is separate from ephemeral service instance state.
Operational instance metadata can be attached to a runtime instance view and
has higher priority than runtime registration metadata, but its write path is
still metadata-oriented.

## 5. Indexes And Service Storage

Naming maintains derived indexes:

- service to publisher client ids;
- service to subscriber client ids;
- service to cluster names;
- service to cached `ServiceInfo`;
- fuzzy watch pattern to matched service keys.

Indexes are derived serving state. New public APIs should not expose these
indexes as authoritative storage contracts.

## 6. Client Redo And Recovery

The Java SDK caches registered instances and subscriptions for redo after gRPC
reconnect. Redo restores runtime intent from the client process according to
the [Client Local Cache And Redo Spec](../client/client-local-cache-redo-spec.md).
It does not change the server resource identity model or service type.

Client disk cache and failover data provide local recovery for discovery reads.
They must not be used as server-side persistence.

## 7. Related Specs

- [Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md)
- [Naming Discovery And Subscription Spec](naming-discovery-subscription-spec.md)
- [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md)
- [Naming Ephemeral Distro Consistency Spec](naming-ephemeral-distro-consistency-spec.md)
- [Naming Persistent CP Consistency Spec](naming-persistent-cp-consistency-spec.md)
- [AP Consistency Spec](../design/foundation-ap-consistency-spec.md)
- [CP Consistency Spec](../design/foundation-cp-consistency-spec.md)
- [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md)
- [Task Execution Spec](../design/foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
