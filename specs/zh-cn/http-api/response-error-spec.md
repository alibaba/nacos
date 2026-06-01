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

# HTTP API 响应与错误规范

本文档细化 [HTTP API 规范](api-spec.md)中的响应契约。鉴权相关失败由
[HTTP 鉴权规范](authorization-spec.md)定义，当前端点覆盖范围记录在
[V3 API 范围](v3-api-surface.md)中。

## 1. JSON 响应包装

V3 JSON 响应默认使用 `com.alibaba.nacos.api.model.v2.Result<T>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

端点文档必须说明 `data` 类型以及任何非默认 HTTP 状态。

## 2. 响应例外

当前有意设计的响应形态例外：

- 文件下载端点可以返回 `ResponseEntity<byte[]>`。
- Copilot 流式端点返回 Server-Sent Events。
- 健康检查 readiness 在未就绪时可以返回 HTTP 500，并携带
  `Result<String>` 响应体。
- 部分遗留或运维端点可以返回纯文本。只有在确认属于兼容行为后，才应保留。

## 3. 错误处理

标注 `@NacosApi` 的 Controller 使用 `NacosApiExceptionHandler` 处理常见
v3 错误：

| 异常类型 | HTTP 状态 | Result code 来源 |
| --- | --- | --- |
| `NacosApiException` | 异常错误码 | 详细 API 错误码 |
| `NacosException` | 异常错误码 | `SERVER_ERROR` |
| 缺少请求参数 | 400 | `PARAMETER_MISSING` |
| 非法参数或数字格式错误 | 400 | `PARAMETER_VALIDATE_ERROR` |
| Media type 错误 | 400 | `MEDIA_TYPE_ERROR` |
| `AccessException` | 403 | `ACCESS_DENIED` |
| 数据访问、Servlet 或 IO 失败 | 500 | `DATA_ACCESS_ERROR` |
| 未处理异常 | 500 | 通用失败 |

## 4. ExceptionHandler 收敛

Nacos 自有的 v3 HTTP API 应收敛到 `@NacosApi` 和
`NacosApiExceptionHandler`，以获得统一异常处理。早于 v3 API 模型存在的
模块级 ExceptionHandler，不应为 v3 API 定义不同的响应形态。

插件性质的模块如果有意维护独立 API 面，可以保留自己的 ExceptionHandler。通用扩展边界由
[Nacos 插件化规范](../plugin/plugin-spec.md)定义。`PrometheusApiExceptionHandler` 是
这类插件式 ExceptionHandler 的例子。

已知待处理项：

- `config/server/exception/GlobalExceptionHandler` 仍作用于
  `com.alibaba.nacos.config.server`，并可能返回纯文本 `ResponseEntity<String>`。
- `naming/exception/ResponseExceptionHandler` 仍作用于
  `com.alibaba.nacos.naming`，并可能返回纯文本 `ResponseEntity<String>`。
- `ConfigOpenApiController` 引入了 `NacosApi`，但当前没有标注 `@NacosApi`。

这些项应作为待处理的收敛问题，使 Config 和 Naming 的 v3 API 使用与其他
Nacos v3 API 一致的 `Result<T>` 错误契约。
