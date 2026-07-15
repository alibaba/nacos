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

# Plugin Specs

Plugin specs define how Nacos extension points are loaded, selected, executed,
configured, and surfaced through the unified plugin management model.
They extend the [Nacos Design Spec](../design/nacos-design-spec.md), must keep
[Resource Model](../design/resource-model-spec.md) semantics stable, and must
follow [HTTP API](../http-api/api-spec.md) rules when exposing HTTP endpoints.

## Common Model

- [Nacos Plugin Spec](plugin-spec.md)
- [Addressing Extension Spec](addressing-plugin-spec.md)

## Data And Configuration

- [Data Source Dialect Plugin Spec](datasource-dialect-plugin-spec.md)
- [Default Data Source Dialect Implementation Spec](default-datasource-dialect-plugin-spec.md)
- [Config Change Plugin Spec](config-change-plugin-spec.md)
- [Config Encryption Plugin Spec](config-encryption-plugin-spec.md)

## Runtime Extensions

- [Environment Plugin Spec](environment-plugin-spec.md)
- [Trace Plugin Spec](trace-plugin-spec.md)
- [Control Plugin Spec](control-plugin-spec.md)
- [Default Control Plugin Implementation Spec](default-control-plugin-spec.md)

## AI Extensions

- [AI Publish Pipeline Plugin Spec](ai-pipeline-plugin-spec.md)
- [AI Storage Plugin Spec](ai-storage-plugin-spec.md)
- [AI Resource Import Plugin Spec](ai-resource-import-plugin-spec.md)

## Security Extensions

- [Auth Plugin Spec](../auth/auth-plugin-spec.md)
- [RAM Auth Plugin Spec](../auth/ram-auth-plugin-spec.md)
- [OIDC Auth Plugin Spec](../auth/oidc-auth-plugin-spec.md)
- [Visibility Plugin Spec](../auth/visibility-plugin-spec.md)
