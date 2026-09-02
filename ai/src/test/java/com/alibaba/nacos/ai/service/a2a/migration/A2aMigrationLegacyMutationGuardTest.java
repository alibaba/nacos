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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class A2aMigrationLegacyMutationGuardTest {
    
    @Test
    void shouldRejectOnlyQuiescingDefinitionMutations() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        A2aMigrationLegacyMutationGuard guard =
            new A2aMigrationLegacyMutationGuard(stateService);
        when(stateService.resolveConfiguredAuthoritative()).thenReturn(null,
            A2aMigrationState.SYNCING, A2aMigrationState.CANONICAL,
            A2aMigrationState.QUIESCING);
        assertDoesNotThrow(guard::checkMutable);
        assertDoesNotThrow(guard::checkMutable);
        assertDoesNotThrow(guard::checkMutable);
        NacosApiException error = assertThrows(NacosApiException.class, guard::checkMutable);
        assertEquals(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(),
            error.getDetailErrCode());
    }
}
