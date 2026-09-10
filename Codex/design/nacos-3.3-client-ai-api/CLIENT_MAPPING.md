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

# 旧 A2A 到 RAD 的客户端转换边界

状态：2026-09-10 评审修订，未实现。补充 [总体设计 §5](README.md)；后续适配优先客户端转换，只有现有契约确实缺少表达能力时才补服务端支持。

**本专题现已移至后续阶段。** 第一步保留所有旧 A2A gRPC 行为，不实施以下转换；当前接口/transport/测试安排见 [PHASE1_PLAN.md](PHASE1_PLAN.md)。

## 1. Endpoint 可以映射，先区分对象与发布身份

对于一个 exact version 的旧批次，标准映射为：

| 旧输入 | RAD 输入 |
| --- | --- |
| Client namespace / agentName | `namespaceId` / `agentName` |
| A2A 操作 | `protocol="a2a"`；不要与 URI 的 http/https scheme 混淆 |
| Endpoint.version = v | batch `runtimeVersion=v`、`versionRange=[v]` |
| address / port / protocol / supportTls / path / query | 按现有 canonical 规则组成绝对 `Endpoint.uri` |
| transport | `Endpoint.transport`，不任意改大小写或改成另一种 transport |
| 单条或同版本集合 | 长度 1 或 N 的 `endpoints[]` 完整替换批次 |

例：`version=1.0.0, address=host, port=443, supportTls=true, path=/rpc, query=a=1`
可转成 `runtimeVersion=1.0.0, versionRange=[1.0.0], uri=https://host:443/rpc?a=1`。
IPv6、前导斜线、空 query 和自定义 scheme 均沿用现有 canonical 规则。此处不是重定义历史 URI 规则。

因此，**对象转换本身可以在客户端做**。服务端 `CanonicalA2aEndpointOperationService` 当前也已证明这些对象能进入标准 Runtime layout；不必为了 URI 映射新增接口。客户端不依赖 `ai` 服务端模块，只复用已有公共校验/规范，必要时写一个小的纯转换器。

## 2. 多版本问题是 publisher 身份，不是 Endpoint 无法表示

当前标准 Register 的身份是 `(publisher, namespace, agent, protocol)`，整批共享一个 `runtimeVersion/versionRange`。

```text
同一 connection C，agent=A，protocol=a2a：
register(v1, [e1]) -> C/A/a2a = (v1, [e1])
register(v2, [e2]) -> C/A/a2a = (v2, [e2])  // 覆盖 v1

旧 API 所需：C/A/v1 与 C/A/v2 两份意图独立存在
```

在 Client 把 map key 加上 version，不能改变服务端的身份。把两个版本的 Endpoint 合并为一个批次也不行：同一批次只有一组版本信息；加宽 range 会错误地使 e1/e2 都匹配两个版本。同一 host/port/transport、不同 path 的版本还可能发生自然键冲突。Discovery 的 `bindings[]` 是多个贡献的聚合结果，不是 Register 允许提交的逐 Endpoint 多版本字段。

可选解法及边界：

- **Client 创建独立真实 owner**：gRPC 要不同的连接；当前 Handler 取 `RequestMeta.connectionId`，不能仅在 Payload 填一个 ID。HTTP 可设计多个独立 Client ID，现有 proxy/coordinator 是一个 ID，仍需相应心跳、失效恢复和清理适配。理论上可不扩展服务端注册协议，但扩大了资源和生命周期管理，且真实连接中断时序与旧子 publisher 不完全相同。
- **共享连接下的逻辑 owner**：复用已有服务端 child publisher 思路，补必要 binding 表达；保留原共享连接与生命周期。需明确鉴权、容量和回收，不能让调用方指定任意他人 publisher。
- **保留旧 Endpoint 请求**：服务器本来就会按当前模式映射到 canonical 或 mirror；改动最小，但不能称为 Client 已发送标准 RAD Register。

这不是“无论怎样都无法客户端解决”，而是“在本次复用一套连接/生命周期的边界内，现有标准请求不能表达”。不要为单版本先切 RAD，等第二个版本出现时再迁移存量 publication。

## 3. 单条、批量与注销本身可在客户端转换

owner 隔离成立后：单条是该版本的完整单元素批次；批量是完整覆盖；旧 canonical 注销是删除该版本的整个 publication。

客户端已知自己提交的完整期望状态，可直接清空目标 owner，或对新公开便利 API 构造覆盖该 owner 全部 Endpoint 的注销集合。无需查询远端再合并，也无需改服务端增量注册逻辑。不能仅将旧注销的一个参数 Endpoint 映射为新 API 的单条移除，否则旧批次剩余成员不会删除。

没有该版本本地意图时沿用旧约定；其他版本、其他客户端和本 SDK 的原生 RAD publication 均不应受影响。并发、失败后 redo、防御性快照仍是客户端状态适配的验证项，不能省略。

## 4. 字段缺口只涉及未公开的旧语义

URI/TLS/path/query/transport 都能转换。真正的字段问题是旧 Endpoint 的 `protocolVersion` 和 `tenant`：

1. 旧 canonical 注册把它们写入 `__nacos.agent.endpoint.*` 保留 metadata。
2. 普通 RAD Register 的公共 metadata 禁止这些保留键；RAD Discover 也剔除它们。
3. RAD CallInterface 的 `protocolVersion` 是定义字段，不一定等于某个 Runtime Endpoint 提交的覆盖值；`tenant` 也不能从 URI 凭空推导。

