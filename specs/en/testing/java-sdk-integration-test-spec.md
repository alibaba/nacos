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

# Java SDK Integration Test Spec

This spec defines the integration-test model for Nacos Java SDK public
contracts. It complements the [API Integration Test Spec](api-integration-test-spec.md):
HTTP API ITs verify deployed HTTP contracts, while Java SDK ITs verify the
typed Java SDK behavior seen by applications.

The goal is SDK scenario coverage. It is not line coverage or branch coverage.

## 1. Scope

The primary Java SDK IT location is `test/java-sdk-test`. Tests in this module
assume a standalone Nacos server is already running and create real Java SDK
clients as external applications.

This spec applies when changing:

- public interfaces such as `ConfigService`, `NamingService`, `AiService`,
  `A2aService`, `LockService`, and their maintainer-client equivalents;
- public factories such as `NacosFactory`, `ConfigFactory`, `NamingFactory`,
  `AiFactory`, and `NacosLockFactory`;
- public request, response, or domain models returned by SDK methods;
- listener, subscription, local cache, redo, factory initialization, shutdown,
  or exception mapping behavior;
- SDK configuration keys and defaulting behavior.

Unit tests remain necessary for isolated implementation branches, but they do
not replace Java SDK ITs for externally visible SDK behavior.

## 2. SDK Change Rule

Before implementing a Java SDK contract addition, modification, deletion, or
deprecation, the change owner must perform an SDK IT impact analysis:

1. Identify the affected SDK interface, factory, model, or listener path.
2. Read the public API, implementation, validators, transport mapping, response
   assembly, exception mapping, lifecycle code, and matching SDK/client specs.
3. Build a scenario matrix for factory/lifecycle behavior, expected capability,
   boundary/validation behavior, listener or subscription behavior, and
   exception/error handling.
4. Add, update, or remove `test/java-sdk-test` cases in the same change set.
5. Update `test/java-sdk-test/JAVA_SDK_IT_COVERAGE.md`.

If the full success path is not practical in standalone IT, the test must still
cover SDK parameter validation, local boundary behavior, controlled exceptions,
and any low-risk observable server interaction. The skipped path and reason
must be documented.

## 3. Required Scenario Groups

Every Java SDK IT should cover these groups when observable.

### 3.1 Factory And Lifecycle

Verify that the SDK can be created through the public factory with realistic
properties, honors server address and namespace defaults, and releases
resources through the public shutdown method.

### 3.2 Expected Capability

Verify that SDK methods perform the promised remote or local behavior. Prefer
publish-then-query, register-then-query, subscribe-then-callback,
lock-then-unlock, release-then-load, and delete-then-absent flows.

Assertions must check typed SDK return values, model fields, callbacks, and
remote side effects instead of only checking that no exception was thrown.

### 3.3 Boundary And Validation

Cover required parameters, optional defaults, invalid enum or type values,
namespace and group defaults, timeout behavior, malformed model objects,
listener identity requirements, duplicate or idempotent calls, and missing
resource behavior.

### 3.4 Exception And Error Handling

Verify that SDK-visible failures produce controlled `NacosException` or
documented return values. Tests should catch regressions where invalid input,
not-found resources, remote failures, or invalid lifecycle use become
unexpected runtime exceptions.

### 3.5 Listener And Subscription Behavior

For listener APIs, verify initial query behavior when applicable, callback
delivery for an observable change, unsubscribe/remove behavior, and cleanup.
Use bounded waits and clear assertion messages.

## 4. Test Organization

Java SDK ITs should live under:

- `com.alibaba.nacos.test.sdk.config`
- `com.alibaba.nacos.test.sdk.naming`
- `com.alibaba.nacos.test.sdk.ai`
- `com.alibaba.nacos.test.sdk.lock`
- `com.alibaba.nacos.test.sdk.maintainer.<domain>` when maintainer SDK ITs are
  added

Prefer one public SDK interface, or one tightly coupled API family, per test
class. Shared client construction, cleanup, bounded waits, random resource
names, and shutdown handling should live in a base class.

## 5. Runtime Rules

Java SDK ITs must:

- use JUnit 5 and Failsafe;
- avoid `@SpringBootTest`, `SpringExtension`, and starting Nacos inside tests;
- read `nacos.host` and `nacos.port`, defaulting to `127.0.0.1:8848`;
- create real SDK clients through public factories;
- generate isolated resource names;
- cleanup created config, naming, AI, or lock resources;
- shut down every SDK instance even when assertions fail;
- use bounded retries for asynchronous server effects.

## 6. Scenario Documentation

Each SDK IT class must include a compact `Scenario coverage` Javadoc section,
or update `test/java-sdk-test/JAVA_SDK_IT_COVERAGE.md` when the matrix is large.
The documentation must say what is verified and why any branch is intentionally
not covered.

## 7. Validation

For Java SDK IT changes, run:

- `mvn -pl test/java-sdk-test spotless:check`
- `mvn -pl test/java-sdk-test -DskipTests test-compile`

When a standalone Nacos server is available, run the relevant Failsafe
selection or
`mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false
verify`.

Java SDK ITs intentionally use the dedicated `java-sdk-integration-test` Maven
profile. The generic `integration-test` profile is reserved for HTTP API IT
workflows and must not accidentally run SDK tests that depend on SDK gRPC
connection readiness or optional server abilities.
