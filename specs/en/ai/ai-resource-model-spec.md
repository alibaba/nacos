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

# AI Resource Model Spec

This document defines the standard metadata and version model for AI Registry
resources. It refines the [AI Registry Spec](ai-registry-spec.md).

## 1. Identity

AI resource metadata identity is:

```text
namespaceId + resourceType + resourceName
```

AI resource version identity is:

```text
namespaceId + resourceType + resourceName + version
```

The public resource name may be exposed with type-specific aliases such as
`mcpName`, `agentName`, `promptKey`, or `name`, but the underlying identity is
still `resourceName`.

## 2. Metadata Row

`AiResource` is the canonical metadata row.

| Field | Meaning |
| --- | --- |
| `namespaceId` | Namespace isolation boundary. |
| `type` | Resource type, such as `prompt`, `skill`, or `agentspec`. |
| `name` | Stable resource name. |
| `desc` | Resource description. |
| `status` | Resource metadata status, currently `enable` or `disable`. |
| `owner` | Creator or owning identity. |
| `scope` | Visibility scope, such as `PUBLIC` or `PRIVATE`. |
| `bizTags` | Business tags used for filtering or UI grouping. |
| `ext` | Extension JSON owned by the resource type. |
| `from` | Source marker for bootstrap, import, or synchronization. |
| `versionInfo` | JSON governance summary, described below. |
| `metaVersion` | Optimistic-lock version used by metadata CAS updates. |
| `downloadCount` | Aggregate download or usage counter where supported. |

`name`, `type`, and `namespaceId` are identity fields and must not be modified
as ordinary metadata.

## 3. Version Row

`AiResourceVersion` is the canonical version row.

| Field | Meaning |
| --- | --- |
| `namespaceId`, `type`, `name` | Parent metadata identity. |
| `version` | Version string unique under the parent resource. |
| `author` | Operator that created or imported the version. |
| `desc` | Version description or commit message. |
| `status` | Version lifecycle status. |
| `storage` | JSON pointer to content storage managed through AI storage plugins. |
| `publishPipelineInfo` | JSON publish review state linked to pipeline execution. |
| `downloadCount` | Per-version download or usage counter where supported. |

Published content should be treated as immutable by default. If a type must
allow content mutation, its type spec must define exact safety rules.

## 4. Version Info JSON

`AiResource.versionInfo` stores the resource-level version summary:

| Field | Meaning |
| --- | --- |
| `editingVersion` | Current draft version, if any. |
| `reviewingVersion` | Current version under review, if any. |
| `onlineCnt` | Count of online versions. |
| `labels` | Label-to-version mappings, including `latest`. |

At most one `editingVersion` and one `reviewingVersion` should exist for one
resource. New draft creation must fail when another working version exists
unless the type spec explicitly defines overwrite behavior.

Labels must not point to draft or reviewing versions. Runtime clients may query
by explicit version, label, or type-specific latest default.

## 5. Storage

The standard model stores metadata in persistence tables and stores payload
content through the AI storage abstraction.

The default storage implementation is Nacos Config based, but Config is only a
storage backend here. AI resource content stored through `nacos_config` must not
be treated as user-owned Config resources.

Storage extension behavior is defined by the
[AI Storage Plugin Spec](../plugin/ai-storage-plugin-spec.md). Database dialect
behavior is defined by the [Data Source Dialect Plugin Spec](../plugin/datasource-dialect-plugin-spec.md).

## 6. Visibility

AI resources implement visibility through the shared visibility plugin model.

Rules:

- create operations should resolve the default scope through the configured
  visibility service;
- read operations should return not found when the resource exists but is not
  visible to the caller;
- write operations must check write visibility before metadata, version, or
  scope mutation;
- query operations should use visibility query advice instead of post-filtering
  large result sets whenever possible.

The extension contract is defined by the
[Visibility Plugin Spec](../auth/visibility-plugin-spec.md).

## 7. Evolution Note

AI Registry is intentionally version-centered because AI assets often change
faster than application configuration or service discovery data. New resource
types should fit the metadata/version split. If upstream AI standards change in
a way that makes an existing model unsafe or misleading, this spec may evolve
with explicit migration and compatibility rules.
