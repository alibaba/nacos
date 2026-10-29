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

V3 Admin and Console history detail and previous-version responses expose
`schema` from `c_schema` in the selected history record's `extInfo`, while
preserving `extInfo` unchanged. They must not use the current Config's schema.
If the extension is absent, invalid JSON, or has no textual `c_schema`, the
model value is null and the response may omit `schema` under the default
serialization rules; the history query still succeeds. Schema text is returned
as stored in that history record.

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

### Publish operation messages

This section defines the first-phase target contract for #10978. Maintainers
clarified that the console has no message control in create or delete workflows,
but backend ordinary and CAS publish APIs handle inserts and updates identically
and must not ignore a message based on whether the config is new. Deletion,
Beta/gray, and rollback are out of scope. Only history-detail display is required;
history-list display is not.

#### Operation scope and input

| Operation | First-phase behavior |
| --- | --- |
| Ordinary publish API (insert or update) | May record the operation message; insert and update use the same handling. |
| CAS publish API | Records the message after successful CAS validation and write; failed CAS does not record it. |
| Console create workflow | Provides no message input; the backend API does not add a create/update branch for this. |
| Config deletion | Adds no message input or recording capability. |
| Beta, tag/gray publish, and rollback | Adds no message input or recording capability in this phase. |
| Metadata-only update, import, export, and clone | Adds no message input and copies no history message. |

Ordinary and CAS publish APIs handle an optional message identically regardless
of whether the resource is newly inserted. The console create form has no input,
but other API callers may provide a message on creation. Import and clone gain
no message input and do not copy history messages. Messages must not carry
across operations.

Ordinary and CAS publishes accept optional `changeMessage` text, with a limit of
512 Unicode code points. Longer input is a parameter-validation error. Missing,
empty, or whitespace-only text means no message; preserve nonblank text including
whitespace and newlines. The message
belongs to the successful update operation, not its snapshot or description, and
does not affect content, md5, dump, or listener payloads. Failed validation or
CAS creates no message or history. Existing skip-history behavior is unchanged.

#### JSON storage and history detail

Use the JSON object property `change_message` in the existing
`his_config_info.ext_info`, rather than storing a bare text value. For example:

```json
{"change_message": "Adjust connection timeout"}
```

Preserve the same history record's existing snapshot extension properties,
including `c_schema` and unknown properties. An omitted message must not inherit
an earlier message. Do not overwrite existing non-object or malformed extension
data with an empty object to merge a message; if merging is unsafe, retain the
original extension and omit the new message. Use the existing publish transaction
for message/history writes without introducing a separate config write.

An update history row continues to store the old content under existing rules;
an insert history row stores the new content. The message describes the publish
operation producing that row, never the current config or an adjacent history
row. V3 Admin and Console history detail expose optional
`changeMessage` from the selected row while retaining its original `extInfo`.
Missing extension data, non-object JSON, malformed JSON, or a nontext property
produces null (which serialization may omit) without failing the query. Do not
backfill old records or change retention. History-list responses do not include
the message field. History detail and previous-version detail may return the
message from the selected row. Searching, sorting, and indexing by message are
out of scope.

#### Datasource plugins and upgrade compatibility

Reuse the existing history extension column; no new message column, index, or
schema migration is required in this phase. Built-in adapters still require
read/write validation. The presence of a column in built-in schemas does not
prove third-party plugin support.

`ext_info` is already read and written by history mappers as an ordinary string
column. Adding an optional property to its JSON object does not change SQL,
Mapper SPI, or datasource plugin interfaces and requires no capability
negotiation. An adapter that reads and writes `ext_info` under the existing contract
can store and return the property. If a plugin filters or does not recognize the
property, history detail may omit the message while config publish and history
writes continue through the existing path. Missing or invalid extension data is
handled as a legacy history record without a message.

Do not implement degradation by catching arbitrary database errors and retrying
the publish. Real connection, SQL, transaction, and config-write failures retain
their original error behavior and must not be swallowed or repeat a successful
update. Verify third-party plugins against their existing `ext_info` contract;
this feature does not claim compatibility with plugins that violate that contract.

Old clients that omit the parameter retain existing behavior. Mixed-version
clusters and older independent consoles do not guarantee message persistence or
display; missing messages must not change config-update results. Upgrade the
relevant nodes and validate datasource adaptation before relying on the feature.

#### Acceptance scenarios

These scenarios require implementation and validation; they are not claims of
existing test coverage or passing results:

| Scenario | Expected result |
| --- | --- |
| API first insert with a message, ordinary update, and successful CAS update | Same persistence path is used; history detail reads the corresponding operation message. |
| Console create workflow, deletion, and out-of-scope operations | Console has no create message control; out-of-scope operations gain no message capability. |
| CAS mismatch or validation failure | Config and history remain unchanged; no message is stored. |
| Omitted/blank text, 512/513 code points, Chinese, supplementary characters, and newlines | Input rules, code-point counting, and text round trips are correct. |
| Legacy history, absent extension, malformed JSON, non-object or nontext values | Queries succeed without a message; original extension data is not overwritten. |
| Built-in and third-party datasources | Verify preservation and retrieval of the property under the existing `ext_info` contract; Mapper SPI is unchanged. |
| Database or transaction failure | Preserve original errors and transaction semantics; no error swallowing or republishing. |
| Old clients, mixed versions, and merged/independent consoles | Config-update semantics remain; messages may be absent, and detail renders plain text. |

HTTP implementation changes must update `test/openapi-test` scenarios, coverage
registry, and tests together. Public SDK contract additions must also update the
appropriate SDK ITs, scenarios, and coverage registry. Persistence tests must
verify plugin compatibility and transaction boundaries; HTTP success alone is
not sufficient evidence.

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
