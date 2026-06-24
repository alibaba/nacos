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

# Jackson 2/3 Adapter Migration TODO

This is a temporary implementation tracker for the Jackson 2 / Jackson 3
adapter work discussed in issue #14466 and defined by
`specs/en/sdk/sdk-java-json-adapter-spec.md` /
`specs/zh-cn/sdk/sdk-java-json-adapter-spec.md`.

Last updated: 2026-06-24.

## Status Legend

- `[x]` Done.
- `[ ]` Not started.
- `[~]` In progress.
- `[!]` Blocked or requires a maintainer decision.

## Current Baseline

- `[x]` Spring Boot dependency has been synced on `develop` and is now Spring
  Boot 4.x.
- `[x]` Java SDK JSON adapter spec has been merged by PR #15360.
- `[x]` Current Jackson exposure points have been scanned across `api`,
  `common`, `client`, `maintainer-client`, and `plugin`.
- `[x]` Neutral JSON API, adapter SPI, adapter selector, type reference, and
  subtype registration model have been added in `nacos-api`.
- `[x]` Confirm final Jackson 3 Maven coordinates and version management in the
  root dependency management before implementation.

## Validation Log

- 2026-06-15, stage 1:
  - `mvn -pl api -Dtest=NacosTypeReferenceTest,JsonAdapterSelectorTest,JsonUtilsTest test`
  - `mvn spotless:apply -pl api`
  - `mvn spotless:check -pl api`
- 2026-06-16, stage 1 coverage supplement:
  - `mvn -pl api -Dtest=NacosTypeReferenceTest,NacosJsonSubtypeTest,JsonAdapterSelectorTest,JsonUtilsTest test`
- 2026-06-16, stage 2 Jackson 2 adapter:
  - `mvn -pl api,common spotless:check`
  - `mvn -pl common -am -Dtest=Jackson2JsonAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl common apache-rat:check`
- 2026-06-16, stage 3 Jackson 3 adapter:
  - `mvn -pl common -am -Dtest=Jackson2JsonAdapterTest,Jackson3JsonAdapterTest -Dsurefire.failIfNoSpecifiedTests=false clean test`
  - `common/target/site/jacoco/jacoco.csv`: Jackson 2 and Jackson 3 adapter
    source files have `LINE_MISSED=0`.
- 2026-06-16, stage 4 API cleanup:
  - `mvn -pl api spotless:apply`
  - `mvn -pl api spotless:check`
  - `mvn -pl api -DskipTests compile`
  - `mvn -pl api -Dtest=HealthCheckerFactoryTest,ConfigInfoTest test`
  - `api/target/site/jacoco/jacoco.csv`: `HealthCheckerFactory` and
    `ConfigBasicInfo` have `LINE_MISSED=0`.
  - `mvn -pl api apache-rat:check`
  - `mvn -pl api dependency:tree -Dincludes=com.fasterxml.jackson.core`
- 2026-06-16, stage 5 common gRPC JSON cleanup:
  - `mvn -pl common spotless:apply`
  - `mvn -pl common spotless:check`
  - `mvn -pl common -am -Dtest=GrpcUtilsTest,ByteBufferInputStreamTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `common/target/site/jacoco/jacoco.csv`: `GrpcUtils` and
    `ByteBufferInputStream` have `LINE_MISSED=0`.
  - `mvn -pl common apache-rat:check`
- 2026-06-17, stage 6 common response type cleanup:
  - `mvn -pl common spotless:apply`
  - `mvn -pl common spotless:check`
  - `mvn -pl common -am -Dtest=AbstractNacosRestTemplateTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `common/target/site/jacoco/jacoco.csv`: `AbstractNacosRestTemplate` has
    `LINE_MISSED=0`.
  - `mvn -pl common apache-rat:check`
