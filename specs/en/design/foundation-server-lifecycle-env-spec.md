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
- During core `contextLoaded`, the pre-context plugin initializer must run
  before custom environment processing.
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
- A missing property must use the default owned by its domain spec consistently
  across configuration objects, plugin activation policies, and conditional
  beans. For example, the Nacos 3.3 auth spec defines a missing
  `nacos.core.auth.enabled` property as enabled, while an explicit `false`
  remains effective.
- Dynamic server configuration must be limited to runtime tunables that are
  explicitly safe to refresh. It must not silently change resource identity,
  storage schema, public API shape, or plugin type ownership.

File watching and `ServerConfigChangeEvent` delivery are node-local. A dynamic
setting that must have one cluster-wide effective value, including an auth
scope switch, must be applied and verified on every member by the deployment
or operational control plane.

## 6. Custom Environment Plugin

When `nacos.custom.environment.enabled` is true, `EnvUtil.customEnvironment`
uses `CustomEnvironmentPluginManager` to obtain custom property values and
adds them as the first property source.

Custom environment rules:

- Custom environment processing belongs to startup/environment preparation,
  not to domain request handling.
- Before processing begins, the pre-context plugin initializer loads enabled
  environment implementations, resolves `STATIC > DEFAULT`, applies each
  configurable implementation, and installs the same initialized instances in
  `CustomEnvironmentPluginManager`.
- The immutable initialization result is handed to the standard core plugin
  manager for inventory and detail queries. The SPI must not be loaded again.
- A custom environment plugin may transform configured keys into runtime
  values, but must not mutate domain state or perform business writes.
- Because the custom property source is added first, custom values may override
  lower-priority property sources. Plugin implementations must document and
  constrain the keys they control.
- Custom environment processing must happen before components that rely on the
  overridden values start serving requests.
- Pre-context configuration and implementation state are restart-only. Runtime
  plugin updates, persisted runtime overrides, local-only overrides, and later
  static refreshes must not alter the accepted startup result.

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

## 10. Experimental Multi-Context AOT

Maven `native` is an explicit Bootstrap build profile: the forked build prepares every selected
context, compiles generated Java, and includes directly generated bytecode and resources. Build
tools and test dependencies must not enter the application runtime. `release-native` packages this
Bootstrap executable for the build platform and requires `native`; ordinary builds and the JVM
launcher remain unchanged. Plugin implementations, drivers and their Native metadata must be on
the build classpath. Embedded Derby requires an explicit experimental runtime-class-loading opt-in.

AOT is an explicit build/runtime mode. An ordinary JVM without AOT must retain
existing bootstrap, conditional bean, datasource plugin and protocol adaptor
loading behavior, without requiring AOT artifacts or validating their manifest.

- Prepare core, web and console according to deployment type, and ai-registry
  when enabled, preserving the original parent relationships. Build-time
  processing must not refresh business services or mark the server ready.
- Generate separate initialization code per phase and select it through a
  versioned phase index. Missing or outdated indexes must fail explicitly.
- The generated initializers are selected by class name, so their reflection hints must be
  registered without a condition. Spring's own hints for them only apply once the application
  class is initialized, and the multi-context startup creates contexts from class literals and
  never initializes those classes.
- Classes that the AOT run generates directly as bytecode (CGLIB subclasses, class-based AOP
  proxies and their FastClass helpers) must be registered for reflection with their constructors,
  methods, fields and unsafe allocation. Spring's own hints cover only part of what its runtime
  uses on them: callback fields, callback setters and constructor-less proxy instantiation.
- Derby and disabled protocol adaptors must not be permanent AOT restrictions.
  Datasource selection retains the precedence of `nacos.plugin.datasource-dialect.type`
  over the legacy `spring.sql.init.platform`. External dialects, Mappers and JDBC
  drivers belong on the build classpath; enabled adaptor contexts are generated too.
- Record deployment, standalone/embedded storage, function mode, profiles,
  datasource and protocol switches, plus datasource/startup SPI provider sets.
  Validate after pre-context environment plugins and before refresh. Do not freeze
  ports, database URLs, credentials or log directories in this manifest.
