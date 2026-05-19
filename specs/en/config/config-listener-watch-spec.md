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

# Config Listener And Watch Spec

This document defines exact Config listening, server push, and fuzzy watch
semantics.

## 1. Exact Listener

An exact listener watches a concrete Config resource:

```text
namespaceId -> groupName -> dataId
```

The client sends its current md5 for each watched config. The server records:

- group key to connection id mappings;
- connection id to group key and md5 mappings;
- whether namespace compatibility transfer is needed for the connection.

When the client registers a listener, the server compares client md5 with
server state. If the client is not up to date, the response includes the changed
config identity so the client can query the content.

## 2. Change Push

After a successful publish, delete, metadata update, or gray-state change, the
Config domain publishes a local change event. The gRPC notifier finds
connections that listen to the changed group key and pushes a
`ConfigChangeNotifyRequest`. Cross-node refresh visibility for Config change
events is defined by the
[AP Consistency Spec](../design/foundation-ap-consistency-spec.md).
The local event dispatch boundary is defined by the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

Change push rules:

- push carries the changed Config identity, not authoritative content;
- clients must query the Config after receiving a change notification;
- push uses retry, task execution, and control points;
- if push retry exceeds the configured limit, the server may unregister the
  stale connection;
- disconnected clients lose in-memory listener state and must re-register after
  reconnect.

Push retry and asynchronous notifier execution must follow the
[Task Execution Spec](../design/foundation-task-execution-spec.md).

## 3. Fuzzy Watch

Fuzzy watch watches a pattern instead of one exact Config. The pattern is
generated as:

```text
namespaceId >> groupPattern >> dataIdPattern
```

The pattern uses `*` matching:

| Pattern form | Meaning |
| --- | --- |
| `*` | Match all values in that segment. |
| `prefix*` | Prefix match. |
| `*suffix` | Suffix match. |
| `*text*` | Contains match. |
| `literal` | Exact match. |

Fuzzy watch tracks the set of group keys that match each pattern. During
initialization, the server diffs the matched set with the client-provided set
and pushes `ADD_CONFIG` or `DELETE_CONFIG` states in batches. After
initialization, Config changes update the matched set and push change events to
matched clients.

## 4. Fuzzy Watch Limits

Fuzzy watch must be bounded:

- `nacos.config.fuzzy.watch.max.pattern.count` limits the number of tracked
  patterns, default `20`.
- `nacos.config.fuzzy.watch.max.pattern.match.config.count` limits matched
  configs per pattern, default `500`.
- `nacos.config.push.batchSize` controls initialization diff batch size,
  default `20`.

When a pattern reaches the matched config limit, the server may suppress
additional matched configs and protect delete notifications during incomplete
matched-set tracking.

## 5. Diagnostics

Admin listener and metric APIs may query listener state by config identity,
client IP, and cluster member. These APIs are management diagnostics and must
not be exposed through runtime Client SDK surfaces.

## 6. Runtime Recovery

Config listener reconnect, connection lifecycle, and push retry behavior follow
the [Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md).
Config local failover, snapshot, listener resync, and fuzzy watch recovery are
defined by the
[Client Local Cache And Redo Spec](../client/client-local-cache-redo-spec.md).
The foundation-level server connection boundary remains defined by the
[Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md).
