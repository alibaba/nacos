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

# Nacos 服务端生命周期与环境配置规范

本文定义 Nacos 进程 bootstrap、启动阶段、环境配置、部署模式、动态服务端配置、application
context 访问、module state 和 server state 的基础规则。

本文与 [Nacos 设计规范](nacos-design-spec.md)、[基础能力规范](foundation-capabilities-spec.md)、
[集群成员规范](foundation-cluster-membership-spec.md)和[可观测钩子规范](foundation-observability-hooks-spec.md)
配合使用。

## 1. 定位

生命周期与环境层在领域模块开始服务流量之前，让 Nacos 进程处于可用状态。它负责准备运行环境、
选择部署模式、初始化 Spring context、加载预置属性、暴露共享环境工具，并上报模块/服务端状态。

这一层不拥有领域资源语义。它决定进程如何启动、共享运行时配置如何读取；Config、Naming、AI、
安全和插件决定各自资源如何行为。

## 2. Bootstrap 与部署类型

`NacosBootstrap` 是打包后的 Nacos server 部署入口。

Bootstrap 规则：

- `nacos.deployment.type` 在创建 context 前选择部署类型，默认值为 `merged`。
- `merged` 在同一进程内启动 core、server web API 和 console context。
- `server` 启动 core 和 server web API context，不启动 console。
- `console` 以独立控制台进程方式启动 console context。
- bootstrap 必须在 module state builder 和条件 bean 依赖部署类型之前设置 `EnvUtil.deploymentType`。
- 不支持的部署类型必须快速失败。
- 当子 context 依赖 core bean 和环境状态时，必须以 core context 作为 parent。

`DeploymentType.SERVER_WITH_MCP` 和类型名 `serverWithMcp` 已存在于代码中，但当前 bootstrap
解析和分支没有把它作为独立公开部署模式启动。在 bootstrap 行为和 module state 规则补齐之前，
不应把它文档化为已支持启动模式。

## 3. 启动阶段

Nacos 启动阶段由 `NacosStartUp` 实现表示，并通过 `NacosStartUpManager` 选择。

启动阶段规则：

- 在 Spring 启动回调使用当前阶段前，必须通过 `NacosStartUpManager.start(phase)` 启动该阶段。
- 内置阶段名为 `core`、`web`、`console` 和 `ai-registry`。
- `StartingApplicationListener` 将 Spring run event 委托给当前 `NacosStartUp`。
- `starting` 创建阶段级启动跟踪和周期性启动日志。
- `environmentPrepared` 创建必要工作目录、注入 Spring environment、加载预置属性并初始化系统属性。
- `contextPrepared` 启动周期性启动日志。
- `contextLoaded` 可以执行自定义环境处理。
- `started` 标记阶段启动完成并记录启动结果。
- `failed` 必须按已启动阶段的逆序执行，以便资源按启动相反顺序关闭。

Core 启动还会创建 `logs`、`conf` 和 `data` 目录，注入 `EnvUtil.environment`，加载
`application.properties`，监听配置文件变化，初始化 `nacos.mode`、`nacos.function.mode` 和
`nacos.local.ip`，并在启动成功后设置 `ApplicationUtils.started`。

## 4. 环境模型

`EnvUtil` 是 Nacos server 代码共享的环境门面。

环境规则：

- 服务端代码应通过 `EnvUtil` 读取 Nacos 运行时属性，避免分散直接访问 `System.getProperty`、
  `System.getenv` 或原始 Spring environment。
- 普通配置属性被模块读取前，必须完成 `EnvUtil.environment` 注入。
- 未显式设置时，`nacos.home` 默认是 `${user.home}/nacos`。
- `nacos.server.main.port` 默认是 `8848`。
- `server.servlet.context-path` 默认是 `/nacos`；根 context path 会归一化为空字符串。
- 单机模式从 standalone 系统属性读取，并通过 `StandaloneProfileApplicationListener` 激活
  standalone Spring profile。
- function mode 可以是 `config`、`naming`、`microservice`、`ai` 或空。空值表示当前部署中
  适用的能力全部开启。
- 集群 member 来源可以是 `conf/cluster.conf` 或 `nacos.member.list`。
- `EnvUtil.getAvailableProcessors` 提供进程级 processor 数量抽象，返回值不得小于 1。

环境值是运行时配置，不是领域数据。领域可以使用环境值选择实现路径，但除非领域规范显式说明，
不得把环境 key 纳入资源身份模型。

## 5. 预置属性与动态配置

