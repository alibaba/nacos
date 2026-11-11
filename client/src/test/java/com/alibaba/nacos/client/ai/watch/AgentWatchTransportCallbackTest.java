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

package com.alibaba.nacos.client.ai.watch;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWatchTransportCallbackTest {
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void legacyCallbackPreservesInvalidationAcknowledgement(boolean accepted) {
        AgentWatchTransportCallback callback =
            mock(AgentWatchTransportCallback.class, CALLS_REAL_METHODS);
        when(callback.invalidate("fingerprint", false)).thenReturn(accepted);
        when(callback.invalidate(null, true)).thenReturn(accepted);
        assertEquals(accepted, callback.invalidateFromNotification("fingerprint", false));
        assertEquals(accepted, callback.invalidateFromNotification(null, true));
        verify(callback).invalidate("fingerprint", false);
        verify(callback).invalidate(null, true);
    }
}
