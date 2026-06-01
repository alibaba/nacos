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

# Naming Ops Spec

This document defines Naming maintainer and operation behavior.

## 1. Operation Scope

Naming operation APIs are management surfaces. They may inspect or modify
service metadata, instance metadata, client state, subscribers, switches,
metrics, and log levels. They must not be exposed through runtime Client SDK
surfaces unless the operation is explicitly part of application runtime
discovery.

## 2. Service And Instance Management

Admin and Maintainer SDK surfaces may:

- create, update, query, list, and delete services;
- register, deregister, update, partially update, and list instances;
- update or delete instance metadata in batch;
- update cluster health checker metadata;
- update persistent instance health when the checker type is `NONE`.

Runtime Client SDK registration remains available, but broad management and
diagnostic operations belong to Admin API, Console API, or Maintainer SDK.

## 3. Client And Subscriber Diagnostics

Naming may expose:

- client list and client detail;
- services published by a client;
- services subscribed by a client;
- clients publishing a service;
- clients subscribing to a service;
- responsible server for an IP-port client;
- subscribers for a service.

Subscriber aggregation is a diagnostic query mode across
[server members](../design/foundation-cluster-membership-spec.md). It is not a
service resource model and must not affect runtime subscription semantics.

## 4. Switches, Metrics, And Logs

Naming operation APIs may expose module switches, metrics, and log-level
updates. Switch updates must remain administrative because they can change
runtime behavior such as health check, heartbeat, cleanup, protection, or
push-related behavior.

Metrics may include service count, instance count, subscription count, client
count, push queues, and health status summaries. Metrics are observational and
must not define resource identity. Shared metrics, trace, log, and diagnostic
rules are defined by the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).

## 5. Cleanup Diagnostics

Naming cleanup behavior includes empty service cleanup and expired metadata
cleanup. Operation documents should describe cleanup as lifecycle maintenance,
not as user-facing service deletion semantics. Explicit deletion still follows
the service lifecycle rules in the
[Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md).

## 6. Authorization And Errors

Naming operation APIs must follow:

- [HTTP API Spec](../http-api/api-spec.md);
- [Response And Error Spec](../http-api/response-error-spec.md);
- [Auth And Permission Spec](../auth/auth-permission-spec.md).

Naming still has a module-level exception handler. New v3 operation APIs should
converge toward the common Nacos API error model.

## 7. Related Specs

- [Naming Spec](naming-spec.md)
- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Health And Protection Spec](naming-health-protection-spec.md)
- [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
