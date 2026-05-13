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

Addressing determines how a Nacos server discovers the other members in its
cluster. The public documentation describes an addressing plugin SPI. Current
server code primarily uses built-in `MemberLookup` implementations and does not
register addressing in the unified `PluginType` registry.

This spec records the current member lookup behavior and the compatibility
expectations for addressing-style extensions.

Addressing is an extension-adjacent mechanism rather than a current unified
plugin type. It is kept in the plugin spec tree because official extension
documentation has historically described address-server based lookup as an
extension point. The shared extension rules are defined by the
[Nacos Plugin Spec](plugin-spec.md), while cluster membership remains part of
the [Nacos Design Spec](../design/nacos-design-spec.md).

## Concepts

| Concept | Meaning |
|---------|---------|
| Member | One Nacos server node in a cluster. |
| Member lookup | Service that discovers and refreshes the member list. |
| Address server | External HTTP endpoint that returns the current server list. |
| Lookup mode | Selected member discovery strategy. |

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

Addressing extensions must preserve member identity format, cluster membership
update semantics, listener notification behavior, and shutdown behavior.
Extensions must not bypass cluster membership validation or inject members with
ambiguous addresses.

If an external addressing SPI is used by a deployment, it should behave like a
single selected member lookup service and must document its configuration keys.

Any future migration of addressing into unified `PluginType` must preserve:

- the `file` and `address-server` lookup names;
- member address format accepted by the cluster module;
- listener notification behavior for member changes;
- startup fallback behavior when no explicit lookup type is configured.
