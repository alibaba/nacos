/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Package model for MCP registry, represents a package configuration.
 * @author xinluo
 */
public class Package {
    
    @JsonProperty("registry_name")
    private String registryName;

    private String name;
    
    private String version;
    
    @JsonProperty("runtime_hint")
    private String runtimeHint;

    @JsonProperty("runtime_arguments")
    private List<Argument> runtimeArguments;

    @JsonProperty("package_arguments")
    private List<Argument> packageArguments;

    @JsonProperty("environment_variables")
    private List<KeyValueInput> environmentVariables;

    public List<Argument> getRuntimeArguments() {
        return runtimeArguments;
    }

    public void setRuntimeArguments(List<Argument> runtimeArguments) {
        this.runtimeArguments = runtimeArguments;
    }

    public String getRegistryName() {
        return registryName;
    }

    public void setRegistryName(String registryName) {
        this.registryName = registryName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRuntimeHint() {
        return runtimeHint;
    }

    public void setRuntimeHint(String runtimeHint) {
        this.runtimeHint = runtimeHint;
    }

    public List<Argument> getPackageArguments() {
        return packageArguments;
    }

    public void setPackageArguments(List<Argument> packageArguments) {
        this.packageArguments = packageArguments;
    }

    public List<KeyValueInput> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(List<KeyValueInput> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }
}
