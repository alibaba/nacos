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

# Naming 规范

本目录定义 Nacos Naming 领域规范。Naming 规范基于
[Nacos 设计规范](../design/nacos-design-spec.md)、
[资源模型规范](../design/resource-model-spec.md)、[基础能力规范](../design/foundation-capabilities-spec.md)、
[集群成员规范](../design/foundation-cluster-membership-spec.md)、
[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)、
[内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)、
[AP 一致性规范](../design/foundation-ap-consistency-spec.md)、
[CP 一致性规范](../design/foundation-cp-consistency-spec.md)、
[客户端运行时规范](../client/README.md)，以及已有 HTTP、gRPC、SDK、鉴权和插件规范，
进一步定义服务发现领域。

## 规范结构

### 顶层规范

- [Naming 规范](naming-spec.md)：顶层定位、责任范围、设计原则、接口面和边界。

### 通用规范

- [Naming 资源规范](naming-resource-spec.md)：service、cluster、instance、client、publisher、
  subscriber 和 service type 资源身份。
- [Naming 发现与订阅规范](naming-discovery-subscription-spec.md)：查询、订阅、推送、模糊订阅、
  本地缓存和 failover 语义。
- [Naming 健康检查与保护规范](naming-health-protection-spec.md)：健康检查、enabled 状态、
  权重、内部过滤和保护阈值。
- [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)：service、cluster、instance
  元数据、元数据优先级和 selector 分类。
- [Naming 运维规范](naming-ops-spec.md)：maintainer、诊断、指标、开关和清理边界。

### 按服务类型分化的规范

- [Naming 实例生命周期规范](naming-instance-lifecycle-spec.md)：通用生命周期，以及临时服务和
  持久服务在注册、心跳、注销、批量注册和清理上的规则。
- [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)：通用 client state，以及
  临时服务 AP 状态、持久服务 CP 状态、索引和 snapshot。
- [Naming 临时服务 Distro 一致性规范](naming-ephemeral-distro-consistency-spec.md)：临时
  client ownership、Distro 同步、verify、anti-entropy、清理和最终可见性。
- [Naming 持久服务 CP 一致性规范](naming-persistent-cp-consistency-spec.md)：持久实例 CP 写入、
  metadata group、snapshot、恢复和可见性边界。

## 实现来源

这些规范来自当前 `naming`、`api`、`client`、`core`、`consistency` 和 `maintainer-client`
实现。面向用户的文档仅作为辅助参考。当实现和较旧文档存在冲突时，以当前代码作为本规范来源。
