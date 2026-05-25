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

# Issue 15183 AI Resource Import Development Plan

Issue: https://github.com/alibaba/nacos/issues/15183

Note: this was the initial English planning draft. The latest landing plan is
`implementation-plan-zh-cn.md`; when the two documents differ, follow the
newer landing plan.

## 1. Goal

Support importing AI resources, initially MCP Server and Skill, from
operator-configured external registries or marketplaces. The import flow should
be pluggable, storage-independent, and safe by default.

The feature must solve three concrete problems:

- Skill marketplaces do not share one registry protocol, so external protocol
  support must be implemented as plugins.
- Existing MCP registry import is coupled to an implementation that can accept
  user-provided URLs. That needs a safer source model controlled by operators.
- Skill import may reference MCP resources, so the design needs dependency
  validation and explicit dependency policy.

## 2. Non-Goals

- Do not complete the MCP `ai_resource` migration in this feature.
- Do not change the canonical Skill package model.
- Do not make `ai-registry-adaptor` a write/import surface.
- Do not allow arbitrary user-provided registry URLs, IP addresses, MCP
  endpoints, or credentials in the unified import APIs.
- Do not recursively import dependencies by default.

## 3. Design Principles

### 3.1 Import Plugins Are Conversion Boundaries

An `ai-resource-import` plugin owns only:

- external registry or marketplace protocol;
- source-specific pagination and query parameters;
- conversion from external records to Nacos import candidates;
- fetching selected external artifacts.

It does not own:

- Nacos resource identity;
- conflict handling;
- authorization or visibility;
- storage backend choice;
- draft/review/publish lifecycle;
- dependency application;
- trace and audit persistence.

### 3.2 Resource Operators Own Application

The AI Registry domain owns resource operators. A resource operator applies an
import artifact to the current resource implementation.

For MCP, the first implementation should call the current
`McpServerOperationService`, `McpServerValidationService`, and related endpoint,
tool, and resource services. This is intentionally allowed even though MCP is
still Config-backed.

When MCP later migrates to `ai_resource`, the MCP operator should be replaced or
rewired. Import plugins and unified APIs should not change.

For Skill, the operator should use existing Skill upload and draft lifecycle
services so that parsing, validation, storage, manifest, and pipeline behavior
remain consistent.

### 3.3 Unified APIs Own Routing

New Admin and Console APIs should be the primary surface. Existing MCP import
APIs remain as compatibility routes and call the same import manager.

### 3.4 Operator-Configured Sources

Users select `sourceId`. Operators configure the source endpoint, credentials,
timeouts, limits, and importer plugin. This avoids server-side requests to
untrusted user-supplied targets.

## 4. Proposed Modules

### 4.1 `plugin/ai`

Add the SPI and shared model for import plugins:

```text
com.alibaba.nacos.plugin.ai.importer.spi
  AiResourceImportServiceBuilder
  AiResourceImportService

com.alibaba.nacos.plugin.ai.importer.model
  AiResourceImportContext
  AiResourceImportCandidate
  AiResourceImportCandidatePage
  AiResourceImportArtifact
  AiResourceImportDependency
  AiResourceImportSource
  AiResourceImportPayloadKind
```

Expected SPI shape:

```java
public interface AiResourceImportServiceBuilder {

    String importerType();

    AiResourceImportService build(Map<String, String> properties);
}

public interface AiResourceImportService {

    String importerType();

    Set<String> supportedResourceTypes();

    AiResourceImportCandidatePage list(AiResourceImportContext context)
            throws NacosException;

    AiResourceImportArtifact fetch(AiResourceImportContext context,
            AiResourceImportCandidate candidate) throws NacosException;
}
```

The SPI model should avoid depending on MCP or Skill concrete classes. Concrete
payloads can be represented by `payloadKind` plus bytes or JSON.

### 4.2 `ai/import/source`

Owns source configuration:

```text
AiResourceImportSourceProperties
AiResourceImportSourceConfig
AiResourceImportSourceManager
AiResourceImportSourceValidator
```

Example properties:

```properties
nacos.ai.resource.import.sources[0].source-id=mcp-official
nacos.ai.resource.import.sources[0].plugin-name=mcp-registry
nacos.ai.resource.import.sources[0].resource-types=mcp
nacos.ai.resource.import.sources[0].endpoint=https://registry.modelcontextprotocol.io
nacos.ai.resource.import.sources[0].enabled=true
nacos.ai.resource.import.sources[0].max-page-count=20
nacos.ai.resource.import.sources[0].max-artifact-size=10485760
```

