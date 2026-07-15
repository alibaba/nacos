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

# RAM 鉴权插件规范

## 范围

RAM 鉴权契约定义 Nacos 客户端如何提供阿里云 RAM 风格的身份材料，以及匹配的服务端鉴权
实现应如何校验这些材料。它扩展[鉴权插件规范](auth-plugin-spec.md)和
[Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)。

当前开源 Nacos 代码包含内置 Java 客户端实现 `RamClientAuthServiceImpl`，但没有名为 `ram`
的独立服务端 `AuthPluginService`。生产环境中的服务端 RAM 鉴权实现由阿里云 MSE 在 MSE
Nacos 实例中提供。开源 Nacos 因此定义客户端身份契约和服务端兼容要求，而实际的 MSE 服务端
校验器在本仓库之外维护。

RAM 鉴权面向机器到机器访问。它不得扩大 Java Client SDK 的能力面；管理类操作仍应使用
Maintainer SDK 或 Admin API。

## 概念

| 概念 | 含义 |
|------|------|
| Access key | 附加到请求上的公开凭据 ID。 |
| Secret key | 用于计算签名的共享密钥，不得随请求发送。 |
| STS credential | 临时 access key、secret key 和 security token。STS 凭据优先于静态 AK/SK。 |
| Signature version | 可选的签名算法族。当前 Java 客户端配置 `signatureRegionId` 时会设置 `signatureVersion=v4`。 |
| Request resource | Java SDK 请求链路传入的 `RequestResource` 对象。RAM 签名必须从该对象推导身份，不应自行解析 payload。 |

## Java 客户端实现

Java 客户端通过客户端鉴权 SPI 加载 `RamClientAuthServiceImpl`。它读取以下常用配置：

| 配置 | 目的 |
|------|------|
| `accessKey` | 静态 RAM access key。 |
| `secretKey` | 静态 RAM secret key。 |
| `ramRoleName` | 用于 STS 凭据发现的 RAM role name。 |
| `signatureRegionId` | 为配置的 region 启用 v4 签名密钥派生。 |
| `isUseRamInfoParsing` | 当未显式配置 AK/SK 时，允许客户端从本地 RAM 凭据环境获取 AK/SK。 |

STS 相关运行时配置包括 `ram.role.name`、`time.to.refresh.in.millisecond`、
`security.credentials`、`security.credentials.url` 和 `cache.security.credentials`。

如果既没有 `ramRoleName`，也没有完整的 `accessKey`/`secretKey`，实现必须返回空 identity
context，并且不能影响无关 SDK 调用。

## MSE 客户端扩展

阿里云 MSE 还在 `nacos-group/nacos-client-mse-extension` 仓库中提供了更完整的 RAM 鉴权
客户端扩展。该扩展面向 MSE Nacos 访问场景，并仍应保持本文定义的 Nacos 客户端鉴权 SPI
契约。MSE 文档当前将该扩展描述为 Maven artifact
`com.alibaba.nacos:nacos-client-mse-extension`，用于需要更完整凭据提供方式的 Java client
集成。

MSE Nacos Client 访问鉴权文档描述了以下凭据提供方式：

| 凭据提供方式 | 底层凭据 | 刷新方式 |
|--------------|----------|----------|
| ECS RAM Role | 从 ECS 或 ACK worker role metadata 获取 STS token。 | 自动刷新。 |
| OIDC Role ARN / RRSA | 通过 Kubernetes service account OIDC token 换取 STS token。 | 自动刷新。 |
| STS Token | 调用方提供的临时 STS 凭据。 | 调用方手动刷新。 |
| Credentials URI | 从外部凭据服务获取 STS 凭据。 | 按 provider 返回结果自动刷新。 |
| RAM Role ARN | 通过 assume RAM role 获取 STS token。 | 自动刷新。 |
| AccessKey | 静态 AK/SK。 | 手动轮转。 |
| 自动轮转 AccessKey | 由外部轮转机制提供 AK/SK。 | 由 provider 自动轮转。 |

在 MSE 部署中，当应用需要 RRSA、外部 credential URI、role assumption 或更完整的凭据生命周期
管理时，MSE 扩展可以替代内置基础 `RamClientAuthServiceImpl`。该扩展仍必须注入与 MSE
服务端 RAM 校验器兼容的身份材料，并且不得改变 Nacos 资源语义。

## 身份注入

`RamClientAuthServiceImpl` 按 `SignType` 选择 resource injector。

| 资源类型 | 注入的身份字段 | 签名使用的资源 |
|----------|----------------|----------------|
| `CONFIG` | `Spas-AccessKey`、`Spas-Signature`、`Timestamp`，可选 `Spas-SecurityToken`、`signatureVersion`。 | 命名空间和分组，编码为历史 RAM config resource 字符串。 |
| `NAMING` | `ak`、`signature`、`data`，可选 `Spas-SecurityToken`、`signatureVersion`。 | 分组服务名和时间戳。 |
| `AI` | `Spas-AccessKey`、`Spas-Signature`、`Timestamp`，可选 `Spas-SecurityToken`、`signatureVersion`。 | 命名空间和分组，编码为 AI RAM resource 字符串。 |
| `LOCK` | `ak`，可选 `Spas-SecurityToken`。 | 当前客户端实现只附加身份材料；后续如果需要签名 lock 资源，必须显式增加格式。 |

规则：

- 对每一种已支持资源类型，STS 凭据都优先于静态 AK/SK。
- `signatureVersion=v4` 必须配套使用 Java 客户端的 v4 signing key 派生方式。
- 不支持的资源类型必须返回空 identity context。
- 客户端实现不得修改业务请求 payload。

## 服务端契约

MSE Nacos 的 RAM 兼容服务端鉴权实现由阿里云 MSE 提供，而不是开源 Nacos 仓库提供。任何
声明兼容该 RAM 身份契约的服务端实现或网关都必须：

- 在 `identityNames()` 中声明所有接受的 RAM 身份字段。
- 使用部署侧 RAM 权威系统校验 access key 或 STS token。
- 根据 Nacos `Resource` 和 `Permission` 语义重新计算签名，并使用与 Java 客户端一致的
  resource string 规则。
- 校验 timestamp 时效，并在部署可以支持时拒绝重放。
- 将 RAM principal 映射为对解析后命名空间、分组或资源类型、资源名的 `READ` 和 `WRITE`
  决策。
- 返回标准 `AuthResult` 失败，使 HTTP 和 gRPC 层可以一致映射错误。

当 Nacos 已经解析出 `Resource` 对象时，服务端实现不得再从传输 payload 中推断资源。

## 待处理问题

- 当前开源代码没有 MSE 服务端 RAM 校验器。如果社区希望提供独立开源的 RAM 服务端鉴权
  插件，需要新增 `AuthPluginService` 实现和对应文档，并且不能依赖专有 MSE 内部实现。
- RAM 身份字段名因历史兼容在不同资源类型之间并不统一。新增资源类型不应继续增加新的字段名
  变体，除非兼容性明确要求。

## 关联规范

- 通用鉴权 SPI 规则：[鉴权插件规范](auth-plugin-spec.md)。
- Java 客户端扩展注册：[Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)。
- 默认用户名/密码鉴权仍由[默认鉴权插件实现规范](default-auth-plugin-spec.md)定义。
- 阿里云 MSE Nacos Client 访问鉴权文档：
  <https://help.aliyun.com/zh/mse/user-guide/access-authentication-by-nacos>。
- MSE Java 客户端扩展仓库：
  <https://github.com/nacos-group/nacos-client-mse-extension>。
