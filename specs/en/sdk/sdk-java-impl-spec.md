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

JSON serialization compatibility for the Java SDK is defined by the
[Java SDK JSON Adapter Spec](./sdk-java-json-adapter-spec.md).

## 1. Scope

The Java SDK has two public families:

- Java Client SDK, provided mainly by `nacos-client` and the public interfaces
  under the `api` module.
- Java Maintainer SDK, provided by `nacos-maintainer-client` and the public
  interfaces under the `maintainer-client` module.

The Java Client SDK is the baseline for existing runtime application behavior.
Its connection, server list, ability negotiation, local cache, and redo behavior
is defined by the [Client Runtime Specs](../client/README.md). The Java
Maintainer SDK is the preferred Java entry point for management, UI, gateway,
and operation scenarios.

Java SDK behavior must be verified with scenario-oriented integration tests
according to the
[Java SDK Integration Test Spec](../testing/java-sdk-integration-test-spec.md)
whenever public SDK interfaces, factories, models, listener behavior,
lifecycle behavior, or exception mapping change.

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

One Java Client SDK instance is bound to one namespace. Applications that need
multiple namespaces should create separate Client SDK instances and close them
when no longer used. Public runtime interfaces do not expose a namespace
argument; their implementations use the namespace bound at construction.
This rule does not apply to the Maintainer SDK: its Agent management interface
is not namespace-bound, accepts an explicit namespace, and provides
default-namespace overloads that use `public`. Agent management Request and
Command objects do not contain a namespace; the explicit method argument is
the sole custom-namespace source.

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

## 4. Java Client SDK Extension Points

Java Client SDK extension points run inside the application process. They are
loaded from the client classpath or registered through SDK APIs, and they are
closed with the owning SDK instance. They are not controlled by the server-side
plugin Admin API.

| Extension point | SPI or API | Contract |
| --- | --- | --- |
| Addressing | `ServerListProvider` | Select and refresh the server list used by HTTP and gRPC clients. Built-ins support fixed `serverAddr` and dynamic `endpoint` modes. |
| Authentication | `AbstractClientAuthService` / `ClientAuthService` | Produce request identity material such as access tokens, RAM signatures, or OIDC bearer tokens for a `RequestResource`. |
| Config filter | `IConfigFilter` and `ConfigService#addConfigFilter` | Intercept config publish requests and query responses in a stable order. |
| Config encryption | `ConfigEncryptionFilter` plus `EncryptionPluginService` | Encrypt `cipher-{algorithm}-` config before publish and decrypt matching config after query when the algorithm plugin is present. |

Client extensions must not redefine Nacos resource identity or broaden the
Client SDK capability surface. If an extension needs management access, it
should use the Maintainer SDK or Admin API rather than adding high-privilege
operations to the runtime client.

Addressing extensions must return addresses parseable by the Java HTTP and
gRPC clients and publish server-list change events when dynamic discovery
changes. Auth extensions must use `RequestResource` rather than parsing
transport payloads for resource-aware signing. Config filters must preserve
request and response field semantics and should fail explicitly when a required
cryptographic plugin is missing.

### 4.1 Built-in Client Auth Services

The Java client currently registers these `AbstractClientAuthService`
implementations through SPI:

| Implementation | Identity material | Contract |
| --- | --- | --- |
| `NacosClientAuthServiceImpl` | `username`, `password`, and `accessToken`. | Integrate with the default Nacos auth plugin login API and refresh the returned token before expiration. |
| `RamClientAuthServiceImpl` | `accessKey`, `secretKey`, `ramRoleName`, `signatureRegionId`. | Produce resource-aware RAM-style signatures as defined by the [RAM Auth Plugin Spec](../auth/ram-auth-plugin-spec.md). |
| `OidcClientAuthServiceImpl` | OIDC client credentials and bearer token. | Use the OAuth2 client credentials flow as defined by the [OIDC Auth Plugin Spec](../auth/oidc-auth-plugin-spec.md). |

The Java client combines identity output from all loaded client auth services.
An implementation that is not configured should return an empty identity
context instead of mutating request payloads or failing unrelated SDK calls. The
default Nacos auth plugin only owns the Nacos username/password and token flow;
[RAM](../auth/ram-auth-plugin-spec.md) and
[OIDC](../auth/oidc-auth-plugin-spec.md) are client-side auth extensions and
become effective only when the selected server-side auth plugin or
deployment-side identity verifier accepts their identity material.

## 5. Java Client SDK Interfaces

### 5.1 ConfigService

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

### 5.2 NamingService

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

### 5.3 AiService, AgentDiscoveryService, And A2aService

The Agent/RAD contract in this subsection is a target contract, not an
inventory of currently implemented Java methods. It becomes active only after
the new Agent/RAD abilities are implemented and negotiated. Until then, the
existing `AiService` and `A2aService` methods remain the active compatibility
surface.

