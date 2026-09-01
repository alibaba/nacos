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

import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAgentProjectionProjectorTest {
    
    @Mock
    private AgentDiscoveryApplicationService discoveryService;
    
    private AgentProjectionKey key;
    
    private DefaultAgentProjectionProjector projector;
    
    @BeforeEach
    void setUp() {
        key = AgentProjectionTestFixtures.key(AgentProjectionTestFixtures.AGENT_NAME);
        projector = new DefaultAgentProjectionProjector(discoveryService, () -> 123L);
    }
    
    @Test
    void testSuccessBuildsFingerprintAndAllProtocolDependencies() throws NacosException {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot(
            AgentProjectionTestFixtures.AGENT_NAME, "a2a", "mcp");
        snapshot.setCallInterfaces(new ArrayList<>(snapshot.getCallInterfaces()));
        snapshot.getCallInterfaces().add(AgentProjectionTestFixtures.callInterface("custom",
            EndpointSource.DECLARED));
        when(discoveryService.projectCurrentFact(any())).thenReturn(snapshot);
        
        AgentProjectionState result = projector.project(key);
        
        assertEquals(AgentProjectionStatus.AVAILABLE, result.getStatus());
        assertTrue(result.isAvailable());
        assertFalse(result.requiresRetry());
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(snapshot), result.getFingerprint());
        assertEquals(123L, result.getComputedAt());
        assertNull(result.getErrorCode());
        assertNull(result.getErrorMessage());
        assertEquals(3, result.getPhysicalDependencies().size());
        assertTrue(result.getPhysicalDependencies().contains(
            AgentProjectionTestFixtures.service(AgentProjectionTestFixtures.AGENT_NAME, "a2a")));
        assertTrue(result.getPhysicalDependencies().contains(
            AgentProjectionTestFixtures.service(AgentProjectionTestFixtures.AGENT_NAME, "mcp")));
        assertTrue(result.getPhysicalDependencies().contains(
            AgentProjectionTestFixtures.service(AgentProjectionTestFixtures.AGENT_NAME,
                "custom")));
    }
    
    @Test
    void testCheckedFailuresAreClassified() throws NacosException {
        assertCheckedFailure(NacosException.NOT_FOUND, AgentProjectionStatus.NOT_FOUND, false);
        assertCheckedFailure(NacosException.RESOURCE_NOT_FOUND,
            AgentProjectionStatus.NOT_FOUND, false);
        assertCheckedFailure(NacosException.NO_RIGHT,
            AgentProjectionStatus.ACCESS_UNCERTAIN, false);
        assertCheckedFailure(NacosException.CONFLICT, AgentProjectionStatus.CONFLICT, false);
        assertCheckedFailure(NacosException.SERVER_ERROR,
            AgentProjectionStatus.TRANSIENT_FAILURE, true);
    }
    
    @Test
    void testRuntimeFailuresAreClassified() throws NacosException {
        doThrow(new NacosRuntimeException(NacosException.NO_RIGHT, "denied"))
            .when(discoveryService).projectCurrentFact(any());
        AgentProjectionState denied = projector.project(key);
        assertEquals(AgentProjectionStatus.ACCESS_UNCERTAIN, denied.getStatus());
        assertEquals(Integer.valueOf(NacosException.NO_RIGHT), denied.getErrorCode());
        
        doThrow(new IllegalStateException("broken"))
            .when(discoveryService).projectCurrentFact(any());
        AgentProjectionState broken = projector.project(key);
        assertEquals(AgentProjectionStatus.TRANSIENT_FAILURE, broken.getStatus());
        assertEquals(Integer.valueOf(NacosException.SERVER_ERROR), broken.getErrorCode());
        assertEquals("broken", broken.getErrorMessage());
        assertTrue(broken.requiresRetry());
        assertTrue(broken.getPhysicalDependencies().isEmpty());
    }
    
    @Test
    void testDeclaredInterfaceCreatesProspectiveRuntimeDependency() throws NacosException {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot(
            AgentProjectionTestFixtures.AGENT_NAME);
        snapshot.setCallInterfaces(Arrays.asList(
            AgentProjectionTestFixtures.callInterface("a2a", EndpointSource.DECLARED)));
        when(discoveryService.projectCurrentFact(any())).thenReturn(snapshot);
        assertEquals(Collections.singleton(
            AgentProjectionTestFixtures.service(AgentProjectionTestFixtures.AGENT_NAME,
                "a2a")),
            projector.project(key).getPhysicalDependencies());
    }
    
    @Test
    void testPublicConstructorUsesSystemClock() throws NacosException {
        when(discoveryService.projectCurrentFact(any())).thenReturn(
            AgentProjectionTestFixtures.snapshot(AgentProjectionTestFixtures.AGENT_NAME));
        DefaultAgentProjectionProjector systemClockProjector =
            new DefaultAgentProjectionProjector(discoveryService);
        
        assertTrue(systemClockProjector.project(key).getComputedAt() > 0L);
    }
    
    @Test
    void testSpringSelectsProductionConstructor() {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("agentDiscoveryApplicationService",
                discoveryService);
            context.register(DefaultAgentProjectionProjector.class);
            context.refresh();
            
            assertTrue(context.getBean(DefaultAgentProjectionProjector.class) != null);
        }
    }
    
    private void assertCheckedFailure(int errorCode, AgentProjectionStatus expected,
        boolean retry) throws NacosException {
        doThrow(new NacosException(errorCode, "failure-" + errorCode))
            .when(discoveryService).projectCurrentFact(any());
        AgentProjectionState result = projector.project(key);
        assertEquals(expected, result.getStatus());
        assertEquals(Integer.valueOf(errorCode), result.getErrorCode());
        assertEquals("failure-" + errorCode, result.getErrorMessage());
        assertEquals(retry, result.requiresRetry());
        assertFalse(result.isAvailable());
    }
}
