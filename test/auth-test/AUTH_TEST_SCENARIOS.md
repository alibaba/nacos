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
| Module authorization | Representative Config, Naming, AI, Core, and Console APIs reject missing identity, invalid identity, and an authenticated user without authority, then accept the same user after the required read permission is granted. |
| Default auth APIs | Administrator login plus user, role, and permission create/query/delete workflows, including rejection of non-admin management attempts. |
| Ambiguous URI handling | Single and double percent encoding, hex case variants, encoded unreserved characters, matrix parameters, duplicate separators, literal and encoded dot segments, slash/backslash variants, Unicode slash lookalikes, malformed UTF-8 and percent escapes, control characters, absolute-form targets, and query confusion cannot reach a protected controller without authorization. |

The malformed-path set is intentionally exercised against a real standalone
server because mock servlet requests do not reproduce connector and servlet
canonicalization. Routable equivalent URIs must return the normal 403 auth response. Invalid or
ambiguous request targets may instead be rejected with a 4xx/5xx response or a closed connection;
redirects and successful responses fail the test because they may expose protected business data.
