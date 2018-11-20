# Contributing to Nacos

Welcome to Nacos! This document is a guideline about how to contribute to Nacos.
If you find something incorrect or missing, please leave comments / suggestions.

## Before you get started

### Code of Conduct

Please make sure to read and observe our [Code of Conduct](./CODE_OF_CONDUCT.md).


## Contributing

We are always very happy to have contributions, whether for typo fix, bug fix or big new features.
Please do not ever hesitate to ask a question or send a pull request.

We strongly value documentation and integration with other projects such as Spring Cloud, Kubernetes, Dubbo, etc.
We are very glad to accept improvements for these aspects.


### Open or Pickup an issue / PR

We use [GitHub Issues](https://github.com/alibaba/Nacos/issues) and [Pull Requests](https://github.com/alibaba/Nacos/pulls) for trackers.

If you find a typo in document, find a bug in code, or want new features, or want to give suggestions,
you can [open an issue on GitHub](https://github.com/alibaba/Nacos/issues/new) to report it.
Please follow the guideline message in the issue template.

If you just want to contribute directly you can choose the issue below.

 - [Contribution Welcome](https://github.com/alibaba/nacos/labels/contribution%20welcome): Heavily needed issue, but currently short of hand. 
 - [good first issue](https://github.com/alibaba/nacos/labels/good%20first%20issue): Good for newcomers, new comer can pickup one for warm-up.
 
Now if you want to contribute, please follow the [contribution workflow](#github-workflow) and create a new pull request.

### Begin your contribution

We use the `develop` branch as the development branch, which indicates that this is a unstable branch.

Here is the workflow for contributors:

1. Fork to your own
2. Clone fork to local repository
3. Create a new branch and work on it
4. Keep your branch in sync
5. Commit your changes (make sure your commit message concise)
6. Push your commits to your forked repository
7. Create a pull request.

When creating pull request:

1. Please follow [the pull request template](./.github/PULL_REQUEST_TEMPLATE.md).
2. Please make sure the PR has a corresponding issue.
3. If your PR contains large changes, e.g. component refactor or new components, please write detailed documents
about its design and usage. 
4. Note that a single PR should not be too large. If heavy changes are required, it's better to separate the changes
to a few individual PRs.
5. After creating a PR, one or more reviewers will be assigned to the pull request.
6. Before merging a PR, squash any fix review feedback, typo, merged, and rebased sorts of commits.
The final commit message should be clear and concise.


If your PR contains large changes, e.g. component refactor or new components, please write detailed documents
about its design and usage. 

### Code review guidance

Our PMC will rotate reviewing the code to make sure all the PR will be reviewed timely and by at least one committer before merge. If we aren't doing our job (sometimes we drop things). And as always, we welcome volunteers for code review. 

Some principles:

- Readability - Important code should be well-documented. API should have Javadoc. Code style should be complied with the existing one.
- Elegance: New functions, classes or components should be well designed.
- Testability - 80% of the new code should be covered by unit test cases. 
- Maintainability - Comply with our [PMD spec](style/codeStyle.xml), and 3-month-frequency update should be maintained at least.



