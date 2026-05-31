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

# Nacos Client Local Cache And Redo Spec

This document defines client-side local cache, local failover, listener
recovery, subscription recovery, and redo behavior. It expands the recovery part
of the [Client Runtime Spec](client-runtime-spec.md).

## 1. Local Data Classes

Client runtime uses several local data classes:

| Data class | Source | Purpose | Authority |
|------------|--------|---------|-----------|
| Config failover file | User-maintained local file | Emergency override for a known Config item. | Highest local read priority, but never writes back to server automatically. |
| Config snapshot | Server query response | Last known Config content and encrypted data key for read fallback. | Recovery cache only. |
| Config listener state | SDK listener registration | Tracks known group keys, listener MD5, and fuzzy watch state. | Runtime intent only. |
| Naming service-info cache | Server push or query response | Last known instances for subscribed or queried services. | Recovery cache only. |
| Naming failover data | User or extension provided local failover source | Overrides discovery view while failover switch is enabled. | Local discovery override only. |
| Redo data | SDK register, subscribe, or endpoint operation | Restores runtime intent after reconnect. | Runtime intent only. |

Local data must not be treated as server-side committed state unless a domain
spec explicitly says so.

## 2. Config Local Recovery

Config read priority is:

1. User-maintained local failover file.
2. Server query.
3. Local snapshot.

The failover file is not created automatically by the client. It exists for
emergency scenarios where an application must boot or continue with a local
override while the Nacos server is unavailable or a remote change is unsafe.

Snapshots are written after successful server queries and removed when the
server confirms that a Config item does not exist. Encrypted data key snapshots
are stored separately from content snapshots. Config filters, including
encryption filters, are applied after the selected local or remote content is
loaded.

Config listeners must check local failover files before sending listener checks.
When a failover file appears, changes, or disappears, the listener state must be
updated and listener callbacks may be triggered according to `CacheData` MD5
rules.

## 3. Config Listener And Fuzzy Watch Recovery

Config gRPC clients register handlers for Config change notification, client
metrics requests, and fuzzy watch notification. On connect, the client must
notify the listen context and fuzzy watch context so known subscriptions are
resynced. On disconnect, it must mark affected `CacheData` entries and fuzzy
watch contexts inconsistent with the server.

Config listener recovery is not a redo of writes. It is a resync of read/watch
runtime intent.

## 4. Naming Local Cache

Naming service-info cache stores `ServiceInfo` objects keyed by grouped service
name and clusters. Server push or query responses update the in-memory map and
write a disk cache when the instance view changes.

The cache is a recovery aid:

- it may be loaded at startup when the load-cache option is enabled;
- it may serve a temporary discovery view during network disruption;
- it must not create, update, or delete Naming server-side resources.

Push-empty protection may ignore empty or invalid pushes to avoid replacing a
known usable view with an accidental empty view.

## 5. Naming Failover View

Naming failover is a local discovery override. When the failover switch is
enabled and valid failover data exists for a service, the SDK may return the
failover view instead of the normal server-driven view.

Changing the failover switch or failover data should publish instance-change
events when the visible instance set changes. When failover is disabled, the SDK
returns to the normal cached server view and should notify listeners if the
visible view changes.

Naming failover must not be used as a server-side data repair mechanism.

## 6. Redo Model

Redo restores runtime intent after a connection is lost and re-established.
Redo data records:

- the expected final state, such as registered or unregistered;
- whether the data was successfully registered on the previous connection;
- whether an unregister operation is in progress;
- the domain payload needed to repeat the operation.

Redo operations include:

- register again;
- unregister again;
- remove obsolete redo data;
- do nothing when the current runtime intent is already satisfied.

Redo tasks must run only when the runtime connection is connected. On
disconnect, registered redo data must be marked not registered so the next
connected period can repair the server-side attachment.

## 7. Domain Redo Rules

Naming redo covers:

- ephemeral instance registration;
- batch ephemeral instance registration;
- service subscription;
- fuzzy watch consistency state.

Persistent Naming service state is server-owned and should not be restored by
client redo unless the domain explicitly treats the operation as runtime intent.

AI redo covers runtime endpoint and subscription intent, such as MCP or agent
endpoint registration. AI resource publish/delete semantics remain governed by
the [AI Registry Spec](../ai/ai-registry-spec.md).

Config listeners are recovered through listener resync and fuzzy watch resync.
Config publish/delete operations are not automatically redone by the Client SDK.

## 8. Shutdown

SDK shutdown must clear in-memory redo state, stop background retry tasks, close
transport clients, and stop local cache/failover refresh tasks. Shutdown should
not delete user-maintained failover files or server-derived snapshots unless the
user explicitly calls a cache cleanup operation.

## 9. Pending Issues

- Naming redo currently uses its own implementation while newer AI redo uses
  common redo abstractions. The implementations should converge on the shared
  redo model.
- Config listener recovery, Naming redo, AI redo, and runtime push recovery
  defined by the [Runtime Push And Reconnect Spec](runtime-push-reconnect-spec.md)
  should share common observability fields.
- Multi-language SDKs should document which local cache and redo behaviors they
  support and where they intentionally differ from Java.
