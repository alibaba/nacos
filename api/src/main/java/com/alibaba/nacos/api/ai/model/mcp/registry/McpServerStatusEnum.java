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

package com.alibaba.nacos.api.ai.model.mcp.registry;

/**
 * McpServerStatusEnum.
 *
 * @author xinluo
 */
public enum McpServerStatusEnum {

    /**
     * active.
     */
    ACTIVE("active"),

    /**
     * deleted.
     */
    DELETED("deleted"),

    /**
     * deprecated.
     */
    DEPRECATED("deprecated");

    /**
     * name.
     */
    private final String name;

    McpServerStatusEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * parse string status to enum.
     * return null if status is not valid.
     * @param status status.
     * @return McpServerStatusEnum.
     */
    public static McpServerStatusEnum parseStatus(String status) {
        for (McpServerStatusEnum value : values()) {
            if (value.getName().equals(status)) {
                return value;
            }
        }
        return null;
    }
}
