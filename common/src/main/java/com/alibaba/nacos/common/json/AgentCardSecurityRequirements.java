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

package com.alibaba.nacos.common.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges the historical Java scope maps and A2A 1.0 SecurityRequirement JSON.
 *
 * @author nacos
 */
final class AgentCardSecurityRequirements {
    
    private AgentCardSecurityRequirements() {
    }
    
    static List<Map<String, Object>> toJson(List<Map<String, List<String>>> requirements) {
        if (requirements == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>(requirements.size());
        for (Map<String, List<String>> requirement : requirements) {
            require(requirement != null);
            if (!(requirement instanceof WrappedRequirement)) {
                result.add(new LinkedHashMap<String, Object>(requirement));
                continue;
            }
            Map<String, Object> schemes = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : requirement.entrySet()) {
                schemes.put(entry.getKey(), Collections.singletonMap("list", entry.getValue()));
            }
            result.add(Collections.singletonMap("schemes", schemes));
        }
        return result;
    }
    
    static List<Map<String, List<String>>> fromJson(List<Map<String, Object>> requirements) {
        if (requirements == null) {
            return null;
        }
        List<Map<String, List<String>>> result = new ArrayList<>(requirements.size());
        for (Map<String, Object> requirement : requirements) {
            require(requirement != null);
            Object wrapped = requirement.get("schemes");
            // A historical scheme can itself be named "schemes" and have an array value.
            boolean standard = requirement.containsKey("schemes") && !(wrapped instanceof List);
            Map<?, ?> schemes = requirement;
            if (standard) {
                require(wrapped instanceof Map && requirement.size() == 1);
                schemes = (Map<?, ?>) wrapped;
            }
            Map<String, List<String>> scopesByScheme = standard
                ? new WrappedRequirement() : new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : schemes.entrySet()) {
                Object scopes = entry.getValue();
                if (standard) {
                    require(scopes instanceof Map);
                    Map<?, ?> stringList = (Map<?, ?>) scopes;
                    scopes = stringList.containsKey("list") ? stringList.get("list")
                        : Collections.emptyList();
                }
                require(scopes instanceof List);
                List<String> values = new ArrayList<>();
                for (Object scope : (List<?>) scopes) {
                    require(scope instanceof String);
                    values.add((String) scope);
                }
                scopesByScheme.put((String) entry.getKey(), values);
            }
            result.add(scopesByScheme);
        }
        return result;
    }
    
    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("Invalid AgentCard securityRequirements: expected "
                + "scheme scope arrays or A2A schemes/list objects");
        }
    }
    
    /** Retain standard wire shape through the existing mutable Java scope-map API. */
    private static final class WrappedRequirement extends LinkedHashMap<String, List<String>> {
        
        private static final long serialVersionUID = 1L;
    }
}