- 2026-06-17, stage 7 client HTTP response JSON cleanup:
  - `mvn -pl client spotless:apply`
  - `mvn -pl client spotless:check`
  - `mvn -pl client -am -Dtest=NamingHttpClientProxyTest,AiHttpClientProxyTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `client/target/site/jacoco/jacoco.xml`: changed response parsing lines in
    `NamingHttpClientProxy` and `AiHttpClientProxy` have covered instructions;
    the classes still have pre-existing uncovered legacy branches.
  - `rg "com\\.fasterxml\\.jackson\\.core\\.type\\.TypeReference|com\\.fasterxml\\.jackson\\.databind\\.JsonNode|new TypeReference|JsonNode" client/src/main/java/com/alibaba/nacos/client/naming/remote/http/NamingHttpClientProxy.java client/src/main/java/com/alibaba/nacos/client/ai/remote/AiHttpClientProxy.java`
    returns no matches.
  - `mvn -pl client apache-rat:check`
  - `mvn apache-rat:check -DskipTests`
- 2026-06-17, stage 8 maintainer-client HTTP response JSON cleanup:
  - `mvn -pl maintainer-client spotless:apply`
  - `mvn -pl maintainer-client spotless:check`
  - `mvn -pl maintainer-client -am -DskipTests compile`
  - `mvn -pl maintainer-client -am -Dtest=NacosMaintainerFactoryTest,DefaultServerListManagerTest,A2aMaintainerServiceDefaultMethodsTest,A2aMaintainerServiceImplTest,AgentSpecMaintainerServiceDefaultMethodsTest,AgentSpecMaintainerServiceImplTest,AiMaintainerFactoryTest,AiMaintainerServiceDefaultMethodsTest,NacosAiMaintainerServiceImplTest,PipelineMaintainerServiceImplTest,PromptMaintainerServiceDefaultMethodsTest,PromptMaintainerServiceImplTest,SkillMaintainerServiceDefaultMethodsTest,SkillMaintainerServiceImplTest,ConfigMaintainerFactoryTest,NacosConfigMaintainerServiceImplTest,AbstractCoreMaintainerServiceTest,HttpRequestTest,NacosNamingMaintainerServiceImplTest,NamingMaintainerFactoryTest,ClientHttpProxyTest,HttpClientManagerTest,ParamUtilTest -Dsurefire.failIfNoSpecifiedTests=false clean test`
  - `maintainer-client/target/site/jacoco/jacoco.xml`: changed response
    parsing lines in non-Pipeline maintainer-client implementations have
    covered instructions.
  - `rg "com\\.fasterxml\\.jackson\\.core\\.type\\.TypeReference|new TypeReference|JacksonUtils\\.toObj" maintainer-client/src/main/java -g '*.java'`
    returns only the legacy `PipelineMaintainerServiceImpl` `JsonNode`
    compatibility path.
  - `mvn -pl maintainer-client apache-rat:check`
- 2026-06-18, stage 8 maintainer-client CI IT follow-up:
  - Fixed config maintainer boolean `Result` unwrapping so business failure
    responses keep the original message instead of becoming a null
    `Boolean` unboxing failure.
  - `mvn -pl maintainer-client spotless:apply`
  - `mvn -pl maintainer-client spotless:check`
  - `mvn -pl maintainer-client -am -Dtest=NacosConfigMaintainerServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl maintainer-client -am test` passed when local test HTTP server
    binding was allowed; the sandboxed attempt failed only on local bind
    permission in OIDC dependency tests.
  - `maintainer-client/target/site/jacoco/jacoco.xml`: new config maintainer
    boolean unwrap lines have no missed instructions.
- 2026-06-24, stage 8 Copilot OpenAPI IT follow-up:
  - Treated idempotent Copilot config publish as successful when publish
    returns `false` but the stored config content already matches the target
    content.
  - Fixed Copilot config maintainer-client initialization to pass the current
    Nacos context path so internal config admin requests target the same
    `/nacos` context path used by OpenAPI IT.
  - Treated publish exceptions as idempotent success when the stored config
    content already matches the target content.
  - Switched the Copilot console configuration API to reuse the existing
    console `ConfigProxy` read/write path so standalone OpenAPI IT uses the
    inner config handler and remote mode keeps the normal maintainer-client
    holder configuration.
  - `mvn -pl copilot -Dtest=CopilotConfigStorageTest test`
  - `mvn -pl copilot spotless:apply`
  - `mvn -pl copilot spotless:check`
  - `mvn -pl copilot test`
  - `mvn -pl console -am -Dtest=ConsoleCopilotConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl console spotless:apply`
  - `mvn -pl console spotless:check`
- 2026-06-24, stage 9 client runtime cleanup:
  - Replaced client and maintainer-client selector subtype preload paths with
    `SelectorFactory.preload()`.
  - Replaced client JSON pre-warm from `JacksonUtils.createEmptyJsonNode()` to
    neutral `JsonUtils.preload()`.
  - Replaced `NacosMcpServerCacheHolder` local Jackson 2 canonical mapper with
    neutral `JsonUtils.toCanonicalJson(...)`.
  - `mvn -pl client,maintainer-client -am -Dtest=InitUtilsTest,PreInitUtilsTest,ParamUtilTest,NacosMcpServerCacheHolderTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `client/target/site/jacoco/jacoco.csv`: `InitUtils` and `PreInitUtils`
    have `LINE_MISSED=0`.
  - `client/target/site/jacoco/jacoco.csv`: `NacosMcpServerCacheHolder` has
    `LINE_MISSED=0`.
  - `maintainer-client/target/site/jacoco/jacoco.csv`: `ParamUtil` has
    `LINE_MISSED=0`.
  - `mvn -pl client,maintainer-client spotless:apply`
  - `mvn -pl client,maintainer-client spotless:check`

