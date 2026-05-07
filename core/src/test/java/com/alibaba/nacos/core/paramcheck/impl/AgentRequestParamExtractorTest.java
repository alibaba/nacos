/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.paramcheck.impl;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.remote.request.QueryAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link AgentRequestParamExtractor} unit test.
 */
class AgentRequestParamExtractorTest {
    
    private AgentRequestParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new AgentRequestParamExtractor();
    }
    
    @Test
    void extractParamForAbstractAgentRequest() throws Exception {
        QueryAgentCardRequest request = new QueryAgentCardRequest();
        request.setNamespaceId("ns1");
        request.setAgentName("agent1");
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("ns1", list.get(0).getNamespaceId());
        assertEquals("agent1", list.get(0).getAgentName());
    }
    
    @Test
    void extractParamForReleaseAgentCardRequestWithoutCard() throws Exception {
        ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        request.setNamespaceId("ns1");
        request.setAgentName("agent1");
        request.setAgentCard(null);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("agent1", list.get(0).getAgentName());
    }
    
    @Test
    void extractParamForReleaseAgentCardRequestWithCard() throws Exception {
        ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        request.setNamespaceId("ns1");
        request.setAgentName("agent1");
        AgentCard card = new AgentCard();
        card.setName("cardName");
        request.setAgentCard(card);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("cardName", list.get(0).getAgentName());
    }
}
