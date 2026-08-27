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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProjectionRegistryTest {
    
    @Test
    void testReferenceCountingLogicalIndexAndRelease() {
        AgentProjectionRegistry registry = new AgentProjectionRegistry();
        AgentProjectionKey beta = AgentProjectionTestFixtures.key("beta");
        AgentProjectionKey alpha = AgentProjectionTestFixtures.key("alpha");
        
        assertTrue(registry.retain(beta));
        assertFalse(registry.retain(beta));
        assertTrue(registry.retain(alpha));
        assertEquals(2, registry.getReferenceCount(beta));
        assertEquals(Collections.singleton(beta), registry.findByAgent("public", "beta"));
        assertEquals(2, registry.size());
        assertEquals(2, registry.activeKeys().size());
        assertTrue(registry.activeKeys().get(0).compareTo(registry.activeKeys().get(1)) < 0);
        assertFalse(registry.release(beta));
        assertEquals(1, registry.getReferenceCount(beta));
        assertTrue(registry.release(beta));
        assertFalse(registry.release(beta));
        assertFalse(registry.isActive(beta));
        assertTrue(registry.findByAgent("public", "beta").isEmpty());
        assertEquals(0, registry.getReferenceCount(beta));
    }
    
    @Test
    void testSuccessfulProjectionRebuildsPhysicalDependencies() {
        AgentProjectionRegistry registry = new AgentProjectionRegistry();
        AgentProjectionKey key = AgentProjectionTestFixtures.key("demo");
        Service a2a = AgentProjectionTestFixtures.service("demo", "a2a");
        Service mcp = AgentProjectionTestFixtures.service("demo", "mcp");
        registry.retain(key);
        
        AgentProjectionUpdate initial = registry.apply(key,
            AgentProjectionTestFixtures.available("fingerprint-a", 1L, a2a),
            EnumSet.of(AgentProjectionChangeReason.INITIAL)).get();
        assertNull(initial.getPrevious());
        assertTrue(initial.isPublicObservationChanged());
        assertEquals(Collections.singleton(a2a), initial.getCurrent().getPhysicalDependencies());
        assertEquals(Collections.singleton(key), registry.findByService(a2a));
        
        AgentProjectionUpdate replacement = registry.apply(key,
            AgentProjectionTestFixtures.available("fingerprint-b", 2L, mcp),
            EnumSet.of(AgentProjectionChangeReason.DEFINITION,
                AgentProjectionChangeReason.RUNTIME))
            .get();
        assertEquals("fingerprint-a", replacement.getPrevious().getFingerprint());
        assertEquals(EnumSet.of(AgentProjectionChangeReason.DEFINITION,
            AgentProjectionChangeReason.RUNTIME), replacement.getReasons());
        assertTrue(registry.findByService(a2a).isEmpty());
        assertEquals(Collections.singleton(key), registry.findByService(mcp));
        assertTrue(registry.release(key));
        assertTrue(registry.findByService(mcp).isEmpty());
    }
    
    @Test
    void testUncertainOutcomesPreserveDependenciesAndNotFoundClearsThem() {
        AgentProjectionRegistry registry = new AgentProjectionRegistry();
        AgentProjectionKey key = AgentProjectionTestFixtures.key("demo");
        Service a2a = AgentProjectionTestFixtures.service("demo", "a2a");
        registry.retain(key);
        registry.apply(key, AgentProjectionTestFixtures.available("available", 1L, a2a),
            Collections.singleton(AgentProjectionChangeReason.INITIAL));
        
        for (AgentProjectionStatus status : new AgentProjectionStatus[] {
            AgentProjectionStatus.ACCESS_UNCERTAIN, AgentProjectionStatus.CONFLICT,
            AgentProjectionStatus.TRANSIENT_FAILURE}) {
            AgentProjectionState failure = AgentProjectionState.failure(status,
                NacosException.SERVER_ERROR, status.name(), 2L);
            AgentProjectionUpdate update = registry.apply(key, failure,
                Collections.singleton(AgentProjectionChangeReason.RETRY)).get();
            assertEquals(Collections.singleton(a2a),
                update.getCurrent().getPhysicalDependencies());
            assertEquals(Collections.singleton(key), registry.findByService(a2a));
        }
        AgentProjectionState notFound = AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, NacosException.NOT_FOUND, "missing", 3L);
        AgentProjectionUpdate update = registry.apply(key, notFound,
            Collections.<AgentProjectionChangeReason>emptySet()).get();
        assertTrue(update.getCurrent().getPhysicalDependencies().isEmpty());
        assertTrue(registry.findByService(a2a).isEmpty());
        assertFalse(update.getCurrent().isAvailable());
        assertFalse(update.getCurrent().requiresRetry());
        assertEquals("missing", update.getCurrent().getErrorMessage());
        assertEquals(Integer.valueOf(NacosException.NOT_FOUND),
            update.getCurrent().getErrorCode());
        assertEquals(3L, update.getCurrent().getComputedAt());
        assertTrue(update.getReasons().isEmpty());
        assertThrows(UnsupportedOperationException.class,
            () -> update.getCurrent().getPhysicalDependencies().add(a2a));
    }
    
    @Test
    void testReleasedProjectionRejectsLateComputation() {
        AgentProjectionRegistry registry = new AgentProjectionRegistry();
        AgentProjectionKey key = AgentProjectionTestFixtures.key("released");
        registry.retain(key);
        registry.release(key);
        Optional<AgentProjectionUpdate> update = registry.apply(key,
            AgentProjectionTestFixtures.available("late", 1L),
            Collections.singleton(AgentProjectionChangeReason.INITIAL));
        assertFalse(update.isPresent());
        assertFalse(registry.getState(key).isPresent());
        assertTrue(registry.findByAgent("public", "missing").isEmpty());
        assertTrue(registry.findByService(AgentProjectionTestFixtures.service("missing", "a2a"))
            .isEmpty());
    }
    
    @Test
    void testRemovingAbsentDefensiveIndexEntryIsIdempotent() {
        AgentProjectionRegistry registry = new AgentProjectionRegistry();
        Map<String, Set<AgentProjectionKey>> index =
            new HashMap<String, Set<AgentProjectionKey>>();
        
        ReflectionTestUtils.invokeMethod(registry, "removeIndex", index, "missing",
            AgentProjectionTestFixtures.key("missing"));
        
        assertTrue(index.isEmpty());
    }
}
