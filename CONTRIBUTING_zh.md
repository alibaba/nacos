# 贡献指南

[English](./CONTRIBUTING.md)

欢迎来到 Nacos！本文档是关于如何为 Nacos 做贡献的指南。

Nacos 采用宽松的 Apache 2.0 许可证发布，遵循标准的 Github 开发流程，使用 Github Issue 跟踪问题并将 Pull Request 合并到 develop 分支。如果您想要贡献，无论是简单的修复还是重大的新功能，请不要犹豫，但请遵循以下指南。

我们始终非常欢迎各种贡献，无论是简单的代码清理还是重大的新功能。我们希望每种编程语言都有高质量、文档完善的代码。代码并不是贡献项目的唯一方式，我们同样重视文档和与其他项目的集成，并乐于接受这些方面的改进。

## 开始之前

### 行为准则

请务必阅读并遵守我们的[行为准则](./CODE_OF_CONDUCT.md)。

### 代码规范

在贡献之前，请阅读并正确配置 Nacos 代码规范：

- Checkstyle 配置：[`style/NacosCheckStyle.xml`](style/NacosCheckStyle.xml)
- IDEA 代码风格：[`style/nacos-code-style-for-idea.xml`](style/nacos-code-style-for-idea.xml)
- 代码规范指南：[`style/codeStyle.md`](style/codeStyle.md)

## 联系我们

