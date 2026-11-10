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

package com.alibaba.nacos.copilot.connection;

/**
 * Where a model connection was read from.
 *
 * @author nacos
 */
public enum ConnectionSource {
    
    /**
     * Declared in the deployment configuration, {@code nacos.copilot.connections.*}.
     */
    DEPLOYMENT("deployment"),
    
    /**
     * Synthesized from the legacy {@code copilot-config.json}.
     */
    LEGACY("legacy");
    
    private final String value;
    
    ConnectionSource(String value) {
        this.value = value;
    }
    
    /**
     * Name used in logs and in the connection status exposed to the console.
     *
     * @return source name
     */
    public String getValue() {
        return value;
    }
}
