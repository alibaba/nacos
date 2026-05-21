# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Cursor, GitHub Copilot, etc.) when working with the Nacos repository. For human contributors, see [CONTRIBUTING.md](./CONTRIBUTING.md).

## AI Contribution Guidelines

- **Do NOT post AI-generated comments** on issues or PRs. Discussions are for humans only.
- **Discuss before implementing**: Ensure the implementation direction is agreed upon with maintainers in the issue comments before starting work.
- **Spec-first coding is mandatory**: Before changing behavior, APIs, SDKs,
  plugins, storage, runtime flow, or domain semantics, AI agents MUST read the
  relevant specs under [`specs/`](./specs/README.md) and treat them as the rule
  source for the implementation.
- **Discuss spec-impacting changes before coding**: If a change touches behavior
  covered by an existing spec, or exposes a gap between the code and the spec,
  AI agents MUST discuss the implementation direction and the required spec
  update with maintainers before starting the code change.
- **Update specs with the design**: Any design proposal that changes or
  clarifies spec-covered behavior MUST include the corresponding spec updates
  in the same change set. When the design is large or controversial, prefer a
  spec/design-only PR first, then follow with implementation PRs.
- **Disclose AI usage**: When a significant part of a commit is AI-generated, add a trailer to your commit message:
  ```
  Assisted-by: Claude Code
  ```
- **Follow [CONTRIBUTING.md](./CONTRIBUTING.md)** for all contribution processes.

## Repository Overview

Nacos (Dynamic Naming and Configuration Service) is an easy-to-use platform designed for dynamic service discovery, configuration management, and AI agent management. It helps you build cloud-native applications and AI Agent applications easily. Key capabilities: service discovery, dynamic configuration, dynamic DNS service, service/metadata management, and AI registry (Prompt, MCP, A2A).

**Current Version**: 3.2.1-SNAPSHOT | **Main Branch**: `develop` | **Java**: JDK 17+ (client modules: JDK 8+) | **Build**: Maven 3.2.5+

## Core Architecture

Key modules and their roles:

- **api / client / client-basic**: Client-facing APIs, gRPC definitions, SDK (Java 8 compatible)
- **common**: Shared utilities, HTTP client, notify center, executor
- **config**: Configuration management server
- **naming**: Service discovery and registration server
- **core**: Core server infrastructure (cluster, distributed consensus)
- **consistency**: JRaft-based CP protocol + custom Distro AP protocol
- **auth**: Authentication and authorization
- **plugin / plugin-default-impl**: Extensible plugin system (Java SPI). Types: auth, visibility, datasource dialect, config change, encryption, trace, environment, control, AI pipeline, AI storage
- **console / console-ui**: Web UI backend (Spring Boot) and frontend (React)
- **ai / copilot / ai-registry-adaptor**: AI Agent support, Copilot integration, and AI registry adaptor
- **sys**: System environment utilities and JVM parameter management
- **bootstrap / server**: Server startup and aggregation
- **persistence**: Data persistence with multi-database support (Derby, MySQL, PostgreSQL)
- **maintainer-client**: Internal maintenance client
- **lock**: Distributed lock support

Communication: **gRPC** (primary) + **HTTP/REST** (legacy compatibility). Protobuf definitions in `api/src/main/proto/`.

## Build & Test Commands

```bash
# Full build (skip tests)
mvn '-Prelease-nacos,!dev' -Dmaven.test.skip=true clean install -U

# Run all unit tests
mvn test

# Run config / naming integration tests
mvn test -Pcit-test
mvn test -Pnit-test

# Format code (run before commit)
mvn spotless:apply

# Pre-submission checks (MUST pass before PR)
mvn -B clean compile apache-rat:check checkstyle:check spotbugs:check spotless:check -DskipTests
```

### Mandatory Formatting Before Commit

Before committing Java code or tests, AI agents MUST run Spotless for the
affected module or nearest aggregator:

1. Run `mvn spotless:apply` first.
2. Run `mvn spotless:check` for the same scope.
3. Then run the relevant compile/check/test command.
4. Commit only after Spotless and the relevant validation pass.

Do not rely on `checkstyle:check`, `spotbugs:check`, or `git diff --check` as a
substitute for Spotless. Spotless uses the project formatter and may accept
formatting that generic whitespace checks report differently.

## Code Style

Follows **Alibaba Java Coding Guidelines**.