Source manager responsibilities:

- load and normalize sources;
- reject duplicate source ids;
- ensure plugin exists and supports configured resource types;
- hide secrets from API responses;
- provide an immutable source view for requests.

### 4.3 `ai/import/core`

Owns orchestration:

```text
AiResourceImportManager
AiResourceImportSearchService
AiResourceImportValidationService
AiResourceImportExecutionService
AiResourceImportSecurityGuard
AiResourceImportTraceService
```

Search flow:

```text
request
  -> validate namespace/resourceType/sourceId
  -> resolve source
  -> resolve importer
  -> importer.search(context)
  -> return candidate summaries
```

Validate flow:

```text
request
  -> validate namespace/resourceType/sourceId/selected items
  -> resolve source and importer
  -> for each selected item:
       importer.fetch(context, item)
       security guard validates artifact size and type
       resourceOperator.validate(artifact)
       dependency validation
  -> return validation response without artifact payload
```

Execute flow:

```text
request
  -> validate namespace/resourceType/sourceId/selected items
  -> resolve source and importer
  -> for each selected item:
       importer.fetch(context, item)
       security guard validates artifact size and type
       resourceOperator.validate(artifact)
       resourceOperator.importResource(artifact, policy)
       trace per-item result
  -> return aggregate result
```

### 4.4 `ai/import/operator`

Owns resource-specific application:

```text
AiResourceOperator
AiResourceOperatorRegistry
McpResourceOperator
SkillResourceOperator
```

Suggested operator interface:

```java
public interface AiResourceOperator {

    String resourceType();

    AiResourceImportValidationItem validate(String namespaceId,
            AiResourceImportArtifact artifact, ImportPolicy policy)
            throws NacosException;

    AiResourceImportItemResult importResource(String namespaceId,
            AiResourceImportArtifact artifact, ImportPolicy policy)
            throws NacosException;
}
```

`McpResourceOperator` initial behavior:

- convert `MCP_DETAIL` artifact to `McpServerDetailInfo`;
- call `McpServerValidationService` for validation;
- detect existing MCP by namespace and name through current MCP index/service;
- call `McpServerOperationService.createMcpServer` or `updateMcpServer`;
- keep endpoint, tool, and resource handling inside MCP operation services.

`SkillResourceOperator` initial behavior:

- require `SKILL_ZIP` artifact or artifact convertible to a standard Skill zip;
- call `SkillOperationService.uploadSkillFromZip`;
- default to draft creation or overwrite behavior based on request policy;
- validate MCP dependencies and return warnings by default.

### 4.5 `ai/import/compat`

Owns legacy routes:

```text
McpLegacyImportAdapter
McpLegacyImportTypeMapper
```

Mapping:

| Legacy input | New behavior |
|--------------|--------------|
| `importType=url`, `data=<sourceId>` | Route to source `<sourceId>`. |
| `importType=url`, `data=<url>` | Reject by default; optionally allow only with explicit operator opt-in. |
| `importType=json` | Route to built-in local MCP JSON importer. |
| `importType=file` | Route to built-in local MCP seed importer. |

`/importToolsFromMcp` should be deprecated. If still needed, it should use a
configured source and a selected candidate rather than user-provided `baseUrl`
and `endpoint`.

### 4.6 Controllers

Add:

```text
AiResourceImportAdminController
AiResourceImportConsoleController
```

Admin paths:

```text
GET  /v3/admin/ai/import/sources
POST /v3/admin/ai/import/search
POST /v3/admin/ai/import/validate
POST /v3/admin/ai/import/execute
```

Console paths:

```text
GET  /v3/console/ai/import/sources
POST /v3/console/ai/import/search
POST /v3/console/ai/import/validate
POST /v3/console/ai/import/execute
```

All endpoints must use `Result<T>` and `@Secured` with `SignType.AI`.

## 5. Request And Response Model

### 5.1 Source List

Request:

```text
resourceType optional
```

Response:

```text
sourceId
pluginName
resourceTypes
enabled
displayName
description
capabilities
```

Do not return endpoint credentials, secret headers, or raw token values.

### 5.2 Search

Request:

```text
namespaceId
resourceType
sourceId
query
cursor
limit
options
```

Response:

```text
sourceId
resourceType
nextCursor
items[]
  externalId
  name
  version
  description
  metadata
```

### 5.3 Validate

Request:

