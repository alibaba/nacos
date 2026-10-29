<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Default Auth and Visibility Account Matrix

`AiVisibilityAccountMatrixOpenApiITCase` is an external-client test against an
already running standalone server. It uses `auth:nacos` and `visibility:nacos`.
It does not modify production behavior or inject database fixtures.

This is an explicitly enabled diagnostic regression suite. Set
`nacos.test.visibility.matrix.enabled=true` to run it; ordinary CI leaves it disabled.
It currently reproduces four pre-existing PRIVATE MCP search failures and requires two
separate server configurations. Enabling the suite retains every assertion and fails on
those defects. Disabling it does not count its scenarios as passing coverage. The scope SPI
HTTP and SDK boundary tests remain part of the normal integration suites.

The [2026-10-28 execution report](AI_VISIBILITY_ACCOUNT_MATRIX_RESULTS.md) retains all 70
matrix combinations, per-check failures, fixture limitations and independent rerun outcomes.
The user-authorized, one-off database fixture experiment subsequently passed all seven
PUBLIC Prompt combinations (43 checks). The ordinary HTTP IT still skips those combinations;
the experiment does not add a scope-management API or an automatic SQL mutation to this test.

## Preconditions

- Client, Admin and Console authentication and the default permission cache remain enabled.
- The selected visibility plugin and its family switch remain enabled.
- Owner, unrelated user, read grantee and read/write grantee are separate non-admin identities.
  Each receives the same ordinary `*:*:ai/*` API permission so the resource-level visibility
  decision is observable. A separate no-API-permission identity tests the preceding RBAC gate.
- The administrator is not the resource owner. Resources are created by the owner through HTTP.
- Use a dedicated server, unique names and valid published online versions. Most fixtures use
  `public`; namespace-isolation checks create and remove their own namespaces.
  MCP must have reached `LIFECYCLE_MANAGED`; the ARD adaptor must be enabled.
- Run once with anonymous AI disabled and once with anonymous AI enabled. The latter requires
  the reserved anonymous identity and default read permission to finish initialization.
- Do not send explicit empty credentials when testing anonymous access. Empty and invalid
  credentials are separate rejection scenarios.

## Expected Matrix

Every row applies independently to Agent, MCP, Prompt, Skill and AgentSpec.
These are expected results, not a claim that every scenario currently passes.

| Identity | PRIVATE read | PRIVATE write | PUBLIC read | PUBLIC write |
| --- | --- | --- | --- | --- |
| Owner | Allow | Allow | Allow | Allow |
| Global administrator, not owner | Allow | Allow | Allow | Allow |
| Other user, no visibility grant | Deny | Deny | Allow | Deny |
| Other user, explicit `r` | Allow | Deny | Allow | Deny |
| Other user, explicit `rw` | Allow | Allow | Allow | Allow |
| Anonymous, opt-in endpoint and enabled switch | Deny | Deny | Allow | Deny |

This represents 60 account/scope/resource combinations. The anonymous-disabled run adds
10 combinations, all denying both read and write. Ordinary management endpoints continue
to reject anonymous callers even when the anonymous AI switch is enabled.

## Assertions and Boundaries

