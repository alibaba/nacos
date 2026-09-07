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

# Stage 7 部署生态改造交接与验收方案

本文是
[Nacos 3.3 Client API 默认鉴权与统一 IT 改造方案](README.md)
阶段 7 的仓库级执行清单。它记录 2026-09-04 对各仓库最新默认分支的只读审计结果，固定跨仓库契约，
并给出能够独立提交、独立验证和独立回滚的修改与测试项。

本文不表示外部仓库已经完成改造。`alibaba/nacos` 主仓库工作区不直接修改这些仓库；每个外部仓库需要
由对应维护者在自己的最新分支提交 PR，并用该仓库 CI 验证。

## 1. 审计快照

| 仓库 | 默认分支 | 审计 commit | 审计结论 |
| --- | --- | --- | --- |
| `nacos-group/nacos-docker` | `master` | `28c16f65fb6ef1586ba6a16ef8d12c6a4411d1d9` | 镜像模板仍显式关闭 Client auth，覆盖 3.3 服务端新默认。 |
| `nacos-group/nacos-k8s` | `master` | `d3a4d5c64b012e6641799c1e19a2ea36b07f7539` | Helm 继承镜像默认但不能一等地显式关闭；Operator 普通 `bool` 吞掉显式 false。 |
| `nacos-group/nacos-setup` | `main` | `dc9646e62fd6025723c7969ff4eabd4a8247095d` | Linux/Windows 新安装已经显式开启鉴权，但缺少语义级回归。 |
| `nacos-group/nacos-group.github.io` | `develop-astro-nacos` | `918e4543e7abb6d2736ca5b9c0955e7dbe149d0b` | next 文档仍把 Client auth 描述为默认关闭，示例 Client API 未携带身份。 |

`harmonycloud/nacos-operator` 的默认分支最后提交停留在 2021 年。本阶段以
`nacos-group/nacos-k8s/operator` 为官方 Operator 实现，不向前者安排重复改造。

## 2. 跨仓库不可变契约

### 2.1 Client auth 三态语义

| 输入 | Nacos 3.3 期望 | 部署层要求 |
| --- | --- | --- |
| 未设置 | Client auth 开启 | 不生成覆盖值，继承 3.3 镜像或发行包默认。 |
| 显式 `true` | Client auth 开启 | 明确生成 `NACOS_AUTH_ENABLE=true` 或等价配置。 |
| 显式 `false` | Client auth 关闭 | 明确生成 `NACOS_AUTH_ENABLE=false`，不能把 false 当成空值。 |

该契约只调整 Client/Open API、SDK 和客户端 gRPC 请求。Admin 和 Console scope 继续默认开启；显式关闭
Client auth 不能顺带关闭 Admin 或 Console auth。

Nacos 3.2 及更早镜像的“未设置”仍由对应镜像自身决定。因此 Helm 和 Operator 不应通过 admission
default、values 默认 `false` 或入口脚本 shell 默认值，把“未设置”提前固化为某个布尔值。

### 2.2 凭据契约

默认开启 Client auth 后，部署层必须同时满足以下要求：

- token secret 和 server identity 不能为空，且不能使用公开、跨安装复用的生产默认值；
- 集群所有节点使用完全一致的 token secret 和 server identity；
- Helm upgrade、Operator reconcile、Pod 重建和滚动更新不能静默轮换凭据；
- Client auth 显式关闭时，Admin/Console auth 及 JRaft server identity 仍可能使用这些凭据，不能把
  “Client=false”等价为“不再注入任何安全配置”；
- CI 只使用运行时生成的临时值，日志、命令行摘要、测试报告和上传产物不得打印密码、token 或 secret。

### 2.3 升级契约

- 手工部署继续从目标版本新模板逐项迁移配置；旧 `application.properties` 中的显式
  `nacos.core.auth.enabled=false` 不被自动改写；
- Docker、Helm 或 Operator 用户若依赖旧版本的隐式关闭，升级 3.3 镜像前必须显式配置 false；
- 新安装或没有显式覆盖的 3.3 部署使用新默认 true；
- 先创建可用身份和权限，再开启或继承 Client auth；集群滚动时所有节点必须使用一致开关与凭据；
- 不把 nacos-setup 当作原地升级旧手工安装的工具。它的安全新安装契约可以继续显式写 true。

## 3. nacos-docker

### 3.1 当前证据