```text
namespaceId
resourceType
sourceId
selectedItems[]
dependencyPolicy
overwriteExisting
options
```

Response:

```text
sourceId
resourceType
items[]
  externalId
  name
  version
  status: valid | warning | invalid | conflict
  exists
  conflictType
  warnings[]
  errors[]
  dependencies[]
```

### 5.4 Execute

Request:

```text
namespaceId
resourceType
sourceId
selectedItems[]
dependencyPolicy
overwriteExisting
skipInvalid
options
```

Response:

```text
success
totalCount
successCount
failedCount
skippedCount
results[]
  externalId
  resourceName
  version
  status: success | failed | skipped
  errorMessage
  warnings[]
```

## 6. Dependency Policy

Initial policies:

| Policy | Behavior |
|--------|----------|
| `IGNORE` | Preserve dependency metadata only. |
| `VALIDATE_ONLY` | Check whether matching resource exists; return warning if missing. |
| `LINK_EXISTING` | Link existing matching resources when supported by the target type. |
| `IMPORT_SELECTED` | Import only dependency items explicitly selected in the request. |

Default: `VALIDATE_ONLY`.

Skill-to-MCP dependencies should be validated before import. The system should avoid
automatic recursive imports because dependency expansion may cross trust,
license, and authorization boundaries.

## 7. Security Plan

### 7.1 Source Control

- Unified import APIs accept `sourceId`, not URL or IP.
- Source endpoint and credentials are configured by operators.
- Source definitions should be available to Console only as sanitized views.

### 7.2 Network Guard

For HTTP-based importers:

- require HTTPS by default;
- set connect and read timeouts;
- cap response size and page count;
- disable redirects or revalidate redirected target;
- resolve DNS and block loopback, link-local, multicast, and private ranges by
  default;
- allow private network import only through explicit operator opt-in.

### 7.3 Artifact Guard

- enforce maximum artifact size;
- validate Skill zip limits before storage;
- do not execute Skill scripts during import;
- validate MCP remote endpoints as data, not as targets to probe, unless the
  source configuration explicitly enables controlled probing.

### 7.4 Audit

Trace or audit events should include:

- source id;
- importer type;
- resource type;
- selected item count;
- per-item result;
- operator identity;
- client address;
- sanitized source metadata.

## 8. Compatibility Plan

### 8.1 Existing MCP Import APIs

Keep routes for one compatibility window:

```text
POST /v3/console/ai/mcp/import/validate
POST /v3/console/ai/mcp/import/execute
```

Internally:

- convert legacy forms to unified validate or execute requests;
- map legacy response fields from unified results;
- log deprecation warnings for `importType=url` with direct URL input.

### 8.2 Remote Console Handler

Current remote Console MCP handler does not support MCP import. The unified API
should be exposed through maintainer client or Admin API so remote mode can use
the same server-side import manager.

### 8.3 `importToolsFromMcp`

This route should be marked deprecated. Replacement options:

1. Search MCP candidates from configured source and show tool summaries from
   registry metadata.
2. Add controlled endpoint probing as a source capability, enabled only by
   operator configuration.

## 9. Implementation Phases

### Phase 0: Spec And API Contract

- Add `ai-resource-import` plugin spec.
- Update plugin, AI Registry, MCP, and Skill specs.
- Add development plan under `specs/tmp/plan/issue-15183`.
- Confirm naming: plugin type `ai-resource-import`, enum
  `AI_RESOURCE_IMPORT`.

### Phase 1: Core SPI And Source Manager

- Add `PluginType.AI_RESOURCE_IMPORT`.
- Add import SPI and model classes in `plugin/ai`.
- Add source property model in `ai`.
- Add importer loading and source validation.
- Add unit tests for duplicate source id, unsupported resource type, missing
  plugin, and secret sanitization.

### Phase 2: Unified Import Manager And API

- Add `AiResourceImportManager`.
- Add source, search, validate, and execute controllers for Admin and Console.
- Add forms and request param extractors.
- Add operator registry and common result model.
- Add trace hooks for search, validate, and execute.
- Add controller tests and manager tests.

### Phase 3: MCP Import Migration

- Split current `McpExternalDataAdaptor` into a default `mcp-registry`
  importer.
- Add built-in local MCP JSON and seed importers for legacy `json` and `file`
  modes.
- Add `McpResourceOperator` around current MCP services.
- Route legacy MCP validate and execute APIs to the unified manager.
- Deprecate direct URL import by default.
- Add compatibility tests for legacy response mapping.

### Phase 4: Skill Import