## Implementation Principles

- Keep `api`, `client`, and `plugin` Java 8 compatible unless the module policy
  changes.
- Put neutral JSON contracts in `nacos-api`; put concrete Jackson 2 and
  Jackson 3 adapters in `nacos-common`.
- Keep Jackson 2 available by default for existing users.
- Make the Jackson 3 adapter non-transitive / provided-like and lazy enough to
  be safe when Jackson 3 is absent or when the runtime is Java 8.
- Keep `JacksonUtils` as the historical Jackson 2 compatibility facade.
  New or migrated SDK code should use neutral `JsonUtils`.
- Do not expose Jackson core/databind types from new public SDK APIs.

## Atomic TODOs

### 1. Neutral JSON API in `nacos-api`

- `[x]` Add `NacosTypeReference<T>`.
  - Files:
    - `api/src/main/java/com/alibaba/nacos/api/utils/json/NacosTypeReference.java`
  - Plan:
    - Capture the generic `java.lang.reflect.Type` in the same usage style as
      Jackson `TypeReference<T>`.
    - Reject raw / missing generic declarations with a clear exception.
  - Validation:
    - Unit tests for `List<String>`, `Map<String, Object>`, and
      `Result<Page<T>>` type capture.

- `[x]` Add neutral adapter SPI.
  - Files:
    - `api/src/main/java/com/alibaba/nacos/api/utils/json/NacosJsonAdapter.java`
    - `api/src/main/java/com/alibaba/nacos/api/utils/json/NacosJsonAdapterName.java`
      or constants in the same package.
  - Plan:
    - Define `name()`, `isAvailable()`, serialization methods, deserialization
      methods for `Class<T>`, `Type`, and `NacosTypeReference<T>`, plus subtype
      registration and canonical JSON hooks.
    - Keep method signatures free of Jackson 2 / Jackson 3 types.
  - Validation:
    - Compile `api` without Jackson core/databind as main dependencies.

- `[x]` Add neutral `JsonUtils` facade and adapter selector.
  - Files:
    - `api/src/main/java/com/alibaba/nacos/api/utils/json/JsonUtils.java`
    - `api/src/main/java/com/alibaba/nacos/api/utils/json/JsonAdapterSelector.java`
      if selector logic is split out for tests.
  - Plan:
    - Load adapters through `ServiceLoader`.
    - Support `nacos.client.json.adapter=auto|jackson2|jackson3`.
    - Auto mode: one available adapter uses that adapter; both available prefer
      Jackson 3; none available fails fast with a diagnostic.
    - Explicit mode: only use the selected adapter and fail if unavailable.
    - Catch `ClassNotFoundException`, `NoClassDefFoundError`,
      `UnsupportedClassVersionError`, `LinkageError`, and
      `ServiceConfigurationError` during discovery and availability checks.
  - Validation:
    - Selector unit tests with fake adapters for auto, explicit, both
      available, none available, unavailable selected adapter, and broken
      provider cases.

