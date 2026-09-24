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

package com.alibaba.nacos.client.ai.remote.capability;

import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.constant.AiConstants.Capability;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, per-binding evidence. Missing evidence is never a negative capability.
 *
 * @author Nacos
 */
public final class AiCapabilitySnapshot {
    
    private static final AiCapabilitySnapshot UNKNOWN =
        new AiCapabilitySnapshot(Collections.<String, AbilityStatus>emptyMap());
    
    private final Map<String, AbilityStatus> capabilities;
    
    private AiCapabilitySnapshot(Map<String, AbilityStatus> capabilities) {
        this.capabilities = Collections.unmodifiableMap(new HashMap<>(capabilities));
    }
    
    /**
     * Return a snapshot without any reliable capability evidence.
     *
     * @return unknown snapshot
     */
    public static AiCapabilitySnapshot unknown() {
        return UNKNOWN;
    }
    
    /**
     * Parse a versioned v3 Result without coercing feature values or exposing raw payloads.
     *
     * @param body response body
     * @return immutable capability evidence
     * @throws NacosException for an explicit v3 error response
     */
    public static AiCapabilitySnapshot parse(String body) throws NacosException {
        Map<?, ?> envelope;
        try {
            envelope = JsonUtils.toObj(body, Map.class);
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
        if (envelope == null || !(envelope.get("code") instanceof Number)) {
            return UNKNOWN;
        }
        Number code = (Number) envelope.get("code");
        if (code.intValue() != code.doubleValue()) {
            return UNKNOWN;
        }
        if (code.intValue() != 0) {
            throw new NacosException(code.intValue(), "AI capability request was rejected.");
        }
        if (!(envelope.get("data") instanceof Map)) {
            return UNKNOWN;
        }
        Map<?, ?> data = (Map<?, ?>) envelope.get("data");
        Object version = data.get("schemaVersion");
        if (!(version instanceof Number)
            || ((Number) version).doubleValue() != Capability.SCHEMA_VERSION
            || !(data.get("capabilities") instanceof Map)) {
            return UNKNOWN;
        }
        Map<?, ?> features = (Map<?, ?>) data.get("capabilities");
        Map<String, AbilityStatus> result = new HashMap<>();
        for (String key : new String[] {Capability.RAD_V1, Capability.MCP, Capability.SKILL,
            Capability.PROMPT, Capability.AGENT_SPEC}) {
            Object value = features.get(key);
            if (value instanceof Boolean) {
                result.put(key, Boolean.TRUE.equals(value) ? AbilityStatus.SUPPORTED
                    : AbilityStatus.NOT_SUPPORTED);
            }
        }
        return new AiCapabilitySnapshot(result);
    }
    
    /**
     * Get evidence for one feature.
     *
     * @param capability feature key
     * @return supported, unsupported, or unknown
     */
    public AbilityStatus get(String capability) {
        AbilityStatus status = capabilities.get(capability);
        return status == null ? AbilityStatus.UNKNOWN : status;
    }
}
