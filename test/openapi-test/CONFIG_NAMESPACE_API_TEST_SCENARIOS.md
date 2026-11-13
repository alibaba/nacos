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

# Config HTTP namespace scenario matrix

This matrix complements existing Config functional ITs and
`ConfigNamespaceResolutionOpenApiITCase`. It adds no API-surface rows to the
coverage totals. Paths omit the server context prefix. Config v1/v2 Controllers
are absent from the current community tree; retained constants and filter URL
patterns are not active legacy API implementations.

The method-level inventory is
[AUTHORIZATION_OPERATION_COVERAGE.md](AUTHORIZATION_OPERATION_COVERAGE.md):
38 operations use the Config namespace parser family. Of these, the eight
management operations below select dedicated parsers. The remaining 30 use
the default parser: 11 methods on ConfigControllerV3, one on
ConfigOpenApiController, four on HistoryControllerV3, one on
ListenerControllerV3, nine on ConsoleConfigController, and four on
ConsoleHistoryController (including the historically named getConfigsByTenant,
whose HTTP parameter is namespaceId). Capacity, metrics, and Config ops use
explicit authorization resources and bypass namespace resource parsing;
their existing annotations and behavior are unchanged.

Parameter-check extractors retain their existing alias validation behavior and
are outside this refactor. The ordinary v3 cases use valid but conflicting tenant
values to isolate resource selection from that separate parameter-validation
stage. Query-chain extractors are also unchanged.

| API operation | Controller namespace binding | Resource parser | Required scenarios |
| --- | --- | --- | --- |
| Client `GET /v3/client/cs/config`; ordinary Admin/Console Config operations | `namespaceId` | Default `ConfigHttpResourceParser` | Omitted, empty, public, explicit namespace; tenant is ignored, including conflicting/repeated tenant; a public READ grant permits the public query and a different namespace grant does not. Existing functional ITs cover explicit namespace operations. |
| Admin `POST /v3/admin/cs/config/clone` | Target `namespaceId`, source `sourceNamespaceId` (scalar) | `ConfigCloneHttpResourceParser` | Cross-namespace clone; empty target writes public; source default retains same-namespace behavior; target WRITE and source READ independently required; no target created on denial; queried target content and source isolation. |
| Console `POST /v3/console/cs/config/clone` | Target `targetNamespaceId`, source `namespaceId` (scalar) | `ConfigCloneHttpResourceParser` with existing special tag | Same clone matrix as Admin; source permission check remains in ConfigProxy for merged and remote Console. |
| Admin/Console `POST /v3/{admin,console}/cs/config/import` | `namespaceId` (scalar) | `ConfigNamespaceHttpResourceParser` | Normal ZIP import preserves readable file content; public default; single text field; query plus multipart text duplication and namespace file parts rejected; missing file retains controlled DATA_EMPTY. |
| Admin `GET /v3/admin/cs/config/export`; Console `GET /v3/console/cs/config/export2` | `ConfigFormV3.namespaceId` (form) | `ConfigExportHttpResourceParser` | Namespace-scoped ZIP and storage-ID selection; public default; missing-primary [] and ! substitutes rejected; explicit primary wins. |
| Admin `DELETE /v3/admin/cs/config/batch`; Console `DELETE /v3/console/cs/config/batchDelete` | `namespaceId` (scalar) | `ConfigNamespaceHttpResourceParser` | Namespace-scoped storage-ID deletion, missing IDs ignored, repeated namespace rejected, default/public. |

Each of the eight dedicated-parser operations is directly exercised with
anonymous, invalid-token, authenticated-no-permission, and administrator
requests. Namespace validation covers repeated identical and distinct values,
missing-primary `field[]`, and explicit-primary precedence. Scalar `!field`
has no default-binding semantics. Both clone source and target are validated.
Validation failures return HTTP 400 / PARAMETER_VALIDATE_ERROR.

Focused module tests additionally verify actual Controller + Spring MVC +
HttpProtocolAuthService execution, plugin failure results and thrown
AccessException, multipart metadata-only access, malformed multipart exceptions,
and isolated environment restoration. Standalone IT uses the default auth
plugin, so custom-plugin exception injection belongs to module tests.

Compatibility: default auth disablement and existing server-identity bypasses
remain unchanged. No new filter, request wrapper, per-request mapping lookup,
or protected API is introduced. The common helper has no tenant alias logic.
Batch delete/export object ownership and clone source-ID scoping are retained.
Java SDK contracts do not change; Maintainer SDK and remote Console use the
existing explicit scalar namespace parameters and remain in the validation scope.
