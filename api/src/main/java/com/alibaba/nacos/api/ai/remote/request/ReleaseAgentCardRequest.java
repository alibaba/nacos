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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;

/**
 * Nacos AI module release new agent card or new version of exist agent card request.
 *
 * @author xiweng.yy
 */
public class ReleaseAgentCardRequest extends AbstractAgentRequest {
    
    private AgentCard agentCard;
    
    private String registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
    
    private boolean setAsLatest;
    
    public AgentCard getAgentCard() {
        return agentCard;
    }
    
    public void setAgentCard(AgentCard agentCard) {
        this.agentCard = agentCard;
    }
    
    public String getRegistrationType() {
        return registrationType;
    }
    
    public void setRegistrationType(String registrationType) {
        this.registrationType = registrationType;
    }
    
    public boolean isSetAsLatest() {
        return setAsLatest;
    }
    
    public void setSetAsLatest(boolean setAsLatest) {
        this.setAsLatest = setAsLatest;
    }
}