The target inheritance is:

```text
AiService extends AgentDiscoveryService, A2aService
```

Adding this parent must not make an already compiled third-party `AiService`
implementation fail linkage immediately. Newly inherited methods use
compatibility default bridges that report unsupported behavior until an
implementation overrides them; the official Nacos implementation overrides the
complete target surface.

`AiService` directly provides the namespace-bound
`publishAgent(AgentPublishRequest)` method and returns `AgentVersionDetail`.
This new method uses the same compatibility default bridge. It does not belong
to `AgentDiscoveryService`, because definition publication is not discovery.
The official implementation copies the request, injects the SDK namespace, and
creates a draft or runs the ordinary submit Pipeline according to
`autoSubmit`, without mutating the caller's object. Equivalent retries,
conflicts, and state convergence follow the
[Agent API Spec](../ai/agent-api-spec.md).

`AgentTransportMode` is a Java 8-compatible API-module enum exposing `GRPC`,
`HTTP`, and `AUTO`; `getValue()` supplies the `nacosAiTransportMode` property
value. The mode is frozen when `AiService` is created, and invalid values fail
factory creation. The [Agent API Spec](../ai/agent-api-spec.md) defines the
transport lifecycle, AUTO probe, and operation fallback rules.

`AgentDiscoveryService` provides these namespace-bound methods:

| Capability | Methods | Contract |
| --- | --- | --- |
| Search | `searchAgents` | Accept `AgentSearchRequest` and return `Page<AgentCatalogEntry>`. |
| Discover | `discoverAgent` overloads | Accept `AgentReference`, with an optional `AgentDiscoveryFilter`, and return one complete `AgentDiscoveryResult`. |
| Watch | `subscribeAgent` overloads | Accept the same reference, optional Filter, and listener; return the current complete result and later deliver complete replacement results. |
| Cancel Watch | `unsubscribeAgent` overloads | Remove the Watch identified by the same reference, Filter, and listener identity. |
| Register Endpoint | `registerAgentEndpoints` | Register one `AgentEndpointRegistrationBatch` and retain it as redo intent. |
| Deregister Endpoint | `deregisterAgentEndpoints` | Deregister one `AgentEndpointDeregistrationBatch` owned by this SDK publisher. |

These public methods do not accept `namespaceId`. The proxy copies the caller's
request or Batch, injects the SDK namespace into the transport object, and does
not mutate the caller's object. If a shared input model already carries a
nonempty namespace different from the SDK namespace, the proxy rejects it
locally. Target Watch, cache, and redo behavior follows the
[Client Local Cache And Redo Spec](../client/client-local-cache-redo-spec.md)
and the
[Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md).

The inherited `A2aService` remains a compatibility facade. New Agent
applications use `AgentDiscoveryService`; existing AgentCard calls continue
through the A2A compatibility adapter.

Legacy A2A Endpoint redo distinguishes desired intent by
`(agentName, exactVersion)` inside a namespace-bound SDK and stores a defensive
snapshot of Endpoint payloads. Legacy AgentCard subscription must correctly
handle exact versions, latest-pointer changes, and resubscription from an
existing cache entry after cancellation. `shutdown()` stops its polling tasks.
Endpoint publication may precede Agent definition creation and never creates a
definition implicitly.

Resource semantics are defined by the [AI Registry Spec](../ai/ai-registry-spec.md),
the [Agent API Spec](../ai/agent-api-spec.md), the
[RAD Protocol Spec](../ai/rad-protocol-spec.md), and the individual AI resource
type specs. The currently implemented compatibility methods include:

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

### 5.4 LockService

`LockService` is an experimental runtime primitive. Its domain semantics are
defined by the [Distributed Lock Spec](../lock/lock-spec.md).

| Capability | Methods | Contract |
| --- | --- | --- |
| User lock | `lock` | Acquire a lock through `LockInstance#lock`. |
| User unlock | `unLock` | Release a lock through `LockInstance#unLock`. |
| Remote lock | `remoteTryLock` | Send a gRPC lock operation request. |
| Remote unlock | `remoteReleaseLock` | Send a gRPC unlock operation request. |
| Lifecycle | `shutdown` | Release client resources. |

## 6. Java Maintainer SDK Factories and Lifecycle

| Interface | Factory | Shutdown method |
| --- | --- | --- |
| `ConfigMaintainerService` | `NacosMaintainerFactory.createConfigMaintainerService(...)` or `ConfigMaintainerFactory.createConfigMaintainerService(...)` | `close()` |
| `NamingMaintainerService` | `NamingMaintainerFactory.createNamingMaintainerService(...)` | `close()` |
| `AiMaintainerService` | `AiMaintainerFactory.createAiMaintainerService(...)` | Not exposed by the current interface |