- `build/conf/application.properties` 直接写入
  `nacos.core.auth.enabled=false`，所以即使 3.3 Java 回退和发行包模板为 true，容器仍实际关闭；
- `build/bin/docker-startup.sh` 只在 `NACOS_AUTH_ENABLE` 非空时追加 JVM 属性。这个行为已经正确保留
  未设置、true、false 三态，不应改成 `${NACOS_AUTH_ENABLE:-true}`；
- 镜像 auth 配置块仍主要使用 3.3 的历史 alias，并把鉴权信息缓存默认写为 false；
- `README.md`、`README_ZH.md` 仍声明 `NACOS_AUTH_ENABLE` 默认 false，且缓存变量重复登记；
- `example/init.d/application.properties` 也显式写 false，需要明确它是历史示例还是当前受支持模板；
- `.github/workflows/ci.yml` 的 smoke 只访问公开首页，不能证明受保护 Client API 的真实状态。

### 3.2 修改批次

**D1：镜像契约与静态检查**

1. 将 `build/conf/application.properties` 的 Client auth 默认改为 true。
2. 保留入口脚本“仅在环境变量非空时追加 JVM 覆盖”的逻辑，并增加 shell 回归测试。
3. 将 3.3 auth 配置块同步到主仓库 canonical key；历史 alias 只作为兼容说明，不继续成为新模板首选。
4. 保持默认鉴权信息缓存为 true，token cache 仍按其独立默认 false。
5. 对 `example/init.d/application.properties` 作明确决策：若仍支持当前版本则同步；若仅为历史示例，移动到
   版本化目录或在文档中明确其适用版本，避免用户误复制到 3.3。
6. 更新中英文 README，分别说明 3.3 未设置为开启、旧镜像仍遵循各自版本默认、显式 false 继续有效。

**D2：真实容器矩阵**

扩展 `.github/workflows/ci.yml`，对 standard 和 slim 镜像运行 3.3 矩阵。Lite 镜像若仍发布，也必须纳入
同一静态契约检查，至少在发布工作流前增加一次 smoke。

### 3.3 测试项

| 场景 | 容器输入 | 匿名 Client | 合法 Client | 匿名 Admin/Console |
| --- | --- | ---: | ---: | ---: |
| 默认 | 不传 `NACOS_AUTH_ENABLE` | 403 | 业务结果成功 | 403 |
| 显式开启 | `NACOS_AUTH_ENABLE=true` | 403 | 业务结果成功 | 403 |
| 显式关闭 | `NACOS_AUTH_ENABLE=false` | 业务结果成功 | 业务结果成功 | 403 |

每个容器都应使用全新数据目录和运行时生成的凭据，完成管理员初始化、登录、创建一条隔离配置、读取并比对
内容。不能只断言首页或 readiness 为 200。还要断言：

- unset 容器中不存在 `NACOS_AUTH_ENABLE` 环境变量，而实际 Client 请求仍为 403；
- 显式 false 只影响 Client scope；
- standard/slim 的解析结果一致；
- 容器日志和 GitHub Actions 输出不包含生成的 token、密码或 secret；
- 所有容器按精确名称在 `always()` 中删除，失败日志经脱敏后上传。

## 4. nacos-k8s Helm Chart

### 4.1 当前证据

- `helm/values.yaml` 没有 Client auth 开关，`helm/templates/statefulset.yaml` 也不生成
  `NACOS_AUTH_ENABLE`；因此 unset 能继承镜像默认，但 Chart 用户没有一等的显式 false；
- Chart 直接提供公开固定的 `authToken`、`identityKey`、`identityValue` 默认值。3.3 默认开启后继续使用这些
  值会产生“看似开启、所有安装共享密钥”的安全问题；
- `hack/ci/helm-smoke-test.sh` 只检查 readiness，未验证任何受保护 API；
- 现有矩阵只覆盖 2.5.3 和 3.2.3，且没有 auth 维度；
- 开放 PR `nacos-group/nacos-k8s#475` 使用 `auth.enable: false`，会覆盖 3.3 新默认；其模板在渲染时
  随机生成凭据，upgrade 时可能变值，不能直接作为本阶段实现。

### 4.2 修改批次

**H1：三态渲染与 Secret**

