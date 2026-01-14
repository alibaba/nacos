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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Package per components.schemas.Package.
 *
 * @author xinluo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Package {

    private String registryType;

    private String registryBaseUrl;

    private String identifier;

    private String version;

    private String fileSha256;

    private String runtimeHint;

    private List<Argument> runtimeArguments;

    private List<Argument> packageArguments;

    private List<KeyValueInput> environmentVariables;

    /**
     * Transport field - required, supports multiple transport types (stdio/streamable-http/sse).
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = StdioTransport.class, name = "stdio"),
            @JsonSubTypes.Type(value = StreamableHttpTransport.class, name = "streamable-http"),
            @JsonSubTypes.Type(value = SseTransport.class, name = "sse")
    })
    @JsonIgnoreProperties(ignoreUnknown = true)
    private Object transport;

    /**
     * Get registry type.
     *
     * @return registry type
     */
    public String getRegistryType() {
        return registryType;
    }

    /**
     * Set registry type.
     *
     * @param registryType registry type
     */
    public void setRegistryType(String registryType) {
        this.registryType = registryType;
    }

    /**
     * Get registry base URL.
     *
     * @return registry base URL
     */
    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    /**
     * Set registry base URL.
     *
     * @param registryBaseUrl registry base URL
     */
    public void setRegistryBaseUrl(String registryBaseUrl) {
        this.registryBaseUrl = registryBaseUrl;
    }

    /**
     * Get identifier.
     *
     * @return identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Set identifier.
     *
     * @param identifier identifier
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Get version.
     *
     * @return version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set version.
     *
     * @param version version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get file SHA 256.
     *
     * @return file SHA 256
     */
    public String getFileSha256() {
        return fileSha256;
    }

    /**
     * Set file SHA 256.
     *
     * @param fileSha256 file SHA 256
     */
    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    /**
     * Get runtime hint.
     *
     * @return runtime hint
     */
    public String getRuntimeHint() {
        return runtimeHint;
    }

    /**
     * Set runtime hint.
     *
     * @param runtimeHint runtime hint
     */
    public void setRuntimeHint(String runtimeHint) {
        this.runtimeHint = runtimeHint;
    }

    /**
     * Get runtime arguments.
     *
     * @return runtime arguments
     */
    public List<Argument> getRuntimeArguments() {
        return runtimeArguments;
    }

    /**
     * Set runtime arguments.
     *
     * @param runtimeArguments runtime arguments
     */
    public void setRuntimeArguments(List<Argument> runtimeArguments) {
        this.runtimeArguments = runtimeArguments;
    }

    /**
     * Get package arguments.
     *
     * @return package arguments
     */
    public List<Argument> getPackageArguments() {
        return packageArguments;
    }

    /**
     * Set package arguments.
     *
     * @param packageArguments package arguments
     */
    public void setPackageArguments(List<Argument> packageArguments) {
        this.packageArguments = packageArguments;
    }

    /**
     * Get environment variables.
     *
     * @return environment variables
     */
    public List<KeyValueInput> getEnvironmentVariables() {
        return environmentVariables;
    }

    /**
     * Set environment variables.
     *
     * @param environmentVariables environment variables
     */
    public void setEnvironmentVariables(List<KeyValueInput> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    /**
     * Get transport.
     *
     * @return transport
     */
    public Object getTransport() {
        return transport;
    }

    /**
     * Set transport.
     *
     * @param transport transport
     */
    public void setTransport(Object transport) {
        this.transport = transport;
    }
}
