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

# RAM Auth Plugin Spec

## Scope

The RAM auth contract defines how Nacos clients provide Aliyun RAM-style
identity material and how a matching server-side auth implementation should
validate it. It extends the [Auth Plugin Spec](auth-plugin-spec.md) and the
[Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md).

The current open-source Nacos code contains a built-in Java client
implementation, `RamClientAuthServiceImpl`. It does not contain a standalone
server-side `AuthPluginService` named `ram`. The production server-side RAM auth
implementation is provided by Aliyun MSE for MSE Nacos instances. Open source
Nacos therefore defines the client identity contract and server compatibility
requirements, while the actual MSE server verifier is maintained outside this
repository.

RAM auth is intended for machine-to-machine access. It must not broaden the
Java Client SDK capability surface; administrative operations should still use
the Maintainer SDK or Admin API.

## Concepts

| Concept | Meaning |
|---------|---------|
| Access key | Public credential id attached to requests. |
| Secret key | Shared secret used to compute signatures. It must never be sent in requests. |
| STS credential | Temporary access key, secret key, and security token. STS credentials take precedence over static AK/SK. |
| Signature version | Optional signature algorithm family. Current Java client sets `signatureVersion=v4` when `signatureRegionId` is configured. |
| Request resource | `RequestResource` object supplied by the Java SDK request path. RAM signing must derive identity from this object instead of parsing payloads. |

## Java Client Implementation

The Java client loads `RamClientAuthServiceImpl` through the client auth SPI.
It reads these common properties:

| Property | Purpose |
|----------|---------|
| `accessKey` | Static RAM access key. |
| `secretKey` | Static RAM secret key. |
| `ramRoleName` | RAM role name used for STS credential discovery. |
| `signatureRegionId` | Enables v4 signing key derivation for the configured region. |
| `isUseRamInfoParsing` | Allows the client to obtain AK/SK from the local RAM credential environment when explicit properties are absent. |

STS-related runtime properties include `ram.role.name`,
`time.to.refresh.in.millisecond`, `security.credentials`,
`security.credentials.url`, and `cache.security.credentials`.

If neither `ramRoleName` nor a complete `accessKey`/`secretKey` pair is
available, the implementation must return an empty identity context and leave
unrelated SDK calls unaffected.

## MSE Client Extension

Aliyun MSE also provides a more complete RAM auth client extension in the
`nacos-group/nacos-client-mse-extension` repository. This extension is intended
for MSE Nacos access and should still preserve the Nacos client auth SPI
contract described here. The MSE documentation currently describes this
extension as the Maven artifact `com.alibaba.nacos:nacos-client-mse-extension`
for Java client integrations that need the richer credential provider modes.

The MSE access-authentication guide describes these credential provider modes
for Nacos Client access to MSE Nacos:

| Credential provider mode | Credential basis | Refresh behavior |
|--------------------------|------------------|------------------|
| ECS RAM Role | STS token from ECS or ACK worker role metadata. | Automatic refresh. |
| OIDC Role ARN / RRSA | STS token obtained by exchanging a Kubernetes service account OIDC token. | Automatic refresh. |
| STS Token | User-provided temporary STS credential. | Manual refresh by the caller. |
| Credentials URI | STS credential obtained from an external credential service. | Automatic refresh according to provider response. |
| RAM Role ARN | STS token obtained by assuming a RAM role. | Automatic refresh. |
| AccessKey | Static AK/SK. | Manual rotation. |
| Auto-rotating AccessKey | AK/SK supplied by an external rotation mechanism. | Automatic rotation by the provider. |

For MSE deployments, the MSE extension can supersede the built-in basic
`RamClientAuthServiceImpl` when the application needs RRSA, external credential
URI, role assumption, or richer credential lifecycle management. The extension
must still inject identity material compatible with the server-side MSE RAM
verifier and must not change Nacos resource semantics.

## Identity Injection

`RamClientAuthServiceImpl` selects a resource injector by `SignType`.

| Resource type | Injected identity fields | Resource used for signing |
|---------------|--------------------------|---------------------------|
| `CONFIG` | `Spas-AccessKey`, `Spas-Signature`, `Timestamp`, optional `Spas-SecurityToken`, optional `signatureVersion`. | Namespace and group, encoded as the historical RAM config resource string. |
| `NAMING` | `ak`, `signature`, `data`, optional `Spas-SecurityToken`, optional `signatureVersion`. | Grouped service name and timestamp. |
| `AI` | `Spas-AccessKey`, `Spas-Signature`, `Timestamp`, optional `Spas-SecurityToken`, optional `signatureVersion`. | Namespace and group, encoded as the AI RAM resource string. |
| `LOCK` | `ak`, optional `Spas-SecurityToken`. | Current client implementation only attaches identity material; a future signed lock format must be added explicitly. |

Rules:

- STS credentials override static AK/SK for every supported resource type.
- `signatureVersion=v4` must be accompanied by the v4 signing key derivation
  used by the Java client.
- Unsupported resource types must return an empty identity context.
- A client implementation must not mutate business request payloads.

## Server Contract

The RAM-compatible server auth implementation for MSE Nacos is provided by
Aliyun MSE rather than the open-source Nacos repository. Any server
implementation or gateway that claims compatibility with this RAM identity
contract must:

- Declare every accepted RAM identity field in `identityNames()`.
- Validate the access key or STS token with the deployment's RAM authority.
- Recompute the signature from Nacos `Resource` and `Permission` semantics,
  using the same resource string rules as the Java client.
- Validate timestamp freshness and reject replay where the deployment can do so.
- Map the RAM principal to Nacos `READ` and `WRITE` decisions for the parsed
  namespace, group or resource type, and resource name.
- Return standard `AuthResult` failures so HTTP and gRPC layers can map errors
  consistently.

The server implementation must not infer resources from transport payloads when
Nacos has already parsed a `Resource` object.

## Pending Issues

- The current open-source tree intentionally lacks the MSE server-side RAM
  verifier. If the community wants a standalone open-source RAM server auth
  plugin, a new `AuthPluginService` implementation and documentation must be
  added without depending on proprietary MSE internals.
- RAM identity field names differ between resource types for historical
  compatibility. New resource types should avoid introducing more naming
  variants unless compatibility requires it.

## Relationships

- General auth SPI rules: [Auth Plugin Spec](auth-plugin-spec.md).
- Java client extension registration: [Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md).
- Default username/password auth remains in
  [Default Auth Plugin Implementation Spec](default-auth-plugin-spec.md).
- Aliyun MSE Nacos access-authentication guide:
  <https://help.aliyun.com/zh/mse/user-guide/access-authentication-by-nacos>.
- MSE Java client extension repository:
  <https://github.com/nacos-group/nacos-client-mse-extension>.
