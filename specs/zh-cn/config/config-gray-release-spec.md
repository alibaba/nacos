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

# Config 灰度发布规范

本文定义 Config 的灰度发布语义。

## 1. 模型

一个 Config 资源可以拥有：

- 一个正式配置；
- 零个或多个灰度配置版本。

灰度版本是同一 Config 身份下的从属发布状态：

```text
namespaceId -> groupName -> dataId -> grayName
```

`grayName` 不是新的顶层 Config 资源。正式配置和灰度版本共享同一个 `namespaceId`、`groupName`
和 `dataId`，但可以拥有不同的内容、md5、encrypted data key、最后修改时间和灰度规则。

## 2. 灰度规则

灰度规则包含：

| 字段 | 含义 |
| --- | --- |
| `type` | 规则类型，例如 `beta` 或 `tag`。 |
| `version` | 规则解析版本。 |
| `expr` | 原始规则表达式。 |
| `priority` | 优先级，值越大越先匹配。 |

规则实现通过 Java SPI 加载 `GrayRule`。规则必须可解析、有效，并能匹配请求标签。

## 3. 内置规则

| 规则 | `grayName` | 匹配标签 | 优先级 |
| --- | --- | --- | --- |
| Beta | `beta` | `ClientIp` 在逗号分隔的 beta IP 列表中。 | `Integer.MAX_VALUE` |
| Tag | `tag_{tag}` | `Vipserver-Tag` 等于请求 tag 值。 | `Integer.MAX_VALUE - 1` |

存在多个灰度版本时，先按优先级降序匹配，优先级相同再按 `grayName` 排序。

## 4. 发布与删除

携带 `betaIps` 的发布会写入 beta 灰度版本。携带 `tag` 的发布会写入 tag 灰度版本。没有灰度选择
字段的发布会写入正式配置。

灰度发布必须：

- 校验规则类型、版本、表达式和优先级；
- 执行最大灰度版本数限制，默认通过 `nacos.config.gray.version.max.count` 配置为 `10`；
- 持久化灰度内容和规则元数据；
- 发布携带对应 `grayName` 的 Config 变更事件，并遵循
  [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)；
- 记录灰度专用事件类型的持久化 trace。

删除灰度版本只移除该版本，不删除正式配置。

## 5. 查询语义

运行时查询会先匹配灰度规则，再回退正式配置：

1. 从客户端 IP、显式 tag 和连接标签构建请求标签。
2. 遍历已排序的灰度版本。
3. 返回第一个命中的灰度版本。
4. 如果请求显式指定 tag 但未命中 tag 灰度，返回 tag-specific not-found。
5. 否则返回正式配置。

Admin beta 查询在 beta 版本存在时返回 beta 版本。Admin 正式查询不应静默返回灰度内容。

## 6. 兼容与清理

当前领域模型是 `config_info_gray` 加 `GrayRule`。beta 和 tag 灰度版本同样通过
`config_info_gray` 中对应的 `grayName` 和序列化规则元数据表示。

从 Nacos 3.3 版本线开始，运行时不再支持从 legacy `config_info_beta`、`config_info_tag`
旧表向 `config_info_gray` 的兼容迁移。从 3.0 之前版本升级且使用过 beta 灰度发布的部署，
必须在升级前完成相关数据迁移。

## 7. 待补充规范

- TODO: 如果未来 Config 灰度规则超出 beta IP 和 tag 匹配，需要定义通用发布治理规范。
