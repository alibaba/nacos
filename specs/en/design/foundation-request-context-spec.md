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

# Nacos Request Filtering And Runtime Context Spec

This document defines the foundation rules for HTTP servlet filters, gRPC
request filters, request-scoped runtime context, parameter extraction,
namespace validation, auth and control hooks.

It complements the [HTTP API Spec](../http-api/api-spec.md),
[gRPC API Spec](../grpc-api/api-spec.md), [Auth And Permission Spec](../auth/auth-permission-spec.md),
[Control Plugin Spec](../plugin/control-plugin-spec.md), and
[Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md).

## 1. Positioning

Request filtering is the pre-handler execution layer for Nacos HTTP and gRPC
requests. It may enrich runtime context, reject invalid requests, enforce
cross-cutting checks, or adapt request metadata for downstream handlers.

Request filtering must not own domain resource semantics. Config, Naming, AI,
Core, and Auth domains continue to define resource identity, lifecycle,
authorization meaning, and operation results in their own specs.

## 2. Runtime Request Context

Nacos uses `RequestContextHolder` and `RequestContext` as the process-local
request context model.

Context rules:

- `RequestContextHolder` is backed by `ThreadLocal`. A request entry point must
  clear it after request handling when the worker thread can be reused.
- `RequestContext` contains request id, request timestamp, `BasicContext`,
  `EngineContext`, `AuthContext`, and named extension contexts.
- `BasicContext` records protocol, request target, encoding, app, user agent,
  and remote/source address information.
- `AuthContext` records API type, parsed identity, resource, and auth result
  when an auth filter has executed.
- Extension contexts may add runtime metadata, but must not redefine standard
  fields or store durable domain state.
- Context is runtime-only. It is not persisted, not a cluster replication
  payload, and not automatically propagated to asynchronous tasks unless a
  component explicitly copies the required fields.

HTTP requests are initialized by `HttpRequestContextFilter`, which runs at the
earliest servlet filter order. It sets the protocol to HTTP, uses the HTTP
method and URI as the target, records encoding and client headers, and clears
the context in `finally`.

gRPC unary requests are initialized by `GrpcRequestAcceptor` after the
connection is validated and the payload is parsed. It uses the request id from
the `Request`, sets the protocol to gRPC, uses the request class name as the
target, records client version as user agent, resolves app metadata, and records
remote/source address from the registered connection.

## 3. HTTP Filter Model

HTTP filters are servlet filters registered by Nacos web configuration and
domain modules.

Core HTTP filter responsibilities:

- `FormSizeFilter` rejects oversized form requests before normal controller
  processing.
- `HttpRequestContextFilter` initializes and clears `RequestContext`.
- `AuthFilter`, `AuthAdminFilter`, and console auth filters process
  `@Secured` APIs and write `AuthContext` when auth is evaluated.
- `NacosHttpTpsFilter` checks `@TpsControl` points through the Control plugin
  manager for HTTP v1/v2 Config and Naming paths.
- `ParamCheckerFilter` extracts structured parameters through
  `ExtractorManager` and validates them with the active `ParamChecker`.
- Domain filters may adapt legacy request parameters, traffic metadata, or
  module-specific compatibility behavior, but must not bypass the common
  response, auth, or validation rules for new APIs.

Filter order rules:

- Request context initialization must run before filters that need request,
  auth, trace, or control metadata.
- Size, authentication, control, and parameter validation filters may reject a
  request before the controller is invoked.
- A filter that rejects an HTTP request must return the standard Nacos result
  format where the target API family expects a wrapped response.
- Filter exceptions should be converted through the unified exception or result
  model when the filter owns the rejection. Unexpected infrastructure failures
  may be rethrown for global exception handling.

## 4. gRPC Request Filter Model

gRPC business requests are accepted by `GrpcRequestAcceptor`, parsed into
`Request` objects, matched to a `RequestHandler`, and then passed through
registered `AbstractRequestFilter` instances before the handler's `handle`
method runs.

