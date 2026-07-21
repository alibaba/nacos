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

Before selecting the registered provider, the router checks unified plugin
state for `ai-storage:{provider}`. A disabled provider fails routing explicitly
and must not receive content operations.

The default provider is `nacos_config`, which stores AI resource content through
Nacos config storage.
When the `nacos_config` provider maps an opaque key to a Nacos config coordinate,
it must use stable physical mappings for the logical `dataId` and canonical
resource group:

- For `dataId`, only ASCII letters, ASCII digits, and `_`, `-`, `.`, and `:` are
  preserved. If the logical value contains any other character, the entire value
  is encoded as `enc.` followed by the lowercase hexadecimal representation of
  its UTF-8 bytes. Logical values beginning with the reserved `enc.` prefix,
  matched case-insensitively, are encoded in the same way so they cannot alias an
  automatically encoded value. If that encoded candidate exceeds 255 characters,
  it is replaced with `sha256.` followed by the complete lowercase hexadecimal
  SHA-256 digest of the candidate.
- A canonical resource group is preserved when it does not exceed 128
  characters. If it exceeds that limit, it is replaced with the stable resource
  prefix followed by `sha256.` and the complete lowercase hexadecimal SHA-256
  digest of the canonical group. Conditional group segments reserve and escape
  both the same case-insensitive `enc.` namespace and the exact
  `sha256.<64-hex>` fallback shape before the canonical group is built.
- Physical forms matching the reserved SHA-256 fallback shape are hashed again,
  even when already within the length limit, so a logical key cannot directly
  alias a generated fallback key.

The SHA-256 fallback is deterministic but not reversible. Logical resource
identity remains owned by AI resource metadata. `save`, `get`, and `delete` must
apply exactly the same physical mappings.

### Agent Logical Coordinate

For `type=agent`, the Agent domain constructs this logical Nacos Config
coordinate before handing the provider an opaque `StorageKey`:

```text
group  = agent-version
dataId = agent__<rad-ascii-v1(agentName)>__<version>.json
```

`rad-ascii-v1` and the complete Agent Version storage contract are defined by
the [Agent Storage Spec](../ai/agent-storage-spec.md). This coordinate is a
logical provider input, not a physical Config identity exposed to callers.

The built-in provider must pass both logical segments through the common
`NacosAiConfigKeyCodec`; it must not bypass that codec because the Agent domain
already encoded `agentName`. A safe value within the physical limit remains
identical to the logical value. The common codec owns all length and
reserved-shape handling: an overlong candidate uses its deterministic SHA-256
fallback, and that physical result is not reversible.

Upper layers may persist the logical key format and content digest, but must not
parse a physical Config key, require it to be reversible, or reconstruct Agent
identity from it. `save`, `get`, and `delete` always recompute the physical
coordinate through the same codec.

The provider does not dual-read coordinates produced by an earlier physical
mapping. Existing affected `nacos_config` rows must therefore be migrated in a
coordinated maintenance window before nodes using only the new mapping start.
Migration must be limited to AI-owned coordinates, preflight target-key
uniqueness, and rebuild config caches after the rewrite. The legacy Prompt
mirror in group `nacos-ai-prompt` is a compatibility coordinate outside this
mapping and must remain unchanged.

## Plugin State And Configuration

AI storage providers participate in unified plugin state. Disabling a
non-critical provider keeps the instance loaded and visible to plugin
management, but the router rejects new operations for that provider. The
built-in `ai-storage:nacos_config` provider is the default backend and a
critical plugin required by server AI capabilities, so it cannot be disabled
through plugin management while the server depends on it.

The following properties select a provider for an AI resource domain:

```properties
nacos.ai.prompt.storage.provider=nacos_config
nacos.ai.skill.storage.provider=nacos_config
nacos.ai.agentspec.storage.provider=nacos_config
nacos.ai.agent.storage.provider=nacos_config
```

They are domain routing policy, not private configuration definitions owned by
`ai-storage:nacos_config`.

When the AI module is active, all providers selected independently for Prompt, Skill, and AgentSpec
are required implementations of this critical routed type. The same provider may satisfy multiple
domains. Before startup succeeds, every distinct selected provider must be discovered and enabled;
a different available provider is not a valid fallback. When the AI module is disabled by function
mode or `nacos.extension.ai.enabled=false`, AI storage is inactive and does not impose a startup
requirement.

AI storage implementations are built from Spring-managed services during context refresh, so this
type does not participate in pre-refresh critical validation. The unified plugin manager performs
the same provider-specific validation immediately after the storage builders register their
instances and before Nacos reports startup success.

The built-in provider has no private configuration, does not implement
`PluginConfigSpec`, and is exposed as `configurable=false`. A built
`AiResourceStorage` implementation that owns private configuration may
implement `PluginConfigSpec` and declare canonical keys under:

```properties
nacos.plugin.ai-storage.{provider}.{itemKey}
```

The storage builder is responsible for constructing the service before core
plugin discovery. Unified configuration metadata and apply behavior belong to
the built service instance, not to the builder or the domain routing keys.

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
