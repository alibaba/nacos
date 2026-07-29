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

package com.alibaba.nacos.api.ai.model.agent;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

/**
 * Shared validation helpers for Agent Admin request models.
 *
 * @author Nacos
 */
final class AgentAdminRequestUtils {
    
    private AgentAdminRequestUtils() {
    }
    
    static void validateIdentity(String agentName) {
        AgentValidationUtils.validateAgentName(agentName);
    }
    
    static void validateVersion(String version) {
        AgentValidationUtils.validateVersion(version);
    }
    
    static void validateWritableStatus(String status) {
        if (!AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(status)
            && !AiConstants.Agent.RESOURCE_STATUS_DISABLE.equals(status)) {
            throw new IllegalArgumentException("Invalid Agent resource status: " + status);
        }
    }
    
    static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
