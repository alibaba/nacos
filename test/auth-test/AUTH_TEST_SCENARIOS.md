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

# Auth Integration Test Scenarios

The `auth-test` module runs against standalone Nacos with all three auth scopes
enabled: Open API, Admin API, and Console API auth.

| Scenario group | Coverage |
| --- | --- |
| Controller authorization | One non-anonymous API from every secured Controller rejects missing identity, invalid identity, and an authenticated identity without authority, then accepts an identity with the required authority. |
| Default auth plugin APIs | Every protected user, role, permission, and visibility API runs the same four authorization states. Successful administrator workflows and the public login/bootstrap APIs also verify response correctness. |
| Ambiguous URI handling | Single and double percent encoding, hex case variants, encoded unreserved characters, matrix parameters, duplicate separators, literal and encoded dot segments, slash/backslash variants, Unicode slash lookalikes, malformed UTF-8 and percent escapes, control characters, absolute-form targets, and query confusion cannot reach a protected controller without authorization. |

## Secured Controller Coverage

The Controller matrix currently contains 47 representative APIs:

| Area | Controllers covered |
| --- | ---: |
| AI server APIs | 11 |
| Console APIs | 17 |
| Config APIs | 7 |
| Core APIs | 5 |
| Naming APIs | 7 |

Four default auth plugin Controllers are covered exhaustively by the API workflows below rather
than by a single representative endpoint. `ArdSearchController`, `ArdWellKnownController`, and
`SkillClientController` are explicitly excluded because their selectable secured methods permit
anonymous access. A source-tree completeness test scans every production `@Secured` Controller and
fails when a new Controller has neither a representative scenario nor an explicit exclusion.

## Default Auth Plugin API Coverage

| Controller | Protected APIs exercised | Additional correctness checks |
| --- | --- | --- |
| `UserControllerV3` | Create, list, search, update password, and delete | Successful login, rejected wrong-password login, and rejected repeated administrator bootstrap |
| `RoleControllerV3` | Create, list, search, and delete | Created role appears in query results and is removed successfully |
| `PermissionControllerV3` | Create, list, existence check, and delete | Created permission appears in results and the existence API returns true |
| `VisibilityGrantControllerV3` | Grant and revoke | A real Skill draft is created as the visibility-controlled resource |

For each protected auth plugin endpoint, the authorized request must also satisfy the
endpoint-specific business assertion. This prevents a non-403 routing error from being mistaken
for successful authorization.

The malformed-path set is intentionally exercised against a real standalone
server because mock servlet requests do not reproduce connector and servlet canonicalization.
Routable equivalent URIs must return the normal 403 auth response. Invalid or ambiguous request
targets may instead be rejected with a 4xx/5xx response or a closed connection; redirects and
successful responses fail the test because they may expose protected business data.
