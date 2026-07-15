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

# Compatibility And Deprecation Spec

This document defines the shared compatibility and deprecation rules for Nacos
APIs, SDKs, storage fields, plugins, adapters, and experimental capabilities.
It complements the [Nacos Design Spec](nacos-design-spec.md), the
[Resource Model Spec](resource-model-spec.md), and the public interface specs.

## 1. Scope

This spec owns:

- how a historical behavior is classified as canonical, compatibility-only,
  deprecated, pending removal, experimental, or removed;
- documentation requirements for deprecated or compatibility-only behavior;
- migration expectations for APIs, SDK interfaces, storage fields, and plugin
  extension points;
- ability-gated fallback removal rules.

It does not set a fixed release calendar. Each deprecation still needs domain
maintainer review and release-note communication.

## 2. Compatibility States

| State | Meaning | New development rule |
| --- | --- | --- |
| Canonical | Current behavior defined by specs and intended for new use. | New code and docs should use it. |
| Compatibility-only | Retained to avoid breaking existing users, but not the target model. | Do not extend it except for bug fixes and migration support. |
| Deprecated | Still available, but users should migrate to a replacement. | Document replacement and migration guidance. |
| Pending removal | Deprecated behavior whose removal conditions are known. | Keep only necessary compatibility tests and migration guidance. |
| Experimental | Not yet promised as stable behavior. | Incompatible changes or removal may be allowed with clear notes. |
| Removed | No longer supported by the current version. | Specs should describe only migration history when needed. |

New specs must identify when a behavior is not canonical. Historical presence
in code, database schema, configuration, or docs is not enough to make behavior
canonical.

## 3. Documentation Rules

Deprecated or compatibility-only behavior must not be silently removed from
documentation while the implementation still supports it. It should be
documented in a compatibility or deprecation section that includes:

- current state;
- replacement API, field, SDK method, or plugin model;
- migration guidance;
- compatibility risks, including auth, visibility, or response-shape
  differences;
- removal conditions when known.

User-facing main flows should describe canonical behavior first. Compatibility
sections should be clearly secondary.

## 4. API And SDK Rules

Open APIs carry the strongest long-term compatibility expectations. Admin,
Console, Maintainer SDK, and plugin-provided APIs may evolve faster, but
incompatible changes still need migration guidance when users may rely on the
documented behavior.

Deprecated endpoints should remain in compatibility sections instead of being
presented as primary APIs. New API definitions must not copy legacy shapes only
because they already exist.

SDKs should preserve binary compatibility for deprecated public methods when
reasonable, especially in Java client/API/plugin modules. New SDK features
should guide users to canonical interfaces and should not expand deprecated
write or broad-query surfaces.

## 5. Ability-Gated Fallback

Ability negotiation is the preferred mixed-version mechanism. A fallback is
allowed only when the owning domain spec documents:

- the ability key or condition that gates canonical behavior;
- the exact fallback behavior;
- whether the fallback changes response shape, consistency, security, or
  performance;
- when the fallback can be removed.

Fallback removal should wait until the minimum supported server/client matrix
no longer needs the fallback, or until the community explicitly accepts the
incompatibility.

## 6. Storage And Schema Rules

Storage fields retained only for compatibility must be documented as
compatibility fields or pending-removal fields. They must not become new
domain semantics unless a later domain spec explicitly promotes them.

Schema cleanup should balance correctness with operational cost. A redundant
field may remain temporarily to avoid frequent schema changes for users, but
new specs, APIs, SDKs, and docs must not build new behavior on that field.

## 7. Plugins And Adapters

Plugin SPI compatibility belongs to the plugin spec that owns the SPI. A plugin
may retain historical configuration keys or extension names as compatibility
aliases, but canonical plugin lookup and enablement should be documented
separately.

Adapters that expose external community protocols are compatibility surfaces,
not the canonical Nacos API model. They may intentionally follow external
response shapes or route conventions, but must be documented as adapter
behavior and should be opt-in when they introduce unauthenticated endpoints or
additional ports.

## 8. Current Known Compatibility Items

The following items are current compatibility or deprecation examples:

- v1/v2 HTTP APIs, which have been removed from the main server distribution
  and migrated to the external
  [nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter);
- pre-spec v3 compatibility endpoints;
- AI Prompt legacy endpoints and legacy Pipeline REST-style endpoints;
- Naming API-defined service selector fields and request parameters;
- Config aggregation fields and related database columns;
- historical plugin configuration keys;
- OIDC browser endpoints that remain under historical paths;
- Distributed Lock, which is experimental until promoted to stable.

This list is not exhaustive. Each domain spec remains responsible for exact
domain behavior and migration details.

For the Nacos 3.3 line, Config default-namespace storage migration between
legacy empty tenant values and `public`, and Config beta/tag old-table migration
to `config_info_gray`, are treated as removed compatibility behavior. Operators
that upgrade from versions before 3.0 must complete the affected data migration
before upgrading when they used the default namespace or beta gray release.

## 9. Legacy HTTP API Adapter

Starting with the Nacos 3.2.0 line, legacy v1 and v2 HTTP APIs are no longer
part of the default Nacos server distribution. They are a separate compatibility
surface provided by
[nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter).

Rules:

- v3 HTTP APIs and current SDKs are the canonical migration target.
- The legacy adapter is a temporary migration aid, not a renewed API contract.
- The adapter must be installed explicitly, such as by placing its jar in the
  Nacos `plugins` directory or adding it as a dependency for embedded/custom
  applications.
- The adapter version must match the target Nacos server version.
- The adapter is not guaranteed to be supported by future Nacos versions and is
  not the place to define new v1/v2 behavior.

Domain specs should mention legacy v1/v2 behavior only as migration context or
when a current compatibility path depends on it.

## 10. Related Specs

- [HTTP API Spec](../http-api/api-spec.md)
- [V3 API Surface](../http-api/v3-api-surface.md)
- [SDK Spec](../sdk/sdk-spec.md)
- [Client Ability Negotiation Spec](../client/client-ability-negotiation-spec.md)
- [Resource Model Spec](resource-model-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Integration And Adapter Spec](../integration/integration-adapter-spec.md)
- [Plugin Specs](../plugin/README.md)
