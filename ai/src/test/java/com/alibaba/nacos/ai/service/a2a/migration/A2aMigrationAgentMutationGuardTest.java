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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class A2aMigrationAgentMutationGuardTest {
    
    @Test
    void shouldIgnoreNullAndUnrelatedResourceSources() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        A2aMigrationAgentMutationGuard guard =
            new A2aMigrationAgentMutationGuard(stateService);
        AiResource ordinary = new AiResource();
        ordinary.setFrom("local");
        assertDoesNotThrow(() -> guard.checkMutable(null));
        assertDoesNotThrow(() -> guard.checkMutable(ordinary));
        verifyNoInteractions(stateService);
    }
    
    @Test
    void shouldAllowMigrationOwnedAgentAfterCanonicalCutover() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL);
        A2aMigrationAgentMutationGuard guard =
            new A2aMigrationAgentMutationGuard(stateService);
        assertDoesNotThrow(() -> guard.checkMutable(migrated()));
    }
    
    @Test
    void shouldBlockMigrationOwnedAgentBeforeCanonicalCutover() {
        for (A2aMigrationState state : new A2aMigrationState[] {null,
            A2aMigrationState.SYNCING, A2aMigrationState.QUIESCING}) {
            A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
            when(stateService.resolveConfigured()).thenReturn(state);
            A2aMigrationAgentMutationGuard guard =
                new A2aMigrationAgentMutationGuard(stateService);
            NacosApiException error = assertThrows(NacosApiException.class,
                () -> guard.checkMutable(migrated()));
            assertEquals(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(),
                error.getDetailErrCode());
        }
    }
    
    private AiResource migrated() {
        AiResource result = new AiResource();
        result.setName("agent");
        result.setFrom(A2aMigrationTargetStore.MIGRATION_RESOURCE_SOURCE);
        return result;
    }
}