1. 引入 `nacos.auth.enabled: null`：`null` 表示不生成覆盖，true/false 均必须生成对应环境变量。
2. 模板必须基于“值是否为 nil”判断，不能使用普通 truthy `if`，否则 false 会再次被吞掉。
3. 凭据优先引用用户提供的 Kubernetes Secret；若 Chart 支持自动生成，必须用 `lookup` 或等价机制复用
   已存在 Secret，保证 upgrade 和重复 reconcile 不轮换。
4. 兼容现有顶层 `authToken`、`identityKey`、`identityValue` 时给出废弃窗口，但不能继续提供公开生产默认。
5. 增加 values/schema 校验：enabled 只接受 null/boolean；启用内联凭据时校验非空和 token 格式。

**H2：文档与 runtime smoke**

1. 更新 `helm/README.md` 的参数表、安装示例、升级说明和带身份的 Client API 示例。
2. 扩展 `hack/ci/helm-smoke-test.sh`，在 3.3 镜像上完成和 Docker 相同的业务/鉴权矩阵。
3. 保留 2.x/3.2 兼容矩阵：unset 必须继续继承被测镜像自己的默认，而不是由 Chart 强制统一。

### 4.3 Render 测试

| values 输入 | StatefulSet 中 `NACOS_AUTH_ENABLE` | 凭据来源 |
| --- | --- | --- |
| `enabled: null` | 0 个 | Secret 引用存在且稳定 |
| `enabled: true` | 恰好 1 个，值为 `"true"` | 同上 |
| `enabled: false` | 恰好 1 个，值为 `"false"` | 同上 |

对 standalone/cluster、embedded/MySQL 至少各取代表组合运行 `helm lint` 和 `helm template`。额外断言：

- 用户 `extraEnv` 或等价入口不能产生第二个同名 auth 环境变量；
- 安装后执行无变更 `helm upgrade`，Secret 数据和 StatefulSet checksum 不发生无意义变化；
- 显式 false 后 Pod 模板确实变化并完成 rollout；
- 3.3 runtime smoke 验证匿名/合法身份和 Admin/Console scope，而非仅验证探针。

## 5. nacos-k8s Operator

### 5.1 当前证据

- `operator/api/v1alpha1/nacos_types.go` 中 `Certification.Enabled` 和 `CacheEnabled` 都是普通 `bool`；
- `operator/pkg/service/operator/Kind.go` 只在 `Enabled == true` 时生成 `NACOS_AUTH_ENABLE`。省略与显式 false
  都不会生成环境变量，3.3 镜像下二者都会继承 true；
- 同一分支只在 enabled=true 时生成 token 环境变量，并没有一等的 server identity 字段。使用当前 Docker
  入口脚本时，用户仍需通过裸 `spec.env` 补齐 identity；
- enabled=true 时，零值 `CacheEnabled` 会生成 `NACOS_AUTH_CACHE_ENABLE=false`，覆盖 3.3 缓存默认；
- `buildDefaultConfigMap` 内有旧 auth 模板，但当前 `MakeEnsure` 不调用它，不能把它误判为实际 Pod 配置来源；
- CRD、chart CRD 和 all-in-one 清单有多份生成产物；当前只有 envtest suite 启动代码，没有
  `KindClient` 行为测试，CI 也只在 tag 时发布镜像。

### 5.2 修改批次

**O1：API 三态与生成逻辑**

1. 将 `Certification.Enabled` 改为 `*bool`；nil 不生成 Client gate，true/false 都生成明确值。
2. 将 `CacheEnabled` 同样改为 `*bool`；nil 继承服务端缓存默认，显式 false 才生成 false。
3. 不在 CRD schema 上添加 `default: true`，否则 API Server 会把 omitted 改写为 explicit true，破坏跨镜像继承。
4. 重新生成 deepcopy、base CRD、Operator Helm CRD 和 all-in-one 清单，并验证 wire format 中 false 被保留。
5. 对 `spec.env` 和一等 auth 字段的同名冲突制定唯一规则。建议 validation 拒绝重复；若保留兼容覆盖，必须
   文档化优先级并用测试固定。

**O2：凭据模型与部署模板**

1. token 和 server identity 的注入从 Client enabled 条件中解耦。
2. 推荐增加 Secret reference，而不是把 secret 作为 CR 明文字段；旧 `token` 字段保留兼容和废弃说明。
3. Client=false 时仍保留 Admin/Console/JRaft 所需凭据。
4. `operator/chart/nacos/templates/nacos.yaml` 当前不渲染 certification；增加对应 values 后同时覆盖 nil/true/false。
5. 更新 sample 和中英文 README 到受支持的 Nacos 版本，增加“升级 3.3 前显式 false”的示例。

