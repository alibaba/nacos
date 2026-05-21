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

# Config Encryption Plugin Spec

## Scope

The encryption plugin type lets Nacos encrypt and decrypt configuration content
without hard-coding one cryptographic algorithm into the config module.

Encrypted config items are identified by the `cipher-{algorithm}-` dataId
prefix. The algorithm part selects an `EncryptionPluginService` whose
`algorithmName()` matches the prefix. Common lifecycle and state rules are
defined by the [Nacos Plugin Spec](plugin-spec.md).

The plugin separates the encryption algorithm from the config domain. The config
domain still owns dataId, group, namespace, history, listener, and publication
semantics according to the [resource model](../design/resource-model-spec.md)
and [HTTP API](../http-api/api-spec.md) contracts.

## Concepts

| Concept | Meaning |
|---------|---------|
| Algorithm name | Stable route key embedded in `cipher-{algorithm}-`. |
| Data key | Per-config key material used to encrypt content. |
| Protected data key | Data key after plugin-specific wrapping or encryption. |
| Cipher dataId | User-visible dataId prefix that declares encrypted content. |

## SPI

Plugins implement `EncryptionPluginService`.

| Method | Requirement |
|--------|-------------|
| `algorithmName()` | Stable algorithm name used for routing. |
| `generateSecretKey()` | Generate a per-config data key or key material. |
| `encrypt(secretKey, content)` | Encrypt plaintext content. |
| `decrypt(secretKey, content)` | Decrypt ciphertext content. |
| `encryptSecretKey(secretKey)` | Protect the stored data key. |
| `decryptSecretKey(secretKey)` | Recover the stored data key. |

The plugin is exposed to the core plugin manager as type `encryption`.

## Java Client Integration

The Java client integrates encryption through the config filter chain. It loads
`IConfigFilter` implementations with Java `ServiceLoader`; the built-in
`ConfigEncryptionFilter` is registered in the client artifact and delegates to
`EncryptionHandler`.

`ConfigEncryptionFilter` behavior:

| Direction | Behavior |
|-----------|----------|
| Publish request | When `dataId` starts with `cipher-{algorithm}-`, encrypt content before transport and set `encryptedDataKey`. |
| Query response | When `dataId` starts with `cipher-{algorithm}-`, decrypt content after receiving ciphertext and `encryptedDataKey`. |

The same `EncryptionPluginService` algorithm name is used on client and server.
If client-side encryption is expected, the client classpath must contain the
matching encryption plugin implementation. If only server-side encryption is
expected, the server may encrypt or decrypt through its own plugin path, but the
client must still preserve `encryptedDataKey` in request and response models.

Client config filters are Java Client SDK extensions. They are not listed or
enabled by the server plugin Admin API, and their order is controlled by
`IConfigFilter#getOrder()`.

## Data Model

Encrypted configs must store the encrypted content and the protected data key.
The config persistence schema contains `encrypted_data_key` for this purpose.
Plain config data keeps `encrypted_data_key` empty. Persistence and dump
boundaries are defined by the
[Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md).

The dataId prefix is part of the user-visible contract:

```text
cipher-{algorithm}-{actualDataId}
```

Example:

```text
cipher-aes-application-dev.yml
```

## Execution Rules

- Client-published encrypted config should be encrypted before transport when a
  matching client-side filter and algorithm plugin are available.
- Console-published encrypted config is processed on the server side.
- Reads must decrypt only when the selected algorithm plugin is available and
  enabled.
- Missing or disabled encryption plugins must fail explicitly rather than
  returning ciphertext as plaintext.
- Non-encrypted config must not be routed to encryption plugins.
- History and dump flows must preserve both ciphertext and protected data key.

The Nacos server repository defines the encryption SPI and routing behavior. An
algorithm implementation is provided by a plugin package on the server and, when
client-side encryption is expected, by a matching client filter.

## Security Requirements

Encryption plugins must not log plaintext, raw keys, or protected key material.
Algorithm names must be stable and lower-case friendly because they appear in
dataIds. Key generation and key wrapping must be deterministic only when the
algorithm explicitly requires it.

Plugins must document:

- cryptographic algorithm and mode;
- key generation source;
- protected data key format;
- whether client-side and server-side encryption are both supported;
- migration behavior when algorithm names or key wrapping formats change.
