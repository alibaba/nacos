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

# Java SDK 集成测试规范

本规范定义 Nacos Java SDK 公开契约的集成测试模型。它与
[API 集成测试规范](api-integration-test-spec.md)互补：HTTP API IT 验证部署后
的 HTTP 契约，Java SDK IT 验证应用侧看到的类型化 Java SDK 行为。

Java SDK IT 的目标是 SDK 场景覆盖，不是行覆盖率或分支覆盖率。

## 1. 范围

Java SDK IT 的主要位置是 `test/java-sdk-test`。该模块假设单机 Nacos 服务已经
启动，并以外部应用身份创建真实 Java SDK 客户端。

本规范适用于以下变更：

- `ConfigService`、`NamingService`、`AiService`、`A2aService`、
  `LockService` 以及 maintainer-client 对应公开 interface；
- `NacosFactory`、`ConfigFactory`、`NamingFactory`、`AiFactory`、
  `NacosLockFactory` 等公开 factory；
- SDK 方法返回的公开 request、response 或领域模型；
- listener、subscription、本地缓存、redo、factory 初始化、shutdown 或异常映射；
- SDK 配置项和默认值行为。

单元测试仍然需要覆盖隔离实现分支，但不能替代对外可见 SDK 行为的 Java SDK IT。

## 2. SDK 变更规则

在实现 Java SDK 契约新增、修改、删除或废弃前，变更负责人必须完成 SDK IT
影响分析：

1. 识别受影响的 SDK interface、factory、模型或 listener 路径。
2. 阅读公开 API、实现、校验器、传输映射、响应组装、异常映射、生命周期代码
   和对应 SDK/client 规范。
3. 形成场景矩阵，覆盖 factory/生命周期行为、预期功能、边界/校验行为、
   listener 或 subscription 行为，以及异常/错误处理。
4. 在同一个变更集中新增、更新或移除 `test/java-sdk-test` 用例。
5. 更新 `test/java-sdk-test/JAVA_SDK_IT_COVERAGE.md`。

如果完整成功路径在单机 IT 中难以实际执行，测试仍必须覆盖 SDK 参数校验、
本地边界行为、受控异常，以及低风险可观测的服务端交互。未覆盖路径和原因
必须记录在文档中。

## 3. 必须覆盖的场景组

每个 Java SDK IT 都应覆盖以下可观测场景组。

### 3.1 Factory 和生命周期

验证 SDK 可以通过公开 factory 使用真实 properties 创建，能正确处理 server
address 和 namespace 默认值，并能通过公开 shutdown 方法释放资源。

### 3.2 预期功能

验证 SDK 方法完成承诺的远程或本地行为。优先使用发布后查询、注册后查询、
订阅后回调、加锁后解锁、发布后加载、删除后确认不存在等流程。

断言必须检查类型化 SDK 返回值、模型字段、回调和远程副作用，不能只判断没有
抛出异常。

### 3.3 边界和校验

覆盖必填参数、可选默认值、非法枚举或类型、namespace/group 默认值、超时行为、
异常模型对象、listener 身份要求、重复或幂等调用，以及资源不存在行为。

### 3.4 异常和错误处理

验证 SDK 可见失败会产生受控 `NacosException` 或文档化返回值。测试应捕捉非法
输入、资源不存在、远端失败或非法生命周期使用变成非预期运行时异常的回归。

### 3.5 Listener 和订阅行为

对于 listener API，应验证适用场景下的初始查询行为、可观测变更触发回调、
unsubscribe/remove 行为和清理逻辑。等待必须有边界，并提供清晰断言信息。

## 4. 测试组织

Java SDK IT 应放在：

- `com.alibaba.nacos.test.sdk.config`
- `com.alibaba.nacos.test.sdk.naming`
- `com.alibaba.nacos.test.sdk.ai`
- `com.alibaba.nacos.test.sdk.lock`
- 新增 maintainer SDK IT 时使用 `com.alibaba.nacos.test.sdk.maintainer.<domain>`

建议一个公开 SDK interface 或一组强关联 API family 对应一个测试类。共享的客户端
构造、清理、有界等待、随机资源名和 shutdown 逻辑应抽象到基础类。

## 5. 运行规则

Java SDK IT 必须：

- 使用 JUnit 5 和 Failsafe；
- 避免 `@SpringBootTest`、`SpringExtension`，也不要在测试类中启动 Nacos；
- 读取 `nacos.host` 和 `nacos.port`，默认 `127.0.0.1:8848`；
- 通过公开 factory 创建真实 SDK 客户端；
- 生成隔离的资源名称；
- 清理创建的 config、naming、AI 或 lock 资源；
- 即使断言失败，也要关闭每个 SDK 实例；
- 对异步服务端效果使用有界重试。

## 6. 场景文档

每个 SDK IT 类都必须包含简洁的 `Scenario coverage` Javadoc；当矩阵较大时，
应更新 `test/java-sdk-test/JAVA_SDK_IT_COVERAGE.md`。文档必须说明验证了什么，
以及为什么有分支被有意跳过。

## 7. 验证

Java SDK IT 变更需要运行：

- `mvn -pl test/java-sdk-test spotless:check`
- `mvn -pl test/java-sdk-test -DskipTests test-compile`

当单机 Nacos 服务可用时，应运行相关 Failsafe 选择，或执行
`mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false
verify`。

Java SDK IT 必须使用独立的 `java-sdk-integration-test` Maven profile。通用
`integration-test` profile 保留给 HTTP API IT 工作流，不能意外运行依赖 SDK
gRPC 连接就绪状态或可选服务端能力的 SDK 测试。
