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
- submit to publish pipeline or direct publish when no pipeline applies;
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

## 5. Migration

Prompt has a migration task from legacy Prompt storage to
`ai_resource + ai_resource_version + AI storage`. Migration must:

- skip already migrated prompts;
- avoid concurrent multi-node migration through a marker;
- preserve existing versions and latest behavior where possible;
- keep legacy mappings as compatibility storage, not formal Config semantics.

## 6. Evolution Note

Prompt formats, variable schemas, tool-call conventions, and model-provider
requirements may change quickly. Prompt spec revisions may introduce new
content fields or validation rules, but they must keep versioned migration
paths for existing prompts.