- Checkstyle config: [`style/NacosCheckStyle.xml`](style/NacosCheckStyle.xml)
- IDEA code style: [`style/nacos-code-style-for-idea.xml`](style/nacos-code-style-for-idea.xml)

### Key Rules for AI Agents

| Rule | Value |
|------|-------|
| Indentation | **4 spaces** (basic offset), 4 spaces (case indent) |
| Line length | **100 characters** max (enforced by Spotless + Checkstyle) |
| Star imports | **Forbidden** — always use explicit imports |
| Unused imports | **Forbidden** |
| Javadoc | Required for API methods (exemptions: `@Override`, `@Test`, `@Before`, `@After`, `@BeforeClass`, `@AfterClass`, `@Parameterized`, `@Parameters`, `@Bean`) |
| Braces | Required for all `if/else/for/while/do-while` blocks, even single-line |
| Switch | Must have `default` case; fall-through must be commented |
| Naming | `camelCase` for methods/variables, `PascalCase` for classes, `UPPER_SNAKE_CASE` for constants |
| Abbreviations | Max 1 consecutive capital letter in names (exception: `VO`) |

### License Header

Every new source file **must** include the Apache License 2.0 header. CI enforces this via `apache-rat:check`.

```java
/*
 * Copyright 1999-${year} Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

## API Standards

Nacos v3 APIs follow strict conventions. AI agents **must** comply with these
standards when generating controller code.

Authoritative API and SDK specs live under [`specs/`](./specs/README.md).
Before coding any API, SDK, plugin, storage, runtime, or domain change, AI
agents **MUST** consult the relevant specs below. If the intended behavior
differs from the existing spec, do not silently implement the code first:
discuss the change with maintainers and update the affected specs as part of
the design. For broad or uncertain changes, submit a spec/design PR first so
the contract is reviewed before implementation.

English:

- Design foundation:
  [Nacos Design Spec](./specs/en/design/nacos-design-spec.md),
  [Resource Model Spec](./specs/en/design/resource-model-spec.md),
  [Compatibility And Deprecation Spec](./specs/en/design/compatibility-deprecation-spec.md),
  [Foundation Capabilities Spec](./specs/en/design/foundation-capabilities-spec.md),
  [Server Lifecycle And Environment Configuration Spec](./specs/en/design/foundation-server-lifecycle-env-spec.md),
  [Cluster Membership Spec](./specs/en/design/foundation-cluster-membership-spec.md),
  [Remote Connection Lifecycle Spec](./specs/en/design/foundation-remote-connection-spec.md),
  [Request Filtering And Runtime Context Spec](./specs/en/design/foundation-request-context-spec.md),
  [Internal RPC And Cluster Request Spec](./specs/en/design/foundation-internal-rpc-spec.md),
  [AP Consistency Spec](./specs/en/design/foundation-ap-consistency-spec.md),
  [CP Consistency Spec](./specs/en/design/foundation-cp-consistency-spec.md),
  [Persistence And Dump Spec](./specs/en/design/foundation-persistence-dump-spec.md),
  [Task Execution Spec](./specs/en/design/foundation-task-execution-spec.md),
  [Event Dispatch And NotifyCenter Spec](./specs/en/design/foundation-event-dispatch-spec.md),
  [Observability Hooks Spec](./specs/en/design/foundation-observability-hooks-spec.md),
  [Core Capabilities Spec](./specs/en/design/core-capabilities-spec.md)
- Interface model:
  [HTTP API Spec](./specs/en/http-api/api-spec.md),
  [Authorization Spec](./specs/en/http-api/authorization-spec.md),
  [Response And Error Spec](./specs/en/http-api/response-error-spec.md),
  [V3 API Surface](./specs/en/http-api/v3-api-surface.md),
  [gRPC API Spec](./specs/en/grpc-api/api-spec.md),
  [SDK Spec](./specs/en/sdk/sdk-spec.md),
  [Java SDK Implementation Spec](./specs/en/sdk/sdk-java-impl-spec.md),
  [Client Runtime Spec](./specs/en/client/client-runtime-spec.md),
  [Client Connection And Failover Spec](./specs/en/client/client-connection-failover-spec.md),
  [Client Ability Negotiation Spec](./specs/en/client/client-ability-negotiation-spec.md),
  [Client Local Cache And Redo Spec](./specs/en/client/client-local-cache-redo-spec.md),
  [Runtime Push And Reconnect Spec](./specs/en/client/runtime-push-reconnect-spec.md)
- Domain model:
  [Config Spec](./specs/en/config/config-spec.md),
  [Config Resource Spec](./specs/en/config/config-resource-spec.md),
  [Config Publish And Query Spec](./specs/en/config/config-publish-query-spec.md),
  [Config Listener And Watch Spec](./specs/en/config/config-listener-watch-spec.md),
  [Config Gray Release Spec](./specs/en/config/config-gray-release-spec.md),
  [Config Persistence And History Spec](./specs/en/config/config-persistence-history-spec.md),
  [Config Consistency, Dump, And Visibility Spec](./specs/en/config/config-consistency-dump-visibility-spec.md),
  [Config Capacity And Ops Spec](./specs/en/config/config-capacity-ops-spec.md),
  [Naming Spec](./specs/en/naming/naming-spec.md),
  [Naming Resource Spec](./specs/en/naming/naming-resource-spec.md),
  [Naming Instance Lifecycle Spec](./specs/en/naming/naming-instance-lifecycle-spec.md),
  [Naming Discovery And Subscription Spec](./specs/en/naming/naming-discovery-subscription-spec.md),
  [Naming Health And Protection Spec](./specs/en/naming/naming-health-protection-spec.md),
  [Naming Metadata And Selector Spec](./specs/en/naming/naming-metadata-selector-spec.md),
  [Naming Consistency And Client State Spec](./specs/en/naming/naming-consistency-client-spec.md),
  [Naming Ephemeral Distro Consistency Spec](./specs/en/naming/naming-ephemeral-distro-consistency-spec.md),
  [Naming Persistent CP Consistency Spec](./specs/en/naming/naming-persistent-cp-consistency-spec.md),
  [Naming Ops Spec](./specs/en/naming/naming-ops-spec.md),
  [AI Registry Spec](./specs/en/ai/ai-registry-spec.md),
  [AI Resource Model Spec](./specs/en/ai/ai-resource-model-spec.md),
  [AI Resource Lifecycle Spec](./specs/en/ai/ai-resource-lifecycle-spec.md),
  [AI Registry Adaptor Spec](./specs/en/ai/ai-registry-adaptor-spec.md),
  [MCP Server Spec](./specs/en/ai/mcp-server-spec.md),
  [A2A Agent Spec](./specs/en/ai/a2a-agent-spec.md),
  [Prompt Spec](./specs/en/ai/prompt-spec.md),
  [Skill Spec](./specs/en/ai/skill-spec.md),
  [AgentSpec Spec](./specs/en/ai/agentspec-spec.md),
  [Core Operations Spec](./specs/en/core/core-operations-spec.md),
  [Console Spec](./specs/en/console/console-spec.md),
  [Distributed Lock Spec](./specs/en/lock/lock-spec.md)
- Extension model:
  [Integration And Adapter Spec](./specs/en/integration/integration-adapter-spec.md),
  [Plugin Specs](./specs/en/plugin/README.md)
- Security model:
  [Auth And Permission Spec](./specs/en/auth/auth-permission-spec.md),
  [Auth Plugin Spec](./specs/en/auth/auth-plugin-spec.md),
  [RAM Auth Plugin Spec](./specs/en/auth/ram-auth-plugin-spec.md),
  [OIDC Auth Plugin Spec](./specs/en/auth/oidc-auth-plugin-spec.md),
  [Visibility Plugin Spec](./specs/en/auth/visibility-plugin-spec.md),
  [Default Auth Plugin Implementation Spec](./specs/en/auth/default-auth-plugin-spec.md)

Simplified Chinese:

- 设计基础：
  [Nacos 设计规范](./specs/zh-cn/design/nacos-design-spec.md)，
  [资源模型规范](./specs/zh-cn/design/resource-model-spec.md)，
  [兼容与废弃策略规范](./specs/zh-cn/design/compatibility-deprecation-spec.md)，
  [基础能力规范](./specs/zh-cn/design/foundation-capabilities-spec.md)，
  [服务端生命周期与环境配置规范](./specs/zh-cn/design/foundation-server-lifecycle-env-spec.md)，
  [集群成员规范](./specs/zh-cn/design/foundation-cluster-membership-spec.md)，
  [远程连接生命周期规范](./specs/zh-cn/design/foundation-remote-connection-spec.md)，
  [请求过滤与运行时上下文规范](./specs/zh-cn/design/foundation-request-context-spec.md)，
  [内部 RPC 与集群请求规范](./specs/zh-cn/design/foundation-internal-rpc-spec.md)，
  [AP 一致性规范](./specs/zh-cn/design/foundation-ap-consistency-spec.md)，
  [CP 一致性规范](./specs/zh-cn/design/foundation-cp-consistency-spec.md)，
  [持久化与 Dump 规范](./specs/zh-cn/design/foundation-persistence-dump-spec.md)，
  [任务执行规范](./specs/zh-cn/design/foundation-task-execution-spec.md)，
  [事件分发与 NotifyCenter 规范](./specs/zh-cn/design/foundation-event-dispatch-spec.md)，
  [可观测钩子规范](./specs/zh-cn/design/foundation-observability-hooks-spec.md)，
  [核心功能规范](./specs/zh-cn/design/core-capabilities-spec.md)
- 接口模型：
  [HTTP API 规范](./specs/zh-cn/http-api/api-spec.md)，
  [鉴权规范](./specs/zh-cn/http-api/authorization-spec.md)，
  [响应与错误规范](./specs/zh-cn/http-api/response-error-spec.md)，
  [V3 API 范围](./specs/zh-cn/http-api/v3-api-surface.md)，
  [gRPC API 规范](./specs/zh-cn/grpc-api/api-spec.md)，
  [SDK 规范](./specs/zh-cn/sdk/sdk-spec.md)，
  [Java SDK 实现规范](./specs/zh-cn/sdk/sdk-java-impl-spec.md)，
  [客户端运行时规范](./specs/zh-cn/client/client-runtime-spec.md)，
  [客户端连接与故障切换规范](./specs/zh-cn/client/client-connection-failover-spec.md)，
  [客户端能力协商规范](./specs/zh-cn/client/client-ability-negotiation-spec.md)，
  [客户端本地缓存与 Redo 规范](./specs/zh-cn/client/client-local-cache-redo-spec.md)，
  [运行时推送与重连规范](./specs/zh-cn/client/runtime-push-reconnect-spec.md)
- 领域模型：
  [Config 规范](./specs/zh-cn/config/config-spec.md)，
  [Config 资源规范](./specs/zh-cn/config/config-resource-spec.md)，
  [Config 发布与查询规范](./specs/zh-cn/config/config-publish-query-spec.md)，
  [Config 监听与 Watch 规范](./specs/zh-cn/config/config-listener-watch-spec.md)，
  [Config 灰度发布规范](./specs/zh-cn/config/config-gray-release-spec.md)，
  [Config 持久化与历史规范](./specs/zh-cn/config/config-persistence-history-spec.md)，
  [Config 一致性、Dump 与可见性规范](./specs/zh-cn/config/config-consistency-dump-visibility-spec.md)，
  [Config 容量与运维规范](./specs/zh-cn/config/config-capacity-ops-spec.md)，
  [Naming 规范](./specs/zh-cn/naming/naming-spec.md)，
  [Naming 资源规范](./specs/zh-cn/naming/naming-resource-spec.md)，
  [Naming 实例生命周期规范](./specs/zh-cn/naming/naming-instance-lifecycle-spec.md)，
  [Naming 发现与订阅规范](./specs/zh-cn/naming/naming-discovery-subscription-spec.md)，
  [Naming 健康检查与保护规范](./specs/zh-cn/naming/naming-health-protection-spec.md)，
  [Naming 元数据与 Selector 规范](./specs/zh-cn/naming/naming-metadata-selector-spec.md)，
  [Naming 一致性与客户端状态规范](./specs/zh-cn/naming/naming-consistency-client-spec.md)，
  [Naming 临时服务 Distro 一致性规范](./specs/zh-cn/naming/naming-ephemeral-distro-consistency-spec.md)，
  [Naming 持久服务 CP 一致性规范](./specs/zh-cn/naming/naming-persistent-cp-consistency-spec.md)，
  [Naming 运维规范](./specs/zh-cn/naming/naming-ops-spec.md)，
  [AI Registry 规范](./specs/zh-cn/ai/ai-registry-spec.md)，
  [AI 资源模型规范](./specs/zh-cn/ai/ai-resource-model-spec.md)，
  [AI 资源生命周期规范](./specs/zh-cn/ai/ai-resource-lifecycle-spec.md)，
  [AI Registry 适配器规范](./specs/zh-cn/ai/ai-registry-adaptor-spec.md)，
  [MCP Server 规范](./specs/zh-cn/ai/mcp-server-spec.md)，
  [A2A Agent 规范](./specs/zh-cn/ai/a2a-agent-spec.md)，
  [Prompt 规范](./specs/zh-cn/ai/prompt-spec.md)，
  [Skill 规范](./specs/zh-cn/ai/skill-spec.md)，
  [AgentSpec 规范](./specs/zh-cn/ai/agentspec-spec.md)，
  [Core 运维规范](./specs/zh-cn/core/core-operations-spec.md)，
  [Console 规范](./specs/zh-cn/console/console-spec.md)，
  [分布式锁规范](./specs/zh-cn/lock/lock-spec.md)
- 扩展模型：
  [集成与适配器规范](./specs/zh-cn/integration/integration-adapter-spec.md)，
  [插件规范](./specs/zh-cn/plugin/README.md)
- 安全模型：
  [鉴权与权限规范](./specs/zh-cn/auth/auth-permission-spec.md)，
  [鉴权插件规范](./specs/zh-cn/auth/auth-plugin-spec.md)，
  [RAM 鉴权插件规范](./specs/zh-cn/auth/ram-auth-plugin-spec.md)，
  [OIDC 鉴权插件规范](./specs/zh-cn/auth/oidc-auth-plugin-spec.md)，
  [可见性插件规范](./specs/zh-cn/auth/visibility-plugin-spec.md)，
  [默认鉴权插件实现规范](./specs/zh-cn/auth/default-auth-plugin-spec.md)

This section is a quick implementation checklist for agents. If it conflicts
with the specs, follow the specs and update this checklist.

### URL Path Patterns

| API Type | Base Path | Purpose | Example |
|----------|-----------|---------|---------|
| **Open API** | `/v3/client/{module}/...` | Client-facing operations | `/v3/client/ns/instance` |
| **Admin API** | `/v3/admin/{module}/...` | Administrative operations | `/v3/admin/ns/service` |
| **Console API** | `/v3/console/{module}/...` | Web console operations | `/v3/console/cs/config` |
| **Auth API** | `/v3/auth/{resource}/...` | Plugin-provided auth operations | `/v3/auth/user` |

### Module Names

| Module | Abbreviation | Scope |
|--------|-------------|-------|
| Config Service | `cs` | Configuration management |
| Naming Service | `ns` | Service discovery |
| Core | `core` | Cluster, namespace management |
| AI | `ai` | AI resource management |
| Plugin | `plugin` | Plugin management |

> **Note**: Auth APIs (`/v3/auth/user`, `/v3/auth/role`, `/v3/auth/permission`) are defined in `plugin-default-impl` module, not in core.

### HTTP Method Semantics

| Method | Usage | Idempotent |
|--------|-------|:----------:|
| `GET` | Query / Retrieve | Yes |
| `POST` | Create / Register | No |
| `PUT` | Update / Modify | Yes |
| `DELETE` | Remove / Deregister | Yes |

### Response Format

**Always** wrap responses in `com.alibaba.nacos.api.model.v2.Result<T>`:

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

### Authentication

**Always** add `@Secured` annotation (`com.alibaba.nacos.auth.annotation.Secured`):

```java
@Secured(action = ActionTypes.READ,       // READ or WRITE
         signType = SignType.CONFIG,       // CONFIG, NAMING, or CONSOLE
         apiType = ApiType.ADMIN_API)      // OPEN_API, ADMIN_API, or CONSOLE_API
```

### Controller Example

```java
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.api.common.ApiType;

@RestController
@RequestMapping("/v3/admin/ns/service")
public class ServiceControllerV3 {

    @PostMapping
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> create(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        // business logic ...
        return Result.success("ok");
    }

    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<Page<ServiceDetailInfo>> list(ServiceListForm serviceListForm) throws NacosException {
        serviceListForm.validate();
        // business logic ...
        return Result.success(result);
    }
}
```

## Java Version Targeting

- **Server modules** (config, naming, core, console, etc.): Java 17+
- **Client/API/Plugin modules** (api, client, plugin): Java 8+ — ensure backwards compatibility when modifying

## PR Convention

All PRs must target the `develop` branch. Follow the [PR template](.github/PULL_REQUEST_TEMPLATE.md).

**Title format**: `[ISSUE #14122] Add JVM --add-opens options for JDK 17+ compatibility`

**Pre-submission checklist**:
```bash
mvn -B clean package apache-rat:check spotbugs:check -DskipTests
mvn clean install
mvn clean test-compile failsafe:integration-test
```

## Security Vulnerabilities

Do NOT report security vulnerabilities via GitHub Issues. Use [ASRC (Alibaba Security Response Center)](https://security.alibaba.com) instead.
