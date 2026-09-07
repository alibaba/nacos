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

# Default-Auth Migration Coverage Baseline

This file freezes the pre-migration coverage state for the
[Nacos 3.3 Client API default-auth design](README.md). It is a dated design
baseline, not the live coverage registry. Live status remains owned by the
scenario documents under `test/openapi-test`, `test/java-sdk-test`,
`test/maintainer-sdk-test`, and, until migration is complete, `test/auth-test`.

| Item | Baseline |
| --- | --- |
| Date | 2026-09-03 |
| Source commit | `208a317a406a065f857d40bb947b8a0a69c29def` |
| Secured Controller source files | 56 |
| Method-level `@Secured` declarations | 386 |
| Four-state representative Controller scenarios | 49 |
| Default-auth Controllers covered by exhaustive workflows | 4 |
| Conditional-anonymous Controller exclusions | 3 |

The three classifications account for every Controller source file containing
a method-level `@Secured` declaration: `49 + 4 + 3 = 56`. The current
source-completeness assertion is Controller-level. Stage 3 must replace or
extend it with an operation-level inventory that records the complete
authorization tuple and direct/equivalent coverage classification for all 386
declarations.

## HTTP API Scenario Baseline

Coverage is counted by documented API-surface rows. Strict coverage is
`Covered / total`; effective coverage is
`(Covered + Partial * 0.5) / total`.

| API surface | Rows | Covered | Partial | Pending | Strict | Effective |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Client OpenAPI | 14 | 13 | 1 | 0 | 92.86% | 96.43% |
| Admin API | 38 | 31 | 7 | 0 | 81.58% | 90.79% |
| Console API | 29 | 24 | 5 | 0 | 82.76% | 91.38% |
| Auth API | 4 | 0 | 2 | 2 | 0.00% | 25.00% |
| Total | 85 | 68 | 15 | 2 | 80.00% | 88.82% |

Migration rules:

- no existing `Covered` row may be downgraded because auth is enabled;
- every functional row must continue to assert business effects and response
  contracts with the correct identity;
- Auth API must reach 4/4 `Covered` before the product default changes;
- existing `Partial` gaps remain explicit until their missing public scenarios
  are implemented or their standalone limitation changes.

## Java SDK Scenario Baseline

| Registry | Rows | Covered | Partial | Pending | Strict | Effective |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Public Java Client SDK | 6 | 4 | 2 | 0 | 66.67% | 83.33% |
| Maintainer SDK | 6 | 4 | 2 | 0 | 66.67% | 83.33% |

The public Client SDK partial rows are Naming and the general AiService/A2aService
surface. The Maintainer SDK partial rows are Core and Agent. These statuses
contain pre-existing functional gaps and must not be conflated with the
auth-enabled gap. Every Maintainer row currently defers auth-enabled behavior;
that generic deferral must be replaced by tested behavior or a precise residual
gap during stages 4 and 5.

## Secured Controller And Method-Count Inventory

The count in the third column is the number of source lines beginning with
`@Secured` in the Controller at the baseline commit. `Representative` means the
current Auth IT executes one four-state request for the Controller, not that all
of its methods have direct authorization coverage.

