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
standalone-server IT classes under `src/test/java/com/alibaba/nacos/test/adminapi/auth`
and `src/test/java/com/alibaba/nacos/test/openapi/auth`.

The branch-level coverage target is API scenario coverage: expected capability,
boundary/validation behavior, and controlled exception/error handling.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public API scenarios remain. |
| Pending | No IT currently verifies this public API scenario. |

## Cross-Scope Migration Guard

The guard below verifies the auth-enabled test environment itself. It is not
an Auth API surface row and therefore does not change the four-row Auth API
coverage calculation.

| IT class | Scenario coverage | Current status | Remaining work |
| --- | --- | --- | --- |
| `AuthScopeGuardITCase` | With `nacos.test.auth.enabled=true`, verifies Client anonymous, invalid, and authenticated-no-permission rejection; Client read-only and read-write success; Admin anonymous and ordinary-client rejection plus administrator success; Console anonymous and ordinary-client rejection plus administrator success; anonymous public liveness and independent ARD well-known access; and local rejection of any Nacos authorization header passed to the external-request helper. | Covered | The guard intentionally uses stable representative reads. Exhaustive plugin workflows, resource boundaries, conditional-anonymous behavior, and URI-bypass coverage run in the same OpenAPI module through the focused classes below. |

## Auth API

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `UserLoginAuthApiITCase`, `DefaultAuthApiITCase` | `POST /v3/auth/user/admin`<br>`POST /v3/auth/user/login`<br>`POST /v1/auth/users/login`<br>`GET,POST,PUT,DELETE /v3/auth/user`<br>`GET /v3/auth/user/list`<br>`GET /v3/auth/user/search` | Covered | Verifies bootstrap and repeated-bootstrap conflict, v1/v3 flat token compatibility, blank/wrong/unknown login equivalence, create/list/search/update/delete correctness, every protected operation with missing, invalid, ordinary, and administrator identities, and the `ONLY_IDENTITY` rule that a user may update only its own password. |
| `DefaultAuthApiITCase` role workflow | `GET,POST,DELETE /v3/auth/role`<br>`GET /v3/auth/role/list`<br>`GET /v3/auth/role/search` | Covered | Verifies every role operation under the four-state authorization matrix, binds a real user, observes the exact role through list/search, deletes the binding, and checks the endpoint-specific result rather than treating any non-403 response as success. Reserved-role, duplicate-binding, missing-user, cache invalidation, and persistence branches remain covered by default-auth plugin unit tests. |
| `DefaultAuthApiITCase` permission workflow | `GET,POST,DELETE /v3/auth/permission`<br>`GET /v3/auth/permission/list` | Covered | Verifies every permission operation under the four-state authorization matrix, creates an exact canonical resource permission, observes it through list and duplicate/existence query, then deletes it. Resource/action separation, adjacent-resource denial, read/write behavior, revocation convergence, and permission-cache invalidation are also exercised by `ResourceAuthorizationITCase`; validation and persistence branches remain covered by plugin unit tests. |
| `VisibilityGrantAuthApiITCase`, `DefaultAuthApiITCase`, `ResourceAuthorizationITCase` | `POST /v3/auth/visibility` (`ADMIN_API`)<br>`DELETE /v3/auth/visibility` (`ADMIN_API`) | Covered | Verifies four-state authorization, grant/revoke correctness, owner success, non-owner and no-permission rejection, administrator success, revocation convergence, write-to-`rw` normalization, unsupported-action validation, and missing-resource 404 behavior. Focused unit tests retain idempotency, maximum canonical-resource length, cache invalidation, and security-metadata branches. |

## Cross-Cutting Authorization Coverage

- [AUTHORIZATION_OPERATION_COVERAGE.md](AUTHORIZATION_OPERATION_COVERAGE.md) is the live
  method-level inventory for all 386 production `@Secured` operations across 56 Controllers.
  `ModuleAuthorizationITCase` compares every operation and normalized annotation tuple with
  source on each run; 50 representative Controller scenarios exercise the common four-state
  matrix.
- `ConditionalAnonymousAuthorizationITCase` directly covers all ten current
  `ALLOW_ANONYMOUS` operations. Missing credentials enter the documented public path, while an
  explicitly blank or invalid credential is rejected and never downgraded to anonymous.
- `IdentityOnlyAuthorizationITCase` directly covers the three Console `ONLY_IDENTITY`
  operations. Missing, blank, and invalid credentials are rejected; a valid ordinary identity
  may enter the identity-only business path without an unrelated resource grant.
- `ResourceAuthorizationITCase` covers Config, Naming, and AI namespace/group/name/action
  boundaries, read-versus-write behavior, owner/visibility semantics, adjacent-resource denial,
  and cache-enabled grant/revoke convergence. Its combined Skill boundary method is retained but
  disabled as `DAUTH-F01` because the Client Skill `name` parameter is not mapped to the same
  authorization resource; Config, Naming, Agent, Prompt, and AgentSpec boundaries remain active.
  `AmbiguousUriAuthITCase` retains the encoded,
  double-encoded, dot-segment, matrix-parameter, separator, Unicode, malformed UTF-8, and raw
  request-target bypass regression matrix.
