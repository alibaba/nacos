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

# Config Resource Spec

This document defines Config resource identity, fields, validation, and metadata.

## 1. Identity

A Config resource is identified by:

```text
namespaceId -> groupName -> dataId
```

| Field | Meaning | Notes |
| --- | --- | --- |
| `namespaceId` | Namespace that owns the config. | Blank or omitted request values are processed as the default namespace id, currently `public`. Storage code may still name this field `tenant` or `tenantId`, but the current model does not require duplicate default-namespace records for empty tenant and `public`. |
| `groupName` | Business group inside a namespace. | New public specs and HTTP v3 forms use `groupName`; lower-level Config model fields and compatibility APIs may still call this value `group`. |
| `dataId` | Config resource name. | `dataId` is the `resourceName` of Config. |

The identity is stable. Changing `namespaceId`, `groupName`, or `dataId` is a new
resource, clone, import, or delete-and-create operation, not an in-place metadata
update.

## 2. Content And Version Fields

| Field | Meaning |
| --- | --- |
| `content` | Opaque configuration payload. It is stored as text content and encoded with the configured persistence encoding. Config must not operate on individual business items inside this payload. |
| `md5` | Content digest used for listener change detection and CAS publish. |
| `encryptedDataKey` | Protected key material for encrypted configs. Empty for normal configs. |
| `type` | Config content type. Valid values are `properties`, `xml`, `json`, `text`, `html`, `yaml`, `toml`, and `unset`; invalid publish input is normalized to `text`. |

Encrypted configs are identified by the `cipher-{algorithm}-` dataId convention
defined in the [Config Encryption Plugin Spec](../plugin/config-encryption-plugin-spec.md).
The Config domain stores the resulting content and `encryptedDataKey`; algorithm
selection and cryptographic operations belong to the encryption plugin.

`type` and `schema`-like metadata do not change the black-box nature of
`content`. They may help presentation, response handling, or extension behavior,
but core Config semantics are defined at whole-resource granularity.

## 3. Metadata Fields

| Field | Meaning | Identity field |
| --- | --- | --- |
| `appName` | Application name or client application metadata. | No |
| `desc` | Human-readable description. | No |
| `configTags` | Comma-separated management tags. | No |
| `use` | Usage description. | No |
| `effect` | Effect description. | No |
| `schema` | Optional schema text. | No |
| `srcUser` / `srcIp` | Audit source of the write operation. | No |
| `createTime` / `modifyTime` | Creation and modification timestamps. | No |

Metadata update must not create a new Config resource identity. Metadata updates
should publish a normal Config change event so listeners can refresh views that
depend on metadata. Local event delivery is defined by the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

## 4. Validation Rules

Config identity fields are required for single-resource operations:

- `dataId` must be non-blank.
- `groupName` must be non-blank.
- `namespaceId` may be omitted only when the interface supports default
  namespace processing.

The Config server validates `dataId`, `groupName`, `namespaceId`, tag, and
selected metadata fields. Public Config names should contain only letters, digits, `_`,
`-`, `.`, and `:` unless a future domain spec explicitly extends the character
set.

Current field limits include:

| Field | Limit |
| --- | --- |
| `namespaceId` | 128 characters when provided. |
| tag | 16 characters. |
| `configTags` | At most 5 tags, each tag at most 64 characters. |
| `desc` | 128 characters. |
| `use` | 32 characters. |
| `effect` | 32 characters. |
| `type` | 32 characters. |
| `schema` | 32768 characters. |
| `content` | Must not exceed the configured `maxContent`; capacity checks may enforce a smaller max-size policy. |

## 5. Internal Group Key

Implementation code may derive an internal group key from `dataId`, `groupName`
value, and `namespaceId` for cache, listener, dump, and fuzzy-watch state. This derived key
is an implementation key and should not replace the canonical public fields in
new API or SDK contracts.

## 6. Related Specs

- [Resource Model Spec](../design/resource-model-spec.md)
- [Config Publish And Query Spec](config-publish-query-spec.md)
- [Config Gray Release Spec](config-gray-release-spec.md)
- [Config Capacity And Ops Spec](config-capacity-ops-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