- `[x]` Add neutral subtype registration model.
  - Files:
    - `api/src/main/java/com/alibaba/nacos/api/utils/json/JsonSubtype.java`
      or an equivalent package-private model.
  - Plan:
    - Record base type, subtype class, and wire type name.
    - Make `JsonUtils` retain subtype registrations and replay them to the
      selected adapter.
  - Validation:
    - Unit tests that register a subtype before adapter initialization and
      confirm it is replayed.

### 2. Default Adapters in `nacos-common`

- `[x]` Add Jackson 2 adapter.
  - Files:
    - `common/src/main/java/com/alibaba/nacos/common/json/Jackson2JsonAdapter.java`
    - `common/src/main/resources/META-INF/services/...NacosJsonAdapter`
  - Plan:
    - Reuse the current `JacksonUtils` mapper configuration:
      `FAIL_ON_UNKNOWN_PROPERTIES=false` and `NON_NULL` inclusion.
    - Convert `Type` / `NacosTypeReference<T>` to Jackson 2 `JavaType`
      internally.
    - Replay neutral subtype registrations using Jackson 2 `NamedType`.
  - Validation:
    - Unit tests for serialization, `Class<T>`, `Type`,
      `NacosTypeReference<T>`, subtype registration, and exception mapping.
    - `mvn -pl common -am -Dtest=Jackson2JsonAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`

- `[x]` Add Jackson 3 adapter.
  - Files:
    - `common/src/main/java/com/alibaba/nacos/common/json/Jackson3JsonAdapter.java`
    - Optional lazy delegate:
      `common/src/main/java/com/alibaba/nacos/common/json/Jackson3JsonAdapterDelegate.java`
    - `common/pom.xml`
  - Plan:
    - Add Jackson 3 dependencies as provided / optional / non-transitive
      according to the final dependency-management decision.
    - Keep the `ServiceLoader` provider Java 8 safe: no Jackson 3 public
      signatures, no Jackson 3 static fields, no eager mapper initialization.
    - Use `Class.forName` in `isAvailable()` and catch
      `UnsupportedClassVersionError` for Java 8 runtimes.
    - Initialize the actual Jackson 3 mapper lazily only after availability is
      confirmed.
  - Validation:
    - Tests for availability when Jackson 3 is absent, present, and explicitly
      selected.
    - Java 8 compatibility check must not fail just because the provider class
      is loadable.
    - Jackson 3 facade and delegate source files have full line coverage in
      the focused `common` module test run.

- `[x]` Add canonical JSON support.
  - Files:
    - `api/.../JsonUtils.java`
    - `common/.../Jackson2JsonAdapter.java`
    - `common/.../Jackson3JsonAdapter.java`
  - Plan:
    - Provide a neutral method such as `toCanonicalJson(Object)` for stable
      sorted-property JSON.
    - Preserve current `NacosMcpServerCacheHolder` behavior that compares
      sorted, non-null JSON.
  - Validation:
    - Unit tests that property order is stable for simple DTOs and maps.

- `[ ]` Keep and narrow `JacksonUtils`.
  - Files:
    - `common/src/main/java/com/alibaba/nacos/common/utils/JacksonUtils.java`
  - Plan:
    - Retain existing Jackson 2 signatures for compatibility:
      `JsonNode`, `ObjectNode`, `ArrayNode`, `TypeReference`, `JavaType`.
    - Avoid adding new usages in migrated code.
    - Optionally delegate neutral-compatible methods to the Jackson 2 adapter
      while keeping Jackson 2-specific methods backed by the Jackson 2 mapper.
  - Validation:
    - Existing `JacksonUtilsTest` continues to pass.

### 3. Clean `nacos-api` Main Dependencies

- `[x]` Replace databind serializer annotations in public API models.
  - Files:
    - `api/src/main/java/com/alibaba/nacos/api/config/model/ConfigBasicInfo.java`
  - Plan:
    - Replace `@JsonSerialize(using = ToStringSerializer.class)` with an
      annotation-only alternative such as `@JsonFormat(shape = STRING)`.
    - Keep wire behavior for large long IDs serialized as strings.
  - Validation:
    - Model serialization unit test for `id` remains string-valued.