| Module | Controller | Secured methods | Current auth classification |
| --- | --- | ---: | --- |
| ai-registry-adaptor | `ArdSearchController` | 5 | Conditional-anonymous exclusion |
| ai-registry-adaptor | `ArdWellKnownController` | 1 | Conditional-anonymous exclusion |
| ai | `A2aAdminController` | 6 | Representative |
| ai | `AgentAdminController` | 17 | Representative |
| ai | `AgentClientController` | 7 | Representative |
| ai | `AgentSpecAdminController` | 18 | Representative |
| ai | `AgentSpecClientController` | 2 | Representative |
| ai | `AiResourceImportAdminController` | 4 | Representative |
| ai | `AiResourceSearchClientController` | 1 | Representative |
| ai | `McpAdminController` | 17 | Representative |
| ai | `McpClientController` | 6 | Representative |
| ai | `PipelineAdminController` | 4 | Representative |
| ai | `PromptAdminController` | 24 | Representative |
| ai | `PromptClientController` | 2 | Representative |
| ai | `SkillAdminController` | 20 | Representative |
| ai | `SkillClientController` | 2 | Conditional-anonymous exclusion |
| config | `CapacityControllerV3` | 2 | Representative |
| config | `ConfigControllerV3` | 15 | Representative |
| config | `ConfigOpenApiController` | 1 | Representative |
| config | `ConfigOpsControllerV3` | 4 | Representative |
| config | `HistoryControllerV3` | 4 | Representative |
| config | `ListenerControllerV3` | 1 | Representative |
| config | `MetricsControllerV3` | 2 | Representative |
| console | `ConsoleA2aController` | 6 | Representative |
| console | `ConsoleAgentController` | 17 | Representative |
| console | `ConsoleAgentSpecController` | 17 | Representative |
| console | `ConsoleAiResourceImportController` | 4 | Representative |
| console | `ConsoleCopilotConfigController` | 2 | Representative |
| console | `ConsoleCopilotController` | 4 | Representative |
| console | `ConsoleMcpController` | 20 | Representative |
| console | `ConsolePipelineController` | 4 | Representative |
| console | `ConsolePromptController` | 18 | Representative |
| console | `ConsoleSkillController` | 20 | Representative |
| console | `ConsoleConfigController` | 13 | Representative |
| console | `ConsoleHistoryController` | 4 | Representative |
| console | `ConsoleClusterController` | 1 | Representative |
| console | `ConsoleNamespaceController` | 6 | Representative |
| console | `ConsolePluginController` | 5 | Representative |
| console | `ConsoleInstanceController` | 3 | Representative |
| console | `ConsoleServiceController` | 8 | Representative |
| core | `CoreOpsControllerV3` | 3 | Representative |
| core | `NacosClusterControllerV3` | 4 | Representative |
| core | `NamespaceControllerV3` | 6 | Representative |
| core | `PluginControllerV3` | 4 | Representative |
| core | `ServerLoaderControllerV3` | 5 | Representative |
| naming | `ClientControllerV3` | 7 | Representative |
| naming | `ClusterControllerV3` | 1 | Representative |
| naming | `HealthControllerV3` | 2 | Representative |
| naming | `InstanceControllerV3` | 8 | Representative |
| naming | `InstanceOpenApiController` | 3 | Representative |
| naming | `OperatorControllerV3` | 4 | Representative |
| naming | `ServiceControllerV3` | 7 | Representative |
| plugin-default-impl | `PermissionControllerV3` | 4 | Exhaustive Auth API workflow |
| plugin-default-impl | `RoleControllerV3` | 4 | Exhaustive Auth API workflow |
| plugin-default-impl | `UserControllerV3` | 5 | Exhaustive Auth API workflow |
| plugin-default-impl | `VisibilityGrantControllerV3` | 2 | Exhaustive Auth API workflow |
| **Total** | **56 Controllers** | **386** | **49 representative + 4 exhaustive + 3 exclusions** |

Module totals provide a second check against accidental omissions:

| Module | Controllers | Secured methods |
| --- | ---: | ---: |
| ai-registry-adaptor | 2 | 6 |
| ai | 14 | 130 |
| config | 7 | 29 |
| console | 17 | 152 |
| core | 5 | 22 |
| naming | 7 | 32 |
| plugin-default-impl | 4 | 15 |
| **Total** | **56** | **386** |

## Public And Conditional-Anonymous Baseline

The HTTP authorization spec explicitly lists these unauthenticated public
operations:

- `GET /v3/admin/core/state`
- `GET /v3/admin/core/state/liveness`
- `GET /v3/admin/core/state/readiness`
- `GET /v3/console/server/state`
- `GET /v3/console/server/announcement`
- `GET /v3/console/server/guide`
- `GET /v3/console/health/liveness`
- `GET /v3/console/health/readiness`
- `POST /v3/auth/user/login`
- `POST /v3/auth/user/admin`, only while no global administrator exists

`ArdSearchController`, `ArdWellKnownController`, and `SkillClientController`
contain secured methods whose selectable paths permit anonymous access. They
are not equivalent to globally unprotected methods. Stage 3 must directly test
their missing-credential success conditions and their explicit invalid-
credential rejection conditions according to the owning endpoint specs.

## Reproduction

The Controller and method totals were produced from production sources with:

```bash
grep -R -l --include='*Controller*.java' \
    '^[[:space:]]*@Secured' . \
    | grep '/src/main/java/' \
    | wc -l

grep -R -h --include='*Controller*.java' \
    '^[[:space:]]*@Secured' . \
    | wc -l
```

The `ModuleAuthorizationITCase` method source contains 49 scenario entries.
Its expected set additionally includes four default-auth Controllers and three
conditional-anonymous exclusions. Any future baseline update must recompute all
three values and update the live scenario registries in the same change.
