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

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current test verifies the expected behavior and its important result shape. |
| Partial | Representative behavior is verified, but important external scenarios remain. |
| Pending | No test currently verifies this public API scenario. |

## ARD

| API surface / test class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ArdSearchServiceImplTest`, `ArdSearchControllerTest`, `ArdOpenApiContractTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard`, `GET /v3/ai/ard/agents`, `POST /v3/ai/ard/search`, `POST /v3/ai/ard/explore` | Covered | Verifies `items`, integral relevance scores, URI-valued `source`, optional trust identity, pagination, ISO date/instant list filters, standard nested and extension field paths, and the Search/List/Explore shapes pinned by the vendored OpenAPI. The live standalone scenario publishes Agent, Skill, Prompt, and MCP resources through the main-server Admin APIs and recalls them from the separate ARD port through the shared index; MCP uses its canonical name as the shared Search identity and retains the compatible ID only in metadata. Agent cases verify one logical identifier, deterministic pure-A2A versus multi-protocol defaults, latest-Version representation eligibility, A2A/Nacos media-type filtering through `artifactKinds`, and representation-aware type facets without duplicating one Agent before totals or pagination. |
| `ArdExceptionHandlerTest`, `AuthFilterTest`, `ArdWebAuthenticationTest`, `ArdOpenApiContractTest` | Errors from ARD controller and authentication operations | Covered | Verifies in the independent adaptor web context that rejected credentials return HTTP 401 with the exact `{errorCode, message}` body, while a valid identity reaches canonical visibility checks without the Nacos `Result<T>` envelope. |
| `ArdSearchServiceImplTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard/ai-catalog.json`, `GET /.well-known/ai-catalog.json` | Covered | Validates generated identifiers against the vendored official JSON Schema, keeps the host identifier metadata intact, verifies that a namespace catalog containing more than 100 resources is not truncated, and confirms that the deployed catalog exposes shared-index resources while the well-known catalog advertises the independent adaptor. |
| `ArdArtifactServiceTest`, `ArdSearchControllerTest`, `ArdWebContextIsolationTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard/artifacts` | Covered | Verifies complete Skill ZIP generation and exact Agent artifact resolution. Agent cases cover native A2A Agent Card and schema-limited Nacos Agent representations, required digest/representation parameters, disabled or missing Agent, offline or missing Version, digest mismatch, unavailable representation, and propagated unexpected persistence failures. The live scenario follows absolute Version/digest URLs returned by ARD and verifies their bodies on the independent port. It also proves that the adaptor root context owns the artifact route while the main server at `/nacos` alone owns `/v3/client/ai/skills`. |
