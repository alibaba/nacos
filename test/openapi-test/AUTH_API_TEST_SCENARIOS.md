<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Auth API IT Scenario Index

This document records which auth plugin API operations are covered by the
standalone-server IT classes under `src/test/java/com/alibaba/nacos/test/adminapi/auth`.

The branch-level coverage target is API scenario coverage: expected capability,
boundary/validation behavior, and controlled exception/error handling.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public API scenarios remain. |
| Pending | No IT currently verifies this public API scenario. |

## Auth API

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `User auth API` | `POST /v3/auth/user/login`<br>`POST /v3/auth/user/admin`<br>`GET,POST,PUT,DELETE /v3/auth/user` | Pending | No standalone IT currently verifies login, bootstrap, user CRUD, password update, or search/list behavior for the default auth plugin. |
| `Role auth API` | `GET,POST,DELETE /v3/auth/role`<br>`GET /v3/auth/role/list`<br>`GET /v3/auth/role/search` | Pending | No standalone IT currently verifies role add/delete/list/search behavior, wildcard search, or controlled missing-role cases. |
| `Permission auth API` | `GET,POST,DELETE /v3/auth/permission`<br>`GET /v3/auth/permission/list` | Pending | No standalone IT currently verifies permission add/delete/list behavior, duplicate checks, or controlled validation errors. |
| `VisibilityGrantAuthApiITCase` | `POST,DELETE /v3/auth/visibility` | Partial | Verifies grant/revoke on an existing skill resource, write-to-`rw` action normalization, unsupported action validation, and missing-resource 404 behavior. The default standalone IT profile does not bootstrap auth identities, so auth-enabled owner/global-admin enforcement is still uncovered. |
