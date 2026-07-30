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

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultPluginStateSynchronizationContextTest {
    
    @Test
    void applyStateChangePersistsBeforeApplying() {
        PluginStatePersistenceService persistence =
            mock(PluginStatePersistenceService.class);
        PluginStateApplier applier = mock(PluginStateApplier.class);
        DefaultPluginStateSynchronizationContext context =
            new DefaultPluginStateSynchronizationContext(persistence, () -> applier);
        
        context.applyStateChange("trace:otel", false);
        
        InOrder order = inOrder(applier, persistence);
        order.verify(applier).validateStateChange("trace:otel", false);
        order.verify(persistence).saveState("trace:otel", false);
        order.verify(applier).applyStateChange("trace:otel", false);
    }
    
    @Test
    void applyConfigChangeDelegatesToCoreApplier() {
        PluginStatePersistenceService persistence =
            mock(PluginStatePersistenceService.class);
        PluginStateApplier applier = mock(PluginStateApplier.class);
        DefaultPluginStateSynchronizationContext context =
            new DefaultPluginStateSynchronizationContext(persistence, () -> applier);
        Map<String, String> config = Collections.singletonMap("endpoint", "value");
        
        context.applyConfigChange("trace:otel", config);
        
        verify(applier).applyConfigChange("trace:otel", config);
    }
    
    @Test
    void missingCoreApplierFailsExplicitly() {
        DefaultPluginStateSynchronizationContext context =
            new DefaultPluginStateSynchronizationContext(
                mock(PluginStatePersistenceService.class), () -> null);
        
        assertThrows(IllegalStateException.class,
            () -> context.applyStateChange("trace:otel", true));
        assertThrows(IllegalStateException.class,
            () -> context.applyConfigChange("trace:otel", Collections.emptyMap()));
    }
}
