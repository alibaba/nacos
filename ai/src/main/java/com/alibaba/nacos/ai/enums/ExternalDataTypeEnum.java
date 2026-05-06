/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.enums;

import com.alibaba.nacos.api.utils.StringUtils;

/**
 * External data type enum. User can import external
 * mcp server data to nacos, the data type is defined
 * by this enum.
 * @author xinluo
 */
public enum ExternalDataTypeEnum {

    /**
     * MCP Server json text, the json text should follow the
     * MCP Server json format as defined in
     * <a href="https://github.com/modelcontextprotocol/registry/blob/main/docs/
     * reference/server-json/server.schema.json">MCP Server</a>.
     */
    JSON("json"),

    /**
     * MCP registry url, the url should be a valid MCP registry url
     * and the api should follow the MCP registry api as defined in
     * <a href="https://github.com/modelcontextprotocol/registry/blob/main/docs/
     * reference/api/openapi.yaml">openapi.yaml</a>.
     */
    URL("url"),

    /**
     * MCP registry seed file
     * <a href="https://github.com/modelcontextprotocol/registry/blob/main/data
     * /seed.json">seed.json</a>.
     */
    FILE("file");

    /**
     * The name of the external data type.
     */
    private final String name;

    ExternalDataTypeEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Parse the external data type from the given value.
     * @param value the value to parse.
     * @return the external data type.
     */
    public static ExternalDataTypeEnum parseType(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        for (ExternalDataTypeEnum type : ExternalDataTypeEnum.values()) {
            if (type.getName().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
