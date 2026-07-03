<!--
  ~ Copyright 1999-2026 Alibaba Group Holding Ltd.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

# SkillSpector Runtime

This directory is the optional runtime location for the `skill-spector` AI
publish pipeline.

The target SkillSpector source version is `2.3.9`, declared by the upstream
`NVIDIA/skillspector` `pyproject.toml`.

The Nacos default distribution includes the pipeline integration and this
launcher, but does not include the SkillSpector Python runtime. Install the
runtime only when `skill-spector` is enabled.

Online installation:

```bash
nacos-setup skill-spector install \
  --nacos-home /path/to/nacos
```

Offline installation:

```bash
nacos-setup skill-spector install \
  --nacos-home /path/to/nacos \
  --file /path/to/skillspector-runtime-2.3.9-linux-x86_64.tar.gz \
  --sha256-file /path/to/skillspector-runtime-2.3.9-linux-x86_64.tar.gz.sha256
```

The runtime is unpacked here with the current platform key:

```text
runtimes/ai-pipeline/skill-spector/
  bin/skill-spector
  runtime/
    linux-x86_64/
      python/bin/python3
    linux-aarch64/
      python/bin/python3
```

The launcher never falls back to system `python3`. If the matching platform
runtime is missing, the pipeline stays unavailable when `skill-spector` is
enabled.
