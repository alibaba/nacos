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

package com.alibaba.nacos.sys.env;

/**
 * Nacos deployment type enum.
 *
 * @author xiweng.yy
 */
public enum DeploymentType {
    
    /**
     * Default deployment type, means the nacos server and console are deployed in one process.
     */
    MERGED(Constants.NACOS_DEPLOYMENT_TYPE_MERGED),
    
    /**
     * Only server deployment type, means only the nacos server is deployed in the process.
     */
    SERVER(Constants.NACOS_DEPLOYMENT_TYPE_SERVER),
    
    /**
     * Only console deployment type, means only the nacos console is deployed in the process.
     */
    CONSOLE(Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE),

    /**
     * Nacos Server and Mcp will be deployed in the process.
     */
    SERVER_WITH_MCP(Constants.NACOS_DEPLOYMENT_TYPE_SERVER_WITH_MCP),
    
    /**
     * Unknown deployment type.
     */
    ILLEGAL("unknown");
    
    private final String typeName;
    
    DeploymentType(String typeName) {
        this.typeName = typeName;
    }
    
    public String getTypeName() {
        return typeName;
    }
    
    public static DeploymentType getType(String type) {
        try {
            return DeploymentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ILLEGAL;
        }
    }
}
