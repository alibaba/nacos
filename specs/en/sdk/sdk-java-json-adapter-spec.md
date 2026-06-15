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

# Nacos Java SDK JSON Adapter Spec

This document defines the JSON serialization compatibility model for the Nacos
Java SDK. It complements the [Java SDK Implementation Spec](./sdk-java-impl-spec.md)
and applies to code shared by the Java Client SDK, Java Maintainer SDK, and
public SDK model objects.

## 1. Scope

The JSON adapter model owns:

- JSON serialization and deserialization used by Java SDK transport paths,
  local cache paths, and typed SDK result parsing;
- runtime adapter selection when multiple JSON implementations are present;
- generic type capture for parameterized Java models such as
  `Result<Page<T>>`, `List<T>`, and `Map<String, Object>`;
- subtype registration used by SDK models such as naming health checkers and
  selectors;
- compatibility rules for legacy Jackson-based utility methods.

The JSON adapter model does not own:

- domain field semantics, which are defined by Config, Naming, AI, and other
  domain specs;
- server-side HTTP message converter behavior, except when server code reuses
  the same shared SDK model classes;
- user application object mapper customization outside Nacos SDK internals.

## 2. Design Goals

The Java SDK JSON layer must satisfy the following goals:

- Keep existing Jackson 2 users working without extra dependencies or code
  changes.
- Support Spring Boot 4 and Jackson 3 environments when Jackson 3 is available
  on the runtime classpath.
- Keep `api`, `client`, and `plugin` modules Java 8 compatible unless the
  module policy changes.
- Avoid exposing Jackson 2 or Jackson 3 core/databind types from new public
  SDK APIs.
- Allow both Jackson 2 and Jackson 3 to exist on the same classpath.
- Provide explicit fallback and diagnostics when no usable JSON adapter is
  available.

## 3. Module Boundaries

### 3.1 Neutral API

The neutral JSON API should be defined in `nacos-api`, because public SDK
models and factories in `api` must be able to use it without depending on
`nacos-common`.

The neutral API should include:

| API | Responsibility |
| --- | --- |
| `JsonUtils` | Public neutral facade for JSON operations and adapter selection. |
| `NacosJsonAdapter` | SPI implemented by concrete JSON providers. |
| `NacosTypeReference<T>` | Generic type capture for parameterized deserialization. |
| JSON subtype registration model | Records base type, subtype, and type name for adapter replay. |

`nacos-api` must not depend on Jackson core or Jackson databind. It may keep
using `jackson-annotations` for model annotations that remain compatible with
Jackson 2 and Jackson 3.

If a user depends only on `nacos-api` and calls JSON functionality without
`nacos-common` or another JSON adapter on the classpath, `JsonUtils` must fail
with a clear error that explains which dependency is missing.

### 3.2 Default Adapters

`nacos-common` should provide the default adapters used by `nacos-client` and
`nacos-maintainer-client`:

| Adapter | Dependency rule | Runtime rule |
| --- | --- | --- |
| Jackson 2 adapter | Jackson 2 core/databind are normal compile dependencies. | Available by default for existing users. |
| Jackson 3 adapter | Jackson 3 dependency must be non-transitive or provided-like. | Available only when Jackson 3 classes are present and usable. |

The Jackson 3 adapter must be safe on Java 8 runtimes. A provider class loaded
by `ServiceLoader` must not expose Jackson 3 classes in public method
signatures, static fields, or eager initialization. It should lazily initialize
the actual Jackson 3 implementation only after availability checks pass.

## 4. Adapter Selection

The Java SDK should support an explicit property:

```text
nacos.client.json.adapter=auto|jackson2|jackson3
```

If the property is absent, `auto` is used.

Adapter selection must follow these rules:

1. Load `NacosJsonAdapter` implementations from the runtime classpath.
2. Call `isAvailable()` on each implementation.
3. If exactly one adapter is available, use that adapter.
4. If Jackson 2 and Jackson 3 adapters are both available, use Jackson 3.
5. If no adapter is available, fail fast with a clear diagnostic message.
6. If the user explicitly selects `jackson2` or `jackson3`, use only that
   adapter and fail fast if it is unavailable.

Adapter availability checks must guard at least:

- `ClassNotFoundException`;
- `NoClassDefFoundError`;
- `UnsupportedClassVersionError`;
- `LinkageError`;
- `ServiceConfigurationError`.

## 5. Neutral Type Model

### 5.1 Generic Types

The Java SDK must use `NacosTypeReference<T>` instead of Jackson
`TypeReference<T>` in new code:

