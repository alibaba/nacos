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
main Nacos server, so its tests live with the adaptor rather than in the
standalone-server test suite.

The contract source is the ARD revision pinned by the Nacos AI Registry
Adaptor specification. Coverage targets response compatibility, protocol error
handling, and routing across the adaptor and main-server web contexts.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current test verifies the expected behavior and its important result shape. |
| Partial | Representative behavior is verified, but important external scenarios remain. |
| Pending | No test currently verifies this public API scenario. |

## ARD

| API surface / test class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ArdSearchServiceImplTest`, `ArdSearchControllerTest`, `ArdOpenApiContractTest` | `GET /v3/ai/ard`, `GET /v3/ai/ard/agents`, `POST /v3/ai/ard/search`, `POST /v3/ai/ard/explore` | Partial | Verifies `items`, integral relevance scores, URI-valued `source`, optional trust identity, pagination, and the Search/List/Explore shapes pinned by the vendored OpenAPI. A live standalone adaptor conformance suite remains to be added. |
| `ArdExceptionHandlerTest`, `AuthFilterTest`, `ArdWebAuthenticationTest`, `ArdOpenApiContractTest` | Errors from ARD controller and authentication operations | Covered | Verifies in the independent adaptor web context that rejected credentials return HTTP 401 with the exact `{errorCode, message}` body, while a valid identity reaches canonical visibility checks without the Nacos `Result<T>` envelope. |
| `ArdSearchServiceImplTest` | `GET /v3/ai/ard/ai-catalog.json`, `GET /.well-known/ai-catalog.json` | Covered | Validates generated identifiers against the vendored official JSON Schema, keeps the host identifier metadata intact, and verifies that a namespace catalog containing more than 100 resources is not truncated. |
| `ArdArtifactServiceTest`, `ArdWebContextIsolationTest` | `GET /v3/ai/ard/artifacts` | Covered | Verifies complete Skill ZIP generation and proves that the adaptor root context owns the artifact route while the main server at `/nacos` alone owns `/v3/client/ai/skills`. |