- `[x]` Migrate `HealthCheckerFactory` to neutral JSON.
  - Files:
    - `api/src/main/java/com/alibaba/nacos/api/naming/pojo/healthcheck/HealthCheckerFactory.java`
    - `api/src/main/java/com/alibaba/nacos/api/naming/pojo/healthcheck/HealthCheckType.java`
  - Plan:
    - Replace local `ObjectMapper` with `JsonUtils`.
    - Route `registerSubType(...)` through the neutral subtype registry.
    - Preserve existing built-in health checker subtype behavior.
  - Validation:
    - Health checker serialize / deserialize tests with built-in and registered
      custom subtype.

- `[x]` Remove Jackson core/databind from `api` main dependencies.
  - Files:
    - `api/pom.xml`
  - Plan:
    - Remove `jackson-core` and `jackson-databind` from main dependencies.
    - Keep `jackson-annotations` if public model annotations still require it.
    - Move test-only Jackson usage to test scope or migrate tests to
      `JsonUtils` with a test adapter.
  - Validation:
    - `mvn -pl api -DskipTests compile`
    - `mvn -pl api test`

### 4. Migrate Common and Client Runtime Usage

- `[x]` Replace Jackson `ByteBufferBackedInputStream`.
  - Files:
    - `common/src/main/java/com/alibaba/nacos/common/remote/client/grpc/GrpcUtils.java`
    - New helper such as
      `common/src/main/java/com/alibaba/nacos/common/utils/ByteBufferInputStream.java`
  - Plan:
    - Add a small Nacos-owned `InputStream` over `ByteBuffer`.
    - Use neutral `JsonUtils` in the gRPC payload serialization and parse
      paths.
  - Validation:
    - Existing gRPC payload conversion tests.
    - Unit tests for the Nacos-owned `ByteBufferInputStream`.

- `[x]` Remove `JavaType` from `AbstractNacosRestTemplate`.
  - Files:
    - `common/src/main/java/com/alibaba/nacos/common/http/client/AbstractNacosRestTemplate.java`
  - Plan:
    - Replace `JacksonUtils.constructJavaType(responseType).getRawClass()`
      with neutral raw-class resolution for `Class`, `ParameterizedType`, and
      unsupported `Type` fallback.
  - Validation:
    - HTTP response handler selection unit tests.

- `[x]` Migrate client HTTP response parsing from Jackson `TypeReference`.
  - Files:
    - `client/src/main/java/com/alibaba/nacos/client/naming/remote/http/NamingHttpClientProxy.java`
    - `client/src/main/java/com/alibaba/nacos/client/ai/remote/AiHttpClientProxy.java`
  - Plan:
    - Replace `TypeReference<T>` with `NacosTypeReference<T>`.
    - Replace HTTP response `JacksonUtils.toObj(...)` calls with
      `JsonUtils.toObj(...)`.
    - Replace simple `JsonNode` reads with `Map<String, Object>`.
  - Validation:
    - Naming HTTP proxy tests.
    - AI HTTP proxy tests.

