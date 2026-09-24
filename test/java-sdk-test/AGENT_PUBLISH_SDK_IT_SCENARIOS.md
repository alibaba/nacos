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

# Agent Code Publication Java SDK IT Scenarios

This matrix records the implemented public `AiService.publishAgent` contract
and the first-version legacy `A2aService` recovery alignment. The tests run as
external Java clients against the standalone server.

## Code-First Definition Publication

| Scenario group | Required assertions | Stable IT / focused test |
| --- | --- | --- |
| `autoSubmit=false` | Create the first direct-content Version as `draft`; Admin and Console read the same content and digest; RAD Discover and legacy A2A query do not expose the draft. | Java SDK IT, OpenAPI IT |
| `autoSubmit=true` | Create and submit the first direct-content Version; without a review Pipeline it becomes `online`; RAD Discover and legacy A2A query expose the same A2A descriptor. | Java SDK IT, OpenAPI IT |
| Resume | Publish a draft with `autoSubmit=false`, then repeat the equivalent request with only `autoSubmit=true`; the existing draft is submitted without a duplicate Version. | Java SDK IT, OpenAPI IT |
| Retry convergence | Equivalent false retry returns the draft; equivalent true retry after online returns the existing Version; retry after a submit-result ambiguity converges by rereading state. | Java SDK IT plus focused unit fault injection |
| Conflict and state | Same exact Version with different content, author, change description, or explicitly supplied initial metadata fails; false against an advanced Version and either mode against offline fail without overwrite. | Java SDK IT where stable; service unit tests for every branch |
| Version evolution | Publish a subsequent direct-content Version and a `basedOnVersion` Version; reject first-Version inheritance and both/neither content sources. | Java SDK IT, OpenAPI IT |
| Namespace and caller isolation | Default and custom namespaces are isolated; a request cannot supply namespace; SDK copying preserves every caller-owned field and nested value. | Java SDK IT plus proxy unit tests |
| Transport parity | The same request and error categories work through explicitly selected gRPC and HTTP transports; unsupported negotiated ability fails locally before a remote request. | Java SDK IT plus proxy unit tests |
| Default visibility and scope preservation | HTTP and gRPC publishers create PUBLIC resources; a separate READ-only consumer discovers without visibility grants. PRIVATE blocks discovery; PUBLIC restores discovery. Equivalent publish retries preserve PRIVATE. | `shouldDiscoverDefaultPublicAndPreservePrivateOnPublishRetry` |
| Scope Watch invalidation | PUBLIC to PRIVATE must emit UNAVAILABLE without a discovery payload. The auth-enabled HTTP subscription currently fails to produce its initial snapshot, before the scope mutation. Restore after `DAUTH-F05` is resolved. | Partial: `shouldInvalidateWatchAfterScopeBecomesPrivate` retained with `@Disabled` |
| Endpoint independence | Endpoint pre-registration before definition succeeds and does not create an Agent; definition-first and Endpoint-first workflows converge after publish. | Java SDK IT |

## Legacy A2aService First-Version Alignment

| Scenario group | Required assertions | Stable IT / focused test |
| --- | --- | --- |
| Endpoint pre-registration | Register a legacy exact-Version Endpoint into the canonical Runtime Service before AgentCard release; definition query remains absent, then release makes both RAD and legacy SERVICE query expose the pre-registered Endpoint. | Java SDK IT |
| Multi-Version redo | One SDK publishes legacy Endpoints for two exact Versions as independent canonical child publishers, the real standalone server restarts, and both Runtime bindings recover without overwriting the protocol-neutral publication on the parent connection. | Directed Java SDK IT |
| Redo snapshot isolation | Mutating the caller's original Endpoint or collection after register does not change replay payload. | Focused client unit test |
| Exact/latest routing | An exact subscription receives changes even when that Version is latest; latest receives a pointer move even if the target exact Version is already cached. | Stable Java SDK IT where deterministic plus cache/notifier unit tests |
| Resubscribe | Unsubscribe and resubscribe with an already cached value restarts polling and observes a later change. | Stable Java SDK IT plus scheduling unit test |
| Shutdown | Repeated SDK shutdown stops legacy AgentCard polling and releases its executor without post-shutdown callbacks. | Java SDK IT plus lifecycle unit test |

## Compound Cross-Surface Workflows

| Workflow | Cross-checks |
| --- | --- |
| Generic SDK publishes A2A with `autoSubmit=true`, then RAD Search/Discover, Console/Admin, and legacy A2A read it | All projections share one canonical definition, exact Version, descriptor, declared Endpoint, and digest. |
| Legacy Endpoint first, generic SDK definition second | Endpoint publication never creates definition; after definition publication RAD and the old SERVICE query resolve the same exact canonical Runtime Endpoint and binding. |
| Generic SDK publishes Version 1, registers runtime Endpoint, subscribes latest, publishes Version 2, and registers its Endpoint | Search, exact/latest Discover, polling subscription, legacy A2A query, and legacy subscription converge at each transition. |
| HTTP publish plus gRPC discover, then gRPC publish plus HTTP discover | Definition state and error mapping are transport-equivalent and no Publisher heartbeat identity is required for persistent definition publication. |

Except for the documented scope-invalidation regression above, Server Watch/Push, local `getAll` or `selectOneHealthy` helpers, management
metadata subscription, rolling upgrade, data migration, dual writes, and
force-publish remain outside this phase.

## Agent model consolidation

The publication request is agent.client.AgentPublishRequest, a sibling of agent.admin.AgentDraftCreateRequest through AbstractAgentDraftRequest. Existing HTTP/gRPC publication scenarios exercise shared inherited content, source validation, idempotence and submit semantics with the renamed input.

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。

### 2026-09-15 JSON 门面替换 review

Admin Form 转类型化 Request 改用 JsonUtils/NacosTypeReference；HTTP Form 字段、namespace 传递、公开 SDK Request 和响应结构均未变化。沿用原场景矩阵：非空嵌套定义、空/非法 JSON、默认 namespace、发布后读回及受控错误。执行状态见模型统一验证记录 §7，不能以替换前的 IT 结果替代新实现的验证。

## C09 恢复记录（2026-09-17）

DAUTH-F05 的显式身份校验已在本轮 A2A/RAD C09 修复；所有七项 Agent Discovery
方法及 Scope Watch 方法已移除该编号的 Disabled，可靠性脚本也已恢复对应入口。
此外，HTTP Watch 在权限或 scope 变化而共享 fingerprint 不变时返回受影响的 opaque ID，
由后续 Discover 执行资源授权。当前用例分别断言 HTTP 404 和 gRPC RESOURCE_NOT_FOUND(-404)。
普通测试和重启/集群 fixture 的执行证据分开登记；恢复入口不等于可靠性验证通过。
以上替代本文历史段落中“仍 Disabled”的当前状态描述，历史失败记录保持。
最终阶段结果见 [SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md)。

### A2A/RAD review 范围收敛

Client 发布契约仍为首版普通提交、已有 DRAFT 完整覆盖、非 DRAFT no-op；Admin/Console 保持原有流程。
本轮移除独立存储对象和 Agent 专用 CAS 加固，不新增跨存储或并发原子性保证；历史事务测试不计入当前覆盖。
公开 API 场景集合未增删，覆盖率分母不变；本轮执行证据及限制见
[既有存储边界](../../specs/zh-cn/ai/agent-storage-spec.md)。
