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

Runtime clients may query Skill by `name`, optional `version`, optional
`label`, and optional md5. If md5 equals the content md5 of the currently
resolved version, the server may return a not-modified error and must not
include a ZIP body. When the client does not send md5, the server must return
the current content as a ZIP together with the corresponding md5. This
contract supports polling-based listening; subscriptions should report Skill
content changes through md5 transitions without exposing broad management
listing behavior to runtime clients.

Skill content md5 is a version-scoped field. It must be computed once when an
upload or publish writes version content and must be persisted with
`ai_resource_version`; runtime query paths must not recompute it. The md5
input is the full set of package bytes of the published version (`SKILL.md`
and all referenced resources), and its scope must match the ZIP bytes returned
on download so that an md5 hit on the client never corresponds to different
server-side bytes.

For versions that exist before the listening contract is enabled and therefore
lack md5, the server must backfill md5 with the same input scope on the first
listening-style query and return that md5 in the same response. While md5 is
missing or backfill fails, the server must return a 200 response with the ZIP
and must not return not-modified.

### 6.1 Client Polling Listener Contract

Nacos does not push Skill changes; the client SDK realizes listener semantics
by periodically issuing a conditional `GET /v3/client/ai/skills`. The listener
contract is composed of the following requirements that both the server and
any SDK implementing this contract must respect:

- **Response headers**: A 200 response must carry `Content-Type:
  application/zip`, `Content-Disposition: attachment;filename=<name>.zip`,
  `ETag: "<md5>"`, `X-Nacos-Skill-Md5: <md5>`, and
  `X-Nacos-Skill-Resolved-Version: <version>`. The resolved-version header
  reflects the actual version after `label`/`latest` routing parameters are
  resolved.
- **304 response**: When the client-supplied md5 equals the md5 of the
  resolved version, the server returns `304 Not Modified` with an empty body.
  It must include `ETag` and `X-Nacos-Skill-Md5`. Per RFC 7232 it must not
  include `Content-Type` and must not include
  `X-Nacos-Skill-Resolved-Version`, since 304 should not restate entity
  metadata.
- **404 response**: When the skill name is valid but the resource is missing,
  the server returns `404` with business error code `20004`. Clients must
  translate this into local cache eviction and emit a content-missing event,
  and must not treat it as a transient error to retry.
- **Polling schedule**: The SDK must adopt a single-threaded `schedule + tail
  self-reschedule` pattern, so that the next query starts from the previous
  task's completion time rather than its start time. This avoids request
  pile-up under slow server responses. The SDK must not use
  `scheduleAtFixedRate`.
- **Default interval**: The default polling interval is `10000` milliseconds
  (`AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL`). The first query happens
  one interval after the subscription. Because the subscription itself
  synchronously primes the cache, the SDK must not issue an immediate
  additional query.
- **Tunable interval**: Clients override the default by passing
  `nacosAiSkillCacheUpdateInterval`
  (`AiConstants.AI_SKILL_CACHE_UPDATE_INTERVAL`) through `Properties`, in
  milliseconds. This setting only applies to Skill and is independent from
  the polling intervals of Prompt, MCP Server, and AgentCard.
- **Cancellation**: `unsubscribeSkill` must cancel the corresponding task,
  remove the md5 cache entry, and stop emitting polling requests to the
  server.

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
