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

# Nacos Client Connection And Failover Spec

This document defines client-side server discovery, connection lifecycle,
failover, TLS, and request identity propagation for Nacos Client SDKs. It
expands the connection part of the
[Client Runtime Spec](client-runtime-spec.md). Server-side connection lifecycle
is defined by the
[Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md).

## 1. Address Resolution

Client SDKs resolve Nacos server addresses through a `ServerListProvider`.
The current Java implementation supports:

- fixed addresses from `serverAddr`;
- dynamic addresses from an endpoint or address server;
- SPI-based providers for extension scenarios.

Fixed address lists are stable after initialization. Dynamic address providers
may refresh periodically and publish a server-list-change event when the
effective list changes.

The effective server address list must be normalized before transport use:

- an address without port uses the default Nacos server port;
- HTTP or HTTPS scheme in the fixed address is preserved for HTTP calls;
- gRPC uses the selected server port plus the configured gRPC port offset;
- context path and namespace are part of the client identity but not part of the
  gRPC host/port pair.

## 2. Server List Refresh

Dynamic server-list refresh must be local and non-authoritative. It only changes
which server the client can connect to. It must not change Config, Naming, AI,
or Lock resource state.

When a dynamic provider receives a changed list:

1. The provider replaces its local list atomically.
2. It publishes `ServerListChangeEvent`.
3. Existing RPC clients check whether the current server is still in the list.
4. If the current server is no longer valid, the RPC client starts reconnect.

If a fixed list is used, the provider should not publish refresh events.

## 3. gRPC Connection Lifecycle

Client gRPC connections follow this lifecycle:

```text
WAIT_INIT -> INITIALIZED -> STARTING -> RUNNING
                                      -> UNHEALTHY -> reconnect -> RUNNING
                                      -> SHUTDOWN
```

The runtime should try an initial synchronous connect during startup. If startup
cannot establish a running connection within the configured retry budget, it may
continue with asynchronous reconnect, but public SDK calls must surface
connection unavailability according to their domain contract.

Reconnect can be triggered by:

- request stream error or completion;
- failed health check;
- explicit server reset request;
- server list refresh that excludes the current server;
- request failure followed by an unsuccessful health check;
- client lifecycle restart.

A server reset request may include a recommended target server. The client may
try the recommended server first when it is still in the effective server list.
If that attempt fails, normal server-list rotation applies.

## 4. Health Check And Half-Open Detection

The client periodically checks connection liveness when the connection has been
idle for the configured keepalive window. A failed health check marks the RPC
client `UNHEALTHY` and schedules reconnect.

gRPC transport keepalive protects against half-open TCP connections. Domain
modules should not implement their own gRPC heartbeat on top of Naming,
Config, AI, or Lock requests. They should react to connection events and domain
pushes instead.

## 5. HTTP Transport

HTTP remains a supported client transport for compatibility and selected
operations. A domain client may use HTTP when:

- the server does not support the required gRPC ability;
- the operation is a legacy compatibility operation;
- the public SDK method intentionally maps to an Open API;
- the feature does not need long-lived push or connection state.

HTTP fallback must be explicit in the domain client. A failed gRPC request must
not automatically mutate resource state through HTTP unless the domain client
has defined that fallback.

## 6. TLS

Client gRPC TLS is transport infrastructure. The runtime may support:

- plaintext channels when TLS is disabled;
- TLS channels with a configured provider, protocols, and ciphers;
- trust-all mode for controlled test environments;
- trust collection certificate files for production trust;
- mutual TLS with client certificate chain, private key, and private key
  password.

When TLS is enabled, the selected Nacos server must support TLS on the gRPC
port. A TLS/client-server mismatch is a connection failure, not a domain
operation failure.

HTTP TLS follows the selected HTTP URL scheme and HTTP client configuration.
Domain specs should not redefine TLS behavior.

## 7. Request Identity Propagation

Client-side auth plugins login through the runtime security proxy and provide a
`LoginIdentityContext` for each request resource. Runtime clients must attach
the resulting identity parameters to HTTP or gRPC request headers before
sending the domain request.

If the server returns a no-right response that indicates an expired or invalid
runtime identity, the client may mark the login context for refresh and retry
according to the domain operation's retry rules. It must not hide an
authorization failure as a successful local-cache result.

## 8. Failure Visibility

Connection failover repairs the transport path. It does not guarantee that a
domain write was applied unless the domain response was received and validated.

Client SDKs should distinguish:

- connection unavailable;
- request timed out with unknown server-side result;
- server rejected the request;
- local failover or local snapshot was used for a read;
- redo has not yet restored runtime intent after reconnect.

## 9. Relationship To Local Recovery

Connection recovery triggers local recovery behavior, but each domain owns its
own recovery state:

- Config listeners resync known group keys and fuzzy watch state.
- Naming redoes ephemeral instance registrations and subscriptions.
- AI redoes runtime endpoint registrations and subscriptions.
- Local cache reads are governed by the
  [Client Local Cache And Redo Spec](client-local-cache-redo-spec.md).

## 10. Pending Issues

- HTTP and gRPC connection metrics should follow the shared field and label
  guidance in the
  [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).
- Multi-language SDKs should align on server list refresh event semantics and
  reconnect status names.
