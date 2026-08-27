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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DefaultAgentWatchOwnerEligibilityCheckerTest {
    
    @Test
    void testAllowedAndDeniedVisibilityUseCapturedOwnerContext() throws Exception {
        AgentPersistenceService persistenceService = mock(AgentPersistenceService.class);
        Agent agent = agent();
        when(persistenceService.getAgent("public", "projection-agent")).thenReturn(agent);
        DefaultAgentWatchOwnerEligibilityChecker checker =
            new DefaultAgentWatchOwnerEligibilityChecker(persistenceService);
        AgentWatchOwnerContext owner = new AgentWatchOwnerContext("identity", "open-api");
        
        try (MockedStatic<VisibilityHelper> helper = mockStatic(VisibilityHelper.class)) {
            helper.when(() -> VisibilityHelper.canReadResource(eq("identity"), eq("open-api"),
                any(VisibilityResource.class))).thenAnswer(invocation -> {
                    VisibilityResource resource = invocation.getArgument(2);
                    assertEquals("public", resource.getNamespaceId());
                    assertEquals("projection-agent", resource.getResourceName());
                    assertEquals(Constants.Agent.RESOURCE_TYPE_AGENT, resource.getResourceType());
                    assertEquals("owner", resource.getOwner());
                    assertEquals("PRIVATE", resource.getScope());
                    return true;
                });
            assertEquals(AgentWatchOwnerEligibility.ALLOWED,
                checker.evaluate(owner, AgentProjectionTestFixtures.key("projection-agent")));
            
            helper.when(() -> VisibilityHelper.canReadResource(eq("identity"), eq("open-api"),
                any(VisibilityResource.class))).thenReturn(false);
            assertEquals(AgentWatchOwnerEligibility.DENIED,
                checker.evaluate(owner, AgentProjectionTestFixtures.key("projection-agent")));
        }
    }
    
    @Test
    void testMissingIsDeniedAndUnexpectedFailureIsUncertain() throws Exception {
        AgentPersistenceService persistenceService = mock(AgentPersistenceService.class);
        DefaultAgentWatchOwnerEligibilityChecker checker =
            new DefaultAgentWatchOwnerEligibilityChecker(persistenceService);
        AgentProjectionKey key = AgentProjectionTestFixtures.key("projection-agent");
        AgentWatchOwnerContext owner = new AgentWatchOwnerContext(null, null);
        assertEquals("", owner.getIdentity());
        assertEquals("", owner.getApiType());
        
        doThrow(new NacosException(NacosException.NOT_FOUND, "missing"))
            .when(persistenceService).getAgent("public", "projection-agent");
        assertEquals(AgentWatchOwnerEligibility.DENIED, checker.evaluate(owner, key));
        
        doThrow(new NacosException(NacosException.RESOURCE_NOT_FOUND, "missing"))
            .when(persistenceService).getAgent("public", "projection-agent");
        assertEquals(AgentWatchOwnerEligibility.DENIED, checker.evaluate(owner, key));
        
        doThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"))
            .when(persistenceService).getAgent("public", "projection-agent");
        assertEquals(AgentWatchOwnerEligibility.UNCERTAIN, checker.evaluate(owner, key));
        
        doThrow(new IllegalStateException("temporary"))
            .when(persistenceService).getAgent("public", "projection-agent");
        assertEquals(AgentWatchOwnerEligibility.UNCERTAIN, checker.evaluate(owner, key));
    }
    
    private Agent agent() {
        Agent result = new Agent();
        result.setNamespaceId("public");
        result.setAgentName("projection-agent");
        result.setOwner("owner");
        result.setScope("PRIVATE");
        return result;
    }
}
