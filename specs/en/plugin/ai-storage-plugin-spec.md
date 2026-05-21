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

# AI Storage Plugin Spec

## Scope

The AI storage plugin type abstracts binary or text content storage for AI
resources. Metadata remains owned by the AI resource model and persistence
services; storage plugins own only content read, write, and delete by key.
Common lifecycle and state rules are defined by the
[Nacos Plugin Spec](plugin-spec.md).

This is a routed storage plugin. Multiple storage providers may be registered.
Each `StorageKey.provider` selects one `AiResourceStorage`.

Storage is intentionally separated from
[AI resource metadata](../ai/ai-resource-model-spec.md). The AI domain owns
resource identity, versions, labels, visibility, and lifecycle. Storage plugins
only own content bytes for an opaque storage key.

## Concepts

| Concept | Meaning |
|---------|---------|
| Storage provider | Named backend selected by `StorageKey.provider`. |
| Opaque key | Provider-specific key that upper layers should not parse. |
| Content | Binary or text payload associated with an AI resource version. |
| Metadata | AI resource record stored by the AI persistence layer. |

## SPI

Storage implementations are created by `AiResourceStorageBuilder`.

| Builder method | Requirement |
|----------------|-------------|
| `type()` | Stable storage provider type. |
| `build()` | Build an `AiResourceStorage`. |

The storage service implements:

| Service method | Requirement |
|----------------|-------------|
| `type()` | Runtime storage provider type. |
| `save(storageKey, content)` | Store content for the key. |
| `get(storageKey)` | Read content for the key, or return null when absent. |
| `delete(storageKey)` | Delete content for the key. |

The plugin is exposed to the core plugin manager as type `ai-storage`.

## Routing

Upper layers must construct a `StorageKey` with a non-empty provider and an
opaque key. `AiResourceStorageRouter` routes by provider. Storage plugins must
not parse Nacos resource identity from opaque keys unless their own provider
contract defines that encoding.

The default provider is `nacos_config`, which stores AI resource content through
Nacos config storage.

## Requirements

Storage plugins must preserve byte content exactly. They must not change
resource metadata, version state, [visibility](../auth/visibility-plugin-spec.md),
or authorization. Missing storage providers must fail explicitly. Publish-time
review remains owned by the [AI pipeline](ai-pipeline-plugin-spec.md).

Implementations must document:

- maximum supported content size;
- consistency expectation after `save` and `delete`;
- whether reads are strongly consistent or eventually consistent;
- backup and migration behavior;
- whether storage keys can be exposed in API responses or logs.

## Current Integration Note

The core plugin manager can list loaded AI storage plugins. Current code notes
that enable or disable through unified plugin management is not yet wired into
`AiResourceStorageRouter`. Routing is controlled by registered storage
providers until that integration is completed.
