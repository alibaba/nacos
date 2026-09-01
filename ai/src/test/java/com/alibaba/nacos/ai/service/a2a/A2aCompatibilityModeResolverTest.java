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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationState;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationStateService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aCompatibilityModeResolverTest {
    
    @Test
    void shouldDefaultToCanonical() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(stateService, () -> null).resolve());
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(stateService, () -> "  ").resolve());
        try (MockedStatic<EnvUtil> envUtil = mockStatic(EnvUtil.class)) {
            envUtil.when(() -> EnvUtil.getProperty(A2aCompatibilityModeResolver.MODE_PROPERTY,
                A2aCompatibilityMode.CANONICAL.name())).thenReturn("CANONICAL");
            assertEquals(A2aCompatibilityMode.CANONICAL,
                new A2aCompatibilityModeResolver(stateService).resolve());
        }
        verify(stateService, times(3)).resolve(A2aCompatibilityMode.CANONICAL);
    }
    
    @Test
    void shouldParseExplicitModesCaseInsensitively() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(stateService, () -> " canonical ").resolve());
        assertEquals(A2aCompatibilityMode.LEGACY,
            new A2aCompatibilityModeResolver(stateService, () -> "legacy").resolve());
        assertThrows(IllegalArgumentException.class,
            () -> new A2aCompatibilityModeResolver(stateService, () -> "unknown").resolve());
    }
    
    @Test
    void shouldKeepAutoOnLegacyAuthorityUntilTerminalMarker() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        when(stateService.resolve(A2aCompatibilityMode.AUTO)).thenReturn(
            A2aMigrationState.SYNCING, A2aMigrationState.QUIESCING,
            A2aMigrationState.CANONICAL);
        A2aCompatibilityModeResolver resolver =
            new A2aCompatibilityModeResolver(stateService, () -> "AUTO");
        assertEquals(A2aCompatibilityMode.LEGACY, resolver.resolve());
        assertEquals(A2aCompatibilityMode.LEGACY, resolver.resolve());
        assertEquals(A2aCompatibilityMode.CANONICAL, resolver.resolve());
    }
    
    @Test
    void terminalMarkerShouldOverrideExplicitLegacyMode() {
        A2aMigrationStateService stateService = mock(A2aMigrationStateService.class);
        when(stateService.resolve(A2aCompatibilityMode.LEGACY))
            .thenReturn(A2aMigrationState.CANONICAL);
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(stateService, () -> "legacy").resolve());
    }
}
