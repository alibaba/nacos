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

# 集成与适配器规范

本文定义 Nacos 可选集成与适配器模块的共享规则。它覆盖暴露外部协议、消费外部资源来源，
或提供控制台侧 assistant workflow 的集成模块。

AI Registry adapter 已有独立的
[AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md)。本文只做关联，不重新定义 MCP
registry、skills.sh 或其他 AI Registry 协议兼容面。

## 1. 定位

集成适配器在外部系统/协议模型和 Nacos 标准领域模型之间进行转换。它不是 Nacos 领域语义的拥有者。

适配器责任：

- 暴露外部协议形态的读 API 或 push stream；
- 当某个集成是事实来源时，将外部资源投影为 Nacos 资源；
- 在已有 Nacos 领域之上提供可选 assistant 或管理 workflow；
- 记录启用方式、鉴权、响应形态和失败边界。

适配器不得在所属领域规范之外创建新的 Config、Naming、AI、安全或插件语义。

## 2. 通用规则

- Nacos 标准行为由领域规范定义，而不是由 adapter response payload 或 route convention 定义。
- 当外部协议要求其他响应形态时，外部协议 API 可以有意不使用 v3 `Result<T>` wrapper。
- 引入未鉴权端点、大范围数据暴露或额外端口的 adapter 应默认要求主动开启。
- 除非所属领域明确记录 adapter 是 source-of-truth writer，否则 adapter 失败必须与核心领域变更隔离。
- 双向或 ingest adapter 必须记录归属、reconciliation、幂等和删除行为。
- Adapter 鉴权、可见性和异常处理必须明确。外部协议接口面可以使用插件式 exception handler，
  但不得重新定义 v3 HTTP API 错误模型。
- 兼容与移除决策遵循[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)。

## 3. 当前集成模块

| 模块 | 状态 | 方向 | 标准语义归属 |
| --- | --- | --- | --- |
| Prometheus service discovery | 可选 adapter | Nacos Naming 到 Prometheus SD JSON | [Naming 规范](../naming/README.md) |
| CMDB compatibility | 兼容性集成 | 外部 CMDB label 到 Nacos 查询/过滤路径 | [Naming 规范](../naming/README.md) |
| Istio adapter | 可选 adapter | Nacos Naming 到 Istio MCP/xDS resource | [Naming 规范](../naming/README.md) |
| K8s Sync | 可选 ingest adapter | Kubernetes Service/Endpoints 到 Nacos Naming | [Naming 规范](../naming/README.md) |
| Copilot console integration | 可选控制台 assistant | Console workflow 到 LLM assistant service | [Console 规范](../console/console-spec.md)，[AI Registry 规范](../ai/README.md) |
| AI Registry adaptor | 可选协议 adapter | Nacos AI Registry 到外部 AI registry protocol | [AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md) |

## 4. Prometheus Service Discovery

`prometheus` 模块暴露从 Naming service 和 instance 数据生成的 Prometheus service-discovery payload。

当前启用方式和接口面：

- 通过 `nacos.prometheus.metrics.enabled=true` 启用；
- 暴露 `/prometheus`、`/prometheus/namespaceId/{namespaceId}` 和
  `/prometheus/namespaceId/{namespaceId}/service/{service}`；
- 返回 Prometheus 兼容 JSON，而不是 Nacos v3 `Result<T>`。

规则：

- Prometheus service discovery 是 Naming 数据的只读投影。
- Payload 形态遵循 Prometheus discovery 预期，不得作为标准 Naming API 使用。
- 当 Nacos auth 启用时，Prometheus 模块会为 Prometheus route 添加专用 Basic authentication 和
  authorization filter。
- `PrometheusApiExceptionHandler` 作为 adapter 专属 exception handler 是允许的，因为该接口面不是
  v3 HTTP API；它不得被复制到普通 Nacos 领域 controller。

## 5. CMDB Compatibility

`cmdb` 模块围绕外部 CMDB label 和 entity lookup 提供兼容性集成。它包含 `CmdbReader`、
`CmdbWriter` SPI、本地加载任务，以及 `/v1/cmdb/ops/label` 下的运维查询 route。

规则：