- This structural manifest is not an exhaustive fingerprint of every conditional
  bean. Other bean-selection changes or plugin implementation changes still
  require regeneration; reports must state the actual validation coverage.
- Accept JVM AOT and Native Image separately. Native plugins/drivers must be
  included at image build time and additions require rebuilding. Loading arbitrary
  jars absent at image build time is not promised. Ordinary JVM plugin extension
  contracts remain unchanged.
- Embedded Derby compiles SQL into new classes at runtime. JVM AOT support does not imply
  compatibility with closed-world Native Image without runtime class definition. Document the
  tested GraalVM version and experimental flags separately; do not change ordinary JVM storage
  behavior to bypass this limitation. See the [AOT experiment](../../../bootstrap/README-AOT.md).
- Regression includes ordinary JVM (including default merged deployment), JVM
  AOT, external datasource plugins and separate protocol adaptor ports. Native
  compilation, process startup and API success are separate validation gates. Embedded-storage
  validation must also restart with existing data, covering checkpoint/log recovery and persisted
  authentication rather than accepting only a fresh database.

Implicit `NacosForm` parameters on HTTP Controllers must receive binding metadata during AOT
processing, including inherited and optional property accessors. Registration must not depend only
on fields observed by tracing requests, instantiate Controllers, or change ordinary JVM request
binding. Independent Console requests to Native Servers follow the same binding contract.

### 10.1 Bundled Reachability Metadata

Bootstrap ships GraalVM reachability metadata for the default Server combination, so a Native
build does not require the operator to trace the Server first.

- Location and format. The metadata is a set of `reachability-metadata.json` files in the GraalVM
  unified format below `META-INF/native-image/` in Bootstrap's resources, one file per owner:
  `com.alibaba.nacos/nacos-bootstrap` holds what Nacos builds itself, each third-party
  artifact holds its own folder below `<groupId>/<artifactId>/<version>/`, and `jdk/runtime`
  holds classes of the JDK. The owner of an entry is the artifact whose jar on the Native
  classpath holds the class or the resource. The version on the folder is the version of that
  jar, so a version bump cannot silently reuse the entries of another version. The `native`
  profile reads the files
  as part of Bootstrap's classes. An ordinary JVM must not read them, and the JVM artifact
  contract must not depend on them.
- Scope. It covers the dynamic access that static analysis and Spring AOT cannot see for the
  bundled default plugins, the bundled MySQL and PostgreSQL drivers, gRPC payload and model
  classes, SPI providers, resources and resource bundles, as exercised by the standalone OpenAPI,
  Java SDK and Maintainer SDK integration suites, and, as exercised on builds of their own, the
  three AI registry contexts, the Kubernetes sync, the Istio adaptor, the DNS adaptor and a
  cluster of three nodes (Distro, Raft and the requests between the nodes), which those suites do
  not reach. Other plugins and drivers, embedded Derby and Oracle are outside the bundled scope.
  Their metadata comes from the extension itself, from the GraalVM reachability metadata
  repository, which applies to the Maven dependencies of the module and not to the jars of the
  plugin directory, or from `nacos.native.metadata`, and their support must be validated and
  reported separately. An Oracle driver needs more: a Native build with it is not supported until
  the redistribution terms of the driver and the database versions that it supports, which are
  fewer than the Oracle 12c and later that the datasource plugin supports, are settled.
- Content rules. Entries derive from tracing representative runs on the exact classpath of the
  Native build. What no trace contains comes from the reports of an exact-mode build or, for
  lookups that the tracing agent does not record (for example the accessors that protobuf looks up
  on a generated message and on its Builder), from the code that makes them; an exact-mode build
  and a restart of the Native Server on the same data must confirm such entries. The classes that
  only a cluster serializes (the requests between the nodes, what Distro carries, the commands of
  the `plugin_state` group) are registered in full, together with the classes of their properties:
  Jackson drops a property that it cannot see without a word, and a getter or a setter that is not
  registered fails at its first call, so the exact-mode build would find them one at a time. Each
  file must be deterministic: sorted, one
  entry per line. No file may contain classes that Spring AOT generates in the same build, agent
  or JVM-internal classes, test or tooling classes, or machine- or run-specific data such as
  absolute paths, host names, addresses or credentials. Lookups of classes and resources that do
  not exist on the Native classpath, and entries for lambda-proxy classes, to which GraalVM 25
  cannot apply metadata, are not recorded; the metadata targets the default reachability mode and
  does not promise compatibility with `--exact-reachability-metadata`.
