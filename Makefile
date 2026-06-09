# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# Makefile usage guide: https://github.com/alibaba/nacos/issues/15338

# Set shell to bash for better compatibility
SHELL := /usr/bin/env bash

# Mark targets as phony (not actual files)
.PHONY: help
# Set default target when running 'make' without arguments
.DEFAULT_GOAL := help

# Display help information with colored output
# Parses comments with '##' and formats them nicely
help: ## Show help information
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-48s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# Maven command configuration
# Prefer using system mvn, fall back to ./mvnw wrapper if mvn does not exist
MVN ?= $(shell command -v mvn >/dev/null 2>&1 && echo "mvn" || echo "./mvnw")
# Maven arguments: -T 4C (parallel build with 4 threads per CPU core)
#                  -e (show error stack traces)
#                  -B (batch mode, non-interactive)
#                  -V (show version information)
MAVEN_ARGS ?= -T 4C -e -B -V

JVM_BASE_ARGS := --add-opens java.base/java.util=ALL-UNNAMED
AUTH_DISABLED_ARGS := -Dnacos.core.auth.enabled=false \
                      -Dnacos.core.auth.admin.enabled=false \
                      -Dnacos.core.auth.console.enabled=false
AUTH_ARGS := -Dnacos.core.auth.server.identity.key=testKey \
             -Dnacos.core.auth.server.identity.value=testValue \
             -Dnacos.core.auth.plugin.nacos.token.secret.key=VGhpc0lzTXlDdXN0b21TZWNyZXRLZXkwMTIzNDU2Nzg= \
             $(AUTH_DISABLED_ARGS)

# Mark additional targets as phony
.PHONY: clean test check-maven build-maven-test build-frontend build-maven build \
	install-and-run-bootstrap \
	install-and-run-bootstrap-config \
	install-and-run-bootstrap-naming \
	install-and-run-bootstrap-microservice \
	install-and-run-bootstrap-ai \
	install-and-run-bootstrap-extension-ai-enabled \
	install-and-run-bootstrap-extension-ai-disabled \
	run-it-tests \
	run-java-sdk-it-tests \
	run-maintainer-sdk-it-tests

# Clean all build artifacts and generated files
clean: ## Clean the project
	$(MVN) $(MAVEN_ARGS) clean

test: ## Run unit tests
	$(MVN) $(MAVEN_ARGS) test

build-frontend: ## Build frontend (console-ui and console-ui-next)
	@echo "Building console-ui..."
	cd console-ui && npm i && npm run build
	@echo "Building console-ui-next..."
	cd console-ui-next && npm i && npm run build

build-maven: ## Build Maven project (skip tests, activation profile release-nacos)
	@echo "Building and Install..."
	$(MVN) $(MAVEN_ARGS) clean install -Prelease-nacos -DskipTests

check-maven: ## Run pre-submission checks (compile, rat, checkstyle, spotbugs, spotless)
	$(MVN) $(MAVEN_ARGS) clean compile apache-rat:check checkstyle:check spotbugs:check spotless:check -e -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

build-maven-test: ## Build Maven project with tests (skip rat, spotbugs checks)
	$(MVN) $(MAVEN_ARGS) '-Prelease-nacos,!dev' clean install -Drat.skip=true -Dspotbugs.skip=true -DtrimStackTrace=false -U -e -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

build: build-frontend build-maven ## Build both frontend and Maven project

# Build and run Nacos in development mode
# Steps:
#   1. Build console-ui (old React UI)
#   2. Build console-ui-next (new React UI)
#   3. Maven clean install (skip tests for faster build)
#   4. Run bootstrap module with Spring Boot
install-and-run-bootstrap: build ## Build and run Nacos bootstrap module
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true"

install-and-run-bootstrap-config: build ## Build and run Nacos in config mode
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true -Dnacos.functionMode=config"

install-and-run-bootstrap-naming: build ## Build and run Nacos in naming mode
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true -Dnacos.functionMode=naming"

install-and-run-bootstrap-microservice: build ## Build and run Nacos in microservice mode
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true -Dnacos.functionMode=microservice"

install-and-run-bootstrap-ai: build ## Build and run Nacos in AI mode
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true -Dnacos.functionMode=ai"

install-and-run-bootstrap-extension-ai-enabled: build ## Build and run Nacos with nacos.extension.ai.enabled=true
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true -Dnacos.extension.ai.enabled=true"

install-and-run-bootstrap-extension-ai-disabled: build ## Build and run Nacos with nacos.extension.ai.enabled=false
	cd bootstrap && $(MVN) $(MAVEN_ARGS) spring-boot:run -Prelease-nacos -DskipTests \
  -Dspring-boot.run.jvmArguments="$(JVM_BASE_ARGS) $(AUTH_ARGS) -Dnacos.standalone=true -Dnacos.extension.ai.enabled=false"

run-it-tests: ## Run IT Tests
	cd test && $(MVN) $(MAVEN_ARGS) clean verify -Pintegration-test

run-java-sdk-it-tests: ## Run Java SDK IT Tests
	$(MVN) $(MAVEN_ARGS) -pl test/java-sdk-test clean verify -Pjava-sdk-integration-test -DskipTests=false

run-maintainer-sdk-it-tests: ## Run Maintainer SDK IT Tests
	$(MVN) $(MAVEN_ARGS) -pl test/maintainer-sdk-test clean verify -Pmaintainer-sdk-integration-test -DskipTests=false