可以在新 SDK 之间约定两个普通 metadata 键来往返，但现有旧查询投影不认识新键，而真实旧 SDK 发布的数据又没有新键。新客户端本地缓存也无法补齐其他进程注册的内容。因此这是 **混合新旧 SDK、完整字段兼容时的服务端映射/暴露缺口**，不是说 RAD 的 metadata 不能装字符串。

最小支持可以是明确的兼容 metadata 映射/返回规则，不必由此新增整套查询或注册 RPC。若改变公开 metadata，必须同时讨论其进入自然键冲突比较、Runtime revision 和 Watch 指纹后的影响；不能把它当无语义变化的私有细节。

## 5. 查询和订阅：多数可转换，不能误报信息缺失

- 旧省略 version → `label=latest`；指定 version → exact version；只选 `protocol=a2a`。
- native descriptor → AgentCard；URL 模式使用声明 Card；SERVICE 模式把 Runtime 集合投影到 interfaces/root URL，没有 Runtime 时回退声明 Card。保留旧的 preferred transport、完整 additionalInterfaces 和排序规则。
- 不对来源做 Filter 时，RAD `endpointSets[]` 按存储顺序输出，空来源也保留。对旧转换产生的定义，可据此恢复存储的 `registrationType`。**初稿认为空 Endpoint 时无法恢复 source order，是错误判断。** 请求中的类型覆盖只影响投影，不覆盖返回的存储类型。
- latest 查询可以直接设置 `latestVersion=true`；exact 查询可另读 latest 或 Search catalog 再比较，false 仍按旧形状使用 null。该字段不是完全不可获得，只是不在单次 exact Discover 中；需定义额外读失败与并发 latest 变化的处理，不能宣称两次请求构成同一快照。
- 订阅可继续用原轮询、缓存和旧 listener，在查询代理处完成转换；无需强制变成原生 RAD Watch。缺失时保留订阅、取消重订阅、latest/exact 身份和 shutdown 都可客户端处理。
- 一旦 GET 存在隐藏字段缺口，订阅也继承该缺口。当前 RAD revision/fingerprint 排除旧保留字段，不能只依赖 RAD Watch 捕获这些字段的单独变化。

上述转换不能扩大来源权限：通用 Agent 只声明 DECLARED 时，RAD Filter 不能额外启用 RUNTIME；旧 SERVICE 强制投影等跨契约边界仍须保留兼容读取或明确补契约。

## 6. 不能仅靠现有客户端转换保持的写语义

`releaseAgentCard` 的 DTO 能转换，但行为不能用现有 `publishAgent` 无损实现：

- 旧 release 直接 online；新 publish 是 draft/ordinary submit，可能停在 reviewing。
- 旧新增版本 `setAsLatest=false` 保留已有 latest；标准上线会移动 latest。
- 旧重复 release 已 online 的 A2A 版本为成功 no-op，即使本次内容不同；普通 publish 比较内容并可能返回冲突。

先读再决定是否发布只能模拟部分稳定状态，不能提供缺少的原子写规则；先上线再恢复 latest 会暴露中间状态，也没有相应 Client 管理权限。应保留既有 release 路径，或只为这个缺口复用既有服务端 legacy release 操作；不要修改通用 publish 契约。

另外，LEGACY/SYNCING/QUIESCING 下的历史权威、mirror/shadow 和写屏障属于服务端行为。标准 RAD Register 直接写 canonical Runtime，并不自动经过旧 migration endpoint router；客户端 DTO 转换无法保证旧节点可见性。已迁移完成时可按前述映射工作，迁移阶段则仍需要已有兼容路径或明确的新服务端适配保证。

## 7. 本轮设计收敛

按方法/字段核对，优先客户端转换。保留需要服务端事实或身份支持的最小集合：旧 release、隐藏兼容字段、共享连接下多版本 owner、迁移期权威和镜像。`a2aCompatV1` 仍只是完整兼容能力的候选声明，不是所有方法必须新增 RPC 的结论；最终是否保留此 flag、如何支持各缺口，随 binding 定稿决定。

相关依据：

- [RAD Register/Endpoint 规范](../../../specs/zh-cn/ai/rad-protocol-spec.md)、[旧 A2A 规范](../../../specs/zh-cn/ai/a2a-agent-spec.md)。
- [现有旧 Endpoint 转换](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/CanonicalA2aEndpointOperationService.java)、[标准注册 Handler](../../../ai/src/main/java/com/alibaba/nacos/ai/remote/handler/agent/AgentEndpointRegisterRpcRequestHandler.java)、[SDK publication manager](../../../client/src/main/java/com/alibaba/nacos/client/ai/AgentEndpointPublicationManager.java)。
- [来源顺序保留](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentDiscoveryApplicationService.java)、[保留 metadata](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/runtime/AgentRuntimeEndpointMapper.java)、[公共 metadata 校验](../../../api/src/main/java/com/alibaba/nacos/api/ai/utils/AgentValidationUtils.java)。
- [旧 Card 投影](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/A2aServerOperationService.java)、[新 publish](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentPublishApplicationService.java)、[两版本预注册 IT](../../../test/java-sdk-test/src/test/java/com/alibaba/nacos/test/sdk/ai/AgentPublishJavaSdkITCase.java)。
