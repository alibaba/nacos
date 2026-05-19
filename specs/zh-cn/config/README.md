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

# Config 领域规范

这些规范从领域边界到具体行为定义 Nacos Config 领域。

## 领域基础

- [Config 规范](config-spec.md)
- [Config 资源规范](config-resource-spec.md)

## 运行时行为

- [Config 发布与查询规范](config-publish-query-spec.md)
- [Config 监听与订阅规范](config-listener-watch-spec.md)
- [Config 灰度发布规范](config-gray-release-spec.md)

## 运维与存储

- [Config 持久化、Dump 与历史规范](config-persistence-history-spec.md)
- [Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md)
- [Config 容量与运维规范](config-capacity-ops-spec.md)

## 相关规范

- [资源模型规范](../design/resource-model-spec.md)
- [基础能力规范](../design/foundation-capabilities-spec.md)
- [集群成员规范](../design/foundation-cluster-membership-spec.md)
- [远程连接生命周期规范](../design/foundation-remote-connection-spec.md)
- [客户端运行时规范](../client/README.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)
- [HTTP API 规范](../http-api/api-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [SDK 规范](../sdk/sdk-spec.md)
- [配置变更插件规范](../plugin/config-change-plugin-spec.md)
- [配置加密插件规范](../plugin/config-encryption-plugin-spec.md)
- [数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)
