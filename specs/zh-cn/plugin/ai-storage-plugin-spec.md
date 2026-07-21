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

# AI 存储插件规范

## 范围

AI 存储插件抽象 AI 资源的二进制或文本内容存储。元数据仍由 AI 资源模型和持久化服务拥有；
存储插件只负责按 key 读、写和删除内容。通用生命周期和状态规则由
[Nacos 插件化规范](plugin-spec.md) 定义。

这是路由型存储插件。可以注册多个存储提供者。每个 `StorageKey.provider` 选择一个
`AiResourceStorage`。

存储与 [AI 资源元数据](../ai/ai-resource-model-spec.md)有意分离。AI 领域拥有资源身份、
版本、标签、可见性和生命周期。存储插件只拥有不透明 storage key 对应的内容字节。

## 概念

| 概念 | 含义 |
|------|------|
| Storage provider | 由 `StorageKey.provider` 选择的命名后端。 |
| Opaque key | provider 专属 key，上层不应解析。 |
| Content | 与 AI 资源版本关联的二进制或文本载荷。 |
| Metadata | AI 持久化层存储的 AI 资源记录。 |

## SPI

存储实现由 `AiResourceStorageBuilder` 创建。

| Builder 方法 | 要求 |
|--------------|------|
| `type()` | 稳定存储提供者类型。 |
| `build()` | 构造 `AiResourceStorage`。 |

存储服务实现：

| Service 方法 | 要求 |
|--------------|------|
| `type()` | 运行时存储提供者类型。 |
| `save(storageKey, content)` | 为该 key 存储内容。 |
| `get(storageKey)` | 读取该 key 的内容，不存在时返回 null。 |
| `delete(storageKey)` | 删除该 key 的内容。 |

该插件以 `ai-storage` 类型暴露给核心插件管理器。

## 路由

上层必须构造 provider 非空且 key 不透明的 `StorageKey`。`AiResourceStorageRouter` 按
provider 路由。除非自身 provider 契约定义了编码方式，存储插件不得从不透明 key 中解析
Nacos 资源身份。

选择已注册 provider 前，router 会检查 `ai-storage:{provider}` 的统一插件状态。Provider
被禁用时路由必须显式失败，且不得调用其内容读写操作。

默认 provider 为 `nacos_config`，它通过 Nacos 配置存储保存 AI 资源内容。
`nacos_config` provider 将不透明 key 映射为 Nacos 配置坐标时，必须对逻辑 `dataId` 和
规范资源 group 使用稳定的物理映射：

- 对 `dataId`，仅 ASCII 字母、ASCII 数字和 `_`、`-`、`.`、`:` 原样保留；逻辑值只要
  包含其他字符，就将整个值编码为 `enc.` 加 UTF-8 字节的小写十六进制。编码候选值超过
  255 个字符时，改为 `sha256.` 加该候选值完整 SHA-256 摘要的小写十六进制。逻辑值以
  保留的 `enc.` 前缀开头时（大小写不敏感）也必须进行同样编码，避免与自动编码结果串键。
- 规范资源 group 不超过 128 个字符时原样保留；超过限制时，改为稳定资源前缀加
  `sha256.`，再加规范 group 完整 SHA-256 摘要的小写十六进制。构造规范 group 之前，
  条件编码的 group segment 也必须转义同一个大小写不敏感的 `enc.` 保留命名空间，以及
  精确匹配 `sha256.<64位十六进制>` 的兜底格式。
- 即使长度未超限，只要逻辑候选值已经符合保留的 SHA-256 物理格式，也必须再次哈希，避免
  逻辑 key 直接伪造成自动生成的哈希 key。

SHA-256 兜底具有确定性但不可逆，逻辑资源身份仍由 AI 资源元数据持有；`save`、`get`、
`delete` 必须使用完全一致的物理映射。

### Agent 逻辑坐标

对于 `type=agent`，Agent 领域在向 provider 传递 opaque `StorageKey` 前构造以下 Nacos
Config 逻辑坐标：

```text
group  = agent-version
dataId = agent__<rad-ascii-v1(agentName)>__<version>.json
```

`rad-ascii-v1` 和完整 Agent Version 存储契约由
[Agent 存储规范](../ai/agent-storage-spec.md)定义。该坐标是 provider 的逻辑输入，不是向
调用方暴露的物理 Config 身份。

内置 provider 必须把两个逻辑段都传给通用 `NacosAiConfigKeyCodec`；不能因为 Agent 领域已经
编码 `agentName` 就跳过该 codec。物理限制内的安全值与逻辑值相同。长度和保留格式处理完全由
通用 codec 负责：超长候选值使用其确定性 SHA-256 兜底，得到的物理结果不可逆。

上层可以持久化逻辑 key format 和 content digest，但不得解析物理 Config key、要求物理 key
可逆，或根据物理 key 重建 Agent 身份。`save`、`get`、`delete` 始终通过同一个 codec 重新
计算物理坐标。

provider 不会双读旧版物理映射产生的坐标。对已受影响的 `nacos_config` 存量数据，
必须在只使用新映射的节点启动前，通过协调的维护窗口完成迁移。迁移必须仅限 AI 自有
坐标，提前校验目标唯一键冲突，并在坐标改写后重建 Config 缓存。`nacos-ai-prompt` group
下的 Prompt legacy mirror 是不属于该物理映射的兼容坐标，必须保持不变。

## 插件状态与配置

AI 存储 provider 接入统一插件 state。禁用非 critical provider 后，实例仍保持加载并可被
插件管理查询，但 router 会拒绝该 provider 的新操作。内置 `ai-storage:nacos_config` 是默认
后端，也是服务端 AI 能力依赖的 critical 插件；服务端仍依赖它时，不能通过插件管理将其禁用。

以下属性为不同 AI 资源领域选择 provider：

```properties
nacos.ai.prompt.storage.provider=nacos_config
nacos.ai.skill.storage.provider=nacos_config
nacos.ai.agentspec.storage.provider=nacos_config
nacos.ai.agent.storage.provider=nacos_config
```

它们属于领域路由策略，不是 `ai-storage:nacos_config` 所拥有的私有配置 definitions。

内置 provider 没有私有配置，不实现 `PluginConfigSpec`，并以 `configurable=false` 暴露。
拥有私有配置的 `AiResourceStorage` 构建结果可以实现 `PluginConfigSpec`，并声明以下标准 key：

```properties
nacos.plugin.ai-storage.{provider}.{itemKey}
```

Storage builder 负责在核心插件发现前构造 service。统一配置元数据和 apply 行为属于构建后的
service 实例，不属于 builder 或领域路由 key。

## 要求

存储插件必须精确保留字节内容，不得改变资源元数据、版本状态、
[可见性](../auth/visibility-plugin-spec.md)或鉴权。存储 provider 缺失时必须显式失败。
发布前审核仍由 [AI Pipeline](ai-pipeline-plugin-spec.md) 负责。

实现必须记录：

- 支持的最大内容大小；
- `save` 和 `delete` 后的一致性预期；
- 读取是强一致还是最终一致；
- 备份与迁移行为；
- storage key 是否可以出现在 API 响应或日志中。
