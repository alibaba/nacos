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
| Endpoint independence | Endpoint pre-registration before definition succeeds and does not create an Agent; definition-first and Endpoint-first workflows converge after publish. | Java SDK IT |

## Legacy A2aService First-Version Alignment

| Scenario group | Required assertions | Stable IT / focused test |
| --- | --- | --- |
| Endpoint pre-registration | Register a legacy exact-Version Endpoint before AgentCard release; definition query remains absent, then release makes SERVICE query expose the pre-registered Endpoint. | Java SDK IT |
| Multi-Version redo | One SDK publishes Endpoints for two exact Versions, the real standalone server restarts, and both Version-specific publications recover. | Directed Java SDK IT |
| Redo snapshot isolation | Mutating the caller's original Endpoint or collection after register does not change replay payload. | Focused client unit test |
| Exact/latest routing | An exact subscription receives changes even when that Version is latest; latest receives a pointer move even if the target exact Version is already cached. | Stable Java SDK IT where deterministic plus cache/notifier unit tests |
| Resubscribe | Unsubscribe and resubscribe with an already cached value restarts polling and observes a later change. | Stable Java SDK IT plus scheduling unit test |
| Shutdown | Repeated SDK shutdown stops legacy AgentCard polling and releases its executor without post-shutdown callbacks. | Java SDK IT plus lifecycle unit test |

## Compound Cross-Surface Workflows

| Workflow | Cross-checks |
| --- | --- |
| Generic SDK publishes A2A with `autoSubmit=true`, then RAD Search/Discover, Console/Admin, and legacy A2A read it | All projections share one canonical definition, exact Version, descriptor, declared Endpoint, and digest. |
| Legacy Endpoint first, generic SDK definition second | Endpoint publication never creates definition; after definition publication the old SERVICE query resolves the exact legacy Endpoint while the protocol-neutral Runtime Registry remains intentionally separate. |
| Generic SDK publishes Version 1, registers runtime Endpoint, subscribes latest, publishes Version 2, and registers its Endpoint | Search, exact/latest Discover, polling subscription, legacy A2A query, and legacy subscription converge at each transition. |
| HTTP publish plus gRPC discover, then gRPC publish plus HTTP discover | Definition state and error mapping are transport-equivalent and no Publisher heartbeat identity is required for persistent definition publication. |

Server Watch/Push, local `getAll` or `selectOneHealthy` helpers, management
metadata subscription, rolling upgrade, data migration, dual writes, and
force-publish remain outside this phase.
