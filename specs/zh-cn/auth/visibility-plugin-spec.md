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

# 可见性插件规范

## 范围

可见性插件类别控制某个资源对调用方是否可见。它与鉴权相互独立：

- 鉴权判断目标资源/动作上的身份和权限。
- 可见性判断目标资源，或范围查询中的某个资源，是否应该对该身份可见。

可见性对于 AI 注册中心资源尤其重要，因为用户可能创建仅 owner 可见、读者公开可见，或通过
显式授权可见的资源。

可见性补充[鉴权与权限规范](auth-permission-spec.md)，并遵守
[Nacos 插件化规范](../plugin/plugin-spec.md)中的通用生命周期规则。它可以与
[鉴权插件](auth-plugin-spec.md)协作，但不能替代鉴权插件。

可见性必须在数据查询阶段生效。列表和搜索 API 不得先对原始候选集合分页，再只在内存中过滤
当前页，因为这会产生错误的 `totalCount`、空页和不可控延迟。

## 资源模型

具备可见性语义的资源必须遵守 [Nacos 资源模型](../design/resource-model-spec.md)，并提供：

| 字段 | 含义 |
|------|------|
| `namespaceId` | 资源所属命名空间。 |
| `resourceType` | 命名空间内的资源类别。 |
| `resourceName` | 资源类型内稳定的资源名。 |
| `scope` | 可见性范围，目前为 `PUBLIC` 或 `PRIVATE`。 |
| `owner` | 资源所有者身份。 |

这遵循 Nacos 资源层次：

```text
NamespaceId -> resourceType -> resourceName
```

## 可见性 SPI

可见性插件实现 `VisibilityService`。

| 方法 | 要求 |
|------|------|
| `getVisibilityServiceName()` | 返回稳定的插件名称。 |
| `init(properties)` | 初始化插件自身属性。 |
| `resolveDefaultScopeForCreate(identity, apiType, resourceType)` | 当创建资源未显式指定 scope 时，决定默认 scope。 |
| `validateVisibility(identity, action, apiType, resource)` | 校验单个资源的可见性。 |
| `adviseQuery(identity, action, apiType, queryContext)` | 为范围查询返回查询谓词和显式授权资源。 |

该插件通过 SPI 发现，并以 `visibility` 类型注册到插件系统。
配置的可见性服务名称由以下配置选择：

```properties
nacos.plugin.visibility.type=nacos
```

## 动作

可见性使用与鉴权一致的读写语义：

| 动作 | 含义 |
|------|------|
| `r` | 读取或列出可见资源。 |
| `w` | 创建、更新、删除或改变可见性敏感的资源状态。 |

写可见性必须严于读可见性。公开读权限不意味着公开写权限。

## 查询建议

当存储层可以应用可见性条件时，范围查询不应先加载所有资源再只在内存中做过滤。
`QueryAdvisor` 携带：

| 字段 | 目的 |
|------|------|
| `BaseVisibilityPredicate` | 基础谓词，例如所有资源、仅公开资源、仅 owner 资源，或公开加 owner 资源。 |
| `AuthorizedResources` | 需要额外包含的显式授权资源名。 |

列出资源的 API 或存储适配层必须组合这两部分，且不得泄漏私有资源。

默认 AI 集成会在执行 count 和分页查询前，将 `QueryAdvisor` 转换为仓储层
`QueryCondition`。基础谓词映射如下：

| 谓词 | 查询行为 |
|------|----------|
| `ALL` | 不追加可见性条件。 |
| `PUBLIC` | 限制为 `scope=PUBLIC`；如果调用方请求了冲突 scope，则返回空集。 |
| `OWNER` | 限制为 `owner=identity`；如果身份为空或 owner 冲突，则返回空集。 |
| `PUBLIC_AND_OWNER` | 限制为 `scope=PUBLIC OR owner=identity`；匿名调用方退化为仅公开资源。 |

如果 `AuthorizedResources` 被填充，它应作为与基础谓词并列的 OR 分支加入查询。默认实现当前
保持该列表为空，并将该字段作为显式资源授权的扩展点。

## 插件状态与配置

可见性插件的启用状态由可见性插件管理器和核心插件状态检查器共同控制。全局开关为：

```properties
nacos.plugin.visibility.enabled=true
```

插件自身属性使用前缀：

```properties
nacos.plugin.visibility.{serviceName}.*
```

当可见性被关闭时，所属领域必须定义行为是全部可见，还是拒绝可见性敏感操作。默认 AI
可见性实现会在鉴权未启用时允许可见。

## 与鉴权的关系

可见性插件可以将显式权限检查委托给当前选中的鉴权插件。显式可见性权限资源使用领域自己
拥有的资源字符串和 `SignType.SPECIFIED`；默认实现使用：

```text
@@visibility/{namespaceId}/{resourceType}/{resourceName}
```

这保留了职责分离：可见性决定候选资源，鉴权仍然是权限判断来源。
[默认鉴权插件实现](default-auth-plugin-spec.md)提供当前内置的可见性实现。

## API 要求

任何返回具备可见性语义资源的 API 都必须：

- 对单资源读写操作调用 `validateVisibility`。
- 在列表或搜索操作返回数据前应用 `adviseQuery`。
- 在资源创建或更新时保留 owner 和 scope 元数据。
- 避免通过数量、错误信息或部分列表响应暴露私有资源名。
- 当 API 需要隐藏资源存在性时，单资源读拒绝应返回 not found。
- 写拒绝应返回 access denied。
