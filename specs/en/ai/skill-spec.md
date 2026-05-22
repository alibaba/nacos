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

# Skill Spec

This document defines the AI Registry contract for Skill resources.

## 1. Identity

Skill identity is:

```text
namespaceId -> skill -> name
```

The skill name is parsed from `SKILL.md` metadata during upload and is the
stable resource name.

## 2. Package Model

A Skill is a packaged AI Agent capability. It contains:

- `SKILL.md` as the main descriptor and instruction file;
- optional resource files referenced by the descriptor;
- metadata such as description, business tags, owner, scope, labels, version,
  and download count.

Skill upload accepts ZIP archives. Batch upload is best effort and must report
per-skill success and failure.

## 3. Agent Skills Standard Compatibility

Nacos Skill packages should align with the
[Agent Skills Specification](https://agentskills.io/specification). The
upstream standard defines a skill as "a directory containing, at minimum, a
`SKILL.md` file." Nacos uses this package convention as the external content
contract and adds registry metadata, versioning, visibility, and storage
semantics around it.

Standard-compatible Skill packages follow these rules:

- `SKILL.md` is required and contains YAML frontmatter followed by Markdown
  instruction content.
- `name` and `description` are required frontmatter fields. Nacos maps `name`
  to the AI resource name and maps `description` to searchable metadata.
- `license`, `compatibility`, `metadata`, and `allowed-tools` are optional
  standard fields. Nacos must preserve them in `SKILL.md`; it may index selected
  fields later, but the descriptor remains the source of truth for package
  content.
- Standard package roots may include optional `scripts/`, `references/`, and
  `assets/` directories. Nacos stores and distributes these files as Skill
  resources.
- Skill names should follow the upstream naming rule: lowercase alphanumeric
  characters and hyphens, no leading or trailing hyphen, no consecutive
  hyphens, and no more than 64 characters.

The standard's progressive disclosure model is also part of the Nacos contract:
metadata supports discovery, `SKILL.md` is loaded when a client activates the
Skill, and referenced resources are loaded only when needed. Nacos indexes
metadata for discovery, but must preserve package file boundaries so clients can
apply progressive loading.

Community registry compatibility, including skills CLI and well-known discovery
endpoints, is defined by the
[AI Registry Adaptor Spec](ai-registry-adaptor-spec.md). The adaptor is an
optional compatibility surface and does not replace the canonical Skill resource
lifecycle.

External Skill import from marketplaces or registries is defined by the
[AI Resource Import Plugin Spec](../plugin/ai-resource-import-plugin-spec.md).
Import plugins must produce standard Skill package artifacts, and the Skill
resource operator must apply those artifacts through the normal Skill upload or
draft lifecycle. Import plugins must not bypass package validation, visibility,
storage, or publish governance.

Nacos registry paths must not execute package scripts during upload, query, or
download. Script execution, static analysis, or security scanning belongs to
publish pipeline plugins or to clients that explicitly activate a Skill. The AI
pipeline plugin contract is defined by the
[AI Pipeline Plugin Spec](../plugin/ai-pipeline-plugin-spec.md).

## 4. Storage And Index

Skill metadata and versions use `ai_resource` and `ai_resource_version`.
Skill file content is stored through AI storage. The default storage is
`nacos_config`, but that is an implementation backend.

Skill also maintains a lightweight manifest for client-side discovery. The
manifest is an index derived from Skill metadata and must not become the source
of truth for lifecycle state.

Storage extension rules are defined by the
[AI Storage Plugin Spec](../plugin/ai-storage-plugin-spec.md).

## 5. Lifecycle

Skill follows the shared [AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md):

- upload creates or overwrites a draft according to request options;
- upload may accept an optional commit message and must store it as the draft
  version description when a draft version is created or overwritten;
- bootstrap built-in Skill may directly create online metadata and version
  rows;
- submit may run publish pipeline and then publish or return to draft;
- labels, online/offline, scope, business tags, and delete operations update
  metadata through CAS where required.

Imported Skills follow the upload and draft rules unless the operation is an
explicit bootstrap flow owned by the server. Dependency handling, such as a
Skill referencing MCP tools, is previewed through the unified import flow and
must not recursively import dependencies by default.

## 6. Runtime Behavior

Runtime clients may download Skill ZIP content by latest, explicit version, or
label. Downloads should increment counters and emit trace or download events
where supported.

Runtime clients should not receive broad management operations such as upload,
publish, delete, or unrestricted listing.

## 7. Pending Alignment Issues

- Enforce the full upstream name validation rule during upload.
- Decide which optional standard frontmatter fields should be indexed into
  Nacos metadata while keeping `SKILL.md` as the package source of truth.
- Define compatibility behavior if future Agent Skills versions change package
  structure, frontmatter fields, or progressive-disclosure recommendations.

## 8. Evolution Note

Skill package conventions may change with AI Agent frameworks. New Skill
package formats should define parsing, validation, storage, and migration
rules. Existing Skill versions must remain retrievable unless explicitly
deprecated.
