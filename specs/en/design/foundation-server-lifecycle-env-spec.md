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

# Nacos Server Lifecycle And Environment Configuration Spec

This document defines the foundation rules for Nacos process bootstrap,
startup phases, environment configuration, deployment mode, dynamic server
configuration, application context access, module state, and server state.

It complements the [Nacos Design Spec](nacos-design-spec.md),
[Foundation Capabilities Spec](foundation-capabilities-spec.md),
[Cluster Membership Spec](foundation-cluster-membership-spec.md), and
[Observability Hooks Spec](foundation-observability-hooks-spec.md).

## 1. Positioning

The lifecycle and environment layer makes a Nacos process usable before domain
modules start serving traffic. It prepares the runtime environment, selects
deployment mode, initializes Spring contexts, loads pre-properties, exposes
shared environment helpers, and reports module/server state.

This layer must not own domain resource semantics. It decides how the process
starts and how shared runtime configuration is read; Config, Naming, AI,
security, and plugins decide how their resources behave.

## 2. Bootstrap And Deployment Type

`NacosBootstrap` is the process entry point for packaged Nacos server
deployments.

Bootstrap rules:

- `nacos.deployment.type` selects the deployment type before contexts are
  created. The default is `merged`.
- `merged` starts core, server web API, and console contexts in one process.
- `server` starts core and server web API contexts without console.
- `console` starts the console context as a standalone console process.
- The bootstrap must set `EnvUtil.deploymentType` before module state builders
  and conditional beans depend on deployment type.
- Unsupported deployment type values must fail fast.
- Child contexts must use the core context as parent when they depend on core
  beans and environment state.

`DeploymentType.SERVER_WITH_MCP` and the type name `serverWithMcp` are present
in code, but the current bootstrap parsing and switch do not start it as an
independent public deployment mode. It must not be documented as a supported
startup mode until bootstrap behavior and module state rules are completed.

## 3. Startup Phases

Nacos startup phases are represented by `NacosStartUp` implementations and
selected through `NacosStartUpManager`.

Startup phase rules:

- A phase must be started through `NacosStartUpManager.start(phase)` before
  Spring startup callbacks use the current phase.
- Built-in phase names are `core`, `web`, `console`, and `ai-registry`.
- `StartingApplicationListener` delegates Spring run events to the current
  `NacosStartUp`.
- `starting` creates phase-level startup tracking and periodic startup logs.
- `environmentPrepared` creates required work directories, injects the Spring
  environment, loads pre-properties, and initializes system properties.
- `contextPrepared` starts periodic startup logging.
- `contextLoaded` may run custom environment processing.
- `started` marks the phase started and logs startup result.
- `failed` must run started phases in reverse order so resources are closed in
  the opposite order from startup.

Core startup additionally creates `logs`, `conf`, and `data` directories,
injects `EnvUtil.environment`, loads `application.properties`, watches
configuration file changes, initializes `nacos.mode`, `nacos.function.mode`,
and `nacos.local.ip`, and sets `ApplicationUtils.started` after successful
startup.

## 4. Environment Model

`EnvUtil` is the shared environment facade for Nacos server code.

Environment rules:

- Server code should read Nacos runtime properties through `EnvUtil` instead
  of scattering direct `System.getProperty`, `System.getenv`, or raw Spring
  environment access.
- `EnvUtil.environment` must be injected before modules read ordinary
  configuration properties.
- `nacos.home` defaults to `${user.home}/nacos` when not explicitly set.
- `nacos.server.main.port` defaults to `8848`.
- `server.servlet.context-path` defaults to `/nacos`; a root context path is
  normalized to an empty string.
- Standalone mode is read from the standalone system property and activates the
  standalone Spring profile through `StandaloneProfileApplicationListener`.
- Function mode may be `config`, `naming`, `microservice`, `ai`, or absent.
  Absence means all applicable capabilities are enabled by the current
  deployment.
- Cluster member source may come from `conf/cluster.conf` or
  `nacos.member.list`.
- `EnvUtil.getAvailableProcessors` provides the process-wide processor sizing
  abstraction and must not return less than one.

Environment values are runtime configuration, not domain data. A domain may
use environment values to choose an implementation path, but must not make
environment keys part of the resource identity model unless the domain spec
explicitly says so.