- Add `skills-well-known` or compatible Skill registry importer.
- Add `SkillResourceOperator`.
- Convert external Skill records to standard Skill zip artifacts.
- Add dependency validation for Skill references to MCP resources.
- Add tests for Skill package validation, overwrite policy, and dependency
  policies.

### Phase 5: Security Hardening

- Implement source network guard.
- Add DNS and redirected target validation.
- Add artifact size and page count guards.
- Add tests for loopback, private address, redirect, oversized response,
  timeout, and oversized Skill zip cases.

### Phase 6: Maintainer SDK And Console

- Add maintainer-client methods for unified import APIs.
- Wire remote Console handler to server-side import APIs.
- Add Console UI source selection, candidate table, conflict display, dependency
  warnings, and execute result display.

## 10. Detailed TODO

### Specs

- [x] Add English `ai-resource-import` plugin spec.
- [x] Add Chinese `ai-resource-import` plugin spec.
- [x] Link the new plugin spec from plugin READMEs.
- [x] Update AI Registry spec with import responsibilities.
- [x] Update MCP spec with storage-independent import boundary.
- [x] Update Skill spec with external import and dependency behavior.

### API And Models

- [ ] Add common import request and response models under API module if exposed
      to maintainer-client.
- [ ] Add server-side form classes for Admin and Console endpoints.
- [ ] Define `sourceId`, `resourceType`, `dependencyPolicy`,
      `overwriteExisting`, and `skipInvalid` validation rules.
- [ ] Define API errors for missing source, disabled source, unsupported
      resource type, and unsafe source target.

### Plugin SPI

- [ ] Add `AiResourceImportServiceBuilder`.
- [ ] Add `AiResourceImportService`.
- [ ] Add import context, candidate, page, artifact, dependency, and payload
      kind models.
- [ ] Add Nacos SPI service file support in default import plugin modules.

### Source Manager

- [ ] Add import source properties.
- [ ] Add source manager.
- [ ] Add source sanitizer for API responses.
- [ ] Add source validation at startup or first use.
- [ ] Add tests for invalid configuration.

### Import Core

- [ ] Add search service.
- [ ] Add validate service.
- [ ] Add execute service.
- [ ] Add operator registry.
- [ ] Add trace service.
- [ ] Add aggregate result builder.
- [ ] Add best-effort behavior for `skipInvalid`.

### MCP

- [ ] Convert `McpExternalDataAdaptor` into an importer.
- [ ] Add `McpResourceOperator`.
- [ ] Add legacy form adapter.
- [ ] Route legacy Console MCP import APIs.
- [ ] Add Admin unified import support for MCP.
- [ ] Update tests for URL source migration.

### Skill

- [ ] Add Skill registry importer.
- [ ] Add Skill artifact conversion to standard zip.
- [ ] Add `SkillResourceOperator`.
- [ ] Defer dependency validation for MCP references until resource dependency
      descriptors are defined.
- [ ] Add dependency policy tests when dependency descriptors are implemented.

### Security

- [ ] Add HTTP client factory for importers.
- [ ] Add endpoint safety validation.
- [ ] Add DNS resolution guard.
- [ ] Add redirect validation.
- [ ] Add response and artifact size limit checks.
- [ ] Add negative SSRF tests.

### Documentation

- [ ] Add user-facing operator configuration documentation after API stabilizes.
- [ ] Add deprecation note for legacy MCP direct URL import.
- [ ] Add migration notes for existing Console behavior.

## 11. Open Questions

- Should local `json` and `file` MCP imports be plugin implementations or
  built-in compatibility handlers?
- Should source configuration be purely environment/application properties in
  the first release, or should it support runtime plugin config updates?
- What metadata schema should Skill importers use to express MCP dependencies?
- Should controlled endpoint probing be part of MCP import, or should it remain
  a separate diagnostic feature?
- What is the deprecation window for legacy direct URL MCP import?

## 12. Acceptance Criteria

- Users can list operator-configured import sources.
- Users can search MCP registry candidates without providing a URL.
- Users can import selected MCP resources through the unified API.
- Existing MCP import APIs still work for compatible `sourceId`, `json`, and
  `file` cases.
- Direct user-provided URL import is rejected by default.
- Users can search and validate Skill candidates from a configured source.
- Skill dependency handling is documented as a future extension point until
  resource dependency descriptors are implemented.
- Import plugins do not depend on MCP Config storage or Skill storage internals.
- Unit tests cover routing, compatibility, and security guards.
