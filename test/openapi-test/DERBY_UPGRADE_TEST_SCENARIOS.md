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

# Embedded Derby upgrade scenarios

This opt-in external-client test covers a real on-disk Derby 10.14.2.0 to
10.16.1.1 transition between two prebuilt standalone Nacos distributions.
It strengthens existing API coverage without adding HTTP operations or changing
the API-surface totals in [API_TEST_COVERAGE.md](API_TEST_COVERAGE.md).
The scenario status records execution against the source baseline and packages
identified in the validation record below.

## Runner

Run on Linux (or inside WSL) with JDK 17, Maven, Bash, Python 3, OpenSSL, curl,
GNU tar and timeout available. Build both distributions beforehand; use the same
Nacos source revision with only the Derby dependency difference when isolating
the dependency upgrade. Different Nacos revisions add server/schema migration
variables and do not establish compatibility for every historical release.

```bash
bash test/scripts/derby-upgrade-it.sh \
  --old-dist /absolute/path/old-nacos.tar.gz \
  --new-dist /absolute/path/new-nacos.tar.gz \
  --report-dir /absolute/path/empty-report-directory
```

The runner accepts only local `.tar.gz` packages. Artifact metadata, including
nested executable-jar dependencies, must show Derby 10.14.2.0 in the old package
and Derby, derbyshared and derbytools 10.16.1.1 in the new package, with no mixed
Derby versions. Ports 8848, 8080, 9080 and derived ports 9848, 9849 and 7848 must
be free before each start. Do not run other OpenAPI ITs concurrently: this
runner clears the selected class's stale Failsafe files between phases.

Each run creates a private Linux `/tmp` directory, independent old/new homes
and an expected-state directory. It keeps the packaged default server settings
and built-in `nacos_config` AI storage, overriding only fixture credentials and
the fixed ports. Authentication stays enabled. The same generated token signing
secret, server identity and admin password are used across both homes. Only the
old server receives `auth-it-identity.sh bootstrap`; ordinary user, role and
permission fixtures belong to the Java seed phase. No `prepare` call, bootstrap,
fixture recreation, SQL import/export, `upgrade=true`, or copying old plugin
jars occurs on the new server.

The script invokes this command from the repository root for each phase:

```bash
mvn -B -pl test/openapi-test -Pintegration-test -DskipTests=false \
  -Dit.test=DerbyUpgradeOpenApiITCase -Dnacos.test.auth.enabled=true verify
```

`NACOS_DERBY_UPGRADE_PHASE` selects `seed`, `verify-upgrade`, `mutate`, or
`verify-restart`. `NACOS_DERBY_UPGRADE_STATE_DIR` points to the shared expected
state. The single Java test dispatches these phases; ordinary integration-test
runs without the opt-in phase skip it. Explicitly selecting
`-Dit.test=DerbyUpgradeOpenApiITCase` without a phase fails, as does an unknown
phase or a missing expected-state file during verification.
Each runner phase requires a fresh Failsafe XML result with at least one
executed test and zero failures/errors, so an all-skipped result fails the run.

## Scenario matrix

Two isolated namespaces contain resources with the same names. Each contains
text, JSON and Chinese/multiline configurations, including an updated record
and its history; a persistent service with two non-ephemeral instances; and
Skill, Prompt and Agent resources with two published versions and one draft.
Assertions compare instance addresses, weights, enabled flags and metadata;
Skill main and auxiliary ZIP files after extraction; Prompt templates and
variables; and Agent definitions with declared Endpoints. Each AI resource has
`latest=2.0.0` and `stable=1.0.0` before mutation. A retained ordinary user must
still log in, read its granted configuration and be denied other configurations
and the other namespace. Naming reads include unhealthy instances; transient
health and connection-owned runtime registrations are not recovery assertions.

