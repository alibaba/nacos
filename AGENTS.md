# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Cursor, GitHub Copilot, etc.) when working with the Nacos repository. For human contributors, see [CONTRIBUTING.md](./CONTRIBUTING.md).

## AI Contribution Guidelines

- **Do NOT post AI-generated comments** on issues or PRs. Discussions are for humans only.
- **Discuss before implementing**: Ensure the implementation direction is agreed upon with maintainers in the issue comments before starting work.
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
- **plugin / plugin-default-impl**: Extensible plugin system (Java SPI). Types: auth, encryption, datasource, control, trace, config, environment
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

# Pre-submission checks (MUST pass before PR)
mvn -B clean compile apache-rat:check checkstyle:check spotbugs:check -DskipTests
```

## Code Style

Follows **Alibaba Java Coding Guidelines**.

- Checkstyle config: [`style/NacosCheckStyle.xml`](style/NacosCheckStyle.xml)
- IDEA code style: [`style/nacos-code-style-for-idea.xml`](style/nacos-code-style-for-idea.xml)

### Key Rules for AI Agents

| Rule | Value |
|------|-------|
| Indentation | **4 spaces** (basic offset), 4 spaces (case indent) |
| Line length | **150 characters** max |
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

Nacos v3 APIs follow strict conventions. AI agents **must** comply with these standards when generating controller code.

### URL Path Patterns

| API Type | Base Path | Purpose | Example |
|----------|-----------|---------|---------|
| **Open API** | `/v3/client/{module}/...` | Client-facing operations | `/v3/client/ns/instance` |
| **Admin API** | `/v3/admin/{module}/...` | Administrative operations | `/v3/admin/ns/service` |
| **Console API** | `/v3/console/{module}/...` | Web console operations | `/v3/console/cs/config` |

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
mvn clean install -DskipITs
mvn clean test-compile failsafe:integration-test
```

## Security Vulnerabilities

Do NOT report security vulnerabilities via GitHub Issues. Use [ASRC (Alibaba Security Response Center)](https://security.alibaba.com) instead.
