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

# Config Persistence, Dump, And History Spec

This document defines Config persistence, local dump cache, and history
semantics.

## 1. Persistent Records

The Config domain stores durable state through repository services. The main
record families are:

| Record family | Purpose |
| --- | --- |
| `config_info` | Formal Config content, md5, type, metadata, source info, namespace, group, and encrypted data key. |
| `config_info_gray` | Gray Config content, md5, gray name, serialized gray rule, namespace, group, and encrypted data key. |
| `his_config_info` | Historical change records for formal and gray publish or remove operations. |
| `tenant_capacity` / `group_capacity` | Capacity limits and usage counters for namespace, group, and cluster scopes. |

Repository interfaces define Config semantics. Concrete SQL and database
dialects are implementation details covered by the
[Datasource Dialect Plugin Spec](../plugin/datasource-dialect-plugin-spec.md).

## 2. Local Dump Cache

Each server maintains local serving state:

- JVM cache keyed by the internal group key, containing md5, timestamp,
  encrypted data key, content type, and gray rule state;
- local dump files containing formal and gray config content.

Startup must dump persisted formal and gray configs into local serving state.
Runtime queries read from the cache and local dump files, not from broad
database queries.

## 3. Change Dump Flow

After a successful write, Config publishes a change event. The dump service
turns the event into a dump task. The task reloads the affected formal or gray
record from persistence and updates the local cache and local dump file.
Local event behavior is defined by the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md),
and dump task behavior is defined by the
[Task Execution Spec](../design/foundation-task-execution-spec.md).

Dump must ignore stale timestamps and preserve newer local state. If the md5 is
unchanged but the timestamp is newer, it updates timestamp state without
rewriting content. If content changes, it updates both local file content and
JVM md5 state.

## 4. Embedded And External Storage

Config supports embedded and external storage modes:

- embedded storage must wait until the CP protocol has readable data before
  startup dump completes;
- external storage initializes dump directly from the configured datasource;
- history cleanup and administrative storage operations must run only on the
  node selected by the storage mode policy.

The detailed Config consistency contract is defined by the
[Config Consistency, Dump, And Visibility Spec](config-consistency-dump-visibility-spec.md).
The embedded storage CP foundation is defined by the
[CP Consistency Spec](../design/foundation-cp-consistency-spec.md), and Config
Notify propagation is defined by the
[AP Consistency Spec](../design/foundation-ap-consistency-spec.md). Persistence,
dump, task, and event boundaries are defined by the
[Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md),
[Task Execution Spec](../design/foundation-task-execution-spec.md), and
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

## 5. History

History records must preserve enough information to inspect and recover config
changes:

- `dataId`, `groupName`, and `namespaceId`;
- content and md5;
- source user and source IP;
- operation type such as publish or remove;
- publish type such as formal or gray;
- `grayName` and extension information when applicable;
- creation and modification timestamps.

History list and detail APIs are management APIs. Page size must be bounded.
History cleanup uses the configured retention window, default `30` days.

## 6. Recovery And Operations

Admin local-cache operation may trigger a full dump from persistence to local
cache. This operation is an administrative repair mechanism and should not be
used as the normal publish path.

When local disk is full or cannot safely store dump content, the server must
treat the condition as fatal because runtime query correctness depends on local
serving state.

## 7. Related Specs

- [Config Consistency, Dump, And Visibility Spec](config-consistency-dump-visibility-spec.md)
- [Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md)
- [CP Consistency Spec](../design/foundation-cp-consistency-spec.md)
- [AP Consistency Spec](../design/foundation-ap-consistency-spec.md)
- [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md)
- [Task Execution Spec](../design/foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
- [Datasource Dialect Plugin Spec](../plugin/datasource-dialect-plugin-spec.md)