gRPC filter rules:

- `AbstractRequestFilter` instances register into `RequestFilters` during
  initialization.
- Filters execute serially inside `RequestHandler.handleRequest`.
- A filter returns `null` to continue. A non-success response stops the chain
  and is returned to the caller.
- Filter exceptions are logged by the request handler and do not by themselves
  abort the handler chain.
- A filter that rejects a request should create the handler's declared response
  type and set the appropriate error code and message.
- `RemoteRequestAuthFilter` evaluates `@Secured`, server identity, identity
  validity, and authority, and writes `AuthContext`.
- `RemoteParamCheckFilter` uses `ExtractorManager` and the active
  `ParamChecker` to validate request parameters.
- `TpsControlRequestFilter` checks `@TpsControl` points through the Control
  plugin manager and returns `OVER_THRESHOLD` when restricted.
- `NamespaceValidationRequestFilter` validates namespace existence when the
  handler opts in through `@NamespaceValidation`.

The gRPC acceptor rejects requests while the server is starting, unknown
request types, invalid connections, invalid payloads, and non-`Request`
payloads before the handler filter chain is entered.

## 5. Parameter Extraction And Validation

`ExtractorManager.Extractor` is the common annotation for mapping a controller
method or request handler to HTTP and RPC parameter extractors.

Parameter extraction rules:

- Extractors produce `ParamInfo` records for shared validators; they should not
  mutate domain state or perform durable writes.
- The annotation may be declared on the method or the declaring class. Method
  annotations take precedence.
- HTTP extractors read servlet requests. RPC extractors read `Request` objects.
- Extractors are loaded through Nacos SPI and must be deterministic for the
  same request input.
- Validation is controlled by server parameter-check configuration and the
  active `ParamChecker`.
- Domain-level validation still belongs to forms, request objects, services, or
  domain handlers. Parameter filters only enforce common structural rules.

## 6. Namespace Validation

Namespace validation is a cross-cutting guard for APIs that explicitly opt in.

Namespace validation rules:

- Namespace validation must be controlled by the global namespace validation
  switch and by the handler-level `@NamespaceValidation` annotation.
- Blank namespace values are treated according to domain defaults and are not
  validated as a missing namespace by the filter.
- Non-blank namespace ids must exist in the namespace operation service before
  the request continues.
- Validation failures must use the standard error code and response model of
  the current transport.
- Namespace validation must not create namespaces, infer tenant ownership, or
  override domain authorization rules.

## 7. Cross-cutting Boundaries

- Auth filters evaluate identity and permissions, but auth resource semantics
  remain defined by the [Auth And Permission Spec](../auth/auth-permission-spec.md).
- Control filters enforce traffic governance, but control point definitions and
  plugin behavior remain defined by the
  [Control Plugin Spec](../plugin/control-plugin-spec.md).
- Request context may provide fields for metrics and trace, but observability
  behavior remains defined by the
  [Observability Hooks Spec](foundation-observability-hooks-spec.md).
- Remote connection metadata comes from the
  [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md).
- Domain handlers must not assume a filter has performed domain-specific
  validation unless the API contract explicitly requires that filter.
- New APIs should prefer shared filters and annotations over duplicating
  equivalent auth, parameter, namespace, or control logic in controllers.

## 8. Pending Issues

- Some module-specific legacy filters and controllers still mix compatibility
  adaptation with validation or business behavior. New v3 APIs should keep this
  behavior outside the formal API contract and migrate common checks to shared
  filters or domain services.
- gRPC connection heartbeat and half-open detection are hidden below Naming and
  other domains today. Detailed transport heartbeat semantics should be
  expanded in a future remote connection or gRPC client spec instead of being
  duplicated in domain specs.

## 9. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md)
- [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [HTTP API Spec](../http-api/api-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
- [Response And Error Spec](../http-api/response-error-spec.md)
- [Auth And Permission Spec](../auth/auth-permission-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
- [Observability Hooks Spec](foundation-observability-hooks-spec.md)
