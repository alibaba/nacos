# 贡献指南

[English](./CONTRIBUTING.md)

欢迎来到 Nacos！本文档是关于如何为 Nacos 做贡献的指南。

如果您发现任何不正确或遗漏的内容，请留下意见或建议。

## 开始之前

### 行为准则

请务必阅读并遵守我们的[行为准则](./CODE_OF_CONDUCT.md)。

## 参与贡献

Nacos 欢迎任何角色的新参与者，包括用户、贡献者、Committer 和 PMC。

![](http://acm-public.oss-cn-hangzhou.aliyuncs.com/contributor_definition.png)

我们鼓励新人积极参与 Nacos 项目，从用户角色发展到贡献者、Committer，甚至 PMC。为了实现这一目标，新人需要积极地为 Nacos 项目做贡献。以下内容介绍了如何以 Nacos 的方式进行贡献。

#### 创建或认领 Issue

如果您发现文档中的拼写错误、代码中的 bug，或者想要新功能、提出建议，可以在 [GitHub 上创建 Issue](https://github.com/alibaba/Nacos/issues/new) 进行反馈。

如果您想直接参与贡献，可以选择以下标签的 Issue：

-   [Contribution Welcome](https://github.com/alibaba/nacos/labels/contribution%20welcome)：急需解决但人手不足的 Issue。

-   [good first issue](https://github.com/alibaba/nacos/labels/good%20first%20issue)：适合新手的 Issue，可以作为入门热身。

我们非常重视文档编写以及与其他项目（如 Spring Cloud、Kubernetes、Dubbo 等）的集成。我们很乐意处理这些方面的任何 Issue。

请注意，每个 PR 必须关联一个有效的 Issue，否则 PR 将被拒绝。

#### 开始贡献

如果您准备开始贡献，请创建一个新的 Pull Request。

我们使用 `develop` 分支作为开发分支，这是一个不稳定的分支。

此外，我们的分支模型遵循 [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)。我们强烈建议新人在创建 PR 之前先阅读上述文章。

以下是贡献者的工作流程：

1.  Fork 仓库到自己的账号下

2.  将 Fork 克隆到本地

3.  创建新分支并在上面开发

4.  保持分支与上游同步

5.  提交更改（确保 commit message 简洁明了）

6.  在本地运行提交前检查（参见下方[提交前检查](#提交前检查)）

7.  将提交推送到您的 Fork 仓库

8.  向 **develop** 分支创建 Pull Request

创建 Pull Request 时：

1.  请遵循 [PR 模板](./.github/PULL_REQUEST_TEMPLATE.md)。

2.  请将 PR 提交到 **develop** 分支。

3.  请确保 PR 关联了对应的 Issue。

4.  如果 PR 包含较大的改动（如组件重构或新组件），请编写详细的设计和使用文档。

5.  注意单个 PR 不要过大。如果需要大量改动，最好将其拆分为多个独立的 PR。

6.  创建 PR 后，一名或多名审核者会被分配到该 PR。

7.  合并前，请将修复审查意见、拼写错误、合并和变基等提交压缩为有意义的提交。最终的 commit message 应当清晰简洁。

如果 PR 包含较大的改动（如组件重构或新组件），请编写详细的设计和使用文档。

### License 头

每个新的源文件（`.java`、`.xml` 等）**必须**包含 Apache License 2.0 头。CI 会通过 `apache-rat:check` 自动检查，缺少 License 头的 PR 将无法通过。

请将以下头信息复制到每个新文件中（非 Java 文件请调整注释风格）：

```java
/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

### 提交前检查

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

### 代码审查指南

Committer 会轮流审查代码，确保所有 PR 在合并前至少经过一名 Committer 的及时审核。如果我们有所疏漏，欢迎随时提醒。同时，我们也欢迎志愿者参与代码审查。

一些原则：

-   可读性 - 重要的代码应有完善的文档。API 应有 Javadoc。代码风格应与现有代码保持一致。

-   优雅性 - 新的函数、类或组件应当设计良好。

-   可测试性 - 新代码应有 80% 的单元测试覆盖率。

-   可维护性 - 遵守我们的[代码规范](style/codeStyle.md)。

### 如何成为 Committer？

一般来说，需要贡献 8 个非琐碎的补丁，并获得至少三个不同的人来审核（您需要三个人的支持）。然后请人提名您。您需要展示：

-   至少为项目贡献了 8 个 PR 和对应的 Issue

-   能够与团队协作

-   了解项目代码库和编码风格

-   能够编写高质量的代码

Committer 通过在带有 "nomination" 标签的 Nacos Issue 中通知团队来提名您，需要包含：

-   您的姓名

-   您的 Git 主页链接

-   解释您为何应该成为 Committer

-   详细说明提名者与您合作过的前 3 个 PR 和对应 Issue，以证明您的能力

需要另外两名 Committer 附议您的提名。如果 5 个工作日（中国时间）内没有人反对，您就是 Committer 了。如果有人反对或需要更多信息，Committer 们会进行讨论并通常在 5 个工作日内达成共识。如果问题无法解决，将在现有 Committer 中进行投票。

![](http://acm-public.oss-cn-hangzhou.aliyuncs.com/nomination_process.png)

在最坏的情况下，这个过程可能会持续两周。请继续贡献！即使在提名失败的罕见情况下，反对意见通常也是容易解决的，比如"需要更多补丁"或"没有足够的人熟悉此人的工作"。
