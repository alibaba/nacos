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

# Agentic Resource Hub

Meeting scope, 2026-10-29: Skill, MCP, Agent, Prompt and AgentSpec support Hub. Only Skill supports cloning.

Shared reads follow Skill's conditional `@GetMapping(params="namespaceId=__nacos_ai_hub__")`
binding and explicit `ONLY_IDENTITY`/anonymous-admission annotations, leaving ordinary methods
and the common authorization filter unchanged. Before delegation, shared bindings require the
bound Namespace to equal the reserved Hub Namespace exactly, rejecting repeated or conflicting
parameters with HTTP 400 / 20002. They must not rely only on the AI parser, which SPECIFIED
identity admission does not invoke. After identity validation the Hub service still checks the feature switch, pinned
source identity and online publication; ordinary namespaces, writes and invalid identities
retain their existing authorization. Client Agent
and AgentSpec keep native content DTOs; source navigation permissions belong to management/search
Hub metadata. Empty Agent metadata tags project to null in RAD catalogs, as ordinary discovery
does, without changing source metadata.

With the server Hub switch disabled, directories return empty pages and content and management
operations return HTTP 501 / `API_FUNCTION_DISABLED`, including cancellation and state queries.
This differs from a private/offline/disabled source: while Hub remains enabled, authorized users
may still revoke such a source's publication.

Apply the same reviewed invariants as Skill: import rollback removes target content before deleting
the draft version and disabled reservation. Failed cleanup retains the storage descriptor and
reservation for recovery. Source metadata/version system failures return sanitized 5xx with an
internal cause and fail the directory, rather than becoming empty or partial successful pages.
Ordinary writes, including A2A and Prompt compatibility operations, reject the reserved Hub
Namespace before storage or delegation; dedicated internal Hub persistence remains available.

Hub reads reuse ordinary resource routes with `namespaceId=__nacos_ai_hub__` and an opaque entry ID. Shares pin immutable source resource/version IDs and one explicit online version. Deleted, disabled, private (except Prompt), offline or revoked sources fail closed without latest/name fallback. Share/adjust require source READ and WRITE; Hub reads require Hub READ and publication validation. Governance uses separate administrator authority and audit.

MCP, Agent, Prompt and AgentSpec expose share, adjust, unshare, force-unshare, source-share, governance-state and import from Hub. They expose no direct source copy/clone operations. Prompt sharing adds no PUBLIC prerequisite or scope operation. Skill's cross-namespace copy button is named Clone and uses the clone API with the existing authorization rules.

Hub follows the existing source detail structure. MCP preserves ordinary service references, backend/frontend endpoints, paths, headers and client configuration. Do not add an instance detail table, extend ordinary MCP detail responses or add Naming queries to ordinary reads. Shared reads never create services or mutate source instances.

Agent runtime-endpoints resolves the pinned source version and ordinary Runtime Snapshot with endpoint state and bindings. No Hub publisher or synthetic Naming service is created.

Directory eligibility and permission filtering precede sorting and pagination. Unit/component checks, compilation and deployed integration outcomes must be recorded separately.


Each resource follows Skill with its own fixed-route Controller, StateController, Proxy, Handler and Service. No `{resourceType}` wildcard controller is exposed. Management SDK operations belong to the existing resource facade. Skill uses `POST /v3/{admin|console}/ai/hub/skills/clone`; the old `/copy` route is removed and the SDK method is `cloneHubSkill`.


Each non-Skill resource has a fixed-route ImportController and typed import request through its own Proxy, Handler, Service and SDK facade. Import creates an independent target draft with Hub-read and target-WRITE checks and never overwrites conflicts.

MCP Hub import creates an independent PRIVATE draft, never an online version or copied latest label. STDIO copies launch/package configuration without Naming writes. Ordinary REF retains the original namespace/group/service coordinates. A version-like service name alone does not establish Direct ownership.

A backend is source-owned Direct only when its namespace, mcp-endpoints group and source-name::pinned-version match and Naming metadata carries the MCP Direct marker. Restore the fixed address from exactly one persistent instance in the ordinary Direct cluster, including unhealthy/disabled instances. Reject missing, invalid or ambiguous fixed addresses; never select the first healthy endpoint or copy ephemeral instances, publishers, clients or heartbeats. Create a fresh target-namespace/target-name::target-version service and fixed-address instance; this does not deploy a new MCP application.

Preserve the rest of remoteServerConfig, protocols, paths, headers and frontend REF/DIRECT/BACKEND definitions. BACKEND resolves the target backend; frontend REF remains unchanged. Tools/Resources have independent storage. Hub sharing and live display continue reading the original service without Naming mutations.

Only materialize target Direct state after reserving the target resource name. Existing Naming coordinates, including orphan services, are conflicts and must never receive new/replacement instances. New services carry the ordinary MCP Direct marker and a unique import ownership token. Rollback removes only this import's owned service, fixed instance and content. Ownership changes or cleanup failures retain the disabled reservation for recovery. Revalidate pinned publication/permissions and the source fixed address around activation. Offline preserves target services; deleting the target removes only its own Direct state, never source/REF services. Revoking the share does not delete completed imports. REF availability still depends on the original service.

Hub detail preserves all existing read-only source content and interactions of the pinned version. Reuse the MCP detail body and Agent runtime endpoint card. Prompt retains template debug, variables, review results, business tags and pinned-version labels; AgentSpec retains AGENTS.md, resource browsing and version review information. Only Hub-specific provenance, download counts and mutation/governance permissions may differ. Do not expand ordinary source detail for Hub.

Agent Hub runtime snapshots retain the source namespace/name and Console links to that original Naming service. The requested Hub ID and pinned version still pass publication authorization checks.

Agent source and Hub share the Agent-specific three-column layout (two main columns and one sidebar), protocol card, and Agent metadata/custom-version-label sidebar. Hub must not substitute a generic basic-info/business-tags/version-label sidebar. Within the existing custom-label card, Hub renders read-only chips with label names, limited to the pinned shared version and excluding latest and other versions. Show an empty state when no custom labels match, without editing or Save. Source resources retain their JSON editor; Hub download counts belong in the provenance area.

All five types expose Open pinned source version only after independent source READ authorization and visibility checks, without requiring WRITE or administrator privileges. Hub READ alone grants no ordinary source access. Non-Skill hub metadata carries sourceReadable and exposes sourceNamespaceId/sourceName only when allowed. Navigation pins the shared version rather than latest. Denied/unavailable optional navigation hides the link without breaking valid Hub reads; ordinary source APIs reauthorize navigation.

MCP Hub must not add generic basic-info, business-tag or version-label sidebar cards absent from ordinary MCP detail. Reuse the source client-configuration, endpoint and governance components without appending a generic resource sidebar.

MCP Hub displays custom version labels in the right sidebar below source governance, sharing the Agent read-only custom-label card and styling rather than the header. Read labels from the pinned-version response, showing only those targeting the shared version and excluding latest and other versions. Use the Agent empty state when none match; do not treat missing label fields in serving detail as the absence of version labels.

Hub labels show names only, without appending target version numbers; detail headers omit the separate fixed-version badge. This presentation change preserves pinned-version reads, source navigation, downloads and imports and leaves ordinary source detail content unchanged.