- Other sources. The files hold only what the Native build does not get elsewhere, so that no
  registration is kept twice and none outlives its source. The build also reads the
  `META-INF/native-image` files in the jars of the Native classpath, except those that
  `native-image.args` excludes, and the files that the AOT generator writes in the same build. An
  entry that these sources provide in full is not kept, and of an entry that they provide in part
  only the rest is kept. Only an entry without a condition counts as provided: an entry with a
  `typeReached` condition is available once its type is reached, and the access that the bundled
  entry serves may come before.
- Reachability metadata repository. The `native` profile always adds the GraalVM reachability
  metadata repository, in the release that `graalvm-reachability-metadata.version` of the root POM
  names, to the Native classpath. The `native-maven-plugin` adds it only to a classpath that it
  builds itself, so the profile runs its `add-reachability-metadata` goal and puts the output on
  the Native classpath. The output directory is deleted before the goal runs, so stale metadata
  cannot survive a dependency or repository-version change. The files do not rely on it and do not
  count it as a source, because it guards nearly all of its entries with a condition: a new release
  of the repository needs no change of the files.
- Conditions. Every entry carries a `typeReached` condition that depends on its owner. The entries
  of Nacos and of the JDK carry `com.alibaba.nacos.bootstrap.NacosBootstrap`, the Server entry
  point, which is initialized before any other Server code runs. The entries of a third-party
  artifact carry the type that makes the Server use the artifact, for example
  `org.springframework.boot.SpringApplication` for the Spring family and for the libraries whose
  classes Spring Boot looks up, and `com.zaxxer.hikari.HikariConfig` for the JDBC drivers. An entry
  is available at run time only once the initialization of its condition type has started, so a
  condition type must be initialized before the first dynamic access that the entry serves, which
  is often earlier than the first use of a class of the artifact itself; a type that is only named
  by a class literal never is. For an artifact that a client and a server both use, the type must
  be one that both initialize: a node of a cluster creates its RPC clients while the core context
  is built, before any gRPC server starts. A condition type is accepted after a comparison with the
  order in which a run of the Server loads and initializes classes. Every entry of an owner carries
  the same condition.
- Maintenance. A change to the dependency set, the plugin SPI providers, reflectively serialized
  model classes or reflective code paths requires refreshing the metadata and validating the
  Native executable again. A refresh is a reviewed source change, and each entry goes into the
  file of its owner.
- Acceptance. The metadata is accepted by running the standalone integration suites against the
  Native executable. What an executable contains is fixed when it is built (the datasource, the
  switches of the AI registries, the adaptors, the plugin jars, standalone or cluster mode), so
  each configuration that is accepted is a validation scenario and runs on a build of its own, and
  what the suites cannot reach (the AI registries, the Kubernetes sync, the DNS adaptor, a cluster)
  needs tests of its own. Tests that also fail against the ordinary JVM Server in the same setup
  are not Native regressions. The suites never restart the Server, so the run must also create
  data that no suite creates, wait until the Raft snapshots of the naming service hold that data,
  restart the Native Server on the same data, confirm from the logs that the groups loaded those
  snapshots, and read that data again. A snapshot that was taken before the data does not hold it,
  and a restart then replays the log and does not reach the code that loads a snapshot. Dynamic
  accesses without metadata can be listed by building with `--exact-reachability-metadata` and
  running with `-XX:MissingRegistrationReportingMode=Warn`. A validation report must name the
  GraalVM version, the scenario, the datasource, the suites and tests that ran and whether the
  Server was restarted on the same data.

## 11. Pending Issues

- Deployment type name `serverWithMcp` exists in code but is not wired as an
  accepted branch in `NacosBootstrap`. Its formal process behavior should be
  specified before exposing it as a supported deployment type.
- Some modules still read selected values directly from system properties for
  historical compatibility. New server code should prefer `EnvUtil` unless a
  low-level JVM integration explicitly requires direct access.

## 12. Related Specs

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
