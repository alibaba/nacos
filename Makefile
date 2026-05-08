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
AUTH_ARGS := -Dnacos.core.auth.server.identity.key=testKey \
             -Dnacos.core.auth.server.identity.value=testValue \
             -Dnacos.core.auth.plugin.nacos.token.secret.key=VGhpc0lzTXlDdXN0b21TZWNyZXRLZXkwMTIzNDU2Nzg=

# Mark additional targets as phony
.PHONY: clean build-frontend build-maven build \
	install-and-run-bootstrap \
	install-and-run-bootstrap-config \
	install-and-run-bootstrap-naming \
	install-and-run-bootstrap-microservice \
	install-and-run-bootstrap-ai \
	install-and-run-bootstrap-extension-ai-enabled \
	install-and-run-bootstrap-extension-ai-disabled

# Clean all build artifacts and generated files
clean: ## Clean the project
	$(MVN) $(MAVEN_ARGS) clean

build-frontend: ## Build frontend (console-ui and console-ui-next)
	@echo "Building console-ui..."
	cd console-ui && npm i && npm run build
	@echo "Building console-ui-next..."
	cd console-ui-next && npm i && npm run build

build-maven: ## Build Maven project (skip tests)
	@echo "Building and Install..."
	$(MVN) $(MAVEN_ARGS) clean install -DskipTests

build: build-frontend build-maven ## Build both frontend and Maven project

# Build and run Nacos in development mode
# Steps:
#   1. Build console-ui (old React UI)
#   2. Build console-ui-next (new React UI)
#   3. Maven clean install (skip tests for faster build)
#   4. Run bootstrap module with Spring Boot
install-and-run-bootstrap: build ## Run Nacos bootstrap module
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
