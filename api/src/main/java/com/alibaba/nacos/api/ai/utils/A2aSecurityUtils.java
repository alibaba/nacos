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
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.ai.model.a2a.SecurityRequirement;
import com.alibaba.nacos.api.ai.model.a2a.StringList;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Card security compatibility at publication and historical read boundaries.
 *
 * <p>Standard Cards use normal POJO binding. Only a failed binding or an incomplete security
 * structure retries the historical flat representation. This utility never writes storage.</p>
 *
 * @author nacos
 */
public final class A2aSecurityUtils {
    
    private static final String REQUIREMENTS = "securityRequirements";
    
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
        try {
            T card = JsonUtils.toObj(json, type);
            normalize(card);
            return card;
        } catch (NacosDeserializationException | IllegalArgumentException original) {
            try {
                // The compatibility retry is deliberately limited to this historical field.
                Map<String, Object> legacy = JsonUtils.toObj(json, Map.class);
                require(legacy != null && legacy.get(REQUIREMENTS) != null);
                validateRawSkillRequirements(legacy.get("skills"));
                legacy.put(REQUIREMENTS, fromLegacy(readLegacyScopes(legacy.get(REQUIREMENTS))));
                T card = JsonUtils.toObj(JsonUtils.toJson(legacy), type);
                normalize(card);
                return card;
            } catch (NacosDeserializationException | IllegalArgumentException incompatible) {
                if (original instanceof NacosDeserializationException) {
                    throw (NacosDeserializationException) original;
                }
                throw new NacosDeserializationException(type, original);
            }
        }
    }
    
    /**
     * Synchronize the two POJO fields, preferring a non-null standard field, including an empty list.
     *
     * @param card caller-isolated Card
     */
    public static void normalize(AgentCard card) {
        require(card != null);
        validateSkillRequirements(card);
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
    
    private static void validateSkillRequirements(AgentCard card) {
        if (card.getSkills() == null) {
            return;
        }
        for (AgentSkill skill : card.getSkills()) {
            if (skill == null || skill.getSecurityRequirements() == null) {
                continue;
            }
            try {
                for (SecurityRequirement item : skill.getSecurityRequirements()) {
                    require(item != null && item.getSchemes() != null);
                    for (Map.Entry<String, StringList> scheme : item.getSchemes().entrySet()) {
                        require(scheme.getKey() != null && scheme.getValue() != null);
                        List<String> scopes = scheme.getValue().getList();
                        require(scopes != null);
                        for (Object scope : scopes) {
                            require(scope instanceof String);
                        }
                    }
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(
                    "Invalid AgentSkill securityRequirements: expected A2A schemes/list objects",
                    invalid);
            }
        }
    }
    
    private static void validateRawSkillRequirements(Object skills) {
        if (skills == null) {
            return;
        }
        require(skills instanceof List);
        for (Object skill : (List<?>) skills) {
            if (skill == null) {
                continue;
            }
            require(skill instanceof Map);
            Object requirements = ((Map<?, ?>) skill).get(REQUIREMENTS);
            if (requirements == null) {
                continue;
            }
            require(requirements instanceof List);
            for (Object item : (List<?>) requirements) {
                require(item instanceof Map);
                Object schemes = ((Map<?, ?>) item).get("schemes");
                require(schemes instanceof Map);
                for (Object scopes : ((Map<?, ?>) schemes).values()) {
                    require(scopes instanceof Map);
                    // Reject explicit nulls before JSON serialization can omit them. An absent
                    // list is the standard empty StringList, but a null list is invalid.
                    if (((Map<?, ?>) scopes).containsKey("list")) {
                        copyScopes(((Map<?, ?>) scopes).get("list"));
                    }
                }
            }
        }
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
            requirement.setSchemes(new LinkedHashMap<>());
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
    
    private static List<Map<String, List<String>>> readLegacyScopes(Object value) {
        require(value instanceof List);
        List<Map<String, List<String>>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            require(item instanceof Map);
            Map<String, List<String>> scopes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                require(entry.getKey() instanceof String);
                scopes.put((String) entry.getKey(), copyScopes(entry.getValue()));
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
