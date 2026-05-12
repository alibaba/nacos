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

# Nacos Java SDK Implementation Spec

This document defines how the Java SDK implements the shared
[SDK Spec](./sdk-spec.md). It covers both the Java Client SDK and the Java
Maintainer SDK.

## 1. Scope

The Java SDK has two public families:

- Java Client SDK, provided mainly by `nacos-client` and the public interfaces
  under the `api` module.
- Java Maintainer SDK, provided by `nacos-maintainer-client` and the public
  interfaces under the `maintainer-client` module.

The Java Client SDK is the baseline for existing runtime application behavior.
The Java Maintainer SDK is the preferred Java entry point for management, UI,
gateway, and operation scenarios.

## 2. Java Client SDK Factories and Lifecycle

| Interface | Factory | Shutdown method |
| --- | --- | --- |
| `ConfigService` | `NacosFactory.createConfigService(...)` | `shutDown()` |
| `NamingService` | `NacosFactory.createNamingService(...)` | `shutDown()` |
| `AiService` | `AiFactory.createAiService(Properties)` | `shutdown()` |
| `LockService` | `NacosLockFactory.createLockService(Properties)` or `NacosFactory.createLockService(Properties)` | `shutdown()` |
| `NamingMaintainService` | `NacosFactory.createMaintainService(...)` | `shutDown()` |

`NamingMaintainService` is deprecated after 3.3.0. New management integrations
should use `nacos-maintainer-client`.

One Java SDK instance is bound to one namespace. Applications that need multiple
namespaces should create separate SDK instances and close them when no longer
used.

## 3. Java Client SDK Configuration

Java Client SDK configuration is represented by `NacosClientProperties`.

The default lookup order is:

```text
Properties -> JVM system properties -> environment variables -> defaults
```

The first lookup source can be changed by `nacos.env.first` or
`NACOS_ENV_FIRST`.

Common properties include:

| Property | Scope | Meaning |
| --- | --- | --- |
| `serverAddr` | common | Nacos server address list. |
| `contextPath` | common | Server context path, defaulting to `nacos`. |
| `endpoint` and endpoint-related properties | common | Dynamic server address endpoint. |
| `namespace` | common | Namespace id bound to the SDK instance. |
| `username`, `password` | common | Login credentials when authentication is enabled. |
| `accessKey`, `secretKey`, `ramRoleName`, `signatureRegionId` | common | RAM-style authentication properties. |
| `configRequestTimeout` | config | Config RPC request timeout override. |
| `namingRequestTimeout` | naming | Naming RPC request timeout override. |
| `nacos.server.grpc.port.offset` | connection | gRPC port offset used by the Java client. |

Deprecated historical properties should remain compatible, but new behavior
should not depend on them.

## 4. Java Client SDK Interfaces

### 4.1 ConfigService

| Capability | Methods | Contract |
| --- | --- | --- |
| Query config | `getConfig`, `getConfigWithResult` | Query one known config by `dataId` and `group`; `getConfigWithResult` also returns md5 for CAS. |
| Query and listen | `getConfigAndSignListener` | Query current config and register the same listener for later changes. |
| Listen | `addListener`, `removeListener` | Add or remove a listener. Callback should prefer the executor supplied by the listener. |
| Publish | `publishConfig`, `publishConfigCas` | Compatibility write surface for creating or updating config. CAS publish must compare the previous md5. |
| Delete | `removeConfig` | Compatibility write surface for deleting config. Existing user docs define deleting a missing config as success. |
| Filter | `addConfigFilter` | Add a client-side config filter. |
| Fuzzy watch | `fuzzyWatch`, `fuzzyWatchWithGroupKeys`, `cancelFuzzyWatch` | Watch config keys by group or dataId pattern and receive key change events. |
| Status and lifecycle | `getServerStatus`, `shutDown` | Query status and release resources. |

Config identity follows the user-facing constraints for `dataId`, `group`, and
content size. New broad config management APIs should be added to the Maintainer
SDK instead of `ConfigService`.

### 4.2 NamingService

| Capability | Methods | Contract |
| --- | --- | --- |
| Register | `registerInstance`, `batchRegisterInstance` | Register one or more instances under a service and group. |
| Deregister | `deregisterInstance`, `batchDeregisterInstance` | Remove one or more instances. |
| Query instances | `getAllInstances`, `selectInstances`, `selectOneHealthyInstance` | Query cached or remote service information by cluster, health, and subscribe options. |
| Subscribe | `subscribe`, `unsubscribe` | Receive service instance change events. Unsubscribe requires the same listener instance. |
| Fuzzy watch | `fuzzyWatch`, `fuzzyWatchWithServiceKeys`, `cancelFuzzyWatch` | Watch service keys by group or service pattern and receive service-level events. |
| List services | `getServicesOfServer` | Compatibility broad query surface. New broad listing should use the Maintainer SDK. |
| Local status | `getSubscribeServices`, `getServerStatus`, `shutDown` | Query subscribed services, status, and release resources. |

The selector overload of `getServicesOfServer` is deprecated and remains only as
a compatibility surface.

### 4.3 AiService and A2aService

`AiService` extends `A2aService`.

