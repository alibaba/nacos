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

Pipeline implementations directly implement `PublishPipelineService`, which
extends `PluginConfigSpec`, and register the service class through Java SPI.
Implementations must provide a public no-argument constructor. The pipeline
manager loads and retains lightweight service instances only when the core
plugin provider is asked to load the `ai-pipeline` type. With
`nacos.plugin.ai-pipeline.enabled=false`, startup defers this SPI loading. When a
server configuration refresh enables the framework, the core plugin manager loads the
services, restores implementation state, resolves effective configuration, and invokes
`applyConfig` before a node may execute. A service must defer runtime resource initialization
until this first `applyConfig` invocation.

The service implements:

| Service method | Requirement |
|----------------|-------------|
| `pipelineId()` | Runtime node id. |
| `execute(context)` | Execute review or interception logic. |
| `getPreferOrder()` | Chain order. Lower values execute earlier. |
| `pipelineResourceTypes()` | AI resource types supported by this node. |
| `getConfigDefinitions()` | Declare the node implementation configuration. |
| `applyConfig(config)` | Apply the effective item-key configuration. |
| `getCurrentConfig()` | Return the configuration accepted by the service. |

The plugin is exposed to the core plugin manager as type `ai-pipeline`.
The former `PublishPipelineServiceBuilder` SPI and its arbitrary
`Properties` construction path are not part of this contract.

## Execution

The pipeline executor:

1. Reads pipeline configuration and checks the pipeline framework switch.
2. Selects implementations whose unified plugin state is enabled and that
   support the target resource type.
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

## Configuration

Pipeline framework configuration and node implementation configuration have
different owners:

| Configuration | Owner | Unified config definition |
|---------------|-------|---------------------------|
| `nacos.plugin.ai-pipeline.enabled` | Dynamic pipeline framework entry switch | Owned by the AI domain module configuration; not part of node definitions and never converted into implementation state. |
| `nacos.plugin.ai-pipeline.type` | Legacy startup chain composition | Read only by the core plugin manager to supply restart-time initial implementation state; persisted or runtime unified state takes precedence. |
| `nacos.plugin.ai-pipeline.{pipelineId}.order` | Pipeline chain ordering | Declared as the `order` item by the corresponding implementation through `PluginConfigSpec`. |
| `nacos.plugin.ai-pipeline.{pipelineId}.{itemKey}` | The corresponding node implementation | Declared by the implementation through `PluginConfigSpec`. |

There is no separate pipeline implementation configuration provider or node
configuration model. The AI domain reads only the family-wide `enabled` entry
switch. The core plugin manager consumes legacy `type` solely for initial state
migration. Canonical implementation keys and aliases, including `order`, are
resolved by the common plugin configuration source chain and delivered as
item-key maps through `applyConfig`.

Unified implementation state is the authoritative source for chain membership.
The legacy `type` list remains only as restart-time compatibility input for the
core plugin manager. Pipeline execution is available after the core plugin
manager has initialized state and applied effective configuration.

### Skill Scanner

The built-in `ai-pipeline:skill-scanner` node declares the following
implementation configuration. Each alias in the table is a historical relative
key under the same `nacos.plugin.ai-pipeline.skill-scanner.` prefix.

| Item key | Alias | Type | Default | Sensitive | Effect mode | Meaning |
|----------|-------|------|---------|-----------|-------------|---------|
| `order` | None | NUMBER | `100` | No | RUNTIME | Execution order in the pipeline chain; lower values execute earlier. |
| `command` | `executable`, `path` | STRING | `skill-scanner` | No | RESTART | CLI command or executable path. Command names are resolved from the server process `PATH` and the user-local bin directory. |
| `use-llm` | `useLlm` | BOOLEAN | `false` | No | RESTART | Enables LLM semantic analysis during scanning. |
| `llm-api-key` | `llmApiKey` | STRING | empty | Yes | RESTART | Passed to the scanner process as `SKILL_SCANNER_LLM_API_KEY`. |
| `llm-model` | `llmModel` | STRING | empty | No | RESTART | Passed to the scanner process as `SKILL_SCANNER_LLM_MODEL`. |
| `llm-provider` | `llmProvider` | STRING | empty | No | RESTART | Passed to the CLI as its LLM provider. The current implementation does not restrict this value to an enum. |
| `enable-meta` | `enableMeta` | BOOLEAN | `false` | No | RESTART | Enables skill-scanner meta checks. |

