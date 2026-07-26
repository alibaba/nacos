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

# Addressing Extension Spec

## Scope

Addressing determines how Nacos runtime components discover Nacos server
addresses. It has both a server-side cluster membership surface and a Java
client-side server list surface.

The public documentation describes addressing extension points. Current server
code primarily uses built-in `MemberLookup` implementations and does not
register addressing in the unified `PluginType` registry. Current Java client
code uses `ServerListProvider` implementations loaded by SPI.

This spec records the current member lookup behavior and the compatibility
expectations for addressing-style extensions.

Addressing is an extension-adjacent mechanism rather than a current unified
plugin type. It is kept in the plugin spec tree because official extension
documentation has historically described address-server based lookup as an
extension point. The shared extension rules are defined by the
[Nacos Plugin Spec](plugin-spec.md), while cluster membership remains part of
the [Cluster Membership Spec](../design/foundation-cluster-membership-spec.md).

## Concepts

| Concept | Meaning |
|---------|---------|
| Member | One Nacos server node in a cluster. |
| Member lookup | Server-side service that discovers and refreshes the cluster member list. |
| Server list provider | Java client-side SPI that returns the Nacos server list for SDK requests. |
| Address server | External HTTP endpoint that returns the current server list. |
| Lookup mode | Selected server-side member discovery strategy. |
| Address source | Diagnostic value that explains where the Java client server list came from. |

## Java Client Addressing

The Java Client SDK loads `ServerListProvider` implementations through SPI in
`AbstractServerListManager`. Config and Naming clients use the selected provider
through `ConfigServerListManager` and `NamingServerListManager`; gRPC clients
then consume the same server list through `ServerListFactory`.

The selected provider is the highest-order implementation whose `match(...)`
returns true for the client properties.

Built-in providers:

| Provider | Trigger | Behavior |
|----------|---------|----------|
| `PropertiesListProvider` | `serverAddr` is configured. | Uses a fixed server address list from client properties. |
| `EndpointServerListProvider` | `endpoint` is configured. | Pulls server addresses from an address endpoint, refreshes periodically, and publishes `ServerListChangeEvent` when the list changes. |

Client addressing properties include:

| Property | Purpose |
|----------|---------|
| `serverAddr` | Fixed server address list. |
| `endpoint` | Dynamic server address endpoint host. |
| `endpointPort` | Endpoint port, defaulting to `8080` in the Java client implementation. |
| `endpointContextPath` | Context path used when building the endpoint URL. |
| `endpointClusterName` | Server list name used by the endpoint path. |
| `endpointQueryParams` | Extra query string appended to the endpoint URL. |
| `endpointRefreshIntervalSeconds` | Refresh interval for endpoint mode. |
| `isUseEndpointParsingRule` | Whether the client applies endpoint parsing rules. |

Client addressing extensions must:

- return server addresses parseable by Nacos HTTP and gRPC clients;
- keep server list refresh independent from request payload semantics;
- publish `ServerListChangeEvent` when a dynamic list changes;
- release background refresh resources from `shutdown()`;
- preserve namespace, context path, and module-name semantics passed through
  `NacosClientProperties`.

Client addressing extensions are Java Client SDK extensions, not server plugin
manager entries. They are not listed or enabled by the server Admin plugin API.

## Current Server Lookup Modes

`LookupFactory` selects one `MemberLookup`.

| Mode | Name | Behavior |
|------|------|----------|
| File config | `file` | Read `cluster.conf` or the configured member list and watch local config changes. |
| Address server | `address-server` | Pull member list from an address server URL and refresh periodically. |
| Standalone | internal | Used when the server runs in standalone mode. |

Selection is controlled by:

```properties
nacos.core.member.lookup.type=file
nacos.core.member.lookup.type=address-server
```

If no mode is configured, the server uses file config when local cluster member
configuration exists; otherwise it uses address-server mode.

File mode owns local static membership. Address-server mode owns remote dynamic
membership. Standalone mode must not publish multi-node membership.

## Address Server Mode

Address server mode uses:

| Property or env | Purpose |
|-----------------|---------|
| `address.server.domain` / `address_server_domain` | Address server host. |
| `address.server.port` / `address_server_port` | Address server port. |
| `address.server.url` / `address_server_url` | Path that returns the server list. |
| `nacos.core.address-server.retry` | Startup pull retry count. |
| `maxHealthCheckFailCount` | Fail count before the address server is marked unhealthy. |

The returned server list must be parseable as Nacos cluster member addresses.

Address-server mode must retry startup pulls according to
`nacos.core.address-server.retry`. Runtime health checks must mark the address
server unhealthy after `maxHealthCheckFailCount` consecutive failures, but must
not invent new members when the address server is unavailable.

## Compatibility Expectations

Addressing extensions must preserve the member identity format and update
semantics defined by the
[Cluster Membership Spec](../design/foundation-cluster-membership-spec.md),
including listener notification behavior and shutdown behavior.
Extensions must not bypass cluster membership validation or inject members with
ambiguous addresses.

If an external addressing SPI is used by a deployment, it should behave like a
single selected member lookup service and must document its configuration keys.

Any future migration of addressing into unified `PluginType` must preserve:

- the `file` and `address-server` lookup names;
- member address format accepted by the cluster module;
- listener notification behavior for member changes;
- startup fallback behavior when no explicit lookup type is configured.
