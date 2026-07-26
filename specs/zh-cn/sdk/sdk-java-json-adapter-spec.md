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

# Nacos Java SDK JSON 适配规范

本文档定义 Nacos Java SDK 的 JSON 序列化兼容模型。本文补充
[Java SDK 实现规范](./sdk-java-impl-spec.md)，适用于 Java Client SDK、Java
Maintainer SDK 以及公开 SDK 模型对象共享的代码。

## 1. 范围

JSON 适配模型负责：

- Java SDK 传输路径、本地缓存路径和类型化 SDK 结果解析使用的 JSON 序列化与
  反序列化；
- 多个 JSON 实现同时存在时的运行时 adapter 选择；
- `Result<Page<T>>`、`List<T>`、`Map<String, Object>` 等参数化 Java 模型的
  泛型类型捕获；
- Naming health checker、selector 等 SDK 模型使用的 subtype 注册；
- 历史 Jackson 工具方法的兼容规则。

JSON 适配模型不负责：

- Config、Naming、AI 等领域规范定义的字段语义；
- 服务端 HTTP message converter 行为，除非服务端代码复用相同的 SDK 公开模型；
- 用户应用在 Nacos SDK 内部之外对 object mapper 的自定义。

## 2. 设计目标

Java SDK JSON 层必须满足以下目标：

- 现有 Jackson 2 用户无需增加依赖或修改代码即可继续工作。
- 当运行时 classpath 存在 Jackson 3 时，支持 Spring Boot 4 和 Jackson 3 环境。
- 除非模块策略变化，`api`、`client` 和 `plugin` 模块保持 Java 8 兼容。
- 新的公开 SDK API 不暴露 Jackson 2 或 Jackson 3 core/databind 类型。
- 允许 Jackson 2 和 Jackson 3 同时存在于同一个 classpath。
- 没有可用 JSON adapter 时，提供明确的 fallback 和诊断信息。

## 3. 模块边界

### 3.1 中立 API

中立 JSON API 应定义在 `nacos-api` 中，因为 `api` 模块中的公开 SDK 模型和
factory 必须能使用它，同时不能依赖 `nacos-common`。

中立 API 应包含：

| API | 职责 |
| --- | --- |
| `JsonUtils` | JSON 操作和 adapter 选择的公开中立门面。 |
| `NacosJsonAdapter` | 具体 JSON provider 实现的 SPI。 |
| `NacosTypeReference<T>` | 参数化反序列化的泛型类型捕获。 |
| JSON subtype 注册模型 | 记录 base type、subtype 和 type name，用于 adapter replay。 |

`nacos-api` 不得依赖 Jackson core 或 Jackson databind。对于 Jackson 2 和
Jackson 3 均兼容的模型注解，可以继续使用 `jackson-annotations`。

如果用户只依赖 `nacos-api`，并在 classpath 中没有 `nacos-common` 或其他 JSON
adapter 的情况下调用 JSON 功能，`JsonUtils` 必须抛出清晰错误，说明缺失的依赖。

### 3.2 默认 Adapter

`nacos-common` 应提供 `nacos-client` 和 `nacos-maintainer-client` 使用的默认
adapter：

| Adapter | 依赖规则 | 运行时规则 |
| --- | --- | --- |
| Jackson 2 adapter | Jackson 2 core/databind 是普通 compile 依赖。 | 默认对现有用户可用。 |
| Jackson 3 adapter | Jackson 3 依赖必须是非传递或类似 provided。 | 只有 Jackson 3 类存在且可用时才可用。 |

Jackson 3 adapter 必须在 Java 8 运行时安全。由 `ServiceLoader` 加载的 provider
类不得在公开方法签名、静态字段或 eager 初始化中暴露 Jackson 3 类。它应在
availability check 通过后再延迟初始化实际 Jackson 3 实现。

## 4. Adapter 选择

Java SDK 应支持显式配置：

```text
nacos.client.json.adapter=auto|jackson2|jackson3
```

未配置时使用 `auto`。

Adapter 选择必须遵循以下规则：

1. 从运行时 classpath 加载 `NacosJsonAdapter` 实现。
2. 对每个实现调用 `isAvailable()`。
3. 如果只有一个 adapter 可用，使用该 adapter。
4. 如果 Jackson 2 和 Jackson 3 adapter 都可用，使用 Jackson 3。
5. 如果没有可用 adapter，快速失败并给出明确诊断信息。
6. 如果用户显式选择 `jackson2` 或 `jackson3`，只使用对应 adapter；如果不可用，
   快速失败。

Adapter availability check 至少必须防御：

- `ClassNotFoundException`；
- `NoClassDefFoundError`；
- `UnsupportedClassVersionError`；
- `LinkageError`；
- `ServiceConfigurationError`。

## 5. 中立类型模型

### 5.1 泛型类型

Java SDK 新代码必须使用 `NacosTypeReference<T>`，而不是 Jackson
`TypeReference<T>`：

```java
JsonUtils.toObj(json, new NacosTypeReference<Result<Page<ServiceView>>>() {
});
```

