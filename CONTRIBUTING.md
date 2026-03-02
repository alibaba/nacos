# Contributing to Nacos

[中文版](./CONTRIBUTING_zh.md)

Welcome to Nacos! This document is a guideline about how to contribute to Nacos.

If you find something incorrect or missing, please leave comments / suggestions.

## Before you get started

### Code of Conduct

Please make sure to read and observe our [Code of Conduct](./CODE_OF_CONDUCT.md).

## Contributing

Nacos welcome new participants of any role, including user, contributor, committer and PMC.

![](http://acm-public.oss-cn-hangzhou.aliyuncs.com/contributor_definition.png)


We encourage newcomers actively joining in Nacos projects and involving from user roles to committer roles, and even PMC roles. In order to accomplish this, new comers needs to actively contribute in Nacos project. The following paragraph introduce how to contribute in Nacos way.

#### Open / pickup an issue for preparation

If you find a typo in a document, find a bug in code or want new features, or want to give suggestions, you can [open an issue on GitHub](https://github.com/alibaba/Nacos/issues/new) to report it.

If you just want to contribute directly you can choose the issue below.

-   [Contribution Welcome](https://github.com/alibaba/nacos/labels/contribution%20welcome): Heavily needed issue, but currently short of hand.
    
-   [good first issue](https://github.com/alibaba/nacos/labels/good%20first%20issue): Good for newcomers, newcomers can pick up one for warm-up.
    

We strongly value documentation and integration with other projects such as Spring Cloud, Kubernetes, Dubbo, etc. We are very glad to work on any issue for these aspects.

Please note that any PR must be associated with a valid issue. Otherwise, the PR will be rejected.

#### Begin your contribution

Now if you want to contribute, please create a new pull request.

We use the `develop` branch as the development branch, which indicates that this is an unstable branch.

Furthermore, our branching model complies with [https://nvie.com/posts/a-successful-git-branching-model/](https://nvie.com/posts/a-successful-git-branching-model/). We strongly suggest new comers walk through the above article before creating PR.

Now, if you are ready to create PR, here is the workflow for contributors:

1.  Fork to your own
    
2.  Clone fork to a local repository
    
3.  Create a new branch and work on it
    
4.  Keep your branch in sync
    
5.  Commit your changes (make sure your commit message is concise)

6.  Run pre-submission checks locally (see [Pre-submission Checks](#pre-submission-checks) below)

7.  Push your commits to your forked repository

8.  Create a pull request to **develop** branch.
    

When creating pull request:

1.  Please follow [the pull request template](./.github/PULL_REQUEST_TEMPLATE.md).
    
2.  Please create the request to **develop** branch.
    
3.  Please make sure the PR has a corresponding issue.
    
4.  If your PR contains large changes, e.g. component refactor or new components, please write detailed documents about its design and usage.
    
5.  Note that a single PR should not be too large. If heavy changes are required, it's better to separate the changes to a few individual PRs.
    
6.  After creating a PR, one or more reviewers will be assigned to the pull request.
    
7.  Before merging a PR, squash any fix review feedback, typo, merged and rebased sorts of commits. The final commit message should be clear and concise.
    

If your PR contains large changes, e.g. component refactor or new components, please write detailed documents about its design and usage.

### License header

Every new source file (`.java`, `.xml`, etc.) **must** include the Apache License 2.0 header. CI enforces this via `apache-rat:check` and your PR will fail without it.

Copy the header below into every new file (adjust the comment style for non-Java files):

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

### Pre-submission checks

Before pushing your commits, run the following command locally to catch issues early:

```bash
mvn -B clean compile apache-rat:check checkstyle:check spotbugs:check -DskipTests
```

| Check | What it verifies |
|-------|-----------------|
| `compile` | Code compiles without errors |
| `apache-rat:check` | All source files have the required Apache License header |
| `checkstyle:check` | Code style complies with [Alibaba Java Coding Guidelines](style/NacosCheckStyle.xml) |
| `spotbugs:check` | No high-priority bug patterns detected by [SpotBugs](https://spotbugs.github.io/) |

To run unit tests:

```bash
mvn clean test
```

### Code review guidance

Committers will rotate reviewing the code to make sure all the PR will be reviewed timely and by at least one committer before merge. If we aren't doing our job (sometimes we drop things). And as always, we welcome volunteers for code review.

Some principles:

-   Readability - Important code should be well-documented. API should have Javadoc. Code style should be complied with the existing one.
    
-   Elegance: New functions, classes or components should be well-designed.
    
-   Testability - 80% of the new code should be covered by unit test cases.
    
-   Maintainability - Comply with our [PMD spec](style/codeStyle.md), and 3-month-frequency update should be maintained at least.
    

### Now how about try become a committer?

Generally speaking, contribute 8 non-trivial patches and get at least three different people to review them (you'll need three people to support you). Then ask someone to nominate you. You're demonstrating your:

-   at least 8 PR and the associated issues to the project,
    
-   ability to collaborate with the team,
    
-   understanding of the projects' code base and coding style, and
    
-   ability to write good code (last but certainly not least)
    

A current committer nominates you by slacking the team on the Nacos issue with the label "nomination"

-   your first and last name
    
-   a link to your Git profile
    
-   an explanation of why you should be a committer,
    
-   Elaborate on the top 3 PR and the associated issues the nominator has worked with you that can demonstrate your ability.
    

Two other committers need to second your nomination. If no one objects in 5 working days (China), you're a committer. If anyone objects or wants more information, the committers discuss and usually come to a consensus (within the 5 working days). If issues cannot be resolved, there's a vote among current committers.

![](http://acm-public.oss-cn-hangzhou.aliyuncs.com/nomination_process.png)

In the worst case, this can drag out for two weeks. Keep contributing! Even in the rare cases where a nomination fails, the objection is usually something easy to address like "more patches" or "not enough people are familiar with this person's work."
