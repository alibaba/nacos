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

# Naming Discovery And Subscription Spec

This document defines query, subscription, push, fuzzy watch, local cache, and
failover behavior.

## 1. Service Query

Service query returns a `ServiceInfo` view for a service. The view contains the
service name, groupName, clusters, cache duration, last reference time, hosts,
and protection-threshold state.

Server-side query may apply:

- cluster filtering;
- enabled-only filtering for runtime Open API list;
- health-only filtering when requested;
- internal server-side filtering rules;
- health protection threshold.

Admin list APIs may return management views such as service summaries, service
detail pages, subscribers, clients, and cluster metadata. Those management views
must not redefine runtime discovery semantics.

## 2. Subscription

gRPC subscription records a subscriber under the caller connection and returns
the current `ServiceInfo` view. Later service changes are pushed to subscribed
clients. Unsubscribe removes the subscriber and stops server push for that
client and service when no local listener remains. Server-side change events
follow the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

The Java SDK maps multiple local listeners for the same service to one server
subscription where possible. Local listener selection is performed by
client-side `NamingSelector` wrappers, as defined by the
[Naming Metadata And Selector Spec](naming-metadata-selector-spec.md).

HTTP Open API does not support long polling or push subscription. Custom HTTP
clients should query service instances explicitly or use gRPC for subscription.

## 3. Push And Local Cache

Server push updates the client `ServiceInfo` cache. The Java SDK:

1. validates the pushed service view;
2. ignores empty or invalid push when push-empty protection is enabled;
3. stores the service view in memory;
4. computes instance diffs;
5. notifies matching local listeners;
6. writes the service view to disk cache.

Clients may re-query the server on subscription setup, reconnect, cache miss, or
polling fallback. Local disk cache is a recovery and failover aid, not a
durable server-side source of truth.

Server push fan-out and retry work must follow the
[Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md) and
the [Task Execution Spec](../design/foundation-task-execution-spec.md).

## 4. Fuzzy Watch

Fuzzy watch allows a client to watch service keys by `serviceName` and
`groupName` patterns within one namespace. The server maintains:

- watched clients per pattern;
- matched service keys per pattern;
- add/delete notifications when service keys enter or leave the matched set;
- initial or diff synchronization batches.

Fuzzy watch has server-side limits for pattern count and matched service count.
When a limit is reached, the server may reject the watch or suppress additional
matched services for that pattern. Clients must handle error responses and sync
responses.

## 5. SDK Selection

The Java SDK offers:

- `getAllInstances` to return the current view;
- `selectInstances` to filter by health, enabled state, and positive weight;
- `selectOneHealthyInstance` to choose one instance by weight;
- `subscribe` and `unsubscribe` to receive `NamingEvent` or client-side
  selector-based events;
- `fuzzyWatch` and `fuzzyWatchWithServiceKeys` for pattern-based service-key
  watching.

SDK selection is local to the client process. It must not be treated as a
server-side traffic policy.

## 6. Failover

The Java SDK may read local failover data when failover mode is enabled. If a
valid failover view exists, the SDK can return it before querying or subscribing
to the server.

Failover data is a client-side emergency view. It must not change the server
resource model, service metadata, or instance lifecycle.

## 7. Related Specs

- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Health And Protection Spec](naming-health-protection-spec.md)
- [Task Execution Spec](../design/foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
- [SDK Spec](../sdk/sdk-spec.md)