| Check | Evidence |
| --- | --- |
| Detail read | Correct target data for permitted callers; a controlled 404 hides non-visible resources. ARD authentication rejection is 401; Nacos HTTP authentication rejection is 403. |
| List/count/pages | Exact-name filtering, visible total and first/second page contents; forbidden resources do not contribute to totals. Anonymous callers use filtered ARD listing or the opted-in AgentSpec Admin list. |
| Search | Resource-specific Client Search for authenticated users and ARD Search for anonymous users; bounded waiting for asynchronous projection. AgentSpec Search is not opted in for anonymous access and must reject it. |
| Label write | A distinct label persists for allowed writers; denied writes return 403 and leave labels unchanged, verified by an administrator. |
| Delete | Each identity uses an independent resource copy with equivalent scope/grants. Allowed deletion is observed through an administrator; denied deletion preserves the resource. |
| B02/B03 | Non-opt-in entry denial plus explicit blank/invalid credential rejection, including PUBLIC resources. |
| B04/B05 | Revoke and `rw` to `r` downgrade converge while the permission cache stays enabled; read and write are checked separately. |
| B06 | PUBLIC/PRIVATE/PUBLIC scope transitions change unrelated-user access without changing owner access; original scope is restored. |
| B07 | A grant for one resource does not allow reading or writing an adjacent private resource, the same name under another resource type, or the same name/type in another namespace. |
| B08 | A read/write grantee cannot grant or revoke another user's resource access. |
| B09 | A visibility `rw` grant without ordinary API permission cannot bypass the front-door RBAC gate. |
| B10 | Mix caller-owned PRIVATE, granted PRIVATE, foreign PUBLIC and hidden PRIVATE resources. Traverse pages of size one, assert visible totals, no duplicates and an empty final page, then check name filtering and the intersection of owner/scope filters with visibility where supported. Prompt lacks PUBLIC fixtures; Prompt and MCP list contracts do not expose owner/scope filters. |

Every failed check is retained while independent checks continue. JUnit still fails the
scenario if any check fails; failures are not converted into skips or passing assertions.
Set `nacos.test.visibility.matrix.report` to append structured JSONL evidence, including
per-check and per-account-row results. Do not enable parallel execution against shared fixtures.

## Coverage Limits

- The current Prompt HTTP contract has no scope creation/update field or scope-update route.
  With the default plugin it creates PRIVATE resources, so PUBLIC Prompt combinations are
  explicitly blocked rather than fabricated through SQL or a replacement plugin. Prompt
  management responses also do not expose owner/scope; owner-only grant management exercises
  the creator identity. The proposed matrix therefore has a public-fixture gap.
- The account matrix directly checks labels and deletion as write paths. It does not claim
  exhaustive account-by-account coverage of draft editing, reviewed publication, forced
  publication, online/offline transitions, runtime endpoint ownership or every Console route.
  Those operations retain their independent lifecycle and API-permission contracts.
- Search projection failures must be reported separately from successful detail/label checks.
  Existing disabled search tests are not evidence that this matrix passes.
- Anonymous read permission is exercised through an endpoint that actually opts in: ARD
  artifacts for Agent/MCP/Prompt/Skill and Client download for AgentSpec. Agent artifacts
  include the published content digest and representation; MCP artifacts use the compatibility
  UUID plus canonical name. ARD does not serve AgentSpec artifacts. No scope makes a
  non-opted-in endpoint anonymous-readable.
- Resource write permission does not authorize grant/revoke management or bypass a special
  operation's additional permission checks.

## Running

Build/start the standalone distribution externally with the normal deployment secret and
server identity configuration. Do not disable authentication or its cache.

```bash
mvn -B -pl test/openapi-test verify -Pintegration-test -DskipTests=false \
  -Dnacos.test.visibility.matrix.enabled=true \
  -Dnacos.test.auth.enabled=true \
  -Dnacos.test.auth.anonymous-ai.enabled=false \
  -Dit.test=AiVisibilityAccountMatrixOpenApiITCase
```

Then restart the dedicated server with `nacos.plugin.auth.nacos.anonymous.ai.enabled=true`,
wait for anonymous initialization, and repeat with the test property set to `true`.
Pass the administrator password through `NACOS_TEST_AUTH_ADMIN_PASSWORD` and use the normal
`nacos.port`, `nacos.console.port` and `nacos.ai.registry.port` overrides for isolated ports.
The test property describes server state; it does not configure the server itself.
For a focused rerun, set `nacos.test.visibility.matrix.only=agent/PRIVATE` (resource/scope)
or `nacos.test.visibility.matrix.only=boundary/agent` (mixed pagination and isolation).
Unselected invocations are skipped; they are not additional passing coverage.

These tests strengthen existing API surfaces and do not add counted surface rows to
`API_TEST_COVERAGE.md`.