- CMDB label 是可选外部 metadata，不是标准 Naming service、instance 或 cluster metadata 模型。
- 新的 Naming selector 或 filtering 行为不得依赖 CMDB 作为标准路径。
- 除非后续 Naming 规范提升新的资源模型，否则 CMDB 集成应保持兼容性定位。

## 6. Istio Adapter

`istio` 模块将 Nacos Naming 资源映射为 Istio MCP 和 xDS resource stream。

当前启用方式和接口面：

- 模块加载由 `nacos.extension.naming.istio.enabled=true` 控制；
- 模块要求 Naming 或 microservice function mode；
- 独立 gRPC server 由 `nacos.istio.mcp.server.enabled` 控制；
- `nacos.istio.mcp.server.port` 默认是 `18848`；
- 模块根据 Nacos service 信息生成 ServiceEntry 相关 MCP/xDS payload 等 Istio resource。

规则：

- Nacos service 和 instance 语义仍由 Naming 规范定义。
- Istio MCP/xDS 响应形态遵循 Istio 和 Envoy 协议预期。
- Adapter 必须通过 debounce 和 push 行为容忍 Naming 变化，且不得成为权威 Naming store。
- 当启用该 adapter 时，部署文档必须记录端口暴露、鉴权和网络放置方式。

## 7. K8s Sync

`k8s-sync` 模块把 Kubernetes Service 和 Endpoints resource 投影到 Nacos Naming resource。

当前启用方式和行为：

- 通过 `nacos.k8s.sync.enabled=true` 启用；
- 可以在 Kubernetes 集群内运行，也可以通过 `nacos.k8s.sync.outsideCluster=true` 和
  `nacos.k8s.sync.kubeConfig` 在集群外运行；
- 使用 Kubernetes informer 监听所有 namespace；
- 在 `DEFAULT_GROUP` 中创建 Nacos service；
- 创建 `ephemeral=false` 的持久 Nacos instance。

规则：

- Kubernetes 是这条 adapter 路径的上游来源。Nacos 存储其投影后的 Naming 视图。
- Kubernetes informer 可能重放 add、update 和 delete event，因此更新必须具备幂等性。
- 删除处理必须移除由 Kubernetes resource 拥有的投影 Nacos instance/service。
- 没有明确 reconciliation 规则时，运维人员不应混合手动管理同一个投影 service。

## 8. Copilot Console Integration

`copilot` 模块为 prompt debug、prompt optimization、skill generation 和 skill optimization
提供控制台 assistant workflow。

当前启用方式和接口面：

- 自动配置默认启用，除非设置 `nacos.copilot.enabled=false`；
- 当 `nacos.deployment.type=server` 时不会加载该模块；
- 控制台 route 位于 `/v3/console/copilot/*`；
- 流式操作使用 server-sent events，而不是普通 JSON response wrapper；
- LLM 访问通过 `nacos.copilot.apiKey`、`nacos.copilot.model`、
  `nacos.copilot.studioUrl` 和 `nacos.copilot.studioProject` 配置。

规则：

- Copilot 是控制台侧 assistant 集成。它不重新定义 AI Registry resource lifecycle、Config
  语义或 Naming 语义。
- Copilot console route 仍必须遵循 Console API 鉴权和 AI `SignType` 规则。
- Copilot 返回的 prompt/skill artifact 必须通过所属 AI resource API 校验后，才能成为标准资源。
- API key 和模型凭据不得通过 trace、metrics、server state 或 assistant stream payload 暴露。
  凭据管理响应必须保持为带明确读写鉴权的 Console API 操作。

## 9. 与 AI Registry Adaptor 的边界

AI Registry 协议兼容由
[AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md)负责。该 adapter 可以暴露外部 registry
protocol route、绑定额外端口，或遵循外部响应形态。其行为仍必须遵守本文的主动开启、安全和
事实来源规则。

## 10. 相关规范

- [Nacos 设计规范](../design/nacos-design-spec.md)
- [兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)
- [Naming 规范](../naming/README.md)
- [Console 规范](../console/console-spec.md)
- [AI Registry 规范](../ai/README.md)
- [AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md)
- [可观测钩子规范](../design/foundation-observability-hooks-spec.md)
