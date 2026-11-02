/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One A2A 1.0 authentication alternative; all schemes in this alternative must be satisfied.
 *
 * @author nacos
 */
public class SecurityRequirement {
    
    private Map<String, StringList> schemes = new LinkedHashMap<>();
    
    /**
     * Get the schemes that must all be satisfied for this alternative.
     * @return scheme names mapped to their required scopes
     */
    public Map<String, StringList> getSchemes() {
        return schemes;
    }
    
    /**
     * Set the schemes for this authentication alternative.
     * @param schemes scheme names mapped to their required scopes
     */
    public void setSchemes(Map<String, StringList> schemes) {
        this.schemes = schemes;
    }
    
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SecurityRequirement
            && Objects.equals(schemes, ((SecurityRequirement) other).schemes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(schemes);
    }
}
