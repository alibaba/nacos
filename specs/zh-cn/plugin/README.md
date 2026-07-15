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

# 插件规范

插件规范定义 Nacos 扩展点如何加载、选择、执行、配置，以及如何通过统一插件管理模型暴露。
插件规范扩展 [Nacos 设计规范](../design/nacos-design-spec.md)，必须保持
[资源模型](../design/resource-model-spec.md)语义稳定，并在暴露 HTTP 端点时遵守
[HTTP API](../http-api/api-spec.md) 规则。

## 通用模型

- [Nacos 插件化规范](plugin-spec.md)
- [寻址扩展规范](addressing-plugin-spec.md)

## 数据与配置

- [数据源方言插件规范](datasource-dialect-plugin-spec.md)
- [默认数据源方言插件实现规范](default-datasource-dialect-plugin-spec.md)
- [配置变更插件规范](config-change-plugin-spec.md)
- [配置加密插件规范](config-encryption-plugin-spec.md)

## 运行时扩展

- [环境插件规范](environment-plugin-spec.md)
- [Trace 插件规范](trace-plugin-spec.md)
- [Control 插件规范](control-plugin-spec.md)
- [默认 Control 插件实现规范](default-control-plugin-spec.md)

## AI 扩展

- [AI 发布 Pipeline 插件规范](ai-pipeline-plugin-spec.md)
- [AI 存储插件规范](ai-storage-plugin-spec.md)
- [AI 资源导入插件规范](ai-resource-import-plugin-spec.md)

## 安全扩展

- [鉴权插件规范](../auth/auth-plugin-spec.md)
- [RAM 鉴权插件规范](../auth/ram-auth-plugin-spec.md)
- [OIDC 鉴权插件规范](../auth/oidc-auth-plugin-spec.md)
- [可见性插件规范](../auth/visibility-plugin-spec.md)
