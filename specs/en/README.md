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

# Nacos Specs

Current specs are organized from top-level design to external interfaces,
extension mechanisms, and cross-cutting security rules.

## Design Foundation

- [Nacos Design Spec](design/nacos-design-spec.md)
- [Resource Model Spec](design/resource-model-spec.md)
- [Compatibility And Deprecation Spec](design/compatibility-deprecation-spec.md)
- [Foundation Capabilities Spec](design/foundation-capabilities-spec.md)
- [Server Lifecycle And Environment Configuration Spec](design/foundation-server-lifecycle-env-spec.md)
- [Cluster Membership Spec](design/foundation-cluster-membership-spec.md)
- [Remote Connection Lifecycle Spec](design/foundation-remote-connection-spec.md)
- [Request Filtering And Runtime Context Spec](design/foundation-request-context-spec.md)
- [Internal RPC And Cluster Request Spec](design/foundation-internal-rpc-spec.md)
- [AP Consistency Spec](design/foundation-ap-consistency-spec.md)
- [CP Consistency Spec](design/foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](design/foundation-persistence-dump-spec.md)
- [Task Execution Spec](design/foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](design/foundation-event-dispatch-spec.md)
- [Observability Hooks Spec](design/foundation-observability-hooks-spec.md)
- [Core Capabilities Spec](design/core-capabilities-spec.md)

## Interface Model

- [HTTP API Spec](http-api/api-spec.md)
- [gRPC API Spec](grpc-api/api-spec.md)
- [SDK Spec](sdk/sdk-spec.md)
- [Java SDK Implementation Spec](sdk/sdk-java-impl-spec.md)
- [Client Runtime Specs](client/README.md)

## Domain Model

- [Config Specs](config/README.md)
- [Naming Specs](naming/README.md)
- [AI Registry Specs](ai/README.md)
- [Core Operations Spec](core/core-operations-spec.md)
- [Console Spec](console/console-spec.md)
- [Distributed Lock Spec](lock/lock-spec.md)

## Extension Model

- [Integration Specs](integration/README.md)
- [Plugin Specs](plugin/README.md)

## Security Model

- [Auth And Permission Spec](auth/auth-permission-spec.md)
- [Auth Plugin Spec](auth/auth-plugin-spec.md)
- [RAM Auth Plugin Spec](auth/ram-auth-plugin-spec.md)
- [OIDC Auth Plugin Spec](auth/oidc-auth-plugin-spec.md)
- [Visibility Plugin Spec](auth/visibility-plugin-spec.md)
- [Default Auth Plugin Implementation Spec](auth/default-auth-plugin-spec.md)

## Testing Model

- [API Integration Test Spec](testing/api-integration-test-spec.md)
- [Java SDK Integration Test Spec](testing/java-sdk-integration-test-spec.md)

Agent guidance files such as [AGENTS.md](../../AGENTS.md) should summarize these
specs for local execution. The specs remain the rule source when API guidance is
used by humans, AI agents, templates, or validation tools.
