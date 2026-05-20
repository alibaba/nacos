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

# Integration And Adapter Spec

This document defines the shared rules for optional Nacos integration and
adapter modules. It covers integration modules that expose external protocols,
consume external resource sources, or provide console-side assistant workflows.

The AI Registry adaptor has a dedicated
[AI Registry Adaptor Spec](../ai/ai-registry-adaptor-spec.md). This document
links to it but does not redefine the MCP registry, skills.sh, or other AI
Registry protocol compatibility surfaces.

## 1. Positioning

An integration adapter translates between an external system/protocol model and
a Nacos canonical domain model. It is not the owner of the Nacos domain
semantics.

Adapter responsibilities:

- expose external protocol-shaped read APIs or push streams;
- project external resources into Nacos resources when an integration is a
  source of truth;
- provide optional assistant or management workflows on top of existing Nacos
  domains;
- document enablement, authentication, response shape, and failure boundaries.

Adapters must not create new Config, Naming, AI, security, or plugin semantics
outside the owning domain specs.

## 2. General Rules

- Canonical Nacos behavior remains defined by the domain specs, not by adapter
  response payloads or route conventions.
- External-shaped APIs may intentionally avoid the v3 `Result<T>` wrapper when
  the external protocol requires another response shape.
- Adapters that introduce unauthenticated endpoints, broad data exposure, or
  additional ports should be opt-in.
- Adapter failures must be isolated from core domain mutations unless the
  owning domain explicitly documents the adapter as a source-of-truth writer.
- Bidirectional or ingest adapters must document ownership, reconciliation,
  idempotency, and deletion behavior.
- Adapter auth, visibility, and exception handling must be explicit. A
  plugin-like exception handler is acceptable for an external protocol surface,
  but it must not redefine the v3 HTTP API error model.
