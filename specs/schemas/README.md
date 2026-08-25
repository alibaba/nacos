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

# Nacos JSON Schemas

This directory contains machine-readable contracts that accompany the Nacos
specifications. All schemas use JSON Schema Draft 2020-12.

## Public schemas

Public protocol and management schemas use semantic-version directories:

```text
ai/<domain>/<MAJOR.MINOR.PATCH>/<schema-name>.schema.json
```

Public schemas use resolvable `https://nacos.io/schemas/...` identifiers and
declare their lifecycle through `x-status`. A `0.x` schema marked
`experimental` is available for implementation and interoperability testing,
but is not yet a stable compatibility promise.

Public schema versions follow these rules:

- a breaking contract change creates a new semantic version;
- an additive, backward-compatible contract change also creates a new version;
- an editorial clarification that does not alter validation belongs in the
  prose specification, not in an already published schema; and
- experimental status does not permit an existing version directory to be
  rewritten after publication.

## Internal schemas

Internal persistence and projection schemas use integer versions:

```text
ai/<domain>/internal/v<schemaVersion>/<schema-name>.schema.json
```

An internal serialized object carries the matching integer `schemaVersion`
when its definition includes that field. Internal schemas use a Nacos URN
rather than a public URL. Any change that affects stored bytes, physical-key
composition, parsing, canonicalization, or generated internal objects creates
the next integer version and requires an explicit reader or migration policy.

## Frozen-version rule

Every version directory is immutable once merged into a release branch.
Corrections that alter validation or serialization must be published under a
new directory. Implementations may support several versions concurrently, but
must select a schema explicitly and must never infer a newer contract from an
older directory name.

## Bundle and entry-point convention

Schema files in this directory are definition bundles. Their top level does
not use a broad `oneOf` to guess the message type. Instead, `x-entrypoints`
maps each supported object name to an explicit `$defs` reference. Validators
must select the expected entry point, for example:

```text
https://nacos.io/schemas/ai/rad/0.1.0/rad-protocol.schema.json#/$defs/AgentDiscoveryRequest
```

This keeps validation deterministic and prevents one transport message from
being accepted accidentally where another was expected.

## Current schemas

| Schema | Version | Status | Purpose |
| --- | --- | --- | --- |
| `ai/rad/0.1.0/rad-protocol.schema.json` | `0.1.0` | Experimental | RAD Search, Discover, Watch snapshot, and Runtime Endpoint publication objects. |
| `ai/agent/0.1.0/agent-management.schema.json` | `0.1.0` | Experimental | Public Agent management resources and bounded read views. |
| `ai/agent/0.2.0/agent-artifact.schema.json` | `0.2.0` | Experimental | Version-pinned protocol-neutral Agent artifact for ARD and other registry adaptors. |
| `ai/agent/internal/v1/agent-storage.schema.json` | `1` | Internal experimental | Agent resource extension, version content and storage pointer, Naming projection, codecs, composers, and digest contracts. |
| `ai/mcp/internal/v1/mcp-resource-ext.schema.json` | `1` | Internal experimental | MCP Resource extension containing the deprecated physical-storage and legacy-API UUID alias. |
| `ai/mcp/internal/v1/mcp-version-storage.schema.json` | `1` | Internal experimental | MCP Version storage pointers to unchanged Server, Tools, and Resources Config objects. |
