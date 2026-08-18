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

Each version descriptor persists its selected storage provider. Reads, draft
replacements, and deletes use that persisted provider, while the effective
provider configuration selects only new versions. A legacy descriptor without
a provider uses `nacos_config`.

Updating or overwriting a draft replaces the complete AgentSpec package.
Replacement resource files are written first. Resource files referenced by the
previous `manifest.json` but omitted from the replacement package must then be
deleted through the version's persisted provider before the replacement
`manifest.json` and storage descriptor are persisted. If cleanup fails, the
update must fail and retain the previous manifest and descriptor so cleanup can
be retried.

Unlike Skill, AgentSpec does not maintain a separate manifest index. Version
metadata and storage pointers are authoritative.

AgentSpec participates in generic AI Resource Search and provides a
resource-specific Search facade with `resourceType=agentspec` fixed. Both reuse
the document/chunk/facet, currentness, visibility, and pagination semantics
from the [AI Resource Search Spec](ai-resource-search-spec.md). The AgentSpec
handler projects the latest online Version's name, description, business tags,
public dependencies, and capability descriptions. Resource content containing
credentials or private runtime values does not enter chunks. The existing
keyword-paged Client Search migrates to this facade and must not filter again
after shared-index pagination. Generic Search restricted to AgentSpec has the
same candidate eligibility as resource-specific Search.

## 4. Lifecycle

AgentSpec follows the shared [AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md):

- upload or create draft;
- update draft;
- submit a draft or reviewed version through publish pipeline or direct publish,
  and submit a reviewing version idempotently;
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
- **Request**: `GET /v3/client/ai/agentspecs?namespaceId=&name=&md5=<cached-md5>`.
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

### 5.2 Authorization Resource Resolution

AgentSpec HTTP APIs use the plural `/ai/agentspecs` path segment and retain
their declared `AI` sign type and API type while resolving the authorization
resource:

- regular Admin and Console operations resolve the resource name from
  `agentSpecName`;
- `GET .../agentspecs/list` is a namespace-range operation and therefore does
  not resolve a single resource name; row visibility is enforced by the
  visibility plugin;
- `PUT .../agentspecs/draft` resolves the authoritative target from
  `agentSpecCard.name`, because `agentSpecName` is optional and the card is the
  object written by the service;
- `GET /v3/client/ai/agentspecs` resolves the resource from the client `name`
  parameter.

## 6. Evolution Note

AgentSpec is expected to evolve with agent framework packaging. Future versions
may add schema validation, signing, dependency manifests, or compatibility
metadata. Such changes must preserve versioned retrieval or provide migration
rules.
