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

# AI Resource Lifecycle Spec

This document defines common lifecycle rules for versioned AI Registry
resources. Type-specific specs may refine these rules.

## 1. Status Model

Metadata status:

| Status | Meaning |
| --- | --- |
| `enable` | Resource is available when visible and at least one queryable version exists. |
| `disable` | Resource is disabled at metadata level; type specs define query behavior. |

Version status:

| Status | Meaning |
| --- | --- |
| `draft` | Editable version under construction. |
| `reviewing` | Submitted for publish pipeline review. |
| `reviewed` | Pipeline approved and waiting for explicit publish. |
| `online` | Published and queryable. |
| `offline` | Existing version removed from normal runtime routing. |

## 2. Standard Flow

The standard lifecycle is:

```text
create/upload draft
  -> update draft
  -> submit
  -> reviewing
  -> reviewed
  -> publish
  -> online
  -> offline/online toggle or delete
```

If no publish pipeline is enabled or no pipeline node matches the resource
type, `submit` may publish directly according to the type implementation.

`force-publish` bypasses pipeline validation and must remain an administrative
operation.

## 3. Draft Rules

- A resource should have at most one working draft unless a type spec defines
  overwrite or multi-draft behavior.
- Draft creation may create a new metadata row or fork from an online version.
- Draft update must only modify the current draft version.
- Deleting a draft clears the metadata `editingVersion` pointer and deletes the
  draft version row and storage content.
- Upload operations may be type-specific but should still produce a draft
  version unless the operation is explicitly bootstrap/import.

## 4. Review And Publish Rules

- Submit resolves an explicit version or the current `editingVersion`.
- Submit must fail when no draft target exists.
- A reviewing version must be recorded in metadata as `reviewingVersion`.
- Pipeline execution state may be written to `publishPipelineInfo` and
  `pipeline_execution`.
- Rejected pipeline results move the version back to `draft` and restore the
  editing pointer.
- Approved pipeline results move the version to `reviewed`.
- Publish moves the version to `online`, clears working pointers, increments
  `onlineCnt` when needed, and optionally updates the `latest` label.

Pipeline extension behavior is defined by the
[AI Publish Pipeline Plugin Spec](../plugin/ai-pipeline-plugin-spec.md). This
domain spec defines only how AI resource lifecycle reacts to pipeline results.

## 5. Labels

- `latest` is the reserved default label for the latest published version.
- Labels map to version strings and must not point to `draft` or `reviewing`
  versions.
- Changing labels does not by itself mutate version content or version status.
- Runtime query by label must resolve the label at request time.

## 6. Delete Rules

- Deleting a version should remove the version row and type-owned storage for
  that version.
- Deleting a resource should remove metadata, all version rows, and all
  type-owned storage.
- Delete operations should be idempotent only when the public API contract says
  missing resources are success.
- Deleting an online version should update `onlineCnt` or labels when the type
  implementation supports it.

## 7. Trace And Counters

AI resource operations should emit trace/audit events for create draft, update
draft, submit, review approved/rejected, publish, force publish, online/offline,
delete, label update, description update, scope update, and download.

Trace plugin behavior is defined by the
[Trace Plugin Spec](../plugin/trace-plugin-spec.md). Counters are diagnostic
and must not define authorization or lifecycle state.

## 8. Evolution Note

Lifecycle states may be expanded as AI publishing workflows mature, for example
to support approval chains, staged rollout, policy evaluation, signing, or
artifact scanning. New states must define compatibility with existing
`draft`, `reviewing`, `reviewed`, `online`, and `offline` behavior.
