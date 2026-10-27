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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Index notifications are best effort and never reverse a committed Hub CAS. */
class HubSkillIndexNotifierTest {
    
    @Test
    void successfulMutationSchedulesCanonicalHubResourceKey() {
        AiResourceIndexMaintenanceService maintenance =
            mock(AiResourceIndexMaintenanceService.class);
        HubSkillIndexNotifier notifier = new HubSkillIndexNotifier();
        notifier.setMaintenanceService(maintenance);
        notifier.entryChanged("find-skills-41");
        verify(maintenance).schedule(AiConstants.Hub.NAMESPACE_ID, "skill", "find-skills-41");
    }
    
    @Test
    void schedulingFailureDoesNotMaskSuccessfulMutation() {
        AiResourceIndexMaintenanceService maintenance =
            mock(AiResourceIndexMaintenanceService.class);
        when(maintenance.schedule(AiConstants.Hub.NAMESPACE_ID, "skill", "find-skills-41"))
            .thenThrow(new IllegalStateException("index offline"));
        HubSkillIndexNotifier notifier = new HubSkillIndexNotifier();
        notifier.setMaintenanceService(maintenance);
        assertDoesNotThrow(() -> notifier.entryChanged("find-skills-41"));
    }
    
    @Test
    void noIndexRuntimeIsSupported() {
        HubSkillIndexNotifier notifier = new HubSkillIndexNotifier();
        assertDoesNotThrow(() -> notifier.entryChanged("find-skills-41"));
    }
}
