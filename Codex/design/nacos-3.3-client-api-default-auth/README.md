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

# Nacos 3.3 Client API 默认鉴权与统一 IT 改造方案

| 项目 | 内容 |
| --- | --- |
| 状态 | 本仓库改造与回归已完成：越界产品修复已回滚并登记，精确失败项已禁用；阶段 7 外部生态 PR 待各仓库推进 |
| 目标版本 | Nacos 3.3.0 |
| 关联 Issue | [alibaba/nacos#15357](https://github.com/alibaba/nacos/issues/15357) |
| 分析基线 | `upstream/develop`，commit `208a317a406a065f857d40bb947b8a0a69c29def`，2026-09-02 |
| 当前范围 | 主仓库默认值、HTTP API IT、Java Client SDK IT、Maintainer SDK IT、CI、部署生态与升级验证 |

实施期间由默认鉴权 IT 暴露、但不属于本次改造范围的产品问题，统一记录在
[默认鉴权 IT 改造中发现的既有产品问题](UNEXPECTED_PRODUCT_FINDINGS.md)。这些问题不在本次变更中
直接修复；回滚后的失败场景按编号显式禁用并保留恢复条件。

## 实施进度

| 阶段 | 状态 | 已完成 / 当前门禁 |
| --- | --- | --- |
| 阶段 0：规范、契约与覆盖基线 | 已完成 | 中英文正式规范、HTTP/SDK 测试规范、受保护 Controller 与覆盖基线已更新；RAT 和文档检查通过。 |
| 阶段 1：统一鉴权测试基础设施 | 已完成 | 已实现统一身份属性、安全的环境变量密码注入、管理员/普通用户初始化脚本、缓存开启下的有界权限收敛、OpenAPI 显式身份与 external helper、Auth Scope Guard、Java SDK/Maintainer SDK auth-on smoke，以及临时 migration workflow；Auth IT 115/115、Java SDK smoke 1/1、Maintainer SDK smoke 1/1 均在真实 standalone server 上通过。 |
| 阶段 2：HTTP 全功能迁移 | 已完成 | OpenAPI helper 按 Client/Admin/Console/Auth 路径选择最小必要身份，公开和外部端点显式匿名；AI 私有夹具通过 owner 或精确 visibility grant 提供可见性。鉴权开启、默认缓存开启的完整 OpenAPI IT 通过 291 个测试，0 失败、0 错误、2 个条件跳过。 |
| 阶段 3：鉴权插件与安全场景合并 | 已完成（有登记缺口） | 旧 Auth IT 已迁入 OpenAPI；Auth API 4/4 Covered；386 个 `@Secured` method 均登记并与源码强校验。回滚越界产品修复后，完整 OpenAPI IT 发现 431 项：423 通过、0 失败、8 跳过，其中 3 项精确关联 `DAUTH-F01/F03`。 |
| 阶段 4：Java Client SDK 默认鉴权验证 | 已完成（有登记缺口） | 功能矩阵默认使用普通读写身份。默认与 Jackson 3 各发现 101 项：81 通过、0 失败、20 跳过；其中 8 项精确关联 `DAUTH-F04/F05`，其余为环境定向场景。 |
| 阶段 5：Maintainer SDK 默认鉴权验证 | 已完成（有登记缺口） | Core/Config/Naming/AI/Agent/MCP 功能在管理员身份下验证。默认与 Jackson 3 各发现 46 项：44 通过、0 失败、2 跳过；其中 1 项关联 `DAUTH-F04`，1 项由可靠性套件实际执行并通过。 |
| 阶段 6：生产默认值与主 CI 最终切换 | 已完成 | 模板、Java 回退、插件策略和 Prometheus 条件统一缺省为 true；旧 Auth IT 已迁移并删除；统一功能工作流在一个默认鉴权环境中覆盖 OpenAPI、两套 Java SDK 和两套 Maintainer SDK；A2A/MCP 历史迁移另由独立 workflow 承载；发行包默认值及动态 false/true 兼容均已验证。 |
| 阶段 7：部署生态与升级兼容 | 审计完成，待外部 PR | 已按各仓库最新默认分支固定准确路径、三态/凭据契约、在途 PR 冲突和独立测试门禁；详见 [Stage 7 部署生态交接](STAGE7_ECOSYSTEM_HANDOFF.md)。 |
| 阶段 8：默认鉴权深度可靠性 | 已完成（有登记缺口） | Config、Naming、Lock、Maintainer、Jackson 3 standalone 重启和三节点安全矩阵、rolling restart、peer restart 通过；Agent standalone restart 与固定节点变更精确禁用为 `DAUTH-F05`，报告为 `passed-with-disabled`。 |

为避免 Failsafe XML 和命令行泄漏敏感值，用户名及非敏感开关可使用
`nacos.test.auth.*` Maven 属性，密码由对应 `NACOS_TEST_AUTH_*_PASSWORD`
环境变量注入；测试代码仍允许本地通过同名 system property 显式覆盖。

## 1. 结论摘要

Nacos 3.3 将 `nacos.core.auth.enabled` 的缺省语义从关闭调整为开启，使 Client OpenAPI、Java
Client SDK 和相应 gRPC 请求默认进入鉴权流程。显式配置 `false` 的已有部署继续保持关闭，不因升级被
强制覆盖；新安装、未显式配置以及使用新模板的部署获得新的安全默认值。

测试体系采用一套默认鉴权全开的标准基线：同一个 CI Job 启动一次 Nacos，依次运行 HTTP API、
Java Client SDK 和 Maintainer SDK 的完整功能测试，并在同一环境中验证身份、权限和异常场景。
这里的“一个 IT”是指一套服务端基线和一条主 CI 流程，不是合并 Maven 模块。三类 IT 继续保留独立
模块、Failsafe profile、测试报告和失败定位能力：

- `test/openapi-test`：HTTP Client、Admin、Console、Auth API 及外部协议适配器；
- `test/java-sdk-test`：面向应用的公开 Java Client SDK；
- `test/maintainer-sdk-test`：面向管理员的 Maintainer SDK；
- 现有 `test/auth-test` 的用例与覆盖清单最终迁入 `test/openapi-test`，独立的
  `auth-it.yml` 在切换完成后删除。

业务功能测试必须使用合法身份验证真实业务结果，鉴权测试作为覆盖层叠加在功能测试之上，不能用少量
鉴权探针替代原有业务、边界、监听、Watch、Redo、错误映射和生命周期场景。默认鉴权信息缓存保持开启，
测试通过有界重试等待权限变更收敛，不再为了测试方便强制关闭缓存。

本文件是实施设计，不替代 `specs/` 下的正式规范。行为修改开始前，需要先在 Issue 中确认方向，并在
同一设计变更中更新相应中英文规范。

## 2. 规范依据

实施与验收以以下规范为准：

- [鉴权与权限规范](../../../specs/zh-cn/auth/auth-permission-spec.md)
- [默认鉴权插件实现规范](../../../specs/zh-cn/auth/default-auth-plugin-spec.md)
- [HTTP API 鉴权规范](../../../specs/zh-cn/http-api/authorization-spec.md)
- [兼容与废弃策略规范](../../../specs/zh-cn/design/compatibility-deprecation-spec.md)
- [服务端生命周期与环境配置规范](../../../specs/zh-cn/design/foundation-server-lifecycle-env-spec.md)
- [API 集成测试规范](../../../specs/zh-cn/testing/api-integration-test-spec.md)
- [Java SDK 集成测试规范](../../../specs/zh-cn/testing/java-sdk-integration-test-spec.md)

实施时还需要按受影响领域读取 Config、Naming、AI、Lock、Core、Console、SDK、Client Runtime、
Connection/Failover、Local Cache/Redo、Push/Reconnect 等规范。若本方案与正式规范冲突，以正式规范和
维护者最终决议为准，并同步修订本方案。

## 3. 当前状态与问题

### 3.1 默认值存在多个语义入口

| 位置 | 当前行为 | 目标行为 |
| --- | --- | --- |
| `distribution/conf/application.properties` | `nacos.core.auth.enabled=false` | 新发行包模板改为 `true` |
| `bootstrap/src/main/resources/application.properties` | 同上 | 与发行包模板同步改为 `true` |
| `NacosServerAuthConfig#getConfigFromEnv` | 属性缺失时回退 `false` | 属性缺失时回退 `true` |
| `AuthPluginTypePolicy#isActive` | Client 鉴权缺失时按 `false` 判断 | 与新的缺省语义保持一致 |
| `PrometheusAuthFilter` | 只有属性显式为 `true` 才创建 | 属性缺失时也按 Client 鉴权开启处理 |
| Admin API 鉴权 | 默认 `true` | 保持不变 |
| Console API 鉴权 | 默认 `true` | 保持不变 |
| 默认鉴权信息缓存 | 默认 `true` | 保持不变，并在 IT 中真实验证 |

两个 `application.properties` 必须同时修改；
`bootstrap/src/test/java/com/alibaba/nacos/bootstrap/ApplicationPropertiesConsistencyTest.java`
负责检查二者一致性。只修改模板而不修改 Java 回退和条件 Bean，会使“未设置属性”的嵌入式、自定义启动
或测试环境产生不同语义。

### 3.2 当前 CI 被拆成两套相反环境

`it-new.yml` 构建发行包后，显式把 Client、Admin、Console 三类鉴权全部改为 `false`，再运行 HTTP、
Java SDK、Maintainer SDK 及 Jackson 3 组合。因此大部分真实功能从未在鉴权开启时执行。

`auth-it.yml` 则显式开启三类鉴权、初始化管理员、关闭鉴权信息缓存，仅运行 `test/auth-test`。该模块对
受保护 Controller 做代表性鉴权探针，对默认鉴权插件 API 和 URI 规范化攻击做集中验证，但不承担全量
业务功能覆盖。

这造成以下问题：

- 功能正确和鉴权正确由两套环境分别证明，无法证明二者组合后的真实行为；
- 两个工作流重复构建、启动、等待和收集故障信息；
- Java Client SDK 与 Maintainer SDK 的鉴权配置、登录、续期和错误映射没有系统验证；
- Auth IT 强制关闭缓存，偏离用户默认运行方式；
- 主 IT 对鉴权开关做文本覆盖，不能发现发行包默认值回归；
- 部署模板、Docker、Helm、Operator 的“未设置”语义可能与主仓库不一致。

### 3.3 当前覆盖基线

完整冻结清单见
[Default-Auth Migration Coverage Baseline](AUTHORIZATION_COVERAGE_BASELINE.md)。
以分析基线中的覆盖注册表计，HTTP API 有 85 个场景行：

| API 面 | 场景行 | Covered | Partial | Pending |
| --- | ---: | ---: | ---: | ---: |
| Client OpenAPI | 14 | 13 | 1 | 0 |
| Admin API | 38 | 31 | 7 | 0 |
| Console API | 29 | 24 | 5 | 0 |
| Auth API | 4 | 0 | 2 | 2 |
| 合计 | 85 | 68 | 15 | 2 |

现有 Auth IT 对 49 个受保护 Controller 选取代表接口，验证无身份、错误身份、正确身份无权限和有权限
四种状态；默认用户、角色、权限、可见性 Controller 另做完整工作流。现有 Java SDK 与 Maintainer
SDK 已覆盖大量业务、边界、监听、Watch、生命周期及异常场景，但覆盖注册表明确把鉴权开启行为列为缺口。

上述数字是设计基线而不是永久常量。每一阶段都必须以当时分支最新的覆盖注册表重新计算，禁止为了完成
迁移而删除场景、降低状态或缩小断言。

## 4. 目标与非目标

### 4.1 目标

1. 属性未配置时，Client API 鉴权在所有运行入口统一为开启。
2. 显式 `nacos.core.auth.enabled=false` 继续有效，并有自动化兼容验证。
3. Required CI 在发行包默认鉴权全开的单机环境中验证 HTTP、Client SDK 和 Maintainer SDK。
4. 保留现有功能、边界、异常、监听、Watch、Redo、生命周期及 Jackson 3 场景，不发生覆盖回退。
5. 所有默认鉴权插件 API 达到完整的功能与鉴权场景覆盖，Auth API 四个场景行达到 4/4 Covered。
6. 所有受保护 HTTP 操作都有可审计归属：直接鉴权测试、等价鉴权组，或明确记录的公开/例外端点。
7. Client SDK 使用普通、最小必要权限的应用身份；Maintainer SDK 使用管理员或明确的管理权限身份。
8. 验证 HTTP、gRPC、AUTO、multipart、listener、subscription、Watch、Redo、重连和 shutdown。
9. Docker、Helm、Operator、手工升级和安装脚本对“未设置、显式 true、显式 false”有一致且可验证的语义。
10. CI 故障时保留脱敏后的服务端日志和三类测试报告，能够定位功能、身份、权限或环境问题。

### 4.2 非目标

- 不把三类 IT 合并为一个 Maven 模块，也不混用各自的 Failsafe profile。
- 不改变 Admin API 和 Console API 已经默认开启的语义。
- 不改变默认鉴权插件的 RBAC 资源模型、公开 Java SDK 接口或业务 API 契约。
- 不把默认插件描述为适合直接暴露公网的完整强安全方案。
- 不要求显式关闭鉴权时运行第二套完整功能 IT；兼容性由小而精确的 smoke 验证。
- 不在本次改造中补齐与默认鉴权无关的所有既有 Partial 场景，但必须保留并继续记录这些缺口。
- 不在主仓库 PR 中直接修改外部部署仓库；外部仓库按独立阶段和独立 PR 验证。

## 5. 核心设计原则

### 5.1 鉴权是功能覆盖层，不是替代层

每个已有成功场景仍要验证真实业务结果。例如发布配置后查询内容、注册实例后查询状态、创建 AI 资源后
读取版本、创建命名空间后检查更新和删除。测试不能只以“不是 403”作为成功。

在此基础上，再按身份与权限矩阵验证认证失败、权限拒绝、读写隔离和资源边界。这样一次标准运行同时证明
业务功能与鉴权功能。

### 5.2 默认路径必须真正使用默认值

最终 CI 不得写入以下开关：

```properties
nacos.core.auth.enabled
nacos.core.auth.admin.enabled
nacos.core.auth.console.enabled
nacos.plugin.auth.nacos.caching.enabled
```

CI 只配置运行所必需且不应提供公共默认值的 token secret、server identity，以及与特定功能测试有关的
容量或加速参数。启动前必须直接断言打包后的配置包含预期默认值，以捕捉模板构建或打包回归。

### 5.3 身份职责清晰

标准测试身份如下：

| 身份模式 | 用途 | 权限原则 |
| --- | --- | --- |
| `Admin` | Admin、Console、Auth API、Maintainer SDK | 全局管理员，验证管理能力，不用于普通 Client 成功路径 |
| `ClientReadWrite` | Client OpenAPI 和 Java Client SDK 正常功能 | 仅授予测试使用的 Config、Naming、AI、Lock 资源读写权限 |
| `ClientReadOnly` | 读写隔离 | 允许读，写必须拒绝 |
| `ClientNoPermission` | 已认证但无权限 | 身份有效，所有受保护业务访问拒绝 |
| `Anonymous` | 无身份请求与明确公开端点 | 受保护 API 拒绝；公开端点按规范成功 |
| `InvalidCredential` | 错误、空白、过期或格式非法的显式凭据 | 必须认证失败，不能降级为匿名身份 |

权限字符串必须从生产 `ResourceParser`、`@Secured` 元组和默认插件 canonical 资源格式推导，不在测试中
发明另一套资源命名。动态资源优先在隔离 namespace/group 中授权；确需通配符时，只覆盖该测试域并验证
相邻资源仍被拒绝。

### 5.4 公共端点和外部请求必须显式

通用 HTTP helper 不再以“空 Header”隐式表示任意语义，而应显式选择身份模式。访问 Nacos 的 raw
request 与访问 ARD 等外部端口的 raw request 必须使用不同 helper，防止把 Nacos 管理员 token 泄漏给
外部服务。

公开健康检查、登录、一次性管理员初始化以及规范允许匿名的端点必须逐项登记。允许匿名的 AI 端点仍要
验证“无显式凭据可匿名、提供空白或错误凭据不得匿名降级”。

### 5.5 测试默认缓存与最终一致性

`nacos.plugin.auth.nacos.caching.enabled=true` 保持默认。用户、角色或权限修改后，通过有明确截止时间的
轮询等待生效；不得使用无界等待，也不得以固定长时间 sleep 作为成功条件。

### 5.6 运行中持续验证鉴权未被关闭

每个大测试阶段前后运行 Auth Scope Guard，分别检查 Client、Admin、Console：

- 无身份访问一个稳定的受保护接口，必须得到预期的 403/`ACCESS_DENIED`；
- 使用对应合法身份访问同一接口，必须得到正确业务响应；
- 检查公开健康接口仍可用；
- 不允许前一测试模块通过动态配置污染后一模块。

## 6. 目标测试架构

```text
Build release package
        |
        v
Assert packaged auth defaults + configure only secrets/identity/test fixtures
        |
        v
Start one standalone Nacos with Client/Admin/Console auth enabled by default
        |
        v
Bootstrap Admin -> create ClientReadWrite/ReadOnly/NoPermission -> grant permissions
        |
        v
Auth Scope Guard
        |
        +--> HTTP API IT (functional + Auth API + authorization/security)
        |         |
        |         v
        |     Auth Scope Guard
        |
        +--> Java Client SDK IT (default adapter + Jackson 3)
        |         |
        |         v
        |     Auth Scope Guard
        |
        +--> Maintainer SDK IT (default adapter + Jackson 3)
                  |
                  v
              Auth Scope Guard
                  |
                  v
        Explicit Client-auth false/true compatibility smoke
                  |
                  v
        Always collect reports/logs and stop server
```

模块职责保持如下：

| 模块 | 正常身份 | 核心责任 | 独立 profile |
| --- | --- | --- | --- |
| `test/openapi-test` | Client API 用 `ClientReadWrite`；Admin/Console/Auth 用 `Admin` | HTTP 契约、业务副作用、默认鉴权 API、认证授权、安全 URI | `integration-test` |
| `test/java-sdk-test` | `ClientReadWrite` | 公开 Client SDK 的功能、传输、监听、缓存、Redo、生命周期和异常 | `java-sdk-integration-test` |
| `test/maintainer-sdk-test` | `Admin` | Maintainer SDK 管理能力、HTTP 登录/重登录、multipart 和错误映射 | `maintainer-sdk-integration-test` |

测试公共属性建议统一使用 `nacos.test.auth.*` 前缀，由 CI 通过 Maven system properties 注入。密码、token
secret 和 server identity 不得硬编码到测试报告或日志；测试输出最多显示用户名和身份模式，不显示密码、
access token 或 secret。

## 7. 场景覆盖模型

### 7.1 三层覆盖

| 层次 | 覆盖要求 | 完成标准 |
| --- | --- | --- |
| 功能契约层 | 所有已有 HTTP/SDK 业务、边界、异常和生命周期场景使用合法身份运行 | 场景数和覆盖状态不下降，断言仍验证真实结果 |
| 鉴权操作清单层 | 枚举所有受保护 Controller method / SDK 资源族，记录 `apiType + signType + parser + resource + action` | 每项都有直接测试、审核后的等价组，或明确的公开/例外说明 |
| 聚焦安全层 | 默认鉴权插件 API、资源 Parser、读写边界、URI 规范化和身份异常 | 每个安全敏感操作执行完整身份矩阵，并验证精确响应与副作用 |

普通业务 API 不应简单复制四遍完整业务流程。具有相同鉴权元组和资源 Parser 的操作可以组成等价组：
合法身份由各自功能 IT 证明，负向身份由组内代表操作证明。但以下情况不得只依赖等价组：

- 默认用户、角色、权限、可见性 API；
- 自定义 `ResourceParser` 或 `SPECIFIED` 资源；
- `ALLOW_ANONYMOUS`、`ONLY_IDENTITY` 或公开初始化行为；
- 文件上传、multipart、raw socket、路径规范化和跨端口请求；
- 曾发生过鉴权绕过或资源串权的操作。

### 7.2 通用鉴权矩阵

| 场景 | 预期 |
| --- | --- |
| 正确身份且有精确权限 | 进入业务逻辑，并满足业务结果断言 |
| 无身份 | HTTP 返回 403 和受控拒绝结果；SDK 返回受控异常或规定结果 |
| 错误、空白、过期或非法格式身份 | 认证失败，不能作为匿名处理 |
| 正确身份但无权限 | 403/受控权限异常，不产生业务副作用 |
| 只有读权限执行读操作 | 成功并返回正确资源 |
| 只有读权限执行写操作 | 拒绝且资源状态不变 |
| 有资源 A 权限访问同域资源 B | 拒绝，验证精确资源边界 |
| namespace/group/resourceName 邻近或通配符边界 | 只允许匹配资源，不发生前缀、大小写或分隔符串权 |
| 全局管理员 | 允许管理操作，并验证实际结果 |
| 普通用户访问管理员操作 | 拒绝，不因拥有普通领域权限而提升 |

### 7.3 HTTP API 场景

| API 面 | 合法身份功能验证 | 重点鉴权与异常验证 |
| --- | --- | --- |
| Client Config | publish/query/CAS/remove/listener/gray 等现有场景 | Config 资源精确匹配、读写分离、namespace/group/dataId 邻接资源 |
| Client Naming | register/query/list/subscribe/deregister 等现有场景 | Naming 服务资源、group/cluster/service 边界、写拒绝无实例副作用 |
| Client AI | Agent/MCP/Prompt/Skill/AgentSpec、Search、Discover、Watch、Endpoint | AI 资源类型、owner/visibility、匿名显式凭据、HTTP/gRPC 等价性 |
| Client Lock | acquire/compete/release/expire | lock key 资源、无权限不获取锁、失败后锁状态不变 |
| Admin Core | state、namespace、plugin、cluster/diagnostic 的现有安全场景 | 普通用户拒绝、管理资源精确授权、公开 state/health 边界 |
| Admin Config/Naming/AI | 保留全部 CRUD、分页、导入导出、上传和生命周期场景 | Admin scope、multipart、资源 Parser、错误身份与无权限 |
| Console | 保留 Core/Config/Naming/AI 全部 UI 后端契约 | Console scope、登录身份、公开 announcement/guide/health 清单 |
| Auth User | bootstrap、login、create/list/search/update-password/delete | 每个受保护操作执行四身份状态；错误登录不泄漏用户存在性 |
| Auth Role | create/list/search/delete | 每个操作四身份状态、重复/不存在/模糊搜索和删除后状态 |
| Auth Permission | create/list/exist/delete | 每个操作四身份状态、action/resource 校验、重复与删除后状态 |
| Auth Visibility | grant/revoke | Admin/owner、非 owner、无权限、资源不存在、`w` 到 `rw` 归一化 |
| URI 安全 | 不适用 | 编码、双编码、dot segment、matrix param、分隔符、Unicode、非法 UTF-8 等不得绕过 |
| ARD/外部适配器 | 保留现有独立端口契约 | 不向外部端口附加 Nacos 凭据，适配器自己的公开/鉴权语义独立验证 |

HTTP 覆盖文档必须同步更新：

- `test/openapi-test/API_TEST_COVERAGE.md`
- `test/openapi-test/CLIENT_API_TEST_SCENARIOS.md`
- `test/openapi-test/ADMIN_API_TEST_SCENARIOS.md`
- `test/openapi-test/CONSOLE_API_TEST_SCENARIOS.md`
- `test/openapi-test/AUTH_API_TEST_SCENARIOS.md`

Auth API 的目标是 4/4 `Covered`。其他已有 `Partial` 必须保留原因；鉴权改造不能把原本 `Covered` 的
业务行降级为只验证身份。

### 7.4 Java Client SDK 场景

所有现有 Client SDK IT 使用 `ClientReadWrite` 通过公开 factory 创建客户端，不直接调用实现内部。新增
`ClientAuthenticationJavaSdkITCase` 或等价场景集，至少覆盖：

| 领域 | 功能回归 | 鉴权专项 |
| --- | --- | --- |
| Config | publish/query/CAS/remove、filter、listener、fuzzy watch、shutdown | 无凭据、错误凭据、无权限、只读成功/写拒绝、拒绝后无缓存伪成功 |
| Naming | register/query/select/list/subscribe/deregister、batch、persistent、shutdown | 读写分离、service 精确权限、subscribe 回调身份、拒绝后无本地缓存伪成功 |
| AI | Agent/MCP/Prompt/Skill/AgentSpec、Search/Discover、Watch、Runtime Endpoint、listener | HTTP/GRPC/AUTO 携带身份一致；匿名与显式错误凭据；visibility/owner；Watch 重连 |
| Lock | acquire/compete/release/reacquire/expire | 无权限不持锁、读写或 lock action 语义、错误身份为受控 `NacosException` |
| 公共生命周期 | factory、server address、namespace、adapter、shutdown | 无凭据客户端可构造但远端受保护操作受控失败；token 刷新；关闭后线程释放 |

默认 JSON adapter 与 Jackson 3 adapter 都必须运行。针对 HTTP、gRPC、AUTO 的鉴权验证至少覆盖每个
传输一条成功与一条身份/权限失败，并在各领域完整功能矩阵中保留已有传输覆盖。

重启与多节点故障场景验证同一 SDK 实例在 token、连接和 Watch 重建后的行为，不能通过新建客户端掩盖
Redo 或重新认证问题。

### 7.5 Maintainer SDK 场景

所有现有 Maintainer SDK IT 使用 `Admin` 身份，保留 Core、Config、Naming、AI 的全部功能、边界和错误
映射。新增 `MaintainerSdkAuthenticationITCase` 或等价场景集，至少覆盖：

| 场景 | 预期 |
| --- | --- |
| 无凭据和错误凭据 | 管理调用受控失败，不成为 JSON 解析或空指针异常 |
| 普通 Client 用户访问管理员 API | 拒绝，即使该用户有同领域 Client 资源权限 |
| 管理员访问 Core/Config/Naming/AI | 成功并验证真实管理副作用或返回模型 |
| 明确管理资源权限的非全局管理员 | 仅在规范允许的管理 API 上成功，不能越权到其他管理资源 |
| token 过期或服务端返回 403 | HTTP proxy 重新登录/重试行为有界，不无限循环，不重复非幂等副作用 |
| multipart/ZIP 上传 | 鉴权 header 正确附加，文件与表单未被登录流程破坏 |
| 400/404/409 等业务错误 | 启用鉴权后仍映射为原有受控 SDK 异常，不被错误归类为 403 |
| shutdown | 登录刷新、HTTP 连接和 SDK executor 全部释放 |

默认 adapter 与 Jackson 3 adapter 都必须执行。覆盖注册表中当前“auth-enabled behavior deferred”的说明
应在完成后移除，并改为记录真实覆盖或仍不可执行的精确缺口。

## 8. 最终 CI 流程

最终保留一个 Required 主流程，建议继续使用当前 `Integration Test` 的稳定 check name，避免分支保护
因删除 `Auth Integration Test` 而出现悬空要求。切换前必须核对仓库 branch protection 配置。

### 8.1 主流程步骤

1. Checkout，安装 JDK 17。
2. `mvn -B clean install -Prelease-nacos -DskipTests=true` 构建发行包。
3. 定位打包后的 `application.properties`。
4. 断言 Client、Admin、Console 鉴权默认值为 `true`，默认鉴权缓存为 `true`。
5. 只写入测试 token secret、server identity、ARD/容量及稳定态 readiness 加速等测试专用配置。
6. 启动 standalone server，等待公开 liveness/readiness 和必要的外部适配器端口。
7. 调用一次性管理员初始化端点，创建 `Admin`；重复初始化必须失败。
8. 通过 Auth API 创建普通测试用户、角色和权限，并用有界轮询等待缓存收敛。
9. 运行 Auth Scope Guard。
10. 运行 `test/openapi-test` 的全量 HTTP IT，包括迁入的 Auth/Security 场景。
11. 再次运行 Auth Scope Guard。
12. 运行 Java Client SDK 默认 adapter IT。
13. 再次运行 Auth Scope Guard。
14. 运行 Maintainer SDK 默认 adapter IT。
15. 运行 Java Client SDK 和 Maintainer SDK 的 Jackson 3 IT。
16. 再次运行 Auth Scope Guard。
17. 在所有主体测试完成后，执行显式 Client auth `false -> true` 动态兼容 smoke。
18. 无论成功或失败，都上传脱敏日志、Failsafe reports、Surefire reports 和启动配置摘要，并停止服务端。

### 8.2 显式关闭兼容 smoke

该 smoke 只改变 `nacos.core.auth.enabled`，不改变 Admin 和 Console 开关：

1. 动态设置 Client auth 为 `false`，等待当前节点配置生效；
2. 无身份访问一个受保护 Client API，验证关闭后的兼容行为；
3. 验证 Admin 和 Console 受保护 API 仍拒绝无身份请求；
4. 通过一个轻量 Client SDK 功能验证显式关闭仍可用；
5. 动态恢复 Client auth 为 `true`；
6. 验证无身份 Client 请求重新被拒绝，合法 Client 身份恢复成功；
7. 检查切换过程没有无限重试、线程泄漏或本地缓存伪成功。

这是显式关闭的兼容门禁，不承担第二套完整 auth-off 功能回归。集群环境的开关切换必须在所有节点应用，
单节点动态 reload 不能证明集群一致性。

### 8.3 故障诊断要求

- 日志上传使用 `if: always()`；服务端关闭也使用 `if: always()`。
- 输出当前三类 auth scope、插件类型和缓存开关，但不输出 secret、密码或 access token。
- 三个 Maven 模块分别保存报告，阶段名包含 HTTP、Client SDK、Maintainer SDK 和 adapter 类型。
- Auth Scope Guard 失败时立即停止后续业务测试，避免产生大量误导性失败。
- 测试创建的用户、角色、权限和业务资源使用稳定前缀与随机后缀，并在失败路径清理。

### 8.4 功能 IT 与迁移 IT 的边界

- `.github/workflows/it-new.yml` 只验证默认鉴权开启后的稳定功能：OpenAPI、Java Client SDK、
  Maintainer SDK、两种 JSON adapter、鉴权矩阵和显式 Client auth 关闭/恢复兼容性。
- MCP 功能用例只接受稳定 `LIFECYCLE_MANAGED` 结果。主流程可以等待全新空实例达到稳定态，
  但不预置历史资源、不验证 `SYNCING` 门禁，也不执行历史数据对账断言。
- `.github/workflows/migration-it.yml` 单独承载可随升级窗口一起移除的迁移能力。MCP Job 使用同一
  持久化实例执行 `SYNCING -> LIFECYCLE_MANAGED` 两阶段；A2A Job 执行历史定义、Runtime Shadow、
  Quiescing、终态 Marker 和回退边界。
- 迁移 Job 显式关闭三类鉴权，使失败只归因于迁移状态机；默认鉴权与权限组合由功能 Job 完整负责。
- 迁移类必须通过 system property 显式启用，普通 Failsafe discovery 只能将其识别为条件跳过，
  不能在后台任务竞争下同时接受切流前和切流后两种结果。
- A2A/MCP 迁移测试类、场景文档和 `migration-it.yml` 共享同一删除边界；项目停止支持对应平滑升级后，
  可以整体移除而不影响稳定功能覆盖。

## 9. 分阶段实施计划

阶段依赖如下：

```text
阶段 0 -> 阶段 1 -> 阶段 2 -> 阶段 3 --+
                  |                       |
                  +-> 阶段 4 -------------+-> 阶段 6 -> 阶段 7 -> 阶段 8
                  |                       |
                  +-> 阶段 5 -------------+
```

阶段 4 和阶段 5 可在阶段 1 完成后与 HTTP 改造并行，但阶段 6 只有在阶段 2、3、4、5 全部通过后才能
切换默认值和删除旧工作流。每个阶段都应形成独立 PR 或可独立回滚的 commit，并满足自己的退出门禁。

### 阶段 0：规范、契约与覆盖基线

**目标**：先固定行为和测试契约，不修改生产默认值。

**改造项**：

- 在 Issue 中确认缺省开启、显式 false 保留、旧手工配置不被自动覆盖、新模板默认开启的兼容规则；
- 更新中英文 auth、default-auth、authorization、compatibility、server-env 规范；
- 把 API IT 规范中的“单机通常关闭鉴权”调整为默认鉴权基线；
- 在 Java SDK IT 规范中明确 Client SDK 普通身份、Maintainer SDK 管理身份及鉴权场景；
- 冻结最新 HTTP、Java SDK、Maintainer SDK 场景注册表，列出公开端点和所有受保护操作；
- 确认主 CI check name 和 branch protection 迁移方式。

**测试项**：

- 中英文规范章节、配置名、默认值和兼容说明一致；
- 所有文档链接有效；
- HTTP 覆盖重新计算 strict/effective coverage；
- 受保护操作清单无未分类 method；
- 现有测试代码和工作流不变，原 CI 仍通过。

**独立验证**：文档 review、链接检查、`git diff --check`，以及现有 CI 全量运行。

**退出门禁**：维护者在 Issue 中确认实现方向和所需规范更新；覆盖基线进入版本控制。

### 阶段 1：统一鉴权测试基础设施

**目标**：在不改变生产默认值的前提下，让三类 IT 都能显式运行在鉴权全开环境。

**改造项**：

- 建立统一测试身份属性和安全的 CI 凭据注入；
- 实现管理员 bootstrap、用户/角色/权限创建和有界权限收敛 helper；
- OpenAPI 基类增加显式身份模式，区分 Nacos authenticated raw request 与 external raw request；
- Java SDK 基类默认注入普通 Client 用户名/密码；
- Maintainer SDK 基类默认注入管理员用户名/密码；
- 增加 Auth Scope Guard；
- 保留当前 auth-off 主流程，新增临时的显式 auth-on migration job 验证基础设施。

**测试项**：

- 管理员首次初始化成功、重复初始化拒绝；
- 三类普通用户登录和权限生效；
- Client/Admin/Console 三个 scope 的无身份拒绝与合法身份成功；
- 密码、token、secret 不进入日志和报告；
- ARD 外部端口不收到 Nacos auth header；
- 当前 auth-off CI 行为未受影响；
- 三个测试模块均可编译，helper 在失败路径关闭客户端和清理状态。

**独立验证**：临时 explicit-auth migration job 全部通过；现有两个工作流仍可并行通过。

**退出门禁**：三类 IT 都能通过统一属性选择合法身份，且无需各自实现一套登录逻辑。

### 阶段 2：HTTP 全功能迁移到显式鉴权开启环境

**目标**：所有已有 HTTP 功能测试使用正确身份运行，功能覆盖不下降。

**改造项**：

- Client OpenAPI 默认使用 `ClientReadWrite`；Admin/Console 默认使用 `Admin`；
- 审计所有 `Header.EMPTY`、直接 `NacosRestTemplate` 和 raw HTTP 调用；
- 公开端点显式使用 `Anonymous`，不再依赖基类的隐式空 Header；
- 保留所有业务、边界、异常、上传、下载、listener、Watch、ARD 和异步收敛场景；
- 每个 API 场景文档记录正常身份和鉴权等价组；
- 在 HTTP 模块前后执行 Auth Scope Guard。

**测试项**：

- 85 个基线场景行及迁移期间新增行全部执行；
- 每个 `Covered` 行仍验证业务状态与完整响应，不仅验证状态码；
- 参数 400、资源 404、冲突 409 等错误未被错误转换为 403；
- public/anonymous/adapter 请求无 token 泄漏；
- 默认鉴权缓存开启时测试稳定，无固定长 sleep；
- 测试结束后三个 scope 仍开启。

**独立验证**：在显式开启三类鉴权的 standalone server 上运行：

```bash
mvn -pl test/openapi-test -Pintegration-test -DskipTests=false verify
```

**退出门禁**：HTTP 功能覆盖数量和状态不低于阶段 0，所有隐式空身份调用已分类。

### 阶段 3：默认鉴权插件、权限边界与安全场景并入 OpenAPI IT

**目标**：完成 Auth API 全覆盖和可审计的 HTTP 授权/绕过防护。

**改造项**：

- 将 `AuthITCase`、`DefaultAuthApiITCase`、`ModuleAuthorizationITCase`、
  `AmbiguousUriAuthITCase` 重构并迁入 `test/openapi-test`；
- 合并已有 `UserLoginAuthApiITCase`、`VisibilityGrantAuthApiITCase`，消除重复和相反环境假设；
- 用户、角色、权限、可见性的每个受保护操作执行完整四身份矩阵和业务断言；
- 把 Controller 级 source completeness 提升为 method/operation 清单；
- 对 Config、Naming、AI、Console、自定义 Parser、`SPECIFIED` 资源建立精确资源边界矩阵；
- 保留并扩展 URI canonicalization/raw socket 绕过测试；
- 在默认缓存开启下验证权限新增、删除和撤销后的收敛。

**测试项**：

- Auth User/Role/Permission/Visibility 四行均为 `Covered`；
- 无身份、错误身份、有效无权限、有权限四状态的响应与副作用正确；
- ReadOnly 读成功、写拒绝且状态不变；
- 有 A 权限不能访问相邻 B，namespace/group/type/name/action 不串权；
- global admin、owner、普通用户语义正确；
- 登录错误不泄漏用户名是否存在；
- 显式错误凭据不能降级为匿名；
- 所有生产 `@Secured` method 都有清单归属；
- 编码、双编码、dot segment、matrix parameter、slash/backslash、Unicode 和非法请求目标不能绕过。

**独立验证**：`test/openapi-test` 全量 IT、Auth 场景选择运行、覆盖注册表重新计算。

**退出门禁**：Auth API 4/4 Covered，无未分类受保护 operation，旧 Auth IT 能力已由新模块完整承接。

**实施记录（2026-09-03）**：

- 新增实时的 [`AUTHORIZATION_OPERATION_COVERAGE.md`](../../../test/openapi-test/AUTHORIZATION_OPERATION_COVERAGE.md)，
  登记 56 个 Controller 的 386 个 `@Secured` method；测试同时校验 operation 名和完整注解元组，新增、删除、
  重命名或修改注解都必须显式更新覆盖归属；
- 50 个普通 Controller 代表操作执行无身份、错误身份、有效无权限和有权限四状态；默认 Auth API 的 13 个
  受保护操作由完整业务工作流直接覆盖；10 个 `ALLOW_ANONYMOUS` 和 3 个 Console `ONLY_IDENTITY` 操作逐项
  直接覆盖；
- Config、Naming 和 AI 的资源名、group、namespace、action、owner/visibility 及撤销收敛边界已加入；旧 URI
  canonicalization/raw socket 集合完整迁移；
- Client Skill 资源参数不一致，以及 MCP Manifest 即时可见性、后台 Search 投影身份问题，均属于既有
  产品缺陷；临时修复已撤回，分别记录为 `DAUTH-F01`、`DAUTH-F02`、`DAUTH-F03`。复跑后 F01
  精确禁用 1 项、F03 精确禁用 2 项，F02 未复现稳定失败；
- CI 在开始整套测试前等待 MCP 进入永久 `LIFECYCLE_MANAGED`，并把 lifecycle/search reconciliation
  周期缩短为测试专用的 1 秒，避免同一长套件跨越单向生命周期切换；
- Controller 矩阵复用预置无权限身份和管理员身份；精细资源授权由独立资源矩阵负责，使 51 项检查由
  599 秒降至 24.83 秒，保留匿名、错误身份、有效无权限和授权身份四态覆盖；
- 回滚越界产品修复后的最终全量 OpenAPI IT 共发现 431 项：423 通过、0 失败、0 错误、8 跳过；
  `DAUTH-F01` 精确禁用 1 项、`DAUTH-F03` 精确禁用 2 项，其余 5 项为既有环境条件跳过。

### 阶段 4：Java Client SDK 默认鉴权验证

**目标**：全部公开 Client SDK 功能在普通身份下运行，并验证认证、授权和传输生命周期。

**改造项**：

- 所有 Config、Naming、AI、Lock 客户端由公开 factory 注入 `ClientReadWrite` 凭据；
- 新增 Client SDK 鉴权专项场景；
- 覆盖 HTTP、gRPC、AUTO 的身份传播与错误映射；
- 对 listener、subscription、Watch、local cache、Redo、reconnect、shutdown 增加鉴权断言；
- 默认 adapter 与 Jackson 3 共用同一场景矩阵；
- 更新 `JAVA_SDK_IT_COVERAGE.md` 和 `JAVA_SDK_IT_SCENARIOS.md`。

**测试项**：

- Config/Naming/AI/Lock 原有全部功能、边界和异常场景通过；
- 无凭据、错误凭据、无权限和只读写拒绝均为受控 SDK 行为；
- 403 不被映射为连接超时、空结果或未受控运行时异常；
- 本地缓存不能让被撤权或错误身份产生伪成功；
- token 刷新、Watch 重连和 Redo 继续使用正确身份；
- 所有客户端 shutdown 后无残留线程或重复回调；
- 默认 adapter 与 Jackson 3 结果一致。

**独立验证**：

```bash
mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false verify
mvn -pl test/java-sdk-test -Pjava-sdk-integration-test,jackson3-sdk-test \
    -DskipTests=false verify
```

**退出门禁**：覆盖注册表不再把通用 auth-enabled 行为列为延期项，剩余缺口有精确原因。

**实施记录（2026-09-03）**：

- `JavaSdkBaseITCase` 在鉴权开启时默认向公开 Config、Naming、AI、Lock factory 注入普通读写身份，
  Maintainer fixture 单独使用管理员身份；密码只从环境变量进入 forked test JVM，不写入 Maven 命令、
  Failsafe XML 或 HTTP 调试日志；
- 新增跨模块鉴权矩阵：Config/Naming 验证读写、只读、无权限、匿名和错误身份及无副作用拒绝，AI
  在 HTTP、gRPC、AUTO 下验证读写边界；错误凭据不得降级为匿名 AI 的断言因复现 `DAUTH-F04`
  而精确禁用；
- Client 登录失败后可能匿名降级、REST debug 日志可能暴露请求体、异步 Agent Watch visibility
  复核依赖请求线程，以及 `NacosAiService.shutdown()` 未注销全局 notifier，均已从本次产品改动中
  撤回，分别记录为 `DAUTH-F04` 至 `DAUTH-F07`；F04/F05 的稳定失败已按具体测试方法禁用，F06
  未造成稳定 IT 失败，F07 仅通过测试侧日志级别避免凭据进入 CI 日志；
- 将 Lock 过期接管 IT 的 500ms 竞态租约调整为 5 秒，分别在默认与 Jackson 3 下定向通过，仍在 10 秒
  有界窗口内验证持锁竞争失败和到期接管成功；
- 隔离 standalone、默认鉴权和默认权限缓存下，默认适配器与 Jackson 3 各发现 101 项，最终均为
  81 通过、0 失败、0 错误、20 跳过；其中 `DAUTH-F04` 1 项、`DAUTH-F05` 7 项，另 12 项为
  migration/restart/cluster 环境条件场景。未删除测试或弱化断言。

### 阶段 5：Maintainer SDK 默认鉴权验证

**目标**：全部 Maintainer SDK 管理功能在管理员身份下运行，并验证管理权限、重登录和错误映射。

**改造项**：

- Core、Config、Naming、AI Maintainer client 默认注入 `Admin` 凭据；
- 新增 Maintainer SDK 鉴权专项场景；
- 验证 `ClientHttpProxy` 登录、auth header、multipart 和 403 后重新登录/重试；
- 保留全部原有管理业务、兼容 API、生命周期和异常场景；
- 更新 Maintainer SDK coverage/scenario 文档。

**测试项**：

- Core/Config/Naming/AI 现有测试全部通过；
- 无凭据、错误凭据和普通 Client 用户访问管理 API 被受控拒绝；
- 管理员成功，受限管理角色不能越权；
- multipart/ZIP 请求带正确身份且内容未损坏；
- 403 重登录有界且不会重复非幂等写入；
- 400/404/409 等原有错误映射保持；
- 默认 adapter 与 Jackson 3 一致；
- shutdown 释放登录刷新与 HTTP 资源。

**独立验证**：

```bash
mvn -pl test/maintainer-sdk-test -Pmaintainer-sdk-integration-test \
    -DskipTests=false verify
mvn -pl test/maintainer-sdk-test \
    -Pmaintainer-sdk-integration-test,jackson3-sdk-test \
    -DskipTests=false verify
```

**退出门禁**：各 Maintainer 覆盖行删除泛化的“auth-enabled deferred”，鉴权专项测试稳定通过。

**实施记录（2026-09-03）**：

- `MaintainerSdkBaseITCase` 在鉴权开启时默认使用管理员，同时支持读写、只读、无权限、匿名和错误凭据，
  Config/Core 与 Naming 客户端统一进入 cleanup/shutdown 栈；密码继续仅通过环境变量进入 forked JVM；
- 新增专项场景：Core 管理面验证管理员成功以及匿名、错误凭据、普通读写、无权限四类拒绝；Config 与
  Naming 按现有资源 RBAC 验证读写、只读、无权限及拒绝写无副作用；错误凭据不得匿名降级的 Skill
  list 断言复现 `DAUTH-F04`，保留并精确禁用；
- 通过替换客户端当前 token 模拟服务端拒绝，验证 `ClientHttpProxy` 在 15 秒上限内由定时登录恢复；恢复前
  的 namespace 创建未产生副作用，恢复后显式创建一次且列表中只有一个资源；
- Config/Core 的 `shutdown()` 已直接验证会停止鉴权刷新线程。`AiMaintainerService` 当前正式接口不暴露
  shutdown，已从泛化延期收敛为独立的 SDK/spec 缺口，本阶段不越权修改公开契约；
- 原有 Skill、批量 Skill 和 AgentSpec ZIP 上传全部在管理员身份下执行并回读内容，因此同时验证 multipart
  auth header 与字节内容；原有 400/404/409、生命周期和兼容 API 断言全部保留；
- 在全新 Derby、三个 scope 鉴权开启、默认鉴权缓存开启和稳定 MCP 生命周期下，默认适配器与 Jackson 3
  各发现 46 项：44 通过、0 失败、0 错误、2 跳过；1 项是可靠性套件已通过的重启场景，另 1 项为
  `DAUTH-F04`。

### 阶段 6：生产默认值与主 CI 最终切换

**目标**：在前述测试全部就绪后，修改产品默认值并只保留一套默认鉴权主 IT。

**改造项**：

- 修改两个 `application.properties` 的 Client auth 默认值和注释；
- 修改 `NacosServerAuthConfig` 缺失属性回退；
- 修改 `AuthPluginTypePolicy` 的缺省激活语义；
- 使 `PrometheusAuthFilter` 的条件语义与缺省开启一致；
- 为 absent/true/false、插件激活、动态 reload、Prometheus 条件和配置一致性补充单元测试；
- `it-new.yml` 切换到第 8 节流程，不再覆盖 auth scope 和 auth cache；
- 删除 `test/auth-test` 及其 aggregator/profile 配置；
- 删除 `auth-it.yml`，确认 Required check 迁移完成；
- 加入最终的显式 false/true 兼容 smoke 和 `always()` 故障收集。

**测试项**：

- 属性缺失为 true、显式 true 为 true、显式 false 为 false；
- 动态 false/true 切换在 standalone 上生效且恢复；
- admin/console 默认值不变；
- auth plugin 选择与启动校验正确；
- Prometheus auth Bean 在缺失/true/false 三种输入下正确；
- 两份模板完全一致；
- 最终打包产物默认值断言通过；
- HTTP、Client SDK、Maintainer SDK、Jackson 3 全量通过；
- 旧 Auth IT 的所有场景在新位置有一一映射。

**独立验证**：生产单元测试、三个模块的独立 IT 命令、最终 GitHub Actions 主流程。

Java 或测试代码提交前按模块执行：

```bash
mvn spotless:apply
mvn spotless:check
```

并运行受影响模块编译/测试以及项目要求的 pre-submission checks。

**退出门禁**：发行包和代码缺省语义统一为 true；显式 false 兼容；Required CI 只依赖统一默认鉴权流程。

**实施记录（2026-09-04）**：

- `distribution/conf/application.properties` 与 bootstrap 模板均将 Client auth 设为 true，
  `NacosServerAuthConfig`、`AuthPluginTypePolicy` 和 `PrometheusAuthFilter` 的缺失属性语义同步为 true；
  absent/true/false、动态 reload、插件策略、条件 Bean 和模板一致性的单元测试分别通过；
- `mvn -B clean install -Prelease-nacos -DskipTests=true` 完成 61 个模块构建；新生成的目录产物和 ZIP
  均逐项确认 Client/Admin/Console auth 与默认鉴权缓存为 true；
- `.github/workflows/it-new.yml` 保留原 `Integration Test` 工作流名称，将原有功能 IT 与 Auth IT 合并为
  一个默认鉴权 Job；正常启动不再重写三个 scope 或缓存开关，凭据仅通过临时环境变量进入 forked JVM，
  各测试阶段前后运行 Auth Scope Guard，并在 `always()` 阶段脱敏、归档和按精确 PID 清理；
- `test/auth-test`、其 aggregator module 和 `.github/workflows/auth-it.yml` 已删除，原 Auth API、URI
  绕过、scope 与权限矩阵在 `test/openapi-test` 中保留可追踪映射；
- 在全新解压的发行包、全新 Derby、默认四个鉴权/缓存开关不被覆盖的环境中，OpenAPI 为
  423/431 通过、8 跳过；Java SDK 默认与 Jackson 3 均为 81/101 通过、20 跳过；Maintainer SDK
  默认与 Jackson 3 均为 44/46 通过、2 跳过；所有执行项 0 失败、0 错误，缺陷跳过均引用
  [`UNEXPECTED_PRODUCT_FINDINGS.md`](UNEXPECTED_PRODUCT_FINDINGS.md)；
- 同一运行实例最终完成动态 `Client=false -> true`：关闭时匿名 Client API 为 200，Admin/Console
  仍为 403；恢复 true 后匿名 Client 为 403、合法普通用户为 200，配置文件最终恢复 true；
- GitHub develop 分支当前只要求 `license/cla` status check，原 Auth IT 并非 Required check；统一工作流
  保留稳定的 `Integration Test` 名称，因此删除旧工作流不会移除现有分支保护依赖。

**迁移测试拆分记录（2026-09-07）**：

- 从 `it-new.yml` 删除历史 A2A 多次重启和切流链，修复功能 Job 清理后仍访问已停止服务端的生命周期
  耦合；新增 `migration-it.yml`，以独立 MCP/A2A Job 保留原迁移覆盖和失败归因。
- 新增 `McpMigrationAdminApiOpenApiITCase` 与 `McpUpgradeMigrationJavaSdkITCase`，分别在显式
  `syncing`、`managed` Phase 下验证历史权威、生命周期门禁、数据对账、Client/Maintainer SDK 投影和
  清理；普通 MCP OpenAPI/Java SDK/Maintainer SDK 类只验证稳定功能，不再接受双态结果。
- 迁移 workflow 显式关闭鉴权，主功能 workflow 继续按发行包默认值开启 Client/Admin/Console 鉴权；
  两者的测试目的、状态准备、报告和可移除边界互不耦合。
- 本地使用同一 Derby 数据目录完成 MCP `syncing -> managed` 重启验证：两个 Phase 的 OpenAPI 与
  Java SDK 场景各 1 项通过、1 项按 Phase 条件跳过；稳定态 MCP OpenAPI 11 项、Java SDK 4 项、
  Maintainer SDK 2 项全部通过。工作流 YAML 解析、三个 IT 模块 Spotless 以及主 CI 等价的 61 模块
  compile/RAT/Checkstyle/SpotBugs/Spotless 检查均通过。

### 阶段 7：部署生态与升级兼容

**目标**：使主仓库之外的主流安装方式遵守相同默认与覆盖规则。

**改造项**：

- nacos-docker 自有 `application.properties` 默认改为 true，并更新 README/环境变量说明；
- Docker 保持 `NACOS_AUTH_ENABLE=false` 的显式关闭能力，修正文档中的错误拼写；
- Helm 暴露三态可表达的 Client auth value，并测试 unset/true/false；
- Operator 将无法区分未设置与 false 的普通 `bool` 改为可选/三态语义，避免新镜像默认值吞掉显式关闭；
- nacos-setup 保持已开启行为并增加回归；
- 官网 next 文档、部署文档和升级指南统一说明新安装与旧配置差异；
- 每个外部仓库使用独立 PR，不阻塞主仓库代码回滚。

**测试项**：

| 部署方式 | 未设置 | 显式 true | 显式 false |
| --- | --- | --- | --- |
| 发行包 | Client auth 开启 | 开启 | 关闭 |
| Docker | 使用镜像/模板新默认，开启 | 开启 | 关闭 |
| Helm | 不生成覆盖或生成 true，最终开启 | 开启 | 关闭 |
| Operator | `nil` 表示继承新默认 | 开启 | 明确生成 false |
| nacos-setup | 保持其安全默认 | 开启 | 按工具契约处理 |

另需验证：已有手工配置文件中的 `false` 不被升级覆盖；新模板为 true；Client 开关不会关闭 Admin/Console；
token secret 和 server identity 的生成/注入仍满足启动要求。

**独立验证**：各部署仓库自己的 render/unit/smoke CI，以及真实容器的 unset/true/false 启动矩阵。

**退出门禁**：主要安装路径没有“主仓库默认 true、部署模板实际 false”或“用户无法显式 false”的差异。

**审计与交接记录（2026-09-04）**：

- 已在 Docker、nacos-k8s、nacos-setup 和官网各自最新默认分支重新确认准确文件、运行语义、CI 与
  开放 PR；完整 commit 快照、文件级改造和测试矩阵见
  [Stage 7 部署生态改造交接与验收方案](STAGE7_ECOSYSTEM_HANDOFF.md)；
- Docker 镜像模板仍显式写 Client auth=false，入口脚本本身已正确保留 unset/true/false；现有 smoke
  只检查公开首页，需改为真实受保护 API 业务矩阵；
- Helm unset 会继承镜像默认，但没有一等显式 false，且当前 values 使用公开固定凭据；开放 PR `#475`
  的 false 默认和非稳定随机凭据不能直接复用；
- 官方 Operator 位于 `nacos-group/nacos-k8s/operator`。其普通 bool 会吞掉 enabled=false，并把
  cache omitted 与 false 混为一谈；凭据模型缺少一等 server identity，且没有生成逻辑行为测试；
- nacos-setup 的 Linux/Windows 新安装已显式写 true，但当前测试只做字符串存在性检查；官网 next
  的 auth、system config、upgrade、quickstart 和 deployment 仍需按 3.3 版本边界更新；
- 此步骤只完成可复现审计和外部交接，未在主仓库工作区伪造外部仓库改动。阶段 7 保持未完成，直到各
  外部 PR 的 render/unit/runtime 门禁全部通过。

### 阶段 8：默认鉴权深度可靠性

**目标**：在 Required 单机 IT 之外，验证重启、滚动升级和多节点场景中的身份与功能恢复。

**运行方式**：scheduled、manual 或带标签触发，不把高成本故障注入全部放入每个 PR；所有深度测试仍然
保持鉴权开启，不回退到 auth-off 环境。

**测试项**：

- 同一 standalone server 停止/启动后，原 Client SDK 实例重连、重新登录和 Redo；
- Config listener、Naming subscribe、AI Watch/Endpoint、Lock 状态按各自契约恢复；
- Maintainer SDK 在重启和 token 失效后恢复，失败重试不重复非幂等写入；
- 三节点使用一致 token secret 和 server identity，滚动停止/启动一个节点；
- HTTP/gRPC Watch 在节点切换后收敛，旧连接 key、迟到事件和能力重新协商不造成越权或丢事件；
- Client auth 动态切换在所有节点一致应用，混合开关状态被检测并拒绝作为成功；
- token 到期与刷新、错误凭据、撤权缓存收敛均有上限，不出现无限重试；
- 本地缓存、Redo 或旧 token 不得绕过最新权限；
- 默认 adapter 与 Jackson 3 至少在代表性的重启场景中保持一致。

**独立验证**：定向 standalone restart suite、三节点 cluster suite 和滚动变更 suite 分别生成报告。

**退出门禁**：单机 Required CI 稳定，深度套件连续多次通过；不稳定场景必须有明确隔离和缺口记录，不能
静默删除。

**实施与验证结果（2026-09-04）**：

- 新增 `test/scripts/default-auth-reliability-it.sh`，按 `standalone`、`cluster` 或 `all` 运行，使用临时
  发行目录、精确 `-Dnacos.home` PID 校验、退出 trap 和 marker 握手完成安全故障注入；
- 新增 `.github/workflows/default-auth-reliability-it.yml`，支持每周定时和手动选择套件，失败时也上传
  `target/default-auth-reliability` 下的 Maven 日志与隔离 Failsafe XML；
- standalone 5 项实际执行并通过：Config 原 listener/双客户端恢复、Naming 临时实例 Redo/订阅恢复、
  Lock 连接级旧状态清理与原客户端互斥恢复、Maintainer 原实例与非幂等 namespace 安全，以及
  Jackson 3 代表性重启；Agent/MCP 重启精确禁用为 `DAUTH-F05`；
- cluster 安全矩阵通过：三节点默认 auth-on、故意 mixed state 检测、显式 Client false 不影响
  Admin/Console、5 秒 token 跨节点过期/重签发、权限撤销与恢复在默认缓存开启下有界收敛；
- cluster Java SDK 的 B 节点 rolling restart 与 peer restart 实际通过；固定 A/B 节点变更在初始
  Agent 读取处复现 `DAUTH-F05` 并精确禁用；
- Java SDK IT 的合成 Runtime Endpoint 端口使用 JVM 内无重复序列，避免大场景独立随机取值偶发生成
  相同自然键；
- 产品修复均已回滚；最终全仓 `clean compile`、RAT、Checkstyle、SpotBugs、Spotless 门禁
  reactor 61/61 通过；
- 完整运行方式、测试矩阵、安全约束和报告结构见
  [`test/DEFAULT_AUTH_RELIABILITY_IT.md`](../../../test/DEFAULT_AUTH_RELIABILITY_IT.md)。

## 10. 预期修改清单

### 10.1 当前主仓库

| 类型 | 主要位置 |
| --- | --- |
| 正式规范 | `specs/en/**`、`specs/zh-cn/**` 中 auth、authorization、compatibility、server env、testing |
| 配置模板 | `distribution/conf/application.properties`、`bootstrap/src/main/resources/application.properties` |
| 运行时默认 | `core/.../NacosServerAuthConfig.java`、`core/.../AuthPluginTypePolicy.java` |
| 条件组件 | `prometheus/.../PrometheusAuthFilter.java` |
| 单元测试 | Core auth config/policy、Prometheus conditional、Bootstrap properties consistency |
| HTTP IT | `test/openapi-test` 的基础类、Auth API、安全 URI、覆盖和场景文档 |
| Java SDK IT | `test/java-sdk-test` 的基础类、鉴权专项类、coverage/scenario 文档 |
| Maintainer SDK IT | `test/maintainer-sdk-test` 的基础类、鉴权专项类、coverage/scenario 文档 |
| 迁移清理 | `test/auth-test` 及 `test/pom.xml` 中相关 module/profile |
| CI | `.github/workflows/it-new.yml`、最终删除 `.github/workflows/auth-it.yml` |
| 用户文档 | 认证、部署、升级、Client 使用说明中的默认值和凭据要求 |

### 10.2 外部生态

- Docker 镜像模板、入口脚本和环境变量文档；
- Helm chart values、模板和 render tests；
- Nacos Operator API 类型、默认逻辑、生成环境变量和升级测试；
- nacos-setup 安装回归；
- Nacos 官网 next 版本中英文文档。

外部仓库的准确文件位置应在阶段 7 开始时以各自最新默认分支重新确认，不能把本设计中的历史路径当作
永久契约。

## 11. 建议 PR 拆分

| PR/批次 | 内容 | 可独立回滚 |
| --- | --- | --- |
| PR 1 | 正式规范、覆盖基线、公开端点与受保护操作清单 | 是，纯设计/文档 |
| PR 2 | 统一身份 helper、三类基类、Auth Scope Guard、临时 migration job | 是，不改产品默认 |
| PR 3 | HTTP 全功能迁移到 explicit auth-on | 是，旧主流程仍保留 |
| PR 4 | Auth API/授权/URI 安全并入 openapi-test | 是，旧 auth-test 暂不删除 |
| PR 5 | Java Client SDK auth-on 与鉴权专项 | 是 |
| PR 6 | Maintainer SDK auth-on 与鉴权专项 | 是 |
| PR 7 | 产品默认值、单元测试、最终 CI 切换、删除旧 Auth IT | 是，但必须整体回滚默认与 CI |
| PR 8+ | Docker、Helm、Operator、setup、官网文档 | 各仓库独立回滚 |
| PR 9 | 深度 restart/cluster/rolling 套件 | 是，不改变 Required 主流程 |

如果维护者认为默认值或统一 CI 仍有争议，应先合并 PR 1；PR 2 至 PR 6 可在显式 auth-on 环境继续完善，
但 PR 7 不应在行为决议前合并。

## 12. 主要风险与控制

| 风险 | 控制措施 |
| --- | --- |
| 隐式空 Header 导致大量测试 403 | 身份模式显式化，逐个审计直接 HTTP 调用，阶段 2 前不切默认值 |
| 为方便测试给所有 Client 使用 Admin | 普通功能强制使用 `ClientReadWrite`，Admin 只用于管理面 |
| Nacos token 泄漏给 ARD/外部服务 | 拆分 Nacos raw 与 external raw helper，增加 header absence 断言 |
| 权限缓存导致偶发失败 | 保持默认缓存，用有界收敛轮询和清晰超时信息 |
| 403 掩盖原本 400/404/409 | 合法身份先通过鉴权，再断言原有错误契约 |
| 权限过宽掩盖 Parser 错误 | 精确资源和相邻资源矩阵，ReadOnly/NoPermission 双重校验 |
| 默认插件把持久化 permission resource 当作正则表达式匹配，资源名中的正则元字符可能不再是字面量 | 本次迁移不静默改变历史匹配语义；Stage 3 使用安全 canonical 名和相邻资源验证隔离，并把字面量化/转义行为作为独立的规范与兼容性决策，在行为变更前补充元字符回归矩阵 |
| 测试动态关闭后污染后续模块 | Auth Scope Guard，兼容 smoke 放在全部主体测试之后并恢复 true |
| 模板 true、Java 缺省 false 或条件 Bean 不一致 | absent/true/false 单元测试和打包产物断言 |
| 旧部署被意外强制开启 | 显式 false 优先，升级文档与容器/Chart/Operator 三态测试 |
| Operator 普通 bool 吞掉 false | 使用可选 bool/三态 API 并做序列化与 env 生成测试 |
| 多节点仅局部 reload | 深度套件检查所有节点，文档明确动态配置为节点本地机制 |
| 合并工作流后失败难定位 | 保留三个模块/profile/报告，阶段名和日志分离 |
| 删除旧 check 破坏分支保护 | 切换前确认 Required check，尽量保留主 check 稳定名称 |

## 13. 最终验收清单

- [x] `nacos.core.auth.enabled` 未配置时在模板、Java 运行时、插件策略和条件组件中均为开启。
- [x] 显式 `false` 在发行包和动态配置中可验证地保持关闭。
- [ ] 显式 `false` 在 Docker、Helm 和 Operator 中可验证地保持关闭。
- [x] Admin 与 Console 默认开启语义未被 Client 开关改变。
- [x] 最终 CI 不覆盖 auth scope 或默认 auth cache，并断言打包产物默认值。
- [x] HTTP、Java Client SDK、Maintainer SDK 原有功能和错误场景无删减、无状态降级。
- [x] Auth API 覆盖达到 4/4 `Covered`。
- [x] 所有受保护 HTTP operation 已登记为直接覆盖、鉴权等价组或公开/例外。
- [x] Config、Naming、AI 及适用的 Console 资源 Parser 和 Read/Write 边界有精确权限测试；Lock 当前没有
  服务端 `SignType.LOCK` 鉴权 guard，保持为明确的产品/规范缺口，不伪造拒绝契约。
- [x] 无身份、错误身份、有效无权限、ReadOnly、有权限和 Admin 场景均有受控断言。
- [x] Client SDK 使用普通应用身份，Maintainer SDK 使用管理身份。
- [x] HTTP、gRPC、AUTO、multipart、listener、subscription、Watch、Redo、reconnect、shutdown 均在鉴权开启下验证。
- [x] 默认缓存开启，所有权限变更和异步业务等待均有界。
- [x] ARD/外部端口不会收到 Nacos 凭据。
- [x] 显式 Client auth false/true smoke 不影响 Admin/Console，并在结束时恢复默认状态。
- [x] `test/auth-test` 删除前，其所有功能在 `test/openapi-test` 中有可追踪映射。
- [x] Required check 和失败诊断完成迁移，日志与报告不泄漏密码、token 或 secret。
- [ ] Docker、Helm、Operator、setup 和官网文档与主仓库默认语义一致。
- [x] 深度重启、滚动和多节点测试全部在鉴权开启环境执行。
- [x] 仍无法覆盖的路径保留为 `Partial`，并记录具体原因、风险和后续条件。

## 14. 实施前置条件

根据仓库贡献约束，默认行为、运行时配置和测试规范均属于需要先讨论的变更。在开始阶段 1 以后的代码
实现前，至少需要完成：

1. Issue 中明确维护者同意 Nacos 3.3 Client API 缺省开启；
2. 确认显式 false、新旧配置文件和部署环境变量的兼容规则；
3. 确认本方案涉及的中英文规范更新范围；
4. 确认主 CI check 的最终名称和 Auth IT 的迁移窗口；
5. 确认 Client 测试用户的权限粒度与默认鉴权插件 canonical 资源格式；
6. 确认阶段 7 各外部仓库的负责人和发布节奏。

满足以上条件后，按阶段 0 至阶段 8 推进。任何阶段发现规范、生产实现和测试预期不一致时，应先修订
设计与规范，再继续修改行为。