| Capability | Methods | Contract |
| --- | --- | --- |
| MCP query | `getMcpServer` | Query MCP Server details by name and optional version. |
| MCP release | `releaseMcpServer` | Create an MCP Server or release a new version. Existing same-version data remains idempotent. |
| MCP endpoint | `registerMcpServerEndpoint`, `deregisterMcpServerEndpoint` | Register or remove endpoints owned by the current client. |
| MCP subscription | `subscribeMcpServer`, `unsubscribeMcpServer` | Subscribe to MCP detail changes. |
| A2A AgentCard query | `getAgentCard` | Query an AgentCard by name, optional version, and registration type. |
| A2A AgentCard release | `releaseAgentCard` | Create an AgentCard or release a new version; `setAsLatest` only affects the new version. |
| A2A endpoint | `registerAgentEndpoint`, `deregisterAgentEndpoint` | Register or remove endpoints owned by the current client. Batch registration replaces endpoints previously registered by this client for the same agent. |
| A2A subscription | `subscribeAgentCard`, `unsubscribeAgentCard` | Subscribe to AgentCard changes. |
| Skill | `downloadSkillZip`, `downloadSkillZipByVersion`, `downloadSkillZipByLabel` | Download Skill zip bytes by latest, version, or label. |
| AgentSpec | `loadAgentSpec`, `subscribeAgentSpec`, `unsubscribeAgentSpec` | Load assembled AgentSpec and subscribe to changes. |
| Prompt | `getPrompt`, `getPromptByVersion`, `getPromptByLabel`, `subscribePrompt`, `unsubscribePrompt` | Query and subscribe to Prompt resources by key, version, or label. |

The Java implementation may mix gRPC, HTTP, and config assembly behind the
interface. The public interface contract should stay independent from transport
details.

### 4.4 LockService

| Capability | Methods | Contract |
| --- | --- | --- |
| User lock | `lock` | Acquire a lock through `LockInstance#lock`. |
| User unlock | `unLock` | Release a lock through `LockInstance#unLock`. |
| Remote lock | `remoteTryLock` | Send a gRPC lock operation request. |
| Remote unlock | `remoteReleaseLock` | Send a gRPC unlock operation request. |
| Lifecycle | `shutdown` | Release client resources. |

## 5. Java Maintainer SDK Factories and Lifecycle

| Interface | Factory | Shutdown method |
| --- | --- | --- |
| `ConfigMaintainerService` | `NacosMaintainerFactory.createConfigMaintainerService(...)` or `ConfigMaintainerFactory.createConfigMaintainerService(...)` | `close()` |
| `NamingMaintainerService` | `NamingMaintainerFactory.createNamingMaintainerService(...)` | `close()` |
| `AiMaintainerService` | `AiMaintainerFactory.createAiMaintainerService(...)` | Not exposed by the current interface |

Maintainer services inherit `CoreMaintainerService` where applicable. They are
higher-privilege clients and should be configured with management credentials.

## 6. Java Maintainer SDK Interfaces

### 6.1 CoreMaintainerService

`CoreMaintainerService` exposes server and cluster maintenance capabilities:

- server state, liveness, readiness, id-generator status, and loader metrics;
- log-level updates;
- cluster node listing and lookup mode updates;
- current client connection inspection and client reload operations;
- namespace listing, query, create, update, delete, and existence check;
- raft operation forwarding for administrative scenarios.

These APIs are administrative by definition and must not be copied into the
Client SDK.

### 6.2 ConfigMaintainerService

`ConfigMaintainerService` includes:

- get, publish, delete, and batch delete config;
- list and search configs with namespace, dataId, group, type, tag, and app
  filters where supported;
- clone and import/export style management models;
- beta and gray release operations through `BetaConfigMaintainerService`;
- history query and rollback-related access through
  `ConfigHistoryMaintainerService`;
- dump, listener, log, and operation endpoints through
  `ConfigOpsMaintainerService`;
- metadata update for configuration descriptions and tags.

Management writes and broad queries should be added here instead of expanding
`ConfigService`.

### 6.3 NamingMaintainerService

`NamingMaintainerService` includes:

- service create, update, remove, detail query, and list operations;
- instance register, deregister, update, list, and metadata maintenance;
- subscriber and client query operations through `NamingClientMaintainerService`;
- naming metrics and log-level operations;
- persistent instance health-status updates;
- health checker listing and cluster metadata updates.

Runtime instance registration remains available in `NamingService`, but service
administration, broad listing, subscriber inspection, and health-check
maintenance belong to the Maintainer SDK.

### 6.4 AiMaintainerService

`AiMaintainerService` exposes typed delegates:

- `mcp()` for MCP Server list, search, detail, create, update, and delete;
- `a2a()` for AgentCard register, query, update, delete, version, search, and
  list operations;
- `prompt()` for Prompt management;
- `skill()` for Skill management;
- `agentSpec()` for AgentSpec management;
- `pipeline()` for Pipeline management.

Runtime AI registration and subscription can remain in `AiService`; broad AI
resource management belongs to `AiMaintainerService`.

## 7. Java Compatibility Rules

- `api`, `client`, and `plugin` modules remain Java 8 compatible unless the
  module policy changes.
- Server-side and maintainer modules follow the repository Java version policy.
- Deprecated Client SDK methods should keep binary compatibility when possible,
  but new designs should point callers to the Maintainer SDK.
- Public model changes should preserve source and binary compatibility where
  practical, especially for objects shared with HTTP and gRPC APIs.

## 8. Documentation References

- Java Client SDK user docs:
  `src/content/docs/next/zh-cn/manual/user/java-sdk` in the Nacos docs project.
- Java Maintainer SDK user docs:
  `src/content/docs/next/zh-cn/manual/admin/maintainer-sdk.md` in the Nacos docs
  project.
