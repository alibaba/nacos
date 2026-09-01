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
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProjectionStateTest {
    
    @Test
    void testPublicObservationEqualityUsesStatusFingerprintAndErrorCode() {
        AgentProjectionState available = AgentProjectionState.available("fp", null, 1L);
        AgentProjectionState same = AgentProjectionState.available("fp",
            Collections.emptySet(), 2L);
        AgentProjectionState differentFingerprint = AgentProjectionState.available("other",
            Collections.emptySet(), 3L);
        AgentProjectionState notFound = AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, NacosException.NOT_FOUND, "missing", 4L);
        AgentProjectionState sameNotFound = AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, NacosException.NOT_FOUND, "different message", 5L);
        AgentProjectionState differentError = AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, NacosException.RESOURCE_NOT_FOUND, "missing", 6L);
        
        assertTrue(available.samePublicObservation(same));
        assertFalse(available.samePublicObservation(null));
        assertFalse(available.samePublicObservation(notFound));
        assertFalse(available.samePublicObservation(differentFingerprint));
        assertTrue(notFound.samePublicObservation(sameNotFound));
        assertFalse(notFound.samePublicObservation(differentError));
        assertFalse(new AgentProjectionUpdate(AgentProjectionTestFixtures.key("demo"), available,
            same, Collections.emptySet()).isPublicObservationChanged());
    }
}
