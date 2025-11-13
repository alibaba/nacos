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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * PositionalArgument per components.schemas.PositionalArgument.
 *
 * @author xinluo
 */
@JsonTypeName("positional")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionalArgument extends InputWithVariables implements Argument {

    private String type = "positional";

    private String valueHint;

    private Boolean isRepeated;

    /**
     * Get type.
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Set type.
     *
     * @param type type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get value hint.
     *
     * @return value hint
     */
    public String getValueHint() {
        return valueHint;
    }

    /**
     * Set value hint.
     *
     * @param valueHint value hint
     */
    public void setValueHint(String valueHint) {
        this.valueHint = valueHint;
    }

    /**
     * Get is repeated flag.
     *
     * @return is repeated
     */
    public Boolean getIsRepeated() {
        return isRepeated;
    }

    /**
     * Set is repeated flag.
     *
     * @param isRepeated is repeated
     */
    public void setIsRepeated(Boolean isRepeated) {
        this.isRepeated = isRepeated;
    }
}
