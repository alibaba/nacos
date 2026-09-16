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

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。

### 统一地址模型新增场景（2026-09-15）

`ArdAdaptorOpenApiITCase.testPublicAgentIndexAndUnifiedArtifacts`：旧 A2A 创建 PUBLIC Agent，再通过 Agent API 发布多协议新版本；ARD Search 返回非空 Nacos Agent 与原生 A2A 表示，实际 HTTP 下载验证 exact version/contentDigest、DECLARED Set/Endpoint 新结构，排除 runtime/健康/管理字段，下线后旧 Artifact URL 返回受控 404。原私有资源 DAUTH-F03 用例继续 Disabled。

## Agent JSON / Artifact Schema 更新（2026-09-16）

`testPublicAgentIndexAndUnifiedArtifacts` 验证 Artifact 声明地址的 0/1/true/true 生效值，允许未使用的维护字段为 null，仍禁止非空 Runtime bindings/state/观测值及旧嵌套 endpoint；contentDigest 与管理版本详情一致。采用 Artifact 0.3.0（管理 Schema 0.2.0）。
