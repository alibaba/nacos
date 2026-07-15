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

# 环境插件规范

## 范围

环境插件允许部署环境在 Nacos 消费服务端配置前，对指定配置值进行转换。典型用途包括解密
数据库密码，或适配部署环境中的特殊配置。

这是有序覆盖插件。多个插件可以处理不同 key，也可以处理同一个 key。当前实现按 `order()`
升序应用，后写入的值覆盖先写入的值，因此更大的 order 拥有更高最终优先级。通用生命周期
和状态规则由 [Nacos 插件化规范](plugin-spec.md) 定义。服务端启动和 `EnvUtil` 接入规则由
[服务端生命周期与环境配置规范](../design/foundation-server-lifecycle-env-spec.md)定义。

该插件用于部署时配置适配。它必须在 Nacos 模块消费最终配置值之前执行，不应作为通用运行时
配置变更机制。

## 概念

| 概念 | 含义 |
|------|------|
| Declared property key | 插件可以读取并转换的服务端配置项。 |
| Custom value | 插件返回的转换后配置值。 |
| Override order | 多个插件处理同一个 key 时的确定性顺序。 |

## SPI

插件实现 `CustomEnvironmentPluginService`。

| 方法 | 要求 |
|------|------|
| `pluginName()` | 稳定插件名称。 |
| `propertyKey()` | 该插件可以转换的配置 key 集合。 |
| `order()` | 覆盖顺序，更大的值拥有更高最终优先级。 |
| `customValue(property)` | 返回声明 key 的转换结果。 |

该插件以 `environment` 类型暴露给核心插件管理器。

## 执行规则

插件管理器只会把声明的 key 传给插件。插件返回结果中不属于声明集合的 key 会被移除。
最终配置映射使用前，值为 null 的条目也会被移除。

环境插件必须在启动过程中具备确定性。它不得依赖配置绑定之后才初始化的 Nacos 模块。

插件只能返回声明过的 key。某个声明 key 没有转换结果时，插件可以省略它。插件管理器在合并
结果前会移除未知 key 和 null 值。

当多个插件返回同一个 key 时，后应用的值生效。由于当前 manager 按 `order()` 升序应用插件，
更大的 order 拥有更高最终优先级。

## 配置

环境插件文档中的部署开关为：

```properties
nacos.custom.environment.enabled=true
```

当前 environment manager 直接加载 SPI 服务，并使用上面的部署开关。该插件类型通过核心
插件管理器路由时，运行时可用性应收敛到 `environment:{pluginName}` 的统一插件状态。

插件应记录：

- 它转换的精确配置 key；
- 原始值是否必需；
- 外部密钥系统或部署 API 不可用时的失败行为；
- 转换后的值是否可能被下游 Nacos 模块记录到日志中。

如果部署使用环境插件准备加密或 secret 配置值，加密边界仍必须遵守
[配置加密插件规范](config-encryption-plugin-spec.md)。
