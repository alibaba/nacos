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

# AI Publish Pipeline Plugin Spec

## Scope

The AI publish pipeline plugin type provides review or interception logic before
AI resources are published. It is designed for generic AI resources such as
Skill, Prompt, MCP, AgentSpec, and future AI resource types.

This is an ordered chain plugin. Matching nodes execute serially by
`PublishPipelineService.getPreferOrder()` in ascending order. A failed node
stops the remaining pipeline and marks the execution rejected. Common lifecycle
and state rules are defined by the [Nacos Plugin Spec](plugin-spec.md).

Pipeline is AI resource governance. It is allowed to approve or reject a publish
operation, but it must not change the canonical identity of the
[AI resource](../ai/ai-resource-model-spec.md) being published. Domain lifecycle
reaction to pipeline results is defined by the
[AI Resource Lifecycle Spec](../ai/ai-resource-lifecycle-spec.md).

## Concepts

| Concept | Meaning |
|---------|---------|
| Pipeline node | One review or interception unit. |
| Pipeline execution | Persisted execution record for one publish operation. |
| Supported resource type | AI resource types a node can process. |
| Approved | All selected nodes passed. |
| Rejected | One selected node failed and stopped the chain. |

## SPI

Pipeline implementations are created by `PublishPipelineServiceBuilder`.

| Builder method | Requirement |
|----------------|-------------|
| `pipelineId()` | Stable pipeline node id. |
| `build(properties)` | Build a configured `PublishPipelineService`. |

The service implements:

| Service method | Requirement |
|----------------|-------------|
| `pipelineId()` | Runtime node id. |
| `execute(context)` | Execute review or interception logic. |
| `getPreferOrder()` | Chain order. Lower values execute earlier. |
| `pipelineResourceTypes()` | AI resource types supported by this node. |

The plugin is exposed to the core plugin manager as type `ai-pipeline`.

## Execution

The pipeline executor:

1. Reads pipeline configuration.
2. Selects nodes that are configured and support the target resource type.
3. Creates a pipeline execution record with `IN_PROGRESS`.
4. Executes selected nodes asynchronously and serially.
5. Persists each node result.
6. Completes as approved only when every node passes.

If the pipeline is disabled or no matching nodes exist, publication proceeds
without pipeline interception. Pipeline output must remain compatible with
[visibility](../auth/visibility-plugin-spec.md) filtering and with any
[AI storage](ai-storage-plugin-spec.md) used for the published content.

Pipeline nodes should return deterministic results for the same resource
version and input metadata. Nodes that call external systems must define timeout
and retry behavior in their implementation documentation.

## Current Integration Note

The core plugin manager can list loaded AI pipeline plugins. Current code notes
that enable or disable through unified plugin management is not yet wired into
pipeline execution. Pipeline execution is controlled by the pipeline config
until that integration is completed.
