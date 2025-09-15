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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Package per components.schemas.Package.
 *
 * @author xinluo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Package {

    @JsonProperty("registry_type")
    private String registryType;

    @JsonProperty("registry_base_url")
    private String registryBaseUrl;

    private String identifier;

    private String version;

    @JsonProperty("file_sha256")
    private String fileSha256;

    @JsonProperty("runtime_hint")
    private String runtimeHint;

    @JsonProperty("runtime_arguments")
    private List<Argument> runtimeArguments;

    @JsonProperty("package_arguments")
    private List<Argument> packageArguments;

    @JsonProperty("environment_variables")
    private List<KeyValueInput> environmentVariables;

    public String getRegistryType() {
        return registryType;
    }

    public void setRegistryType(String registryType) {
        this.registryType = registryType;
    }

    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    public void setRegistryBaseUrl(String registryBaseUrl) {
        this.registryBaseUrl = registryBaseUrl;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    public String getRuntimeHint() {
        return runtimeHint;
    }

    public void setRuntimeHint(String runtimeHint) {
        this.runtimeHint = runtimeHint;
    }

    public List<Argument> getRuntimeArguments() {
        return runtimeArguments;
    }

    public void setRuntimeArguments(List<Argument> runtimeArguments) {
        this.runtimeArguments = runtimeArguments;
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