| Phase / scenario | Expected behavior | Status |
| --- | --- | --- |
| Artifact guard | Reject missing/wrong/mixed Derby metadata before starting either server. | Verified |
| Process guard | Reject occupied ports; track exact PID and `-Dnacos.home`; bounded liveness/readiness and target API waits. | Verified |
| Seed on old Derby | Create namespace, config/history, auth user/role/grant and AI fixtures through HTTP; persist generated IDs, versions and content as expected state. | Verified |
| Offline transfer | Require graceful TERM exit of the old server; back up its complete data directory and copy into an empty new data directory. | Verified |
| Read-only upgrade verification | Start the new package on copied data and verify original records, public IDs, history, authorization and AI version/content state without recreating them. Prompt MD5 and Agent digest remain exact; Skill checks exact bytes and version/status because the public API does not expose SQL IDs or a digest. | Verified |
| New-version mutation | Change configuration and AI resources and verify returned/persisted state; Prompt mutation publishes the existing v3 draft before creating and publishing v4. | Verified |
| New-version restart verification | Gracefully stop/restart the same new home and prove original and mutated data remain; Prompt latest version is 4, online count 4, stable version 1, and original v3 content remains with online status. | Verified |
| Opt-in boundary | Normal IT run skips; invalid explicit phase and missing/invalid expected state fail; all-skipped runner phase cannot pass. | Verified |

The fixture class is `DerbyUpgradeOpenApiITCase`. This is an HTTP persistence
regression test, not SQL-level Derby compatibility certification. It does not
cover downgrade, hard upgrade, cluster rolling upgrades, external databases,
all historical Nacos schemas, or every API family. HTTP responses and fixture
assertions use bounded polling and limited fixture counts; Maven phases have a
900-second ceiling, startup has at most 240 readiness attempts, and graceful
shutdown has a 90-second ceiling. A forced KILL is cleanup only and fails the
run; it never qualifies the database for copying.

## Evidence

The report directory contains a summary with package SHA-256 hashes, Java and
Derby versions, per-phase Maven/Failsafe results, startup/server logs, and the
secret-free `expected.json` snapshot with original and current fixture state.
Optional `NACOS_DERBY_UPGRADE_OLD_COMMIT` and
`NACOS_DERBY_UPGRADE_NEW_SOURCE_ID` identify package source revisions.
`NACOS_DERBY_UPGRADE_JAVA_MEMORY` overrides the default
`-Xms512m -Xmx512m -Xmn128m` server memory setting.

Failure evidence remains in the report directory; generated passwords, signing
secret, server identity and JWTs are redacted before exit. Raw database backups
and credential-bearing configuration stay in the private temporary directory
and are removed during cleanup. Reports contain no reusable login credentials.
The runner stops only processes whose PID command line still contains the
exact expected Nacos home. It never invokes the distribution shutdown script
or signals unrelated Nacos installations.

## Validation record

The standalone scenario passed on 2026-10-19 under WSL Linux x86_64 with
OpenJDK 17.0.15. Both Nacos 3.3.0-RC distributions use source baseline
`1511b5a85f744acb0ad54e8213b1f4d82674799c`; the new distribution includes
the Derby dependency, packaging and runtime-metadata changes in this change set.

| Distribution | Derby artifacts | Package SHA-256 |
| --- | --- | --- |
| Old | `derby:10.14.2.0` | `a5ccc5882a9a94bcb32aa67de6cd766e3e792e0e3682469c90a08fe6a0873ed6` |
| New | `derby`, `derbyshared`, `derbytools`: `10.16.1.1` | `ba64091da5940e7bfa44339a8c30f0cffe709cb225d5b552a86d64530b0c4aaf` |

| Phase | Executed | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| `seed` | 1 | 0 | 0 | 0 |
| `verify-upgrade` | 1 | 0 | 0 | 0 |
| `mutate` | 1 | 0 | 0 | 0 |
| `verify-restart` | 1 | 0 | 0 | 0 |

The runner exited successfully with `result=passed`. Artifact validation
rejected an old package supplied as the new package, missing split artifacts
and mixed Derby versions. Directed checks confirmed occupied-port rejection,
exact PID/home ownership checks, report redaction, and rejection of missing,
empty, all-skipped or failed phase reports. No test-owned Nacos process remained
after completion.

The fixture reported one skipped test without explicit opt-in. Explicit
execution with a missing, empty or unknown phase, a missing state-directory
setting, a missing manifest or an unsupported manifest format each reported
one failure and zero skipped tests.
