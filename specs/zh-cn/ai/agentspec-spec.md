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

# AgentSpec 规范

本文档定义 AgentSpec 资源在 AI Registry 中的领域契约。

## 1. 身份

AgentSpec 身份为：

```text
namespaceId -> agentspec -> name
```

`name` 从 AgentSpec manifest 中读取，是稳定资源名。

## 2. 包模型

AgentSpec 是版本化 Agent 定义包，包含：

- `manifest.json` 作为主描述文件；
- 可选资源文件，例如 agent instructions 或类型化资产；
- description、bizTags、owner、scope、labels、version 和 download count 等元数据。

AgentSpec upload 接收 ZIP 包。解析器必须先校验 manifest 和资源引用，再写入版本。

## 3. 版本模型

AgentSpec 使用标准 `ai_resource` 和 `ai_resource_version` 模型。它通过 AI 存储保存
`manifest.json` 和资源文件。

每个版本描述都持久化选定的存储 provider。读取、draft 覆盖和删除使用已持久化的
provider，有效 provider 配置只选择新版本。缺少 provider 的历史描述使用
`nacos_config`。

更新或覆盖 draft 会替换完整 AgentSpec 包。服务端先写入替换包中的资源文件，再通过该版本
持久化的 provider 删除旧 `manifest.json` 引用但替换包已省略的资源文件，最后持久化新的
`manifest.json` 和存储描述。清理失败时更新必须失败，并保留旧 manifest 和存储描述以便
重试清理。

不同于 Skill，AgentSpec 不维护独立 manifest index。版本元数据和存储指针是事实来源。

## 4. 生命周期

AgentSpec 遵循共享的 [AI 资源生命周期规范](ai-resource-lifecycle-spec.md)：

- upload 或 create draft；
- update draft；
- 提交 draft 或 reviewed 版本通过发布流水线或直接发布，提交 reviewing 版本按幂等调用返回；
- publish、force publish、update labels、update bizTags、update scope、online/offline
  和 delete。

AgentSpec 可以使用简单生成版本或明确目标版本。类型实现必须拒绝重复版本。

## 5. 运行时行为

运行时客户端可以通过明确版本、label 或 latest 加载组装后的 AgentSpec。订阅应在解析后
AgentSpec 发生变化时通知客户端。

运行时客户端不应获得 upload、publish、force publish、delete 或宽范围管理列表能力。

### 5.1 客户端监听协议

客户端使用 HTTP 轮询 + 条件查询（基于 MD5 的 ETag）检测内容变更，避免每次轮询都下载
完整内容。

- **轮询间隔**：通过 `nacosAiAgentSpecCacheUpdateInterval` 配置，默认 10 000 ms。
- **请求**：`GET /v3/client/ai/agentspecs?namespaceId=&name=&md5=<cached-md5>`。
- **304 Not Modified**：服务端将请求中的 MD5 与存储的 `contentMd5`（发布时预算）比对。
  若一致则返回 HTTP 304 + `ETag` header，客户端保持本地缓存不变。
- **200 OK**：响应携带 `Result<AgentSpec>` JSON 及响应头
  `X-Nacos-AgentSpec-Md5`、`X-Nacos-AgentSpec-Resolved-Version`。客户端更新本地缓存
  和 md5Cache，并发布 `AgentSpecChangedEvent`。
- **存量回填**：对于 contentMd5 字段不存在的旧版本，服务端在首次条件查询时懒计算并存储
  MD5。

### 5.2 鉴权资源解析

AgentSpec HTTP API 使用复数形式的 `/ai/agentspecs` 路径段。解析鉴权资源时，保留注解中
声明的 `AI` sign type 和 API type：

- 常规 Admin 和 Console 操作从 `agentSpecName` 解析资源名；
- `GET .../agentspecs/list` 是 namespace 范围查询，不解析单个资源名，返回数据的资源
  可见性由 visibility 插件控制；
- `PUT .../agentspecs/draft` 从 `agentSpecCard.name` 解析实际写入目标，因为
  `agentSpecName` 可选，而服务实际写入的对象来自 card；
- `GET /v3/client/ai/agentspecs` 从客户端请求参数 `name` 解析资源名。

## 6. 演进说明

AgentSpec 预计会随 agent framework 包格式演进。未来版本可能增加 schema 校验、签名、
依赖 manifest 或兼容性元数据。这些变化必须保留版本化获取能力，或提供迁移规则。
