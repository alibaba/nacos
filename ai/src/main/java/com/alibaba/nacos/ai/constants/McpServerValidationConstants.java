/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.constants;

/**
 * Constants for MCP server validation.
 *
 * @author nacos
 */
public final class McpServerValidationConstants {
    
    /**
     * Validation status: valid.
     */
    public static final String STATUS_VALID = "valid";
    
    /**
     * Validation status: invalid.
     */
    public static final String STATUS_INVALID = "invalid";
    
    /**
     * Validation status: duplicate.
     */
    public static final String STATUS_DUPLICATE = "duplicate";
    
    private McpServerValidationConstants() {
        // Private constructor to prevent instantiation
    }
}