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

# AI Registry Specs

AI Registry specs define the Nacos 3.x AI resource model. They extend the
[Nacos Design Spec](../design/nacos-design-spec.md), follow the shared
[Resource Model Spec](../design/resource-model-spec.md), and expose their
behavior through the [HTTP API](../http-api/api-spec.md),
[gRPC API](../grpc-api/api-spec.md), [SDK](../sdk/sdk-spec.md), and
[Client Runtime](../client/README.md) specs.

## Overview

- [AI Registry Spec](ai-registry-spec.md)
- [AI Resource Model Spec](ai-resource-model-spec.md)
- [AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md)
- [AI Registry Adaptor Spec](ai-registry-adaptor-spec.md)

## Resource Types

- [MCP Server Spec](mcp-server-spec.md)
- [A2A Agent Spec](a2a-agent-spec.md)
- [Prompt Spec](prompt-spec.md)
- [Skill Spec](skill-spec.md)
- [AgentSpec Spec](agentspec-spec.md)

## Extension Links

AI Registry may use extension points, but extension contracts are defined by
plugin specs:

- [AI Publish Pipeline Plugin Spec](../plugin/ai-pipeline-plugin-spec.md)
- [AI Storage Plugin Spec](../plugin/ai-storage-plugin-spec.md)
- [AI Resource Import Plugin Spec](../plugin/ai-resource-import-plugin-spec.md)
- [Visibility Plugin Spec](../auth/visibility-plugin-spec.md)
- [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
