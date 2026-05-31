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

# 配置加密插件规范

## 范围

配置加密插件用于让 Nacos 在不把单一加密算法硬编码进配置模块的情况下，对配置内容进行加密
和解密。

加密配置通过 `cipher-{algorithm}-` dataId 前缀识别。`algorithm` 部分会路由到
`algorithmName()` 匹配的 `EncryptionPluginService`。通用生命周期和状态规则由
[Nacos 插件化规范](plugin-spec.md) 定义。

该插件将加密算法从配置领域中分离出来。配置领域仍然拥有 dataId、group、namespace、
history、listener 和发布语义，并遵守[资源模型](../design/resource-model-spec.md)和
[HTTP API](../http-api/api-spec.md) 契约。

## 概念

| 概念 | 含义 |
|------|------|
| Algorithm name | 嵌入 `cipher-{algorithm}-` 的稳定路由 key。 |
| Data key | 用于加密内容的每条配置密钥材料。 |
| Protected data key | 经过插件封装或加密后的 data key。 |
| Cipher dataId | 声明加密内容的用户可见 dataId 前缀。 |

## SPI

插件实现 `EncryptionPluginService`。

| 方法 | 要求 |
|------|------|
| `algorithmName()` | 稳定算法名，用于路由。 |
| `generateSecretKey()` | 生成每条配置使用的数据密钥或密钥材料。 |
| `encrypt(secretKey, content)` | 加密明文内容。 |
| `decrypt(secretKey, content)` | 解密密文内容。 |
| `encryptSecretKey(secretKey)` | 保护需要存储的数据密钥。 |
| `decryptSecretKey(secretKey)` | 恢复需要使用的数据密钥。 |

该插件以 `encryption` 类型暴露给核心插件管理器。

## Java 客户端集成

Java 客户端通过配置 filter chain 集成加解密。它使用 Java `ServiceLoader` 加载
`IConfigFilter` 实现；内置 `ConfigEncryptionFilter` 注册在 client artifact 中，并委托
`EncryptionHandler` 执行。

`ConfigEncryptionFilter` 行为：

| 方向 | 行为 |
|------|------|
| 发布请求 | 当 `dataId` 以 `cipher-{algorithm}-` 开头时，在传输前加密 content，并设置 `encryptedDataKey`。 |
| 查询响应 | 当 `dataId` 以 `cipher-{algorithm}-` 开头时，在收到 ciphertext 和 `encryptedDataKey` 后解密 content。 |

客户端和服务端使用同一个 `EncryptionPluginService` algorithm name。若期望客户端侧加密，
客户端 classpath 必须包含匹配的加密插件实现。若只期望服务端侧加解密，服务端可以通过自身
插件路径处理，但客户端仍必须在请求和响应模型中保留 `encryptedDataKey`。

客户端配置 filter 属于 Java Client SDK 扩展，不由服务端插件 Admin API 列出或启停，
执行顺序由 `IConfigFilter#getOrder()` 控制。

## 数据模型

加密配置必须存储密文内容和受保护的数据密钥。配置持久化表使用 `encrypted_data_key` 保存
该信息。普通配置的 `encrypted_data_key` 为空。持久化与 dump 边界由
[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)定义。

dataId 前缀属于用户可见契约：

```text
cipher-{algorithm}-{actualDataId}
```

示例：

```text
cipher-aes-application-dev.yml
```

## 执行规则

- 当存在匹配的客户端过滤器和算法插件时，客户端发布的加密配置应在传输前完成加密。
- 控制台发布的加密配置由服务端处理。
- 读取时只有在对应算法插件可用且已启用时才可以解密。
- 加密插件缺失或被禁用时，必须显式失败，不得把密文当作明文返回。
- 非加密配置不得路由到加密插件。
- 历史和 dump 流程必须同时保留密文和受保护的数据密钥。

Nacos 服务端仓库定义加密 SPI 和路由行为。算法实现由服务端插件包提供；当需要客户端侧
加密时，也需要提供匹配的客户端过滤器。

## 安全要求

加密插件不得记录明文、原始密钥或受保护的密钥材料。算法名会出现在 dataId 中，因此必须
稳定且适合小写使用。除非算法明确要求，密钥生成和密钥保护不应依赖可预测行为。

插件必须记录：

- 加密算法和模式；
- 密钥生成来源；
- 受保护 data key 格式；
- 是否同时支持客户端侧和服务端侧加密；
- 算法名或密钥封装格式变化时的迁移行为。