Maintainer services inherit `CoreMaintainerService` where applicable. They are
higher-privilege clients and should be configured with management credentials.

## 7. Java Maintainer SDK Interfaces

### 7.1 CoreMaintainerService

`CoreMaintainerService` exposes server and cluster maintenance capabilities:

- server state, liveness, readiness, id-generator status, and loader metrics;
- log-level updates;
- cluster node listing and lookup mode updates;
- current client connection inspection and client reload operations;
- namespace listing, query, create, update, delete, and existence check;
- raft operation forwarding for administrative scenarios.

These APIs are administrative by definition and must not be copied into the
Client SDK.

### 7.2 ConfigMaintainerService

`ConfigMaintainerService` includes:

- get, publish, delete, and namespace-scoped batch delete config;
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
Batch delete by storage ID must explicitly carry or default a namespace. A
convenience method without namespace means default-namespace delete, not a
cross-namespace global delete.
Clone by storage ID must explicitly carry or default both source and target
namespaces. The legacy single-namespace clone method means same-namespace clone,
not an ID-only cross-namespace source lookup.
Maintainer SDK methods that expose storage-ID selectors, such as `ids` for
batch delete, are compatibility methods and are pending removal. New maintainer
contracts should select configs by `namespaceId`, `groupName`, and `dataId`, or
by explicit lists of that identity tuple.

### 7.3 NamingMaintainerService

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

### 7.4 AiMaintainerService

`AiMaintainerService` exposes typed delegates:

- `mcp()` for MCP Server compatibility operations and typed Version lifecycle management;
- `a2a()` for AgentCard register, query, update, delete, version, search, and
  list operations;
- `prompt()` for Prompt management;
- `skill()` for Skill management;
- `agentSpec()` for AgentSpec management;
- `pipeline()` for Pipeline management.

The Agent management delegate is `agent()`, which returns
`AgentMaintainerService` and maps one-to-one to the Agent Admin HTTP API. Its
instance is not namespace-bound. Operations provide explicit-namespace forms
and convenience overloads that use the default namespace `public`. Agent
Request and Command objects do not contain `namespaceId`; explicit overloads
take it as a separate method argument. Agent definition creation uses
`createDraft`: the first draft creates missing Agent metadata, while later
drafts reuse that metadata. `a2a()` remains available for its compatibility
window.

The MCP management delegate is `mcp()`, which returns `McpMaintainerService`.
Its historical methods remain binary-compatible. Detail and direct-online
create/update methods are deprecated since 3.3.0 and planned for removal in
4.0.0; their Javadoc points to exact Version reads and the typed
draft-submit-publish flow. Historical cross-resource list/search and
published-Version or full-Resource delete methods remain non-deprecated until
semantics-equivalent typed replacements exist. Typed lifecycle additions map
one-to-one to the MCP Admin form/query routes: list/get Version,
create/update/delete draft, submit, publish, force-publish, redraft, online,
offline, and label replacement.
Explicit methods accept `namespaceId` separately; convenience overloads use
the default namespace. `McpLifecycleDraftRequest`,
`McpLifecycleVersionCommand`, and `McpLifecycleLabelsUpdateRequest` add no
top-level namespace or compatibility `mcpId` selector, and they do not expose
JSON-library types. Historical identity fields inside the reused
`McpServerBasicInfo` content do not participate in lifecycle target resolution.

Runtime AI registration and subscription can remain in `AiService`; broad AI
resource management belongs to `AiMaintainerService`.

## 8. Java Compatibility Rules

- `api`, `client`, and `plugin` modules remain Java 8 compatible unless the
  module policy changes.
- Java SDK JSON serialization and deserialization must go through the neutral
  JSON adapter model defined by the
  [Java SDK JSON Adapter Spec](./sdk-java-json-adapter-spec.md). New public
  SDK APIs must not expose concrete Jackson core/databind types.
- Server-side and maintainer modules follow the repository Java version policy.
- Newly added API methods on Client SDK and Maintainer SDK service interfaces
  (`XxxService`) must declare `@Since` with the first Nacos version that
  supports the method.
- Deprecated Client SDK methods should keep binary compatibility when possible,
  but new designs should point callers to the Maintainer SDK.
- Public model changes should preserve source and binary compatibility where
  practical, especially for objects shared with HTTP and gRPC APIs.

## 9. Documentation References

- Java Client SDK user docs:
  `src/content/docs/next/zh-cn/manual/user/java-sdk` in the Nacos docs project.
- Java Maintainer SDK user docs:
  `src/content/docs/next/zh-cn/manual/admin/maintainer-sdk.md` in the Nacos docs
  project.
