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

# Naming Health And Protection Spec

This document defines Naming health state, health checking, enabled state,
weight, and protection-threshold behavior.

## 1. Health State

`healthy` describes whether an instance is currently considered available by
Naming health logic. It can be updated by:

- heartbeat checks for instances of ephemeral services;
- active health checks for instances of persistent services;
- manual health update for instances of persistent services whose cluster
  health checker is `NONE`;
- synchronization of health state across responsible servers.

`enabled` describes whether the instance is allowed to accept discovery traffic.
Disabled instances should not be returned to runtime Open API consumers and are
filtered by Java SDK selection.

## 2. Ephemeral Service Health

Instances of ephemeral services are non-persistent runtime state. Their health
is driven by the runtime publisher liveness, not by server-side active health
checks.

### 2.1 HTTP And Compatibility Heartbeat

HTTP clients, 1.x clients, and other clients that do not use gRPC keep
ephemeral instances alive by reporting heartbeats. Beat check tasks inspect the
last heartbeat time. If an instance is unhealthy for longer than its heartbeat
timeout, it may be marked unhealthy. If it exceeds the delete timeout and
expiration is enabled, it may be removed. Beat check task scheduling must
follow the [Task Execution Spec](../design/foundation-task-execution-spec.md).

Heartbeat timing can be customized by reserved metadata keys:

| Key | Meaning |
| --- | --- |
| `preserved.heart.beat.interval` | Expected heartbeat interval. |
| `preserved.heart.beat.timeout` | Timeout before the instance is considered unhealthy. |
| `preserved.ip.delete.timeout` | Timeout before the instance may be deleted. |

### 2.2 gRPC Connection Liveness

gRPC clients keep ephemeral instances alive through the
[remote connection lifecycle](../design/foundation-remote-connection-spec.md).
Naming observes connection close and release events to remove or redo runtime
publisher/subscriber state. Local event delivery follows the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

The gRPC transport has its own heartbeat and liveness detection to avoid
half-open connections. That heartbeat is hidden behind the gRPC connection
layer from the Naming module perspective. Naming should depend on connection
lifecycle events defined by the
[Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md),
not duplicate transport-level heartbeat logic.

## 3. Persistent Service Active Health Check

Instances of persistent services are checked by server-side health check
processors. Cluster metadata selects the checker type and port behavior.

### 3.1 Active Check Types

Built-in checker types include TCP, HTTP, MySQL, and NONE, and additional
checker types may be registered through the health checker registry.

Active health checks should only change health state when the responsible
server performs the check and the service health check switch allows it.

### 3.2 Manual Health Update

Manual instance health update is allowed for instances of persistent services
only when the cluster health checker is `NONE`. If active health check is
configured, manual health update must be rejected because health is owned by the
checker.

## 4. Weight

`weight` is an instance-level value used by client-side weighted selection.
Runtime selection should ignore instances whose weight is less than or equal to
zero. Server-side query stores and returns the weight; it does not guarantee
that every consumer uses weighted balancing.

## 5. Protection Threshold

Service `protectThreshold` prevents discovery results from collapsing to too
few healthy instances. After cluster, enabled, internal server-side filtering,
and health filtering, if the healthy ratio is lower than or equal to the
threshold, the server marks the result as reaching protection threshold and
returns the broader filtered instance set with unhealthy instances presented as
healthy in that protected view.

Protection threshold is a discovery availability protection mechanism. It is
not an assertion that the underlying instances are actually healthy.

## 6. Runtime Connection Health

gRPC connection heartbeat, half-open detection, and client-side connection
liveness behavior are defined by the
[Client Connection And Failover Spec](../client/client-connection-failover-spec.md).
The foundation-level server connection lifecycle boundary is defined by the
[Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md).

## 7. Related Specs

- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Discovery And Subscription Spec](naming-discovery-subscription-spec.md)
- [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md)
- [Task Execution Spec](../design/foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
