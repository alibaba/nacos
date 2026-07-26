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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import com.alibaba.nacos.common.notify.Event;

/**
 * Nacos AI module agent card changed event in nacos- client.
 *
 * @author xiweng.yy
 */
public class AgentCardChangedEvent extends Event {
    
    private static final long serialVersionUID = 2010793364377243018L;
    
    private final String agentName;
    
    private final String version;
    
    private final AgentCardDetailInfo agentCard;
    
    public AgentCardChangedEvent(AgentCardDetailInfo agentCard) {
        this.agentCard = agentCard;
        this.agentName = agentCard.getName();
        this.version = buildVersion(agentCard);
    }
    
    private String buildVersion(AgentCardDetailInfo agentCard) {
        if (null == agentCard.isLatestVersion() || agentCard.isLatestVersion()) {
            return CacheKeyUtils.LATEST_VERSION;
        }
        return agentCard.getVersion();
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public AgentCardDetailInfo getAgentCard() {
        return agentCard;
    }
}
