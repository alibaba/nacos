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

# AgentSpec Spec

This document defines the AI Registry contract for AgentSpec resources.

## 1. Identity

AgentSpec identity is:

```text
namespaceId -> agentspec -> name
```

`name` is read from the AgentSpec manifest and is the stable resource name.

## 2. Package Model

An AgentSpec is a versioned agent specification package. It contains:

- `manifest.json` as the main descriptor;
- optional resource files such as agent instructions or typed assets;
- metadata such as description, business tags, owner, scope, labels, version,
  and download count.

AgentSpec upload accepts ZIP archives. The parser must validate the manifest
and resource references before writing a version.

## 3. Version Model

AgentSpec uses the standard `ai_resource` and `ai_resource_version` model. It
uses AI storage for `manifest.json` and resource files.

Unlike Skill, AgentSpec does not maintain a separate manifest index. Version
metadata and storage pointers are authoritative.

## 4. Lifecycle

AgentSpec follows the shared [AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md):

- upload or create draft;
- update draft;
- submit through publish pipeline or direct publish;
- publish, force publish, update labels, update business tags, update scope,
  online/offline, and delete.

AgentSpec may use simple generated versions or explicit target versions. A
type-specific implementation must reject duplicate versions.

## 5. Runtime Behavior

Runtime clients may load an assembled AgentSpec by explicit version, label, or
latest. Subscriptions should notify clients when the resolved AgentSpec changes.

Runtime clients should not receive upload, publish, force publish, delete, or
broad management listing operations.

### 5.1 Client Listener Protocol

The client uses HTTP polling with a conditional query (MD5-based ETag) to detect
content changes without downloading the full payload every cycle.

- **Polling interval**: configurable via `nacosAiAgentSpecCacheUpdateInterval`;
  default 10 000 ms.
- **Request**: `GET /v3/client/ai/agentspec?namespaceId=&name=&md5=<cached-md5>`.
- **304 Not Modified**: server compares the request MD5 against the stored
  `contentMd5` (computed at publish time). If they match the server returns
  HTTP 304 with an `ETag` header; the client keeps its local cache unchanged.
- **200 OK**: the response carries `Result<AgentSpec>` JSON with response headers
  `X-Nacos-AgentSpec-Md5` and `X-Nacos-AgentSpec-Resolved-Version`. The client
  updates its local cache and md5Cache, then publishes an
  `AgentSpecChangedEvent`.
- **Legacy backfill**: for versions published before the contentMd5 field
  existed, the server lazily computes and stores the MD5 on the first
  conditional query.

## 6. Evolution Note

AgentSpec is expected to evolve with agent framework packaging. Future versions
may add schema validation, signing, dependency manifests, or compatibility
metadata. Such changes must preserve versioned retrieval or provide migration
rules.