Core 启动会从配置好的 application 配置资源加载 `application.properties`，并写入
`nacos_application_conf` property source。

动态配置规则：

- 启动时加载的 application property source 是服务端配置源，不是 Config 领域资源。
- Core 启动会监听 Nacos conf 目录，并在 `application.properties` 变化时重新加载内容。
- 重新加载会通过 `NotifyCenter` 发布 `ServerConfigChangeEvent`。
- 继承 `AbstractDynamicConfig` 的组件必须订阅 `ServerConfigChangeEvent`，从 `EnvUtil` 重新读取值，
  并在 reload 失败时保留上一份有效值。
- 动态服务端配置只应覆盖明确可安全刷新的运行时参数。不得静默改变资源身份、存储 schema、公开
  API 形态或插件类型归属。

## 6. 自定义环境插件

当 `nacos.custom.environment.enabled` 为 true 时，`EnvUtil.customEnvironment` 会使用
`CustomEnvironmentPluginManager` 获取自定义属性值，并将它们作为第一优先级 property source。

自定义环境规则：

- 自定义环境处理属于启动/环境准备阶段，不属于领域请求处理。
- 自定义环境插件可以把配置 key 转换为运行时值，但不得修改领域状态或执行业务写入。
- 因为自定义 property source 会被放到第一位，自定义值可以覆盖低优先级 property source。插件
  实现必须文档化并约束自己控制的 key。
- 自定义环境处理必须发生在依赖被覆盖值的组件开始服务请求之前。

## 7. Application Context 与 Started 状态

`ApplicationUtils` 是共享 application context holder 和 started-state 门面。

Application context 规则：

- 第一个初始化的 Spring context 会被保存为全局 context。当某个子 context 以前一个 context 作为
  parent 初始化时，第一个子 context 会成为保存的 context。
- 共享 bean 查询、事件发布、资源查询和 class loader 访问只有在构造器注入不适用时才应通过
  `ApplicationUtils` 执行。
- `ApplicationUtils.started` 是 gRPC request acceptor 的进程就绪门禁。需要完整启动服务的请求在
  该值为 false 时必须拒绝流量。
- started 标记必须由生命周期代码维护，不应由领域 service 设置。

## 8. Module State 与 Server State

`ModuleStateBuilder`、`ModuleStateHolder` 和 server state service 暴露面向运维的运行时状态。

Module state 规则：

- Module state builder 通过 Nacos SPI 加载。
- builder 可以通过 `isIgnore` 跳过自身。
- builder 必须声明自己是否匹配当前 `DeploymentType`。
- 可缓存 builder 构建一次。不可缓存 builder 可以在查询状态时重建。
- Module state 是诊断和运维状态，不得包含密钥、完整黑盒 Config content 或高基数用户数据。
- Server state 聚合可以在 console 或 maintainer 路径中合并本地 module state 与远端 server
  state，但它仍然是运维视图。

Health、readiness、metrics 和 diagnostics 的可观测形态由
[可观测钩子规范](foundation-observability-hooks-spec.md)进一步定义。

## 9. 生命周期边界

- 生命周期代码负责准备进程，不定义 Config、Naming、AI 或 Auth 资源语义。
- 环境属性配置行为，但领域规范决定某个行为是公开、内部、兼容还是待移除。
- 启动 watcher 和动态配置事件是本地进程机制。当领域需要跨节点一致性时，必须通过持久化、AP
  一致性、CP 一致性或内部 RPC 实现。
- 关闭和启动失败处理必须以可控顺序释放公共 executor、`NotifyCenter`、文件 watcher 和 Spring
  context。
- 新组件需要启动钩子时，应优先使用 `NacosStartUp`、Spring lifecycle callback 或模块级 lifecycle
  抽象，而不是临时静态初始化。

## 10. 待处理问题

- 代码中存在部署类型名 `serverWithMcp`，但 `NacosBootstrap` 尚未将其作为可接受分支接入。正式
  暴露为支持的部署类型前，应先定义它的进程行为。
- 部分模块仍因历史兼容直接读取少量 system property。新的服务端代码应优先使用 `EnvUtil`，除非
  底层 JVM 集成明确需要直接访问。

## 11. 相关规范

- [Nacos 设计规范](nacos-design-spec.md)
- [基础能力规范](foundation-capabilities-spec.md)
- [请求过滤与运行时上下文规范](foundation-request-context-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [可观测钩子规范](foundation-observability-hooks-spec.md)
- [插件规范](../plugin/plugin-spec.md)
- [环境插件规范](../plugin/environment-plugin-spec.md)
