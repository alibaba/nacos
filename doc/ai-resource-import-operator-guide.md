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

# AI Resource Import Operator Guide

This guide describes how to enable operator-configured AI resource import
sources for the Nacos console. It covers external registry or marketplace import
for MCP Servers and Skills.

This guide does not cover the MCP tool helper used while editing an MCP Server
schema. The `importToolsFromMcp` API connects to a user-provided MCP runtime
endpoint and only helps populate the current MCP tool specification. It is not
an external marketplace or registry import flow.

## Scope

AI resource import uses this console flow:

```text
list sources -> search candidates -> select candidates -> validate -> execute
```

The browser only receives candidate metadata, validation status, and import
results. MCP specifications, Skill archives, and other full import artifacts are
downloaded and converted on the server side by importers and resource operators.

All external import sources should be configured by operators in
`conf/application.properties`. Users should not submit arbitrary registry roots,
URLs, IPs, or credentials through import APIs.

## Built-in Preset Sources

Nacos provides built-in import source presets in
`plugin-default-impl/nacos-default-ai-importer-plugin`. Preset properties use
the `nacos.plugin.ai.importer.*` prefix.

### Official MCP Registry

Enable the official MCP registry source:

```properties
nacos.plugin.ai.importer.mcp.official.enabled=true
```

Default values:

| Property | Default |
|----------|---------|
| `source-id` | `mcp-official` |
| `display-name` | `Official MCP Registry` |
| `endpoint` | `https://registry.modelcontextprotocol.io/v0/servers` |
| `resource type` | `mcp` |
| `importer` | `mcp-registry` |

Optional overrides:

```properties
nacos.plugin.ai.importer.mcp.official.source-id=mcp-official
nacos.plugin.ai.importer.mcp.official.display-name=Official MCP Registry
nacos.plugin.ai.importer.mcp.official.endpoint=https://registry.modelcontextprotocol.io/v0/servers
nacos.plugin.ai.importer.mcp.official.connect-timeout-ms=3000
nacos.plugin.ai.importer.mcp.official.read-timeout-ms=10000
nacos.plugin.ai.importer.mcp.official.max-page-count=20
nacos.plugin.ai.importer.mcp.official.max-item-count=500
nacos.plugin.ai.importer.mcp.official.max-artifact-size=10485760
```

### Skill Well-known Source

Enable a Skill well-known registry source:

```properties
nacos.plugin.ai.importer.skills.well-known.enabled=true
nacos.plugin.ai.importer.skills.well-known.url=https://developers.cloudflare.com
```

Default values:

| Property | Default |
|----------|---------|
| `source-id` | `skills-well-known` |
| `display-name` | `Skill Well-known Registry` |
| `resource type` | `skill` |
| `importer` | `skills-well-known` |

The `url` property is required when this preset is enabled. It may point to a
registry root, `/.well-known/agent-skills`, or `/.well-known/skills`. The
importer supports Skill well-known discovery 0.1-compatible indexes and 0.2.0
indexes.

Optional overrides:

```properties
nacos.plugin.ai.importer.skills.well-known.source-id=cloudflare-skills
nacos.plugin.ai.importer.skills.well-known.display-name=Cloudflare Skills
nacos.plugin.ai.importer.skills.well-known.connect-timeout-ms=3000
nacos.plugin.ai.importer.skills.well-known.read-timeout-ms=10000
nacos.plugin.ai.importer.skills.well-known.max-artifact-size=10485760
```

### skills.sh Source

Enable the skills.sh source:

```properties
nacos.plugin.ai.importer.skills.skills-sh.enabled=true
```

Default values:

| Property | Default |
|----------|---------|
| `source-id` | `skills-sh` |
| `display-name` | `skills.sh` |
| `endpoint` | `https://skills.sh` |
| `resource type` | `skill` |
| `importer` | `skills-sh` |

The importer uses `GET {endpoint}/api/search` to search candidates and
`GET {endpoint}/api/download/{owner}/{repo}/{skillId}` to fetch selected
artifacts. If the console search query is empty, the importer uses `skill` as
the default query. A one-character query is rejected before calling skills.sh.

Optional overrides:

