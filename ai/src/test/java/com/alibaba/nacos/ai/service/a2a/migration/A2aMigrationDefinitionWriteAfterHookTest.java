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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aMigrationDefinitionWriteAfterHookTest {
    
    @Test
    void shouldSubmitAcceptedAndDroppedHintsWithoutChangingCaller() {
        A2aMigrationDefinitionHintReconciler reconciler =
            mock(A2aMigrationDefinitionHintReconciler.class);
        A2aMigrationDefinitionWriteAfterHook hook =
            new A2aMigrationDefinitionWriteAfterHook(reconciler);
        when(reconciler.submit("ns", "agent")).thenReturn(true, false);
        
        assertDoesNotThrow(() -> hook.afterSuccessfulMutation("ns", "agent"));
        assertDoesNotThrow(() -> hook.afterSuccessfulMutation("ns", "agent"));
        verify(reconciler, org.mockito.Mockito.times(2)).submit("ns", "agent");
    }
    
    @Test
    void shouldIsolateUnexpectedHintFailure() {
        A2aMigrationDefinitionHintReconciler reconciler =
            mock(A2aMigrationDefinitionHintReconciler.class);
        A2aMigrationDefinitionWriteAfterHook hook =
            new A2aMigrationDefinitionWriteAfterHook(reconciler);
        when(reconciler.submit("ns", null)).thenThrow(new IllegalStateException("full"));
        
        assertDoesNotThrow(() -> hook.afterSuccessfulMutation("ns", null));
    }
}
