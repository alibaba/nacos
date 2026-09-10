#!/usr/bin/env bash
#
# Copyright 1999-2026 Alibaba Group Holding Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
mkdir -p "${SCRIPT_DIR}/target/compatibility-libs"
mvn -B -f "${SCRIPT_DIR}/src/test/compatibility/pom.xml" \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree \
    -Dmdep.outputFile="${SCRIPT_DIR}/target/compatibility-libs/legacy-classpath.txt" \
    -DoutputFile="${SCRIPT_DIR}/target/compatibility-libs/legacy-dependency-tree.txt"
mvn -B -f "${PROJECT_ROOT}/pom.xml" -pl test/java-sdk-test \
    -Pjava-sdk-integration-test,ai-api-compatibility -DskipTests=false "$@" verify