- `[x]` Migrate maintainer-client HTTP response parsing.
  - Files:
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/remote/ClientHttpProxy.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/naming/NacosNamingMaintainerServiceImpl.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/config/NacosConfigMaintainerServiceImpl.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/core/AbstractCoreMaintainerService.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/ai/*MaintainerServiceImpl.java`
  - Plan:
    - Replace `TypeReference<T>` with `NacosTypeReference<T>`.
    - Move parsing through `JsonUtils`.
    - Leave legacy `Pipeline JsonNode` methods until typed DTO APIs are added.
  - Validation:
    - Maintainer-client unit tests.
    - Changed response parsing lines have no missed JaCoCo instructions in the
      focused stage 8 test run.

- `[x]` Migrate subtype preload paths.
  - Files:
    - `client/src/main/java/com/alibaba/nacos/client/naming/utils/InitUtils.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/utils/ParamUtil.java`
  - Plan:
    - Replace `JacksonUtils.registerSubtype(...)` with
      `JsonUtils.registerSubtype(...)`.
    - Ensure selector subtype registrations replay to both Jackson 2 and
      Jackson 3 adapters.
  - Validation:
    - Selector serialization / deserialization tests.

- `[x]` Replace JSON pre-warm path.
  - Files:
    - `client/src/main/java/com/alibaba/nacos/client/utils/PreInitUtils.java`
  - Plan:
    - Replace `JacksonUtils.createEmptyJsonNode()` with a neutral
      `JsonUtils.preload()` / `JsonUtils.getAdapterName()` style call.
  - Validation:
    - Ensure async preload still initializes the selected adapter without
      forcing Jackson 2.

- `[x]` Migrate MCP cache canonical comparison.
  - Files:
    - `client/src/main/java/com/alibaba/nacos/client/ai/cache/NacosMcpServerCacheHolder.java`
  - Plan:
    - Replace local Jackson 2 `JsonMapper` with neutral
      `JsonUtils.toCanonicalJson(...)`.
  - Validation:
    - Cache change detection test for semantically equal objects with stable
      ordering.

### 5. Pipeline Maintainer API Typing

- `[ ]` Move or duplicate pipeline public DTOs into `nacos-api`.
  - Current files:
    - `ai/src/main/java/com/alibaba/nacos/ai/pipeline/model/PipelineExecution.java`
    - `ai/src/main/java/com/alibaba/nacos/ai/pipeline/model/PipelineExecutionResult.java`
    - `ai/src/main/java/com/alibaba/nacos/ai/pipeline/model/PipelineNodeResult.java`
    - `ai/src/main/java/com/alibaba/nacos/ai/pipeline/model/PipelineExecutionStatus.java`
    - `plugin/ai/src/main/java/com/alibaba/nacos/plugin/ai/pipeline/model/Checkpoint.java`
  - Plan:
    - Move shared execution DTOs to an API package such as
      `com.alibaba.nacos.api.ai.model.pipeline`.
    - Resolve `PipelineNodeResult -> Checkpoint` first by moving `Checkpoint`
      to `api` or introducing an API-owned checkpoint DTO.
    - Update `ai` server code and tests to import the API DTOs.
  - Validation:
    - `ai` unit tests and pipeline repository tests continue to pass.

- `[ ]` Add typed maintainer-client methods.
  - Files:
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/ai/PipelineAdminClient.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/ai/PipelineMaintainerService.java`
    - `maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/ai/PipelineMaintainerServiceImpl.java`
  - Plan:
    - Add:
      - `Result<PipelineExecution> getPipelineExecution(String pipelineId)`
      - `Result<Page<PipelineExecution>> listPipelineExecutions(...)`
    - Keep existing `JsonNode` methods as deprecated compatibility methods.
    - Use `NacosTypeReference<Result<PipelineExecution>>` and
      `NacosTypeReference<Result<Page<PipelineExecution>>>` for parsing.
  - Validation:
    - Maintainer-client pipeline tests for typed and deprecated methods.

### 6. Dependency and Compatibility Matrix

- `[ ]` Adjust dependency management.
  - Files:
    - Root `pom.xml`
    - `api/pom.xml`
    - `common/pom.xml`
    - `client/pom.xml`
    - `maintainer-client/pom.xml`
  - Plan:
    - Keep Jackson 2 compile dependency through the default Jackson 2 adapter
      path so existing users remain compatible.
    - Make Jackson 3 available for compilation but not transitively forced on
      existing users.
    - Ensure `client` does not need direct Jackson core/databind once migrated.
  - Validation:
    - Dependency tree checks for `nacos-api`, `nacos-client`, and
      `nacos-maintainer-client`.

- `[ ]` Add dependency conflict guard tests / samples.
  - Plan:
    - Jackson 2 only: default behavior unchanged.
    - Jackson 3 only: Java 17 / Spring Boot 4 style classpath works.
    - Jackson 2 + Jackson 3: auto mode selects Jackson 3.
    - Explicit Jackson 2 / Jackson 3 property works.
    - Explicit missing adapter fails with actionable diagnostics.
  - Validation:
    - Prefer focused module tests first; add an integration sample if the module
      test classpath cannot represent the matrix.

### 7. Final Cleanup

- `[ ]` Run a final scan for forbidden public Jackson core/databind exposure.
  - Command shape:
    - `rg "com\\.fasterxml\\.jackson\\.(core|databind)|tools\\.jackson|JsonNode|TypeReference|JavaType" api client common maintainer-client plugin -g '*.java'`
  - Expected result:
    - Public new SDK APIs do not expose Jackson core/databind types.
    - Remaining Jackson 2 exposure is limited to legacy `JacksonUtils` and
      deprecated compatibility methods.

- `[ ]` Update specs if implementation discovers a mismatch.
  - Files:
    - `specs/en/sdk/sdk-java-json-adapter-spec.md`
    - `specs/zh-cn/sdk/sdk-java-json-adapter-spec.md`

- `[ ]` Remove this temporary tracker once all migration PRs are complete.
  - File:
    - `docs/jackson-json-adapter-migration-todo.md`

## Suggested PR Split

1. Neutral JSON API and selector in `nacos-api`. Done in the first
   implementation PR.
2. Jackson 2 adapter in `nacos-common` and `JacksonUtils` compatibility
   alignment.
3. Jackson 3 adapter and dependency management.
4. `api` cleanup: `HealthCheckerFactory`, `ConfigBasicInfo`, and removing
   Jackson core/databind main dependencies.
5. Runtime migration: `GrpcUtils`, `AbstractNacosRestTemplate`,
   `PreInitUtils`, subtype preload paths, and MCP canonical JSON.
6. Client and maintainer-client `TypeReference` migration.
7. Pipeline DTO exposure and typed maintainer-client APIs.
8. Dependency matrix tests and final forbidden-exposure scan.

## Issue Comment Draft

经过 spec 合入和当前代码检查，建议后续按小的原子 PR 推进 Jackson 2 / Jackson 3
适配，避免一次性大改影响现有 client 用户：

- [ ] 在 `nacos-api` 增加中立 JSON API：`JsonUtils`、
  `NacosJsonAdapter`、`NacosTypeReference<T>` 和 subtype 注册模型。
- [ ] 在 `nacos-common` 增加默认 adapter：Jackson 2 adapter 保持 compile
  兼容；Jackson 3 adapter 使用 provided/optional 风格依赖并延迟初始化，确保 Java 8
  运行时安全。
- [ ] 保留 `JacksonUtils` 作为 Jackson 2 兼容门面，新代码迁移到 `JsonUtils`。
- [ ] 清理 `nacos-api` 对 Jackson core/databind 的主依赖：
  `HealthCheckerFactory` 改用中立 JSON；`ConfigBasicInfo` 避免 databind
  serializer 注解。
- [ ] 替换 client/common 运行时使用点：
  `TypeReference -> NacosTypeReference`、`JavaType` 内部化、
  `ByteBufferBackedInputStream` 改为 Nacos 自有实现，MCP cache 使用中立
  canonical JSON。
- [ ] Pipeline maintainer API 后续改为类型化返回：
  将 `PipelineExecution` / `PipelineExecutionResult` /
  `PipelineExecutionStatus` / `PipelineNodeResult` 及其 `Checkpoint` 依赖下沉到
  `api` 或提供 API DTO，新增 typed 方法，旧 `JsonNode` 方法保留 deprecated 兼容入口。
- [ ] 补充验证矩阵：仅 Jackson 2、仅 Jackson 3、Jackson 2+3 共存 auto 优先
  Jackson 3、显式选择 Jackson 2/3、缺失 adapter 诊断、subtype 注册、
  `Result<Page<T>>` / `List<T>` / `Map<String,Object>` 泛型反序列化，以及最小
  Spring Boot 4 + nacos-client 样例。

整体目标是让现有 Jackson 2 用户无感，同时 Spring Boot 4 / Jackson 3 用户在满足运行
环境时自动切到 Jackson 3，并逐步清理 Nacos public API 对 Jackson 2 core/databind
类型的绑定。