- Compatibility and removal decisions follow the
  [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 3. Current Integration Modules

| Module | Status | Direction | Canonical owner |
| --- | --- | --- | --- |
| Prometheus service discovery | Optional adapter | Nacos Naming to Prometheus SD JSON | [Naming Specs](../naming/README.md) |
| CMDB compatibility | Compatibility integration | External CMDB labels to Nacos lookup/filtering paths | [Naming Specs](../naming/README.md) |
| Istio adapter | Optional adapter | Nacos Naming to Istio MCP/xDS resources | [Naming Specs](../naming/README.md) |
| K8s Sync | Optional ingest adapter | Kubernetes Service/Endpoints to Nacos Naming | [Naming Specs](../naming/README.md) |
| Copilot console integration | Optional console assistant | Console workflows to LLM assistant services | [Console Spec](../console/console-spec.md), [AI Registry Specs](../ai/README.md) |
| AI Registry adaptor | Optional protocol adapter | Nacos AI Registry to external AI registry protocols | [AI Registry Adaptor Spec](../ai/ai-registry-adaptor-spec.md) |

## 4. Prometheus Service Discovery

The `prometheus` module exposes Prometheus service-discovery payloads derived
from Naming service and instance data.

Current enablement and surface:

- enabled by `nacos.prometheus.metrics.enabled=true`;
- exposes `/prometheus`,
  `/prometheus/namespaceId/{namespaceId}`, and
  `/prometheus/namespaceId/{namespaceId}/service/{service}`;
- returns Prometheus-compatible JSON, not Nacos v3 `Result<T>`.

Rules:

- Prometheus service discovery is a read-only projection of Naming data.
- The payload shape follows Prometheus discovery expectations and must not be
  used as the canonical Naming API.
- When Nacos auth is enabled, the Prometheus module adds dedicated Basic
  authentication and authorization filters for the Prometheus route.
- `PrometheusApiExceptionHandler` is allowed as an adapter-specific exception
  handler because this surface is not a v3 HTTP API. It must not be copied into
  ordinary Nacos domain controllers.

## 5. CMDB Compatibility

The `cmdb` module provides a compatibility integration around external CMDB
labels and entity lookups. It includes the `CmdbReader` and `CmdbWriter` SPIs,
local loading tasks, and an operational lookup route under `/v1/cmdb/ops/label`.

Rules:

- CMDB labels are optional external metadata. They are not the canonical Naming
  service, instance, or cluster metadata model.
- New Naming selector or filtering behavior must not depend on CMDB as the
  standard path.
- CMDB integrations should remain compatibility-oriented unless a later Naming
  spec promotes a new resource model.

## 6. Istio Adapter

The `istio` module maps Nacos Naming resources into Istio MCP and xDS resource
streams.

Current enablement and surface:

- module loading is gated by `nacos.extension.naming.istio.enabled=true`;
- the module requires Naming or microservice function mode;
- the dedicated gRPC server is gated by `nacos.istio.mcp.server.enabled`;
- `nacos.istio.mcp.server.port` defaults to `18848`;
- the module generates Istio resources such as ServiceEntry-derived MCP/xDS
  payloads from Nacos service information.

Rules:

- Nacos service and instance semantics remain defined by Naming specs.
- Istio MCP/xDS response shape follows Istio and Envoy protocol expectations.
- The adapter must tolerate Naming changes through debounce and push behavior
  without becoming the authoritative Naming store.
- Port exposure, auth, and network placement must be documented by deployment
  docs when this adapter is enabled.

## 7. K8s Sync

The `k8s-sync` module projects Kubernetes Service and Endpoints resources into
Nacos Naming resources.

Current enablement and behavior:

- enabled by `nacos.k8s.sync.enabled=true`;
- can run inside a Kubernetes cluster, or outside the cluster with
  `nacos.k8s.sync.outsideCluster=true` and `nacos.k8s.sync.kubeConfig`;
- uses Kubernetes informers for all namespaces;
- creates Nacos services in `DEFAULT_GROUP`;
- creates persistent Nacos instances with `ephemeral=false`.

Rules:

- Kubernetes is the upstream source for this adapter path. Nacos stores a
  projected Naming view.
- Updates must be idempotent because Kubernetes informers can replay add,
  update, and delete events.
- Delete handling must remove projected Nacos instances/services owned by the
  Kubernetes resource.
- Operators must not mix manual ownership of the same projected service without
  a clear reconciliation rule.

## 8. Copilot Console Integration

The `copilot` module provides console assistant workflows for prompt debugging,
prompt optimization, skill generation, and skill optimization.

Current enablement and surface:

- auto-configuration is enabled by default unless
  `nacos.copilot.enabled=false`;
- the module is not loaded when `nacos.deployment.type=server`;
- console routes are under `/v3/console/copilot/*`;
- stream operations use server-sent events rather than the ordinary JSON
  response wrapper;
- LLM access is configured through `nacos.copilot.apiKey`,
  `nacos.copilot.model`, `nacos.copilot.studioUrl`, and
  `nacos.copilot.studioProject`.

Rules:

- Copilot is a console-side assistant integration. It does not redefine AI
  Registry resource lifecycle, Config semantics, or Naming semantics.
- Console API authorization and AI `SignType` rules still apply to Copilot
  console routes.
- Prompt/skill artifacts returned by Copilot must be validated by the owning AI
  resource APIs before they become canonical resources.
- API keys and model credentials must not be exposed through trace, metrics,
  server state, or assistant stream payloads. Credential management responses
  must remain Console API operations with explicit read/write authorization.

## 9. Boundary With AI Registry Adaptor

AI Registry protocol compatibility is owned by the
[AI Registry Adaptor Spec](../ai/ai-registry-adaptor-spec.md). That adapter may
expose external registry protocol routes, bind an additional port, or follow
external response shapes. Its behavior must still respect this document's
opt-in, security, and source-of-truth rules.

## 10. Related Specs

- [Nacos Design Spec](../design/nacos-design-spec.md)
- [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md)
- [Naming Specs](../naming/README.md)
- [Console Spec](../console/console-spec.md)
- [AI Registry Specs](../ai/README.md)
- [AI Registry Adaptor Spec](../ai/ai-registry-adaptor-spec.md)
- [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md)
