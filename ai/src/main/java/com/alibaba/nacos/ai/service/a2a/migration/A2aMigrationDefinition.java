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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public final class A2aMigrationDefinition {
    
    private final Agent agent;
    
    private final List<AgentVersionDetail> versions;
    
    private final String latestVersion;
    
    A2aMigrationDefinition(Agent agent, List<AgentVersionDetail> versions,
        String latestVersion) {
        this.agent = agent;
        this.versions = Collections.unmodifiableList(
            new ArrayList<AgentVersionDetail>(versions));
        this.latestVersion = latestVersion;
    }
    
    public Agent getAgent() {
        return agent;
    }
    
    public List<AgentVersionDetail> getVersions() {
        return versions;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
}
