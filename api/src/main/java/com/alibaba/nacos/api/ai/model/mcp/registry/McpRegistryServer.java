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

/**
 * McpRegistryServer.
 * @author xinluo
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName", "PMD.LowerCamelCaseVariableNamingRule"})
public class McpRegistryServer {
    
    private String id;
    
    private String name;
    
    private String description;
    
    private Repository repository;
    
    private ServerVersionDetail version_detail;
    
    private List<Package> packages;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public ServerVersionDetail getVersion_detail() {
        return version_detail;
    }

    public void setVersion_detail(ServerVersionDetail version_detail) {
        this.version_detail = version_detail;
    }
    
    public List<Package> getPackages() {
        return packages;
    }
    
    public void setPackages(List<Package> packages) {
        this.packages = packages;
    }
}
