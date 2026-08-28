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

# Prompt Spec

This document defines the AI Registry contract for Prompt resources.

## 1. Identity

Prompt identity is:

```text
namespaceId -> prompt -> promptKey
```

`promptKey` is the resource name. Legacy Config storage may use fixed group
`nacos-ai-prompt` and `{promptKey}.json` data ids, but that mapping is
compatibility storage.

## 2. Content Model

A Prompt version contains:

- prompt template content;
- optional variable definitions;
- md5 for conditional runtime fetch;
- version metadata such as author, commit message, status, and storage pointer.

Nacos treats Prompt content as an AI artifact. It should not parse or execute
prompt templates beyond validation required by the Prompt model.

## 3. Lifecycle

Prompt follows the shared [AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md):

- create draft from new content or an existing version;
- update or delete the current draft;
- submit a draft or reviewed version to publish pipeline or direct publish when
  no pipeline applies, and submit a reviewing version idempotently;
- publish, force publish, online/offline, update labels, update description,
  update business tags, and delete;
- query by explicit version, label, or `latest`.

Prompt labels must not point to draft or reviewing versions.

## 4. Runtime Behavior

Runtime clients may query Prompt by `promptKey`, optional `version`, optional
`label`, and optional md5. If md5 equals the current version content md5, the
server may return a not-modified error.

Subscriptions should report Prompt changes without exposing broad management
listing behavior to runtime clients.

Prompt participates in generic AI Resource Search and provides a
resource-specific Search facade with `resourceType=prompt` fixed. Both reuse
the same index and Query Planner from the
[AI Resource Search Spec](ai-resource-search-spec.md). The Prompt handler
projects only caller-visible, enabled resources whose latest resolves to an
online Version, including name, description, business tags, and template
description suitable for search. Credentials, secret defaults, and runtime
parameter values that may occur in a template do not enter chunks. Generic
Search restricted to Prompt matches resource-specific Search eligibility,
visibility, and currentness.
The Client facade is `GET /v3/client/ai/prompt/search`; it accepts `query`,
repeated `tagsAll`, `pageNo`, and `pageSize`, and returns
`Page<PromptMetaSummary>`.

## 5. Storage

Prompt persists the selected storage provider in each version descriptor.
Operations on an existing version use that persisted provider; the effective
provider configuration applies only to new versions. A legacy descriptor
without a provider uses `nacos_config`.

## 6. Migration

Prompt has a migration task from legacy Prompt storage to
`ai_resource + ai_resource_version + AI storage`. Migration must:

- skip already migrated prompts;
- avoid concurrent multi-node migration through a marker;
- preserve existing versions and latest behavior where possible;
- keep legacy mappings as compatibility storage, not formal Config semantics.

## 7. Evolution Note

Prompt formats, variable schemas, tool-call conventions, and model-provider
requirements may change quickly. Prompt spec revisions may introduce new
content fields or validation rules, but they must keep versioned migration
paths for existing prompts.
