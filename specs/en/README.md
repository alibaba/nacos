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

## Interface Model

- [HTTP API Spec](http-api/api-spec.md)
- [gRPC API Spec](grpc-api/api-spec.md)
- [SDK Spec](sdk/sdk-spec.md)
- [Java SDK Implementation Spec](sdk/sdk-java-impl-spec.md)

## Extension Model

- [Plugin Specs](plugin/README.md)

## Security Model

- [Auth And Permission Spec](auth/auth-permission-spec.md)
- [Auth Plugin Spec](auth/auth-plugin-spec.md)
- [RAM Auth Plugin Spec](auth/ram-auth-plugin-spec.md)
- [OIDC Auth Plugin Spec](auth/oidc-auth-plugin-spec.md)
- [Visibility Plugin Spec](auth/visibility-plugin-spec.md)
- [Default Auth Plugin Implementation Spec](auth/default-auth-plugin-spec.md)

Agent guidance files such as [AGENTS.md](../../AGENTS.md) should summarize these
specs for local execution. The specs remain the rule source when API guidance is
used by humans, AI agents, templates, or validation tools.
