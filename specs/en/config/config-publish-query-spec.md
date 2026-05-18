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

# Config Publish And Query Spec

This document defines Config publish, delete, query, list, import, export, and
clone semantics.

## 1. Publish

Publishing a formal Config writes the resource identified by
`namespaceId + group + dataId`.

Publish behavior:

- validates identity, content, tag, metadata, namespace, and capacity limits;
- normalizes blank namespace to the default namespace id;
- normalizes invalid config `type` to `text`;
- encrypts content when the dataId matches an encryption plugin and no
  `encryptedDataKey` is supplied;
- writes the formal config or the gray config variant according to request
  fields;
- records persistence trace data;
- publishes a Config change event after a successful write.

Config change events follow the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

CAS publish must compare the supplied `casMd5` with the stored md5. A mismatch
is a resource conflict and must not overwrite the stored config.

## 2. Delete

Deleting a formal Config removes the resource identified by
`namespaceId + group + dataId`, records a remove trace, and publishes a Config
change event. Deleting a gray config removes only the named gray variant.

Deleting a missing config is tolerated by compatibility surfaces, but new
management workflows should avoid presenting a missing delete as evidence that a
resource previously existed.

## 3. Runtime Query Chain

Runtime query uses the Config query chain. The default chain is:

```text
entry/cache lock
  -> content type wrapper
  -> gray rule match
  -> special tag not-found handling
  -> formal config fallback
```

The effective semantic order is:

1. Normalize namespace and find the cache item for `dataId + group + namespace`.
2. If the cache item is missing, return config-not-found.
3. If the cache item is being dumped or locked for modification, return query
   conflict and let the client retry.
4. Match gray rules against request labels such as client IP and tag.
5. If a gray rule matches, return the matched gray content, md5,
   `encryptedDataKey`, last modified time, config type, and gray metadata.
6. If an explicit special tag was requested but no matching tag gray exists,
   return a tag-specific not-found response.
7. Return formal config content, md5, `encryptedDataKey`, last modified time,
   and config type.
8. Resolve the response content type from the config type, defaulting to text.

Runtime query must use cached and dumped content rather than broad persistence
queries. The shared dump and cache boundary is defined by the
[Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md).

## 4. Admin Query

Admin query returns Config detail for management users. When the stored config
is encrypted, Admin query decrypts the content before returning the detail
object.

Runtime query responses may include encrypted content and `encryptedDataKey`.
Client-side decryption belongs to the client config filter and encryption
plugin path described by the [Config Encryption Plugin Spec](../plugin/config-encryption-plugin-spec.md).

## 5. List And Search

Config list and search are management operations. They support exact or fuzzy
conditions over `dataId`, `groupName`, `namespaceId`, `appName`, `configTags`,
`type`, and content detail depending on the API.

Search must be protected by queue, thread, capacity, and wait-time controls so a
broad management search does not disrupt runtime query and listen paths.
Executor and queue boundaries are defined by the
[Task Execution Spec](../design/foundation-task-execution-spec.md).

## 6. Import, Export, And Clone

Import, export, and clone are management operations:

- export packages config content plus metadata;
- import requires metadata and content to agree, applies the requested
  same-config policy, and publishes change events for successful writes;
- clone copies selected configs into a target namespace and optionally target
  group or target dataId;
- import and export must preserve config type, description, app name, group,
  dataId, content, and encryption semantics.

## 7. Interface Rules

| Surface | Rule |
| --- | --- |
| HTTP Open API | Query one known config through `/v3/client/cs/config`; no HTTP listen or broad management behavior. |
| HTTP Admin API | CRUD, metadata, list/search, import, export, clone, beta query/delete, listener, capacity, metrics, and ops use `/v3/admin/cs/*`. |
| gRPC API | Query, publish compatibility, remove compatibility, listen, fuzzy watch, and push messages must preserve the same Config identity and md5 semantics. |
| Client SDK | Runtime query and listener APIs should be preferred for applications; broad management APIs belong to Maintainer SDK. |