```properties
nacos.plugin.ai.importer.skills.skills-sh.source-id=skills-sh
nacos.plugin.ai.importer.skills.skills-sh.display-name=skills.sh
nacos.plugin.ai.importer.skills.skills-sh.endpoint=https://skills.sh
nacos.plugin.ai.importer.skills.skills-sh.connect-timeout-ms=3000
nacos.plugin.ai.importer.skills.skills-sh.read-timeout-ms=10000
nacos.plugin.ai.importer.skills.skills-sh.max-artifact-size=10485760
```

## Custom Source Configuration

Operators may configure custom sources through
`nacos.ai.resource.import.sources[index].*`. Custom sources require the global
switch:

```properties
nacos.ai.resource.import.enabled=true
```

Example: a private Skill well-known registry.

```properties
nacos.ai.resource.import.enabled=true
nacos.ai.resource.import.sources[0].source-id=enterprise-skills
nacos.ai.resource.import.sources[0].display-name=Enterprise Skills
nacos.ai.resource.import.sources[0].plugin-name=skills-well-known
nacos.ai.resource.import.sources[0].resource-types=skill
nacos.ai.resource.import.sources[0].endpoint=https://skills.example.com
nacos.ai.resource.import.sources[0].connect-timeout-ms=3000
nacos.ai.resource.import.sources[0].read-timeout-ms=10000
nacos.ai.resource.import.sources[0].max-page-count=20
nacos.ai.resource.import.sources[0].max-item-count=500
nacos.ai.resource.import.sources[0].max-artifact-size=10485760
```

Example: a private MCP registry that uses the MCP registry importer.

```properties
nacos.ai.resource.import.enabled=true
nacos.ai.resource.import.sources[1].source-id=enterprise-mcp
nacos.ai.resource.import.sources[1].display-name=Enterprise MCP Registry
nacos.ai.resource.import.sources[1].plugin-name=mcp-registry
nacos.ai.resource.import.sources[1].resource-types=mcp
nacos.ai.resource.import.sources[1].endpoint=https://mcp-registry.example.com/v0/servers
```

Preset sources do not require `nacos.ai.resource.import.enabled=true`; enabling
their own `nacos.plugin.ai.importer.*.enabled` switch is enough.

## Security Options

External sources are treated as untrusted by default.

Default behavior:

- HTTPS is required.
- Localhost, loopback, link-local, multicast, and private network targets are
  rejected.
- Derived artifact URLs are checked with the same network policy as the source.
- Redirects must not bypass the same network policy.
- Connect timeout, read timeout, response size, page count, item count, and
  artifact size are bounded by source configuration.

Preset source opt-in options:

```properties
nacos.plugin.ai.importer.<preset>.allow-http=true
nacos.plugin.ai.importer.<preset>.allow-private-network=true
```

For example, a controlled test environment may allow an internal well-known
Skill source:

```properties
nacos.plugin.ai.importer.skills.well-known.enabled=true
nacos.plugin.ai.importer.skills.well-known.url=http://127.0.0.1:8088
nacos.plugin.ai.importer.skills.well-known.allow-http=true
nacos.plugin.ai.importer.skills.well-known.allow-private-network=true
```

Custom source opt-in options are configured through `properties.*`:

```properties
nacos.ai.resource.import.sources[0].properties.allow-http=true
nacos.ai.resource.import.sources[0].properties.allow-private-network=true
```

Only enable these options for trusted development or private deployment
environments.

## Legacy MCP Registry Import API

The unified import API is the default path for the new console. Legacy MCP
registry import endpoints are disabled by default:

```text
POST /v3/console/ai/mcp/import/validate
POST /v3/console/ai/mcp/import/execute
```

Operators may reopen them temporarily during a migration window:

```properties
nacos.ai.resource.import.legacy-mcp-api-enabled=true
```

Legacy direct URL import remains disabled even when the legacy endpoints are
enabled. It should only be reopened in controlled deployments:

```properties
nacos.ai.resource.import.legacy-mcp-api-enabled=true
nacos.ai.resource.import.allow-user-url=true
```

Prefer configuring a source and using the unified console import flow instead
of allowing user-provided registry URLs.

## Console Verification

After changing configuration, restart Nacos and open the new console import
dialog.

Expected behavior:

- MCP import lists enabled `mcp` sources such as `mcp-official`.
- Skill import lists enabled `skill` sources such as `skills-well-known` or
  `skills-sh`.
- Search shows candidate name, description, version, and safe metadata only.
- The conflict policy is shown as `Skip` or `Overwrite`.
- Browser responses do not include MCP tools, Skill archive content, or secret
  values.
