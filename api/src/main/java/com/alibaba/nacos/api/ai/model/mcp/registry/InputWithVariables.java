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

import java.util.Map;

/**
 * InputWithVariables model for MCP registry, supporting variable mapping.
 * @author xinluo
 */
public class InputWithVariables extends Input {
    private Map<String, Input> variables;

    /**
     * Get variable mapping for input value replacement.
     * @return variable map
     */
    public Map<String, Input> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Input> variables) {
        this.variables = variables;
    }
}