`NacosTypeReference<T>` 捕获 `java.lang.reflect.Type`。每个 adapter 将该 `Type`
转换为自己的内部类型模型，例如 Jackson 2 或 Jackson 3 的 `JavaType`。新的 Nacos
API 不得暴露 Jackson `TypeReference`。

### 5.2 JavaType

新的公开 API 不得暴露 Jackson `JavaType`。需要参数化反序列化的方法应接收
`Type`、`Class<T>` 或 `NacosTypeReference<T>`。具体 adapter 负责构造自己的内部
类型表示。

### 5.3 Tree Value

新的公开 SDK API 应避免 Jackson `JsonNode`。优先使用：

- 当响应契约已知时使用具体 DTO；
- 简单动态 JSON 对象使用 `Map<String, Object>`；
- 只有在 map 访问不足时，再考虑未来的 Nacos 自有 tree wrapper。

现有 `JsonNode` 方法可以作为 deprecated 兼容面保留，直到相关大版本或兼容窗口允许
移除。

## 6. Subtype 注册

中立 JSON 层必须支持 subtype 注册，并且不暴露 Jackson `NamedType` 或 mapper API。

Subtype 注册必须记录：

- base type；
- 具体 subtype；
- wire type name。

`JsonUtils` 必须保留 subtype 注册，并在选中的 adapter 初始化或替换时 replay。这是
Naming health checker、selector 等模型保持兼容所必需的。

## 7. 公开 API 规则

新增或修改的 Java SDK 公开 API 不得暴露以下具体 Jackson core/databind 类型：

- `ObjectMapper`；
- `JsonMapper`；
- `JsonNode`；
- `ObjectNode`；
- `ArrayNode`；
- `TypeReference`；
- `JavaType`；
- `ByteBufferBackedInputStream` 等 Jackson 专属 stream helper。

历史兼容工具，尤其是 `JacksonUtils`，可以保留已有 Jackson 专属签名。新代码应使用
`JsonUtils`。

当 `com.fasterxml.jackson.annotation` 中的模型注解可以同时被 Jackson 2 和
Jackson 3 理解时，可以继续保留。公开模型类不应在存在 annotation-only 替代方案时
依赖 Jackson databind serializer 或 deserializer 类。例如，long 转 string 的渲染应
优先使用 annotation 层 format，而不是
`@JsonSerialize(using = ToStringSerializer.class)`。

## 8. 已知迁移目标

以下实现区域应迁移到中立 JSON 层：

| 区域 | 期望迁移 |
| --- | --- |
| `api` 模块依赖 | 移除 Jackson core/databind 依赖；按需保留 annotation 依赖。 |
| `HealthCheckerFactory` | 使用中立序列化、反序列化和 subtype 注册。 |
| SDK HTTP 响应解析 | 使用 `NacosTypeReference` 替换 Jackson `TypeReference`。 |
| 简单动态 JSON 读取 | 用 DTO 或 `Map<String, Object>` 替换 Jackson `JsonNode`。 |
| gRPC byte buffer 解析 | 用 Nacos 自有 input stream 或 byte array 路径替换 Jackson `ByteBufferBackedInputStream`。 |
| Canonical JSON 比较 | 通过中立的 `JsonUtils.toCanonicalJson` 类 API 处理。 |
| Pipeline Maintainer API | 优先返回类型化 `PipelineExecution`，而不是 `JsonNode`。 |

Java Maintainer SDK 方法返回的 Pipeline execution DTO 应放在 `nacos-api` 或其他
`nacos-maintainer-client` 可用的公开模型模块中。已废弃的 `JsonNode` 方法可以作为历史
兼容方法保留。

## 9. 依赖兼容性

Jackson 2 和 Jackson 3 可以共存，因为它们的 core/databind 包路径不同：

- Jackson 2 使用 `com.fasterxml.jackson.*`；
- Jackson 3 使用 `tools.jackson.*`；
- Jackson annotations 仍位于 `com.fasterxml.jackson.annotation.*`。

SDK 不得依赖 classpath 共存来选择 Jackson 2。如果 Jackson 2 和 Jackson 3 都可用，
`auto` 模式选择 Jackson 3。

## 10. 验证要求

Java SDK JSON adapter 层变更必须包含聚焦测试，覆盖：

- 只有 Jackson 2：现有行为保持兼容；
- 只有 Jackson 3：Java 17 和 Spring Boot 4 风格应用可以使用 SDK；
- Jackson 2 和 Jackson 3 同时存在：`auto` 选择 Jackson 3；
- 显式选择 Jackson 2 和显式选择 Jackson 3；
- 选中的 adapter 缺失时的诊断信息；
- subtype 注册和反序列化；
- `NacosTypeReference` 对 `Result<Page<T>>`、`List<T>` 和
  `Map<String, Object>` 的支持；
- Pipeline DTO 暴露后，类型化 Pipeline Maintainer SDK 结果解析；
- 使用 `nacos-client` 的最小 Spring Boot 4 应用。