```java
JsonUtils.toObj(json, new NacosTypeReference<Result<Page<ServiceView>>>() {
});
```

`NacosTypeReference<T>` captures a `java.lang.reflect.Type`. Each adapter
converts that `Type` to its own internal type model, such as Jackson 2 or
Jackson 3 `JavaType`. New Nacos APIs must not expose Jackson `TypeReference`.

### 5.2 JavaType

New public APIs must not expose Jackson `JavaType`. Methods that need
parameterized deserialization should accept `Type`, `Class<T>`, or
`NacosTypeReference<T>`. Concrete adapters are responsible for constructing
their internal type representation.

### 5.3 Tree Values

New public SDK APIs should avoid Jackson `JsonNode`. Prefer:

- a concrete DTO when the response contract is known;
- `Map<String, Object>` for simple dynamic JSON objects;
- a future Nacos-owned tree wrapper only when map-based access is insufficient.

Existing `JsonNode` methods may remain as deprecated compatibility surfaces
until the relevant major or compatibility window allows removal.

## 6. Subtype Registration

The neutral JSON layer must support subtype registration without exposing
Jackson `NamedType` or mapper APIs.

Subtype registration must record:

- the base type;
- the concrete subtype;
- the wire type name.

`JsonUtils` must retain subtype registrations and replay them when the selected
adapter is initialized or replaced. This is required for compatibility with
models such as naming health checkers and selectors.

## 7. Public API Rules

New or changed Java SDK public APIs must not expose these concrete Jackson
core/databind types:

- `ObjectMapper`;
- `JsonMapper`;
- `JsonNode`;
- `ObjectNode`;
- `ArrayNode`;
- `TypeReference`;
- `JavaType`;
- Jackson-specific stream helpers such as `ByteBufferBackedInputStream`.

Legacy compatibility utilities, especially `JacksonUtils`, may keep existing
Jackson-specific signatures. New code should use `JsonUtils` instead.

Model annotations from `com.fasterxml.jackson.annotation` may remain when they
are understood by both Jackson 2 and Jackson 3. Public model classes must not
depend on Jackson databind serializer or deserializer classes when an
annotation-only alternative exists. For example, long-to-string rendering
should prefer an annotation-level format over
`@JsonSerialize(using = ToStringSerializer.class)`.

## 8. Known Migration Targets

The following implementation areas should migrate to the neutral JSON layer:

| Area | Expected migration |
| --- | --- |
| `api` module dependencies | Remove Jackson core/databind dependencies; keep annotation dependency if needed. |
| `HealthCheckerFactory` | Use neutral serialization, deserialization, and subtype registration. |
| SDK HTTP response parsing | Replace Jackson `TypeReference` with `NacosTypeReference`. |
| Simple dynamic JSON reads | Replace Jackson `JsonNode` with DTOs or `Map<String, Object>`. |
| gRPC byte buffer parsing | Replace Jackson `ByteBufferBackedInputStream` with a Nacos-owned input stream or byte array path. |
| Canonical JSON comparison | Route through a neutral `JsonUtils.toCanonicalJson` style API. |
| Pipeline maintainer APIs | Prefer typed `PipelineExecution` results over `JsonNode` results. |

Pipeline execution DTOs that are returned by Java Maintainer SDK methods should
live in `nacos-api` or another public model module available to
`nacos-maintainer-client`. Deprecated `JsonNode` methods may remain as legacy
compatibility methods.

## 9. Dependency Compatibility

Jackson 2 and Jackson 3 may coexist because their core/databind packages differ:

- Jackson 2 uses `com.fasterxml.jackson.*`;
- Jackson 3 uses `tools.jackson.*`;
- Jackson annotations remain under `com.fasterxml.jackson.annotation.*`.

The SDK must not rely on classpath coexistence to select Jackson 2. If both
Jackson 2 and Jackson 3 are usable, `auto` mode selects Jackson 3.

## 10. Verification Requirements

Changes to the Java SDK JSON adapter layer must include focused tests for:

- Jackson 2 only: existing behavior remains compatible;
- Jackson 3 only: Java 17 and Spring Boot 4 style applications can use the SDK;
- Jackson 2 and Jackson 3 together: `auto` selects Jackson 3;
- explicit Jackson 2 and explicit Jackson 3 selection;
- missing selected adapter diagnostics;
- subtype registration and deserialization;
- `NacosTypeReference` for `Result<Page<T>>`, `List<T>`, and
  `Map<String, Object>`;
- typed Pipeline Maintainer SDK result parsing when Pipeline DTOs are exposed;
- a minimal Spring Boot 4 application using `nacos-client`.
