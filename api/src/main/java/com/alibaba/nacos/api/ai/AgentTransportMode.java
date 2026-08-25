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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;

/**
 * Transport mode for Agent/RAD operations exposed by {@link AiService}.
 *
 * @author Nacos
 */
public enum AgentTransportMode {
    
    /**
     * Require gRPC and keep reconnecting when the initial connection is unavailable.
     */
    GRPC(AiConstants.AI_TRANSPORT_MODE_GRPC),
    
    /**
     * Use HTTP without starting the Agent gRPC transport during service construction.
     */
    HTTP(AiConstants.AI_TRANSPORT_MODE_HTTP),
    
    /**
     * Prefer an available gRPC connection and safely route to HTTP otherwise.
     */
    AUTO(AiConstants.AI_TRANSPORT_MODE_AUTO);
    
    private final String value;
    
    AgentTransportMode(String value) {
        this.value = value;
    }
    
    /**
     * Return the client property value.
     *
     * @return lower-case property value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Parse one exact transport property value, ignoring character case.
     *
     * @param value property value
     * @return parsed transport mode
     * @throws IllegalArgumentException when the value is null, padded, or unsupported
     */
    public static AgentTransportMode fromValue(String value) {
        for (AgentTransportMode each : values()) {
            if (each.value.equalsIgnoreCase(value)) {
                return each;
            }
        }
        throw new IllegalArgumentException("Unsupported Agent transport mode: " + value);
    }
}