### 5.3 单元与升级测试

新增表驱动 `KindClient` 测试，至少覆盖：

| CR 输入 | 期望 Pod env |
| --- | --- |
| certification omitted | 无 `NACOS_AUTH_ENABLE`、无 `NACOS_AUTH_CACHE_ENABLE` |
| enabled=true | 唯一 `NACOS_AUTH_ENABLE=true` |
| enabled=false | 唯一 `NACOS_AUTH_ENABLE=false` |
| cacheEnabled=true/false | 分别生成真实布尔值，nil 不生成 |
| raw env 与一等字段冲突 | 按约定拒绝或产生唯一、确定值 |

另外验证：

- JSON/YAML round trip 后 omitted 仍为 nil、false 仍为非 nil false；
- 老 CR 在只升级 Operator、不换镜像时继续继承旧镜像默认；换到 3.3 镜像时按迁移文档继承 true；
- 在换 3.3 镜像前写入 enabled=false，生成的 StatefulSet 明确携带 false；
- 无变更 reconcile 不轮换 Secret，改动开关会更新 Pod template 并触发 rollout；
- standalone 和三节点 cluster 分别完成一次真实启动，所有节点身份一致，Client/Admin/Console 结果符合契约；
- PR CI 至少运行 `go test ./...`、`go vet ./...`、生成文件一致性检查和 Helm render，不能只依赖 tag 发布。

## 6. nacos-setup

### 6.1 当前证据

- `lib/config_manager.sh#apply_security_config` 与
  `windows/lib/config_manager.ps1#Apply-SecurityConfig` 都明确写入
  `nacos.core.auth.enabled=true`；这符合 nacos-setup 的安全新安装契约；
- 两端当前写入历史 token secret key，3.3 可兼容读取，但新安装应优先写 canonical key；
- `tests/test_security.sh` 主要 grep 函数或字符串是否存在，没有调用配置函数验证最终文件；
- Windows CI 仅做 PowerShell 语法检查，没有验证配置语义；
- nacos-setup 会解压并配置新的安装目录，不应替代手工原地升级的“保留旧 false”契约。

### 6.2 修改与测试项

1. 保持新建 standalone、cluster、cluster join 都显式写 Client auth=true。
2. 按目标 Nacos 版本选择 token key：3.3+ 写 canonical key；仍支持的旧版本继续写其可识别 key。
3. 增加真正调用 Bash 配置函数的临时文件测试，断言同名属性恰好一个、值为 true、token/identity 未损坏。
4. 增加 Windows 语义测试，不能只停留在 Parser 通过。
5. 验证集群所有节点和 `share.properties` 使用同一组凭据，join 后不重新生成。
6. 验证日志默认不输出凭据；仅显式 verbose 时的现有展示行为需在文档中给出安全警告。
7. 在 README 中明确：nacos-setup 创建的新安装始终启用鉴权；希望保留旧 false 的用户应按发行包升级流程
   迁移，而不是用 setup 覆盖旧目录。

独立门禁为 Linux/macOS Bash 全套测试、Windows PowerShell 语义测试，以及用 3.3 发行包完成一次 standalone
和三节点配置生成。无需为了本阶段增加“默认关闭”的 setup 模式。

## 7. 官网 next 文档

### 7.1 必改文档

| 类别 | 中英文路径 | 当前问题 |
| --- | --- | --- |
| 权限手册 | `manual/admin/auth.mdx` | 核心配置表仍写 Client auth 默认 false。 |
| 系统参数 | `manual/admin/system-configurations.md` | 同上。 |
| 升级手册 | `manual/admin/upgrading.mdx` | 尚未说明 3.3 默认切换和容器/Chart/Operator 迁移。 |
| 快速开始 | `quickstart/quick-start.mdx` | 声称未开启 Client auth，Client curl 无身份。 |
| Docker 快速开始 | `quickstart/quick-start-docker.mdx` | 同上。 |
| Kubernetes 快速开始 | `quickstart/quick-start-kubernetes.mdx` | 同上。 |
| 集群部署 | `manual/admin/deployment/deployment-cluster.md` | 部分文本仍声明 Client auth 默认关闭。 |
| setup 部署 | `manual/admin/deployment/deployment-nacos-setup.md` | 功能验证中的 Client curl 无身份。 |

