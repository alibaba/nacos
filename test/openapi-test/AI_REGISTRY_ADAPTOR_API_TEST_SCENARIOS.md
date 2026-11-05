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
`ArdAdaptorOpenApiITCase` connects to that second web context on an independently
started standalone server and verifies cross-context behavior through public APIs.

The contract source is the ARD revision pinned by the Nacos AI Registry
Adaptor specification. Coverage targets response compatibility, protocol error
handling, and routing across the adaptor and main-server web contexts.

The [ARD v0.91 test design](ARD_V091_TEST_DESIGN.md) is the single catalog of
per-feature UT/IT, fixtures, assertions and regression groups, including
[token recovery](ARD_V091_TEST_DESIGN.md#recovery-case). The [compatibility review](ARD_V091_COMPATIBILITY_REVIEW.md)
owns BC01..20 code evidence and before/after migration analysis.
The test design distinguishes implemented coverage from pending acceptance.
The scenario rows below describe current coverage; design-only cases do not increase it.

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
| `ArdSearchServiceImplTest`, `ArdSearchControllerTest`, `ArdOpenApiContractTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard`, `GET /v3/ai/ard/agents`, `POST /v3/ai/ard/search`, `POST /v3/ai/ard/explore` | Partial | Public-resource HTTP cases verify query results, legacy binding and pagination; context and facet scenarios are detailed below. Private authorized-resource completeness remains excluded as `DAUTH-F03`, so this row stays Partial. |
| `ArdExceptionHandlerTest`, `AuthFilterTest`, `ArdWebAuthenticationTest`, `ArdOpenApiContractTest` | Errors from ARD controller and authentication operations | Covered | Verifies in the independent adaptor web context that rejected credentials return HTTP 401 with the exact `{errorCode, message}` body, while a valid identity reaches canonical visibility checks without the Nacos `Result<T>` envelope. |
| `ArdSearchServiceImplTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard/ai-catalog.json`, `GET /.well-known/ai-catalog.json` | Partial | Live public catalogs cover 105 resources, namespace isolation, legacy schema and unchanged projection after reading the new manifest. Private authorized completeness remains blocked by `DAUTH-F03`. |
| `ArdWellKnownControllerTest`, `ArdWebAuthenticationTest`, `ArdAdaptorOpenApiITCase`, `ConditionalAnonymousAuthorizationITCase` | `GET /.well-known/ard.json` | Covered | New manifest schema, registry-only publication, context, absolute base URL followed once to Search, explicit invalid/blank credentials, valid identity and anonymous policy; the method-level authorization inventory includes the new operation. Legacy routes retain their contracts. No namespace-level new route is introduced. |
| `ArdArtifactServiceTest`, `ArdSearchControllerTest`, `ArdWebContextIsolationTest`, `ArdAdaptorOpenApiITCase` | `GET /v3/ai/ard/artifacts` | Partial | Public-resource HTTP and component tests cover exact Version/digest artifacts, representation selection, web-context isolation and offline errors. Private authorized-resource completeness remains excluded as `DAUTH-F03`. |

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一的公开契约见[Agent 资源规范](../../specs/zh-cn/ai/agent-management-spec.md)。
ARD artifact 的非空 HTTP 覆盖见下文；编译通过和历史测试数量不自动提升覆盖等级。

### 统一地址模型新增场景（2026-09-15）

`ArdAdaptorOpenApiITCase.testPublicAgentIndexAndUnifiedArtifacts`：旧 A2A 创建 PUBLIC Agent，再通过 Agent API 发布多协议新版本；ARD Search 返回非空 Nacos Agent 与原生 A2A 表示，实际 HTTP 下载验证 exact version/contentDigest、DECLARED Set/Endpoint 新结构，排除 runtime/健康/管理字段，下线后旧 Artifact URL 返回受控 404。原私有资源 DAUTH-F03 用例继续 Disabled。

## Agent JSON / Artifact Schema 更新（2026-09-16）

`testPublicAgentIndexAndUnifiedArtifacts` 验证 Artifact 声明地址的 0/1/true/true 生效值，允许未使用的维护字段为 null，仍禁止非空 Runtime bindings/state/观测值及旧嵌套 endpoint；contentDigest 与管理版本详情一致。采用 Artifact 0.3.0（管理 Schema 0.2.0）。

## ARD query-example publication

Query-example publication is covered by `ArdEntryRendererTest`, the shared
schema tests and `ArdAdaptorOpenApiITCase.testRepresentativeQueriesPreserveSearchAndArtifactContent`.
Missing/0/1/2/5/6 counts are structurally valid in the new schema and warned when
outside its recommendation. Legacy responses select at most five without altering
the source. An Agent published through the existing API is found by its last
example even when that example is omitted from Search/List; the downloaded
artifact retains all six supplied examples. Exact projected 0/1 counts remain
component cases because public resource projectors derive additional examples.

## ARD online relevance filtering

| Scenario | Coverage |
| --- | --- |
| Default-off Search header, unchanged JSON, invalid cursor | `ArdAdaptorOpenApiITCase.testSearchEnhancementStatusAndCursorBoundary` against standalone ARD |
| Enabled empty result bypass | Same IT with server enhancement enabled and `-Dnacos.test.ard.relevance.empty-status=bypassed` |
| Jev protocol, invalid scores/IDs, 429, no retry | `JevRelevanceProviderTest`, deterministic local HTTP stub |
| Concurrent batches, timeout/cancel, node saturation, all-or-nothing fallback | `AiResourceRelevanceFilterTest` |
| Threshold, stable order/score, empty continuation, fallback boundary, currentness, cursor binding | `AiResourceSearchRelevanceTest` |
| Header propagation and no JSON field leakage | `ArdSearchControllerTest` |

The paid-provider success path is not a prerequisite for standalone IT. Deterministic provider
and Search Core tests exercise nonempty success and failure paths without external credentials.
Live empty-result IT covers the HTTP boundary in both configurations; it does not claim live
nonempty Jev filtering coverage. No public Java SDK contract changes.

## ARD query contexts and pagination

Search 正式 HTTP 场景由`testQueryContextAndIndependentFilterGroups`（非空IRI/别名、重绑/reset、OR/AND、legacy与双Schema）、
`testQueryValidationKeepsLegacyHttpBinding`（原始JSON、context/标准值错误与绑定兼容）、
`testQueryCursorContinuationAndExplicitRecovery`（有效续页、等价context、页大小变化、token错误及原条件重查）承担。
增强token的配置/查询指纹由真实Core+确定性provider UT验证，并在本地独立增强部署复跑HTTP续页；
受控远程context的成功与加载错误由本地HTTP矩阵验证。不新增API，DAUTH-F03仍单独Disabled。

`testExploreResolvesContextAndKeepsFacetOptionsIndependent` verifies query/facet aliases,
independent limit/minCount options, nonempty full-set counts and agreement with Search.

Query namespace isolation and query/body matching are covered by `ArdAdaptorOpenApiITCase.testQueryNamespaceBindingAndContextIsolation`, including same-name public resources in two namespaces and invalid credentials before body parsing.

Pagination coverage extends `ArdAdaptorOpenApiITCase.testLegacyCatalogEnumeratesMoreThanOneHundredPublicResources`
with 105-resource Search/List page-size boundaries, complete non-overlapping traversal with changed
page sizes, stable List ordering/date filters, raw input coercion, and invalid List token recovery.
Search cap increases from 50 to 100; List and existing artifact/identifier contracts are unchanged.
GET non-integer pageSize still returns the previously observed 500 and is a separate error-mapping gap.

`ArdContextResolverTest` covers the offline base, IRI expansion, reset and
controlled loader success/error cases; see the [context test design](ARD_V091_TEST_DESIGN.md#context-processing).
Loopback loader UT does not count as deployed API IT or raise the existing coverage classifications.
