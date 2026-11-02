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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.SecurityRequirement;
import com.alibaba.nacos.api.ai.model.a2a.StringList;
import com.alibaba.nacos.api.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Card security compatibility at publication and historical read boundaries.
 *
 * <p>Normal POJO serialization needs no converter. This utility must run before binding historical
 * JSON to the standard model, since ignoring its unknown flat keys would lose security scopes.</p>
 *
 * @author nacos
 */
public final class A2aSecurityUtils {
    
    private static final String REQUIREMENTS = "securityRequirements";
    
    private static final String SECURITY = "security";
    
    private A2aSecurityUtils() {
    }
    
    /**
     * Read a Card at a compatibility boundary, preserving historical scope declarations.
     *
     * @param json Card JSON
     * @param type Card model type
     * @param <T> Card model
     * @return Card with equivalent standard and legacy authentication declarations
     */
    public static <T extends AgentCard> T readCard(String json, Class<T> type) {
        Map<String, Object> card = JsonUtils.toObj(json, Map.class);
        return readCard(card, type);
    }
    
    /**
     * Bind a native Card descriptor without changing the caller's map.
     *
     * @param source raw Card descriptor
     * @param type Card model type
     * @param <T> Card model
     * @return normalized Card
     */
    public static <T extends AgentCard> T readCard(Map<String, Object> source, Class<T> type) {
        require(source != null);
        Map<String, Object> card = new LinkedHashMap<>(source);
        normalize(card);
        return JsonUtils.toObj(JsonUtils.toJson(card), type);
    }
    
    /**
     * Normalize only the two Card-level security fields in a caller-owned JSON object.
     *
     * @param card raw Card object, not a Skill or an arbitrary recursive JSON tree
     * @return whether the JSON object changed
     */
    public static boolean normalize(Map<String, Object> card) {
        Object current = card.get(REQUIREMENTS);
        Object legacy = card.get(SECURITY);
        if (current == null && legacy == null) {
            return false;
        }
        List<Map<String, List<String>>> scopes = readScopes(current == null ? legacy : current,
            current != null);
        List<Map<String, Object>> standard = new ArrayList<>();
        for (Map<String, List<String>> requirement : scopes) {
            Map<String, Object> schemes = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : requirement.entrySet()) {
                schemes.put(entry.getKey(),
                    Collections.singletonMap("list", new ArrayList<>(entry.getValue())));
            }
            standard.add(Collections.singletonMap("schemes", schemes));
        }
        boolean changed = !Objects.equals(current, standard) || !Objects.equals(legacy, scopes);
        if (changed) {
            card.put(REQUIREMENTS, standard);
            card.put(SECURITY, scopes);
        }
        return changed;
    }
    
    /**
     * Synchronize the two POJO fields, preferring a non-null standard field, including an empty list.
     *
     * @param card caller-isolated Card
     */
    public static void normalize(AgentCard card) {
        if (card.getSecurityRequirements() == null) {
            if (card.getSecurity() != null) {
                card.setSecurityRequirements(fromLegacy(card.getSecurity()));
            }
        } else if (!isNormalized(card)) {
            card.setSecurity(toLegacy(card.getSecurityRequirements()));
        }
    }
    
    /**
     * Check whether the standard and historical fields describe the same requirements.
     *
     * @param card Card
     * @return true when both fields are absent or semantically equal
     */
    public static boolean isNormalized(AgentCard card) {
        return Objects.equals(toLegacy(card.getSecurityRequirements()), card.getSecurity());
    }
    
    /**
     * Convert legacy scope maps to independent standard POJOs.
     *
     * @param legacy legacy scope maps, or null
     * @return standard requirements, or null
     */
    public static List<SecurityRequirement> fromLegacy(List<Map<String, List<String>>> legacy) {
        if (legacy == null) {
            return null;
        }
        List<SecurityRequirement> result = new ArrayList<>();
        for (Map<String, List<String>> item : legacy) {
            require(item != null);
            SecurityRequirement requirement = new SecurityRequirement();
            for (Map.Entry<String, List<String>> scheme : item.entrySet()) {
                require(scheme.getKey() != null);
                StringList scopes = new StringList();
                scopes.setList(copyScopes(scheme.getValue()));
                requirement.getSchemes().put(scheme.getKey(), scopes);
            }
            result.add(requirement);
        }
        return result;
    }
    
    /**
     * Convert standard requirements to independent legacy scope maps.
     *
     * @param requirements standard requirements, or null
     * @return legacy requirements, or null
     */
    public static List<Map<String, List<String>>> toLegacy(List<SecurityRequirement> requirements) {
        if (requirements == null) {
            return null;
        }
        List<Map<String, List<String>>> result = new ArrayList<>();
        for (SecurityRequirement item : requirements) {
            require(item != null && item.getSchemes() != null);
            Map<String, List<String>> schemes = new LinkedHashMap<>();
            for (Map.Entry<String, StringList> scheme : item.getSchemes().entrySet()) {
                require(scheme.getKey() != null && scheme.getValue() != null);
                schemes.put(scheme.getKey(), copyScopes(scheme.getValue().getList()));
            }
            result.add(schemes);
        }
        return result;
    }
    
    private static List<Map<String, List<String>>> readScopes(Object value, boolean allowStandard) {
        require(value instanceof List);
        List<Map<String, List<String>>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            require(item instanceof Map);
            Map<?, ?> requirement = (Map<?, ?>) item;
            Object wrapper = requirement.get("schemes");
            boolean standard = allowStandard && requirement.containsKey("schemes")
                && !(wrapper instanceof List);
            Map<?, ?> schemes = requirement;
            if (standard) {
                require(wrapper instanceof Map && requirement.size() == 1);
                schemes = (Map<?, ?>) wrapper;
            }
            Map<String, List<String>> scopes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : schemes.entrySet()) {
                require(entry.getKey() instanceof String);
                Object list = entry.getValue();
                if (standard) {
                    require(list instanceof Map);
                    Map<?, ?> stringList = (Map<?, ?>) list;
                    list = stringList.containsKey("list") ? stringList.get("list")
                        : Collections.emptyList();
                }
                scopes.put((String) entry.getKey(), copyScopes(list));
            }
            result.add(scopes);
        }
        return result;
    }
    
    private static List<String> copyScopes(Object source) {
        require(source instanceof List);
        List<String> result = new ArrayList<>();
        for (Object value : (List<?>) source) {
            require(value instanceof String);
            result.add((String) value);
        }
        return result;
    }
    
    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException(
                "Invalid AgentCard securityRequirements or security: "
                    + "expected scheme scope arrays or A2A schemes/list objects");
        }
    }
}