`guide/user/auth.md` 是仍存在于 next 树中的旧版说明。不能机械替换其中所有 false：展示“显式关闭前/开启后”
的代码块需要保留语义；配置表和 Docker 默认说明则应改成版本化表述，或明确该页只适用于历史版本。

### 7.2 文档内容门禁

1. 明确从 3.3 起“未设置”为 true，显式 false 继续有效。
2. 区分新模板、复用旧配置、Docker/Helm/Operator 未设置和 nacos-setup 新安装。
3. 给出升级前置顺序：配置凭据与身份、创建用户/权限、显式选择是否保持 false、再滚动升级。
4. 所有 Client/Admin 功能 curl 先登录并携带合法 token；同时保留一个匿名 403 示例解释默认效果。
5. 明确 Admin/Console scope 不受 Client=false 影响。
6. 中英文的属性名、默认值、版本边界、示例状态码和升级步骤逐项一致。
7. 执行 `npm run build` 并检查站内链接。

开放 PR `nacos-group/nacos-group.github.io#1144` 只把 `latest`（当前稳定 3.2）文档从 true 修回 false，
没有修改 `next`。它与本阶段的 3.3 next 改造不应合并为一次无版本边界的全局替换；3.3 发布切换
next/latest 时再确认最终落点。

## 8. 外部 PR 顺序与独立门禁

| 顺序 | PR | 前置 | 独立退出门禁 |
| ---: | --- | --- | --- |
| 1 | nacos-docker 模板与容器矩阵 | 可构建的 3.3 RC/发行产物 | standard/slim unset/true/false 全通过。 |
| 2 | nacos-k8s Helm | 可拉取或本地加载同一 3.3 镜像 | render 矩阵、Secret 稳定性、Kind runtime 通过。 |
| 3 | nacos-k8s Operator API/凭据 | 三态契约确定 | Go/CRD/render/升级测试通过。 |
| 4 | nacos-setup | 可用的 3.3 配置模板 | Bash/PowerShell 语义测试和 standalone/cluster 配置通过。 |
| 5 | 官网 next 文档 | 上述字段名和用户路径稳定 | 中英文 review、站点 build、链接检查通过。 |

各 PR 不应依赖另一个外部 PR 才能回滚。Helm 和 Operator 位于同一仓库，仍建议拆成两个 commit 或两个
PR，以免 CRD API 变更阻塞 Chart 的 3.3 发布。

## 9. 在途工作处理

| 仓库 | 在途 PR | 处理建议 |
| --- | --- | --- |
| nacos-docker | `#360`，2024-05-08 后无更新 | 内容基于旧配置结构且仍默认 false，不作为 3.3 实现；由人类维护者决定关闭或取舍。 |
| nacos-k8s | `#475`，merge state 为 dirty | 不直接续用；其 false 默认和每次 render 随机 secret 与本契约冲突。 |
| nacos-setup | 无匹配开放 PR | 新建独立回归 PR。 |
| 官网 | `#1144` | 仅修复当前稳定版 latest；保留版本边界，并另建 next/3.3 PR。 |

遵循主仓库贡献规则，AI 不向这些 issue 或 PR 自动发表讨论评论。是否复用、关闭或 supersede 现有 PR，
由维护者人工协调。

## 10. Stage 7 完成定义

只有以下项目全部满足，阶段 7 才可从“审计完成、外部待推进”改为“已完成”：

- [ ] Docker 3.3 的 unset/true/false 真实容器矩阵通过，且模板不再写 false；
- [ ] Helm 能保留 nil 和显式 false，凭据不会在 upgrade 时轮换；
- [ ] Operator CRD round trip 保留 false，Pod env 与凭据生成有单元和 runtime 证据；
- [ ] nacos-setup Linux/Windows 配置语义测试通过；
- [ ] next 中英文 auth、system config、upgrade、quickstart 和 deployment 文档一致；
- [ ] 手工旧配置 false、Client=false 下 Admin/Console 仍为 403 均有自动化或发布验收证据；
- [ ] 每个外部 PR 记录被测分支、commit、镜像 digest 和测试结果，不以“Pod ready”代替鉴权业务验证。
