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

# Config Gray Release Spec

This document defines gray release semantics for Config.

## 1. Model

A Config resource may have:

- one formal config;
- zero or more gray config variants.

Gray variants are subordinate release state of the same Config identity:

```text
namespaceId -> groupName -> dataId -> grayName
```

`grayName` is not a new top-level Config resource. Formal and gray variants
share the same `namespaceId`, `groupName`, and `dataId`, but may have different
content, md5, encrypted data key, last modified time, and gray rule.

## 2. Gray Rule

A gray rule contains:

| Field | Meaning |
| --- | --- |
| `type` | Rule type, such as `beta` or `tag`. |
| `version` | Rule parser version. |
| `expr` | Raw rule expression. |
| `priority` | Higher priority rules are matched first. |

Rule implementations are loaded through Java SPI using `GrayRule`. A rule must
be parseable, valid, and able to match request labels.

## 3. Built-in Rules

| Rule | `grayName` | Match label | Priority |
| --- | --- | --- | --- |
| Beta | `beta` | `ClientIp` is in the comma-separated beta IP list. | `Integer.MAX_VALUE` |
| Tag | `tag_{tag}` | `Vipserver-Tag` equals the requested tag value. | `Integer.MAX_VALUE - 1` |

When multiple gray variants exist, matching uses descending priority and then
`grayName` order as the tie breaker.

## 4. Publish And Delete

A publish with `betaIps` writes the beta gray variant. A publish with `tag`
writes a tag gray variant. A formal publish writes the formal config when no
gray selector is provided.

Gray publish must:

- validate rule type, version, expression, and priority;
- enforce the maximum gray-version count, default `10` through
  `nacos.config.gray.version.max.count`;
- persist gray content and rule metadata;
- publish a Config change event with the affected `grayName`, following the
  [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md);
- record persistence trace with a gray-specific event type.

Deleting a gray variant removes only that variant and not the formal config.

## 5. Query Semantics

Runtime query matches gray rules before formal config fallback:

1. Build request labels from client IP, explicit tag, and connection labels.
2. Iterate sorted gray variants.
3. Return the first matching gray variant.
4. If an explicit tag was requested and no tag gray matches, return
   tag-specific not-found.
5. Otherwise return the formal config.

Admin beta query returns the beta variant when present. Admin formal query does
not silently return gray content.

## 6. Compatibility And Cleanup

The current domain model is `config_info_gray` plus `GrayRule`. Beta and tag
gray variants are also represented in `config_info_gray` with their
corresponding `grayName` and serialized rule metadata.

Starting with the Nacos 3.3 line, runtime compatibility migration from the
legacy `config_info_beta` and `config_info_tag` tables into `config_info_gray`
is removed. Deployments that upgrade from versions before 3.0 and used beta
gray release must migrate that data before upgrading.

## 7. Pending Specs

- TODO: Define a general rollout governance spec if future Config gray rules go
  beyond beta IP and tag matching.
