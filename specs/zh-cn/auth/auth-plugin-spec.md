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

# 鉴权插件规范

## 范围

鉴权插件类别允许 Nacos 在不修改 API Controller 或资源解析器的情况下替换认证与授权实现。
通用契约为：

```text
IdentityContext + Resource + Action -> 允许或拒绝
```

鉴权插件不拥有 Nacos 资源模型。它消费由 Nacos Controller、协议过滤器和资源解析器创建的
资源。

## 服务端 SPI

服务端鉴权插件实现 `AuthPluginService`。

| 方法 | 要求 |
|------|------|
| `getAuthServiceName()` | 返回稳定的插件名称，由 `nacos.core.auth.system.type` 选择。 |
| `identityNames()` | 声明可以从请求中提取的身份字段。 |
| `enableAuth(action, type)` | 判断该动作和 SignType 是否需要鉴权。 |
| `validateIdentity(identityContext, resource)` | 认证调用方，并补充身份元数据。 |
| `validateAuthority(identityContext, permission)` | 校验调用方是否拥有目标资源和动作的权限。 |
| `isLoginEnabled()` | 声明是否暴露插件提供的登录能力。 |
| `isAdminRequest()` | 声明当前请求是否属于管理员初始化流程。 |

当身份或权限被拒绝时，插件必须抛出或返回 Nacos 鉴权异常，使协议层可以映射为标准 API
错误。

## 客户端 SPI

客户端鉴权插件负责提供请求身份材料。客户端插件只能注入所选服务端插件需要的凭据或 token，
不得改变请求载荷的语义。

Java 客户端必须支持内置用户名/密码和 token 流程。自定义客户端鉴权插件可以提供 AK、
签名、证书或外部 token，但必须与匹配的服务端鉴权插件声明的身份字段保持兼容。

## 选择与状态

选中的鉴权实现由以下配置指定：

```properties
nacos.core.auth.system.type=nacos
```

鉴权插件同时以 `auth` 类型注册到核心插件系统。只有被选中且处于启用状态的鉴权插件可以
处理请求。如果插件已加载但被插件状态禁用，则不得参与鉴权判断。

## 身份上下文

`IdentityContext` 是与传输协议无关的调用方描述。它可以包含：

- 远端 IP 等内置字段。
- `Authorization`、`accessToken`、`username`、`password` 等 header 或参数。
- AK、签名、租户声明、外部主体等插件自定义字段。
- 已认证用户名、用户 ID、全局管理员标记等认证结果元数据。

身份字段名属于插件契约的一部分。服务端和客户端插件实现必须对这些名称达成一致。

## 资源与权限

鉴权插件接收 Nacos `Resource` 和 `Permission` 对象。插件可以将这些对象映射到外部权限
系统，但必须保留：

- 命名空间隔离。
- 分组或资源类型语义。
- 资源名语义。
- `READ` 和 `WRITE` 动作语义。
- 通过 `SignType.SPECIFIED` 声明的显式资源。

## 插件 API

如果鉴权插件暴露 HTTP API，这些 API 必须：

- 使用 `/v3/auth/{resource}` 路径族。
- 使用 `Result<T>` 作为响应封装。
- 使用标准 Nacos 错误码和异常处理。
- 为受保护的管理端点添加 `@Secured`。
- 记录登录、初始化等有意公开的端点。

默认 Nacos 鉴权插件是当前 `/v3/auth/user`、`/v3/auth/role` 和
`/v3/auth/permission` API 的参考实现。

## 与可见性的关系

鉴权回答调用方是谁，以及调用方是否拥有某个资源/动作的权限。可见性回答单资源操作或范围
查询中哪些资源应对调用方可见。

可见性插件可以将显式权限检查委托回当前选中的鉴权插件。因此鉴权插件必须让显式资源和领域
资源的权限判断都保持稳定。

## 安全要求

内置 Nacos 鉴权插件面向可信内网环境设计，并不是针对恶意公网环境的完整强鉴权方案。需要
更强认证能力的部署，应提供或选择符合自身安全要求的鉴权插件。

