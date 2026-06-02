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

# Nacos HTTP API Spec

This document defines the common design model for Nacos HTTP APIs. It is the
entry point for API design rules. Detailed current surfaces, authorization rules,
and response rules are maintained in linked documents.

## 1. Design Motivation

Nacos uses gRPC as the primary client communication protocol for high-frequency
runtime traffic. HTTP APIs still exist because they serve different needs:

- language-neutral access for clients that cannot use the official SDK or gRPC;
- operational access for administrators and maintenance tools;
- web-console access for UI workflows;
- compatibility and migration paths for existing Nacos users;
- easy inspection, scripting, and integration with common HTTP infrastructure.

The HTTP API design therefore separates audiences before it separates resources.
A client-facing API, an operator API, and a console API may operate on similar
domain objects, but they do not have the same compatibility promise, permission
model, or response expectations.

## 2. Design Principles

### 2.1 Audience First

Every HTTP API must first declare its audience:

| Audience | Path prefix | Intended caller |
| --- | --- | --- |
| Open API | `/v3/client` | SDKs and custom runtime clients |
| Admin API | `/v3/admin` | operators and maintainer tooling |
| Console API | `/v3/console` | [Nacos console UI](../console/console-spec.md) |
| Auth API | `/v3/auth` | plugin-provided auth APIs and bootstrap flows |

An endpoint should not be documented as an Open API only because it is reachable
over HTTP. Open APIs carry a stronger compatibility expectation than Admin or
Console APIs.

### 2.2 Stable Resource Shape

HTTP paths should follow this shape after the Nacos server context path:

```text
/v3/{audience}/{module}/{resource}[/{subResource}]
```

Current module names include:

| Module | Meaning |
| --- | --- |
| `core` | cluster, namespace, server state, plugin, and operations |
| `cs` | configuration service |
| `ns` | naming service |
| `ai` | MCP, A2A, Prompt, Skill, AgentSpec, and Pipeline |
| `auth` | user, role, and permission |
| `copilot` | console copilot features |

The deployment context path, usually `/nacos`, is outside the controller mapping.
User-facing examples may include it, but code-level path definitions should not.

### 2.3 HTTP Method Semantics

V3 HTTP APIs use methods according to operation semantics:

| Method | Meaning |
| --- | --- |
| `GET` | Query or download data. |
| `POST` | Create, publish, register, upload, submit, or trigger work. |
| `PUT` | Update existing state or set idempotent mutable state. |
| `DELETE` | Remove, deregister, or delete bindings and drafts. |

Any exception should be recorded in the endpoint-specific spec before it is
treated as intentional behavior.

### 2.4 Consistent Response Contract

JSON HTTP APIs should return `com.alibaba.nacos.api.model.v2.Result<T>` unless
there is a deliberate response-shape reason not to. Downloads, streaming APIs,
and health probes are common exceptions.

Detailed response and error rules are defined in
[Response And Error Spec](response-error-spec.md).

### 2.5 Explicit Authorization

HTTP APIs should declare authorization through `@Secured` unless the endpoint is
explicitly public, bootstrap-only, or health-oriented. Authorization must reflect
the API audience, resource domain, and action.

Detailed rules are defined in [Authorization Spec](authorization-spec.md).
The shared HTTP filter and runtime request context model is defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).

### 2.6 Compatibility Is Part Of The API

Open APIs must be reviewed as long-lived compatibility surfaces. Admin and Console
APIs may evolve faster, but incompatible changes still need deprecation notes or
migration guidance when documented users can depend on them.

Deprecated endpoints should stay documented in a compatibility section instead of
being silently removed from docs while code still supports them.

### 2.7 Documentation Follows Spec

User-facing documentation should be generated from, or manually checked against,
the spec and implementation. When code and documentation differ, the difference
must be classified before it is resolved:

- `Normative Spec`: behavior Nacos intentionally promises.
- `Current Behavior`: behavior currently implemented but not yet confirmed as a
  long-term contract.
- `Spec Decision Required`: behavior that must not be treated as promised until
  the spec is updated.

### 2.8 Agent Guidance And Automated Validation

Agent guidance files, AI skills, controller templates, and API compliance
checkers should treat this spec family as their source of truth.

They may keep short implementation checklists for local context, but they must
not define conflicting API rules. If an agent guide, template, checker, website
document, or implementation disagrees with the spec, the disagreement should be
resolved by updating the incorrect artifact or by explicitly updating the spec.

Automated validation should map findings to concrete spec rules, including:

- audience and path prefix;
- module and resource naming;
- HTTP method semantics;
- `Result<T>` response shape and documented exceptions;
- `@Secured` declaration, action, sign type, and API type;
- `@Since` declaration on newly added controller methods;
- deprecated compatibility endpoints and their migration status.

## 3. Current V3 Documents

The current v3 HTTP API surface is recorded in [V3 API Surface](v3-api-surface.md).

Additional detail specs:

- [Authorization Spec](authorization-spec.md)
- [Response And Error Spec](response-error-spec.md)
- [Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md)

## 4. Rules For Adding Or Changing HTTP APIs

1. Pick the audience first: Open, Admin, Console, or Auth.
2. Choose the module and resource path using the stable path shape.
3. Use HTTP methods according to section 2.3.
4. Declare authorization and action semantics.
5. Add `@Since` to newly added controller methods to declare the first Nacos
   version that supports the API.
6. Use `Result<T>` for JSON responses unless a documented exception applies.
7. Put validation in a form object or dedicated validator.
8. Add or update API integration tests according to the
   [API Integration Test Spec](../testing/api-integration-test-spec.md),
   including route, validation, auth, response-shape, and scenario coverage for
   meaningful changes.
9. Update the matching spec and website documentation in the same change.

New Open APIs require an explicit compatibility note. New Admin or Console APIs
require an explicit authorization note. New non-`Result<T>` APIs require an
explicit response-shape note.