The canonical full key is
`nacos.plugin.ai-pipeline.skill-scanner.{itemKey}`. The implementation must
continue accepting the listed aliases, while queries and runtime persistence
return or store only canonical item keys. `llm-api-key` must be masked before a
plugin detail API response and must not be written to logs.

The current Skill Scanner service resolves its command and constructs immutable
scan options during its first configuration application, so scanner fields are
`RESTART`. `order` is independent of scanner resources and may be changed at
runtime. If neither the configured command nor the default command can be
resolved to an executable, the node remains loaded and queryable, but an
attempted scan must reject publication with an installation hint.

### SkillSpector

The built-in `ai-pipeline:skill-spector` node declares the following
implementation configuration. Each alias in the table is a historical relative
key under the same `nacos.plugin.ai-pipeline.skill-spector.` prefix.

| Item key | Alias | Type | Default | Sensitive | Effect mode | Meaning |
|----------|-------|------|---------|-----------|-------------|---------|
| `order` | None | NUMBER | `90` | No | RUNTIME | Execution order in the pipeline chain; lower values execute earlier. |
| `command` | `executable`, `path` | STRING | `skill-spector` | No | RESTART | CLI command or executable path. Command names are resolved from the server process `PATH`, `~/ai-infra/ai-pipeline/bin`, and `~/.local/bin`. |
| `use-llm` | `useLlm` | BOOLEAN | `false` | No | RESTART | Enables SkillSpector LLM analysis. Static scanning remains enabled when this is false. |
| `provider` | None | STRING | empty | No | RESTART | LLM provider passed to the SkillSpector subprocess. |
| `model` | None | STRING | empty | No | RESTART | LLM model passed to the SkillSpector subprocess. |
| `api-key` | `apiKey` | STRING | empty | Yes | RESTART | Credential passed to the environment variable corresponding to the effective provider. |
| `base-url` | `baseUrl` | STRING | empty | No | RESTART | OpenAI-compatible endpoint passed as `OPENAI_BASE_URL`. |
| `log-level` | `logLevel` | STRING | `WARNING` | No | RESTART | SkillSpector subprocess log level. The current implementation does not restrict this value to an enum. |
| `risk-score-threshold` | `riskScoreThreshold` | NUMBER | `50` | No | RESTART | Reports whose risk score is greater than the effective threshold are rejected. Integer values are clamped to `0..100`; an absent or non-integer value uses the default. |
| `max-findings` | `maxFindings` | NUMBER | `20` | No | RESTART | Maximum findings included in the review message. Integer values above `100` are capped at `100`; an absent, non-integer, zero, or negative value uses the default. |

The canonical full key is
`nacos.plugin.ai-pipeline.skill-spector.{itemKey}`. Canonical keys take
precedence when both a canonical key and an alias are configured. Queries and
runtime persistence return or store only canonical item keys. `api-key` must be
masked before a plugin detail API response and must not be written to logs.
Explicit non-numeric values for NUMBER items are rejected by the common plugin
configuration type check before the configuration is applied.

The current SkillSpector service resolves its command and constructs immutable
scan options during its first configuration application, so scanner fields are
`RESTART`. `order` is independent of scanner resources and may be changed at
runtime. Existing subprocess environment variables take precedence over values
copied from plugin configuration. If neither the configured command nor the
default command can be resolved to an executable, the node remains loaded and
queryable, but an attempted scan must reject publication with an installation
hint.

## Unified State Integration

The core plugin manager lists loaded AI pipeline plugins by `pipelineId`.
`PublishPipelineManager` filters configured candidates through unified state
for `ai-pipeline:{pipelineId}` before resource-type matching and ordering. A
disabled node remains registered but does not participate in publication.
