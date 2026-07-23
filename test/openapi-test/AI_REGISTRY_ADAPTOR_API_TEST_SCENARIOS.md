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
| `ArdSearchServiceImplTest`, `ArdSearchControllerTest` | `GET /v3/ai/ard`, `GET /v3/ai/ard/agents`, `POST /v3/ai/ard/search`, `POST /v3/ai/ard/explore` | Partial | Verifies `items`, integral relevance scores, URI-valued `source`, optional trust identity, pagination, and representative validation. A live standalone adaptor conformance suite remains to be added. |
| `ArdExceptionHandlerTest` | Errors from ARD controller operations | Covered | Verifies the exact `{errorCode, message}` body without the Nacos `Result<T>` envelope and the pinned protocol error-code mapping. |
| `ArdSearchServiceImplTest` | `GET /.well-known/ai-catalog.json` | Covered | Validates the generated catalog against the vendored official JSON Schema from the pinned ARD revision. |
| `ArdArtifactServiceTest`, `ArdWebContextIsolationTest` | `GET /v3/ai/ard/artifacts` | Covered | Verifies complete Skill ZIP generation and proves that the artifact route is owned only by the adaptor context while `/v3/client/ai/skills` remains owned only by the main-server context. |

