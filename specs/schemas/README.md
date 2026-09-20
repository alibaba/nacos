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

Public protocol and management schemas keep only the current contract at stable
paths without semantic-version directories:

```text
ai/<domain>/<schema-name>.schema.json
ai/<domain>/<binding>/<schema-name>.schema.json
```

Public schemas use logical `https://nacos.io/schemas/...` identifiers matching
these paths and declare their lifecycle through `x-status`. Protocol/schema
version metadata remains in the documents. A `0.x` schema marked
`experimental` is available for implementation and interoperability testing,
but is not yet a stable compatibility promise.

Public schema revisions follow these rules:

- breaking and additive contract changes update the semantic-version metadata,
  the affected schema files, and their companion specifications together;
- keep one current definition per public schema in the working tree;
- retain historical definitions through Git tags/commits instead of duplicate
  version directories; and
- editorial clarifications do not require a contract version change.

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

## Revision pinning

A versionless public `$id` identifies the current contract; it does not pin
historical content. For reproducible validation, resolve the complete schema
set from one pinned Git tag or commit, including all referenced schemas.
Do not mix files from different revisions or fetch a moving current schema
when validating against a historical contract. Tests load schemas directly
from the checked-out repository.

Internal version directories continue to identify storage formats and retain
the explicit reader/migration rules described above.

## Bundle and entry-point convention

Schema files in this directory are definition bundles. Their top level does
not use a broad `oneOf` to guess the message type. Instead, `x-entrypoints`
maps each supported object name to an explicit `$defs` reference. Validators
must select the expected entry point, for example:

```text
https://nacos.io/schemas/ai/rad/rad-protocol.schema.json#/$defs/AgentDiscoveryRequest
```

This keeps validation deterministic and prevents one transport message from
being accepted accidentally where another was expected.

## Current schemas

Starting with `0.3.0`, RAD, its Watch binding, Agent management, and Agent
Artifact share one public contract release version. Their cross-schema
references select the same release from one Git revision. Historical public
schema revisions are retained in Git; Artifact payload `schemaVersion: "1.0"`
and internal storage `v1` are separate version domains.

| Schema | Version | Status | Purpose |
| --- | --- | --- | --- |
| `ai/rad/rad-protocol.schema.json` | `0.5.0` | Experimental | Discover adds current Agent description/tags; Watch compares tags as a set. |
| `ai/rad/watch/rad-watch-binding.schema.json` | `0.5.0` | Experimental | Watch binding references RAD 0.5.0 and permits absent optional references as null. |
| `ai/agent/agent-management.schema.json` | `0.5.0` | Experimental | Annotation-independent Agent management models and Endpoint defaults. |
| `ai/agent/agent-artifact.schema.json` | `0.5.0` | Experimental | Artifact references management 0.5.0; payload schemaVersion remains 1.0. |
| `ai/agent/internal/v1/agent-storage.schema.json` | `1` | Internal experimental | Agent resource extension, version content and storage pointer, Naming projection, codecs, composers, and digest contracts. |
| `ai/mcp/internal/v1/mcp-resource-ext.schema.json` | `1` | Internal experimental | MCP Resource extension containing the deprecated physical-storage and legacy-API UUID alias. |
| `ai/mcp/internal/v1/mcp-version-storage.schema.json` | `1` | Internal experimental | MCP Version storage pointers to unchanged Server, Tools, and Resources Config objects. |