- **Nacos Gitter**：[https://gitter.im/alibaba/nacos](https://gitter.im/alibaba/nacos)
- **Nacos 微博**：[https://weibo.com/u/6574374908](https://weibo.com/u/6574374908)
- **Nacos SegmentFault**：[https://segmentfault.com/t/nacos](https://segmentfault.com/t/nacos)

### 邮件列表

邮件列表是讨论 Nacos 相关任何事项的推荐方式：

- [dev-nacos@googlegroups.com](mailto:dev-nacos%2Bsubscribe@googlegroups.com)：开发邮件列表。如果您在使用或开发 Nacos 时遇到任何问题，可以在这里提问。
- [commits-nacos@googlegroups.com](mailto:commits-nacos%2Bsubscribe@googlegroups.com)：所有提交都会发送到此邮件列表。如果您对 Nacos 的开发感兴趣，可以订阅此列表。
- [users-nacos@googlegroups.com](mailto:users-nacos%2Bsubscribe@googlegroups.com)：所有 Github Issue 更新和 Pull Request 更新都会发送到此邮件列表。
- [nacos_dev@linux.alibaba.com](mailto:nacos_dev@linux.alibaba.com)

## 报告 Bug

如果您在 Nacos 项目中发现 bug 或文档错误，请通过[创建 Issue](https://github.com/alibaba/nacos/issues/new) 告知我们。我们非常重视 bug 和错误，认为任何问题都不算小问题。在创建 bug 报告之前，请先检查是否已存在报告相同问题的 Issue。

为了使 bug 报告准确且易于理解，请尽量创建符合以下要求的 bug 报告：

- **具体**：尽可能包含详细信息：哪个版本、什么环境、什么配置等。如果 bug 与运行 Nacos 服务器相关，请附上 Nacos 日志（启动日志和 Nacos 配置信息尤为重要）。

- **可复现**：包含重现问题的步骤。我们理解某些问题可能难以复现，请包含可能导致问题的步骤。如有可能，请将受影响的 Nacos 数据目录和堆栈跟踪附加到 bug 报告中。

- **唯一**：不要重复已有的 bug 报告。

在创建 bug 报告之前，阅读 [Elika Etemad 关于如何提交好的 bug 报告的文章](http://fantasai.inkedblade.net/style/talks/filing-good-bugs/)可能会有所帮助。

### 报告安全漏洞

**请勿**通过 GitHub Issues 报告安全漏洞。请通过 [ASRC（阿里巴巴安全响应中心）](https://security.alibaba.com)进行报告。

## 代码贡献

Nacos 欢迎任何角色的新参与者，包括用户、贡献者、Committer 和 PMC。

![贡献者角色](http://acm-public.oss-cn-hangzhou.aliyuncs.com/contributor_definition.png)

我们鼓励新人积极参与 Nacos 项目，从用户角色发展到贡献者、Committer，甚至 PMC。

### 寻找要参与的 Issue

如果您发现文档中的拼写错误、代码中的 bug，或者想要新功能、提出建议，可以在 [GitHub 上创建 Issue](https://github.com/alibaba/nacos/issues/new) 进行反馈。

如果您想直接参与贡献，可以选择以下标签的 Issue：

- [Contribution Welcome](https://github.com/alibaba/nacos/labels/contribution%20welcome)：急需解决但人手不足的 Issue。
- [Good First Issue](https://github.com/alibaba/nacos/labels/good%20first%20issue)：适合新手的 Issue，可以作为入门热身。

**请注意，每个 PR 必须关联一个有效的 Issue，否则 PR 将被拒绝。**

### 贡献须知

在提交更改之前，请确保：

1. 阅读并遵循 Nacos [代码规范](style/codeStyle.md)，确保您的 IDE 已配置代码风格并安装了必要的插件。

2. 如果更改是非琐碎的，请包含覆盖新功能的单元测试。

3. 如果您正在引入全新的功能或 API，最好先发起讨论并在基本设计上达成共识。

### 贡献流程（详细步骤）

此贡献流程适用于所有 Nacos 社区内容，包括但不限于 `Nacos`、`Nacos wiki/doc`、`Nacos SDK`。

#### 1. Fork 仓库

将 [alibaba/nacos](https://github.com/alibaba/nacos) 仓库 Fork 到您的 Github 账号。

#### 2. 克隆到本地

```bash
git clone ${您的_fork_nacos_仓库地址}
cd nacos
```

#### 3. 添加上游仓库

```bash
git remote add upstream https://github.com/alibaba/nacos.git

git remote -v
# origin     ${您的_fork_nacos_仓库地址} (fetch)
# origin     ${您的_fork_nacos_仓库地址} (push)
# upstream   https://github.com/alibaba/nacos.git (fetch)
# upstream   https://github.com/alibaba/nacos.git (push)

git fetch origin
git fetch upstream
```

#### 4. 创建开发分支

我们使用 `develop` 分支作为开发分支，这是一个不稳定的分支。基于 `upstream/develop` 创建新分支：

```bash
# 从远程仓库检出分支到本地
git checkout -b upstream-develop upstream/develop

# 创建开发分支（通常使用 issue 编号作为分支名）
git checkout -b develop-issue#${issue编号}
```

#### 5. 进行修改

进行修改时，请确保：

- 此分支上的更改**仅与该 Issue 相关**
- 尽量保持更改最小化 - **一个分支做一件事，一个 PR 解决一个 Issue**
- 使用英文描述您的提交，主要使用**谓语 + 宾语**格式，例如：`Fix xxx problem/bug`
- 简单的提交可以使用 `For xxx` 描述，例如：`For codestyle`
- 如果提交与某个 ISSUE 相关，可以添加 ISSUE 编号作为前缀，例如：`For #10000, Fix xxx problem/bug`

#### 6. 运行提交前检查

在推送代码之前，请在本地运行以下命令以尽早发现问题：

```bash
mvn -B clean compile apache-rat:check checkstyle:check spotbugs:check -DskipTests
```

| 检查项 | 说明 |
|-------|------|
| `compile` | 代码是否能正常编译 |
| `apache-rat:check` | 所有源文件是否包含 Apache License 头 |
| `checkstyle:check` | 代码风格是否符合[阿里巴巴 Java 开发规约](style/NacosCheckStyle.xml) |
| `spotbugs:check` | 是否存在 [SpotBugs](https://spotbugs.github.io/) 检测到的高优先级 bug |

运行单元测试：

```bash
mvn clean test
```

#### 7. 变基（Rebase）分支

在您进行修改的同时，其他人的更改可能已经被提交和合并。此时可能会有冲突，请使用 rebase 命令进行合并和解决：

```bash
git fetch upstream
git rebase -i upstream/develop
```

或者

```bash
git checkout upstream-develop
git pull
git checkout develop-issue#${issue编号}
git rebase -i upstream-develop
```

变基的好处：
1. 您的提交记录将很干净，没有 `Merge xxxx branch` 的信息
2. 变基后，您分支的提交日志是单链的，更容易回溯

**如果您使用 IntelliJ IDEA**，建议使用 IDE 的版本控制面板，它有更方便的可视化界面来解决冲突和执行压缩操作。

#### 8. 推送到 Fork 仓库

```bash
git push origin develop-issue#${issue编号}
```

**注意**：如果在变基后再次推送时提示有冲突，可以强制推送到您的 fork 分支：`git push -f origin develop-issue#${issue编号}`。这是因为变基后提交 ID 发生了变化。

#### 9. 创建 Pull Request

向 **develop** 分支创建 Pull Request，并遵循 [PR 模板](./.github/PULL_REQUEST_TEMPLATE.md)。

##### Pull Request 检查清单

- [ ] 确保已为此更改创建了 Github Issue（通常在开始工作之前）。拼写错误等琐碎更改不需要 Github Issue。您的 Pull Request 应该只解决这个 Issue，不要引入其他更改 - 一个 PR 解决一个 Issue。
- [ ] PR 标题格式如 `[ISSUE #123] Fix UnknownException when host config not exist`。PR 中的每个提交都应有有意义的主题和正文。
- [ ] 编写详细的 PR 描述，足以说明 PR 做了什么、如何做的以及为什么这样做。
- [ ] 编写必要的单元测试来验证您的逻辑正确性。当存在跨模块依赖时，尽量使用 mock。如果提交了新功能或重大更改，请记得添加集成测试。
- [ ] 运行 `mvn -B clean apache-rat:check checkstyle:check spotbugs:check -DskipTests` 确保基本检查通过。
- [ ] 运行 `mvn clean install -DskipITs` 确保单元测试通过。
- [ ] 运行 `mvn clean test-compile failsafe:integration-test` 确保集成测试通过。
- [ ] 如果此贡献较大，请签署 [Apache 个人贡献者许可协议](http://www.apache.org/licenses/#clas)。

##### Pull Request 指南

1. 请将 PR 提交到 **develop** 分支。
2. 请确保 PR 关联了对应的 Issue。
3. 如果 PR 包含较大的改动（如组件重构或新组件），请编写详细的设计和使用文档。
4. 注意单个 PR 不要过大。如果需要大量改动，最好将其拆分为多个独立的 PR。
5. 创建 PR 后，一名或多名审核者会被分配到该 PR。
6. 合并前，请将修复审查意见、拼写错误、合并和变基等提交压缩为有意义的提交。最终的 commit message 应当清晰简洁。

#### 10. 等待审核和合并

Nacos 社区将审核您的 Pull Request 并可能提出意见。您可以返回步骤 5 根据意见修改代码，并使用步骤 7 重新提交。

如果没有更多问题，Nacos 社区将合并您的 PR。恭喜您成为 Nacos 的正式贡献者！

## License 头

每个新的源文件（`.java`、`.xml` 等）**必须**包含 Apache License 2.0 头。CI 会通过 `apache-rat:check` 自动检查，缺少 License 头的 PR 将无法通过。

请将以下头信息复制到每个新文件中（非 Java 文件请调整注释风格）：

```java
/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

## 文档贡献

在贡献文档时，请确认并检查以下内容：

1. 文档确实存在错误或缺失。
2. 您熟悉 [Markdown](https://www.markdownguide.org/getting-started)。
3. 您熟悉文档站点，能够根据[文档 README](https://github.com/nacos-group/nacos-group.github.io) 完成本地调试。

## 代码审查指南

Committer 会轮流审查代码，确保所有 PR 在合并前至少经过一名 Committer 的及时审核。如果我们有所疏漏，欢迎随时提醒。同时，我们也欢迎志愿者参与代码审查。

### 审查原则

- **可读性**：重要的代码应有完善的文档。API 应有 Javadoc。代码风格应与现有代码保持一致。
- **优雅性**：新的函数、类或组件应当设计良好。
- **可测试性**：新代码应有 80% 的单元测试覆盖率。
- **可维护性**：遵守我们的[代码规范](style/codeStyle.md)，至少每 3 个月更新一次。

## 成为 Committer

我们始终欢迎新的贡献者加入。我们看重的是持续的贡献、良好的品味和对项目的持续兴趣。如果您有兴趣成为 Committer，请联系现有的 Committer，他们可以帮助您完成这个过程。

### 重要贡献领域

- Wiki 和 JavaDoc
- Nacos Console
- Nacos SDK（C++、.NET、PHP、Python、Go、Node.js）

### 成为 Committer 的前提条件

- **可读性**：API 和重要方法必须有 Javadoc。
- **可测试性**：确保主要流程的单元测试覆盖率超过 80%。
- **可维护性**：遵守我们的[代码规范](style/codeStyle.md)，至少每 3 个月更新一次。
- **可部署性**：我们鼓励您部署到 [Maven 仓库](http://search.maven.org/)。

### 提名流程

一般来说，需要贡献 8 个非琐碎的补丁，并获得至少三个不同的人来审核（您需要三个人的支持）。然后请人提名您。您需要展示：

- 至少为项目贡献了 8 个 PR 和对应的 Issue
- 能够与团队协作
- 了解项目代码库和编码风格
- 能够编写高质量的代码

Committer 通过在带有 "nomination" 标签的 Nacos Issue 中通知团队来提名您，需要包含：

- 您的姓名
- 您的 Git 主页链接
- 解释您为何应该成为 Committer
- 详细说明提名者与您合作过的前 3 个 PR 和对应 Issue，以证明您的能力

需要另外两名 Committer 附议您的提名。如果 5 个工作日（中国时间）内没有人反对，您就是 Committer 了。如果有人反对或需要更多信息，Committer 们会进行讨论并通常在 5 个工作日内达成共识。如果问题无法解决，将在现有 Committer 中进行投票。

![提名流程](http://acm-public.oss-cn-hangzhou.aliyuncs.com/nomination_process.png)

在最坏的情况下，这个过程可能会持续两周。请继续贡献！即使在提名失败的罕见情况下，反对意见通常也是容易解决的，比如"需要更多补丁"或"没有足够的人熟悉此人的工作"。
