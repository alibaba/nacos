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

package com.alibaba.nacos.ai.model.agent;

import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;

import java.util.List;

/**
 * Complete protocol-neutral content stored for one Agent Version.
 *
 * @author Nacos
 */
public class AgentVersionContent {
    
    public static final String KIND = "AgentVersionContent";
    
    public static final int SCHEMA_VERSION = 1;
    
    private String kind;
    
    private Integer schemaVersion;
    
    private List<AgentCallInterface> callInterfaces;
    
    public AgentVersionContent() {
    }
    
    public AgentVersionContent(List<AgentCallInterface> callInterfaces) {
        this.kind = KIND;
        this.schemaVersion = SCHEMA_VERSION;
        this.callInterfaces = callInterfaces;
    }
    
    public String getKind() {
        return kind;
    }
    
    public void setKind(String kind) {
        this.kind = kind;
    }
    
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public List<AgentCallInterface> getCallInterfaces() {
        return callInterfaces;
    }
    
    public void setCallInterfaces(List<AgentCallInterface> callInterfaces) {
        this.callInterfaces = callInterfaces;
    }
}
