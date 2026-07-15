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

# AI Registry 规范

AI Registry 规范定义 Nacos 3.x 的 AI 资源模型。它扩展
[Nacos 设计规范](../design/nacos-design-spec.md)，遵循共享的
[资源模型规范](../design/resource-model-spec.md)，并通过
[HTTP API](../http-api/api-spec.md)、[gRPC API](../grpc-api/api-spec.md) 和
[SDK](../sdk/sdk-spec.md) 规范暴露能力；运行时连接、能力协商和 redo 行为遵循
[客户端运行时规范](../client/README.md)。

## 总览

- [AI Registry 规范](ai-registry-spec.md)
- [AI 资源模型规范](ai-resource-model-spec.md)
- [AI 资源生命周期规范](ai-resource-lifecycle-spec.md)
- [AI Registry 适配器规范](ai-registry-adaptor-spec.md)

## 资源类型

- [MCP Server 规范](mcp-server-spec.md)
- [A2A Agent 规范](a2a-agent-spec.md)
- [Prompt 规范](prompt-spec.md)
- [Skill 规范](skill-spec.md)
- [AgentSpec 规范](agentspec-spec.md)

## 扩展关联

AI Registry 可以使用扩展点，但扩展契约由插件规范定义：

- [AI 发布流水线插件规范](../plugin/ai-pipeline-plugin-spec.md)
- [AI 存储插件规范](../plugin/ai-storage-plugin-spec.md)
- [AI 资源导入插件规范](../plugin/ai-resource-import-plugin-spec.md)
- [可见性插件规范](../auth/visibility-plugin-spec.md)
- [Trace 插件规范](../plugin/trace-plugin-spec.md)