## 5. Pre-properties And Dynamic Configuration

Core startup loads `application.properties` from the configured application
configuration resource into the `nacos_application_conf` property source.

Dynamic configuration rules:

- The application property source loaded during startup is a server
  configuration source, not a Config domain resource.
- Core startup watches the Nacos conf directory and reloads
  `application.properties` content when the file changes.
- Reloading publishes `ServerConfigChangeEvent` through `NotifyCenter`.
- Components that extend `AbstractDynamicConfig` must subscribe to
  `ServerConfigChangeEvent`, re-read values from `EnvUtil`, and keep the
  previous valid values when reload fails.
- Dynamic server configuration must be limited to runtime tunables that are
  explicitly safe to refresh. It must not silently change resource identity,
  storage schema, public API shape, or plugin type ownership.

## 6. Custom Environment Plugin

When `nacos.custom.environment.enabled` is true, `EnvUtil.customEnvironment`
uses `CustomEnvironmentPluginManager` to obtain custom property values and
adds them as the first property source.

Custom environment rules:

- Custom environment processing belongs to startup/environment preparation,
  not to domain request handling.
- A custom environment plugin may transform configured keys into runtime
  values, but must not mutate domain state or perform business writes.
- Because the custom property source is added first, custom values may override
  lower-priority property sources. Plugin implementations must document and
  constrain the keys they control.
- Custom environment processing must happen before components that rely on the
  overridden values start serving requests.

## 7. Application Context And Started State

`ApplicationUtils` is the shared application context holder and started-state
facade.

Application context rules:

- The first initialized Spring context is stored as the global context. When a
  child context is initialized with the previous context as parent, the first
  child context becomes the stored context.
- Shared bean lookup, event publication, resource lookup, and class loader
  access should go through `ApplicationUtils` only when constructor injection
  is not practical.
- `ApplicationUtils.started` is the process readiness gate for gRPC request
  acceptors. Requests that require a fully started server must reject traffic
  while it is false.
- Setting the started flag must be owned by lifecycle code, not by domain
  services.

## 8. Module State And Server State

`ModuleStateBuilder`, `ModuleStateHolder`, and server state services expose
operator-facing runtime state.

Module state rules:

- Module state builders are loaded through Nacos SPI.
- Builders may opt out through `isIgnore`.
- Builders must declare whether they match the current `DeploymentType`.
- Cacheable builders are built once. Non-cacheable builders may be rebuilt on
  state query.
- Module state is diagnostic and operational state. It must not contain
  secrets, full opaque Config content, or high-cardinality user data.
- Server state aggregation may combine local module state with remote server
  state in console or maintainer paths, but it remains an operational view.

The observable shape of health, readiness, metrics, and diagnostics is further
defined by the [Observability Hooks Spec](foundation-observability-hooks-spec.md).

## 9. Lifecycle Boundaries

- Lifecycle code prepares the process. It must not define Config, Naming, AI,
  or Auth resource semantics.
- Environment properties configure behavior, but domain specs decide whether a
  behavior is public, internal, compatible, or pending removal.
- Startup watchers and dynamic config events are local process mechanics.
  Cross-node consistency must be provided by persistence, AP consistency, CP
  consistency, or internal RPC when a domain requires it.
- Shutdown and startup failure handling must release common executors,
  `NotifyCenter`, file watchers, and Spring contexts in a controlled order.
- New components that need startup hooks should prefer `NacosStartUp`,
  Spring lifecycle callbacks, or module-specific lifecycle abstractions instead
  of ad hoc static initialization.

## 10. Pending Issues

- Deployment type name `serverWithMcp` exists in code but is not wired as an
  accepted branch in `NacosBootstrap`. Its formal process behavior should be
  specified before exposing it as a supported deployment type.
- Some modules still read selected values directly from system properties for
  historical compatibility. New server code should prefer `EnvUtil` unless a
  low-level JVM integration explicitly requires direct access.

## 11. Related Specs

- [Nacos Design Spec](nacos-design-spec.md)
- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Observability Hooks Spec](foundation-observability-hooks-spec.md)
- [Plugin Spec](../plugin/plugin-spec.md)
- [Environment Plugin Spec](../plugin/environment-plugin-spec.md)
