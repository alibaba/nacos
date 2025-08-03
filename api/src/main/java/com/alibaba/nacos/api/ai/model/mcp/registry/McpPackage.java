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
 * MCP Package information from registry.
 * @author nacos
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName", 
        "checkstyle:SummaryJavadoc", "PMD.LowerCamelCaseVariableNamingRule"})
public class McpPackage {
    
    private String registry_name;
    
    private String name;
    
    private String version;
    
    private List<PackageArgument> package_arguments;
    
    private List<EnvironmentVariable> environment_variables;
    
    public String getRegistry_name() {
        return registry_name;
    }
    
    public void setRegistry_name(String registry_name) {
        this.registry_name = registry_name;
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
    
    public List<PackageArgument> getPackage_arguments() {
        return package_arguments;
    }
    
    public void setPackage_arguments(List<PackageArgument> package_arguments) {
        this.package_arguments = package_arguments;
    }
    
    public List<EnvironmentVariable> getEnvironment_variables() {
        return environment_variables;
    }
    
    public void setEnvironment_variables(List<EnvironmentVariable> environment_variables) {
        this.environment_variables = environment_variables;
    }
}