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

# Nacos 客户端运行时规范

客户端运行时规范定义公共 Client SDK interface 之下的实现行为，并关联
[SDK 规范](../sdk/sdk-spec.md)、[gRPC API 规范](../grpc-api/api-spec.md)，以及 Config、
Naming、AI Registry 和分布式锁等领域规范。

- [客户端运行时规范](client-runtime-spec.md)
- [客户端连接与故障切换规范](client-connection-failover-spec.md)
- [客户端能力协商规范](client-ability-negotiation-spec.md)
- [客户端本地缓存与 Redo 规范](client-local-cache-redo-spec.md)
- [运行时推送与重连规范](runtime-push-reconnect-spec.md)
