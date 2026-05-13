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

# HTTP API Response And Error Spec

This document refines the response contract from the
[HTTP API Spec](api-spec.md). Authorization-related failures are defined with
the [HTTP Authorization Spec](authorization-spec.md), and current endpoint
coverage is recorded in the [V3 API Surface](v3-api-surface.md).

## 1. JSON Response Envelope

The default v3 JSON response envelope is
`com.alibaba.nacos.api.model.v2.Result<T>`:

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

Endpoint docs must state the `data` type and any non-default HTTP status.

## 2. Response Exceptions

Current intentional response-shape exceptions:

- File download endpoints may return `ResponseEntity<byte[]>`.
- Streaming copilot endpoints return Server-Sent Events.
- Health readiness may return HTTP 500 with a `Result<String>` body when not
  ready.
- Some legacy or operational endpoints may return plain text. These should be
  kept only when confirmed as compatibility behavior.

## 3. Error Handling

Controllers annotated with `@NacosApi` use `NacosApiExceptionHandler` for common
v3 errors:

| Exception type | HTTP status | Result code source |
| --- | --- | --- |
| `NacosApiException` | exception error code | detailed API error code |
| `NacosException` | exception error code | `SERVER_ERROR` |
| missing request parameter | 400 | `PARAMETER_MISSING` |
| invalid argument or number format | 400 | `PARAMETER_VALIDATE_ERROR` |
| media type errors | 400 | `MEDIA_TYPE_ERROR` |
| `AccessException` | 403 | `ACCESS_DENIED` |
| data access, servlet, or IO failures | 500 | `DATA_ACCESS_ERROR` |
| unhandled exceptions | 500 | generic failure |

## 4. Exception Handler Convergence

Nacos-owned v3 HTTP APIs should converge on `@NacosApi` and
`NacosApiExceptionHandler` for unified exception handling. Module-level exception
handlers that predate the v3 API model should not define a different response
shape for v3 APIs.

Plugin-style modules may keep their own exception handler when they intentionally
own a separate API surface. The common extension boundary is defined by the
[Nacos Plugin Spec](../plugin/plugin-spec.md). `PrometheusApiExceptionHandler`
is an example of this kind of plugin-style exception handler.

Known convergence items:

- `config/server/exception/GlobalExceptionHandler` still applies to
  `com.alibaba.nacos.config.server` and can return plain text
  `ResponseEntity<String>`.
- `naming/exception/ResponseExceptionHandler` still applies to
  `com.alibaba.nacos.naming` and can return plain text `ResponseEntity<String>`.
- `ConfigOpenApiController` imports `NacosApi` but is not currently annotated
  with `@NacosApi`.

These items should be treated as pending cleanup so Config and Naming v3 APIs use
the same `Result<T>` error contract as other Nacos v3 APIs.
