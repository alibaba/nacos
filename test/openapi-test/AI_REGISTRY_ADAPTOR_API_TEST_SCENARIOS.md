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

# AI Registry Adaptor API Test Scenario Index

This document records the external Agentic Resource Discovery (ARD) protocol
coverage in `ai-registry-adaptor`. ARD runs in a web context separate from the
main Nacos server. Fast contract tests live with the adaptor, while
`ArdAdaptorOpenApiITCase` starts that second web context with a real standalone
server and verifies cross-context behavior through public APIs.

The contract source is the ARD revision pinned by the Nacos AI Registry
Adaptor specification. Coverage targets response compatibility, protocol error
handling, and routing across the adaptor and main-server web contexts.

Live requests to the independent adaptor port use a dedicated external-request
helper. The helper rejects Nacos `Authorization` headers before sending a
request, and `AuthScopeGuardITCase` verifies both that boundary and anonymous
well-known catalog access in the auth-enabled migration profile. The live ARD
fixture grants its anonymous identity exact read visibility to each private AI
resource, so the external protocol is tested without forwarding an
administrator or Client credential.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current test verifies the expected behavior and its important result shape. |
| Partial | Representative behavior is verified, but important external scenarios remain. |
| Pending | No test currently verifies this public API scenario. |

## ARD

| API surface / test class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ArdSearchServiceImplTest`, `ArdSearchControllerTest`, `ArdOpenApiContractTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard`, `GET /v3/ai/ard/agents`, `POST /v3/ai/ard/search`, `POST /v3/ai/ard/explore` | Partial | Contract and component coverage remains active. The live standalone shared-index method is retained but disabled as `DAUTH-F03` because private canonical projections are incomplete after the out-of-scope product fix is rolled back. |
| `ArdExceptionHandlerTest`, `AuthFilterTest`, `ArdWebAuthenticationTest`, `ArdOpenApiContractTest` | Errors from ARD controller and authentication operations | Covered | Verifies in the independent adaptor web context that rejected credentials return HTTP 401 with the exact `{errorCode, message}` body, while a valid identity reaches canonical visibility checks without the Nacos `Result<T>` envelope. |
| `ArdSearchServiceImplTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard/ai-catalog.json`, `GET /.well-known/ai-catalog.json` | Partial | Schema, large-catalog, and well-known component coverage remains active; live shared-index catalog projection is part of the `DAUTH-F03` disabled method. |
| `ArdArtifactServiceTest`, `ArdSearchControllerTest`, `ArdWebContextIsolationTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard/artifacts` | Partial | Artifact resolution, errors, and web-context isolation remain covered by component tests; the live absolute Version/digest cross-context assertion is part of the `DAUTH-F03` disabled method. |
