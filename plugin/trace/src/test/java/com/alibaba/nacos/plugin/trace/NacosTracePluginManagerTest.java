/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.trace;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import com.alibaba.nacos.common.trace.event.TraceEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class NacosTracePluginManagerTest {
    
    @BeforeAll
    static void setUp() {
        NacosTracePluginManager.getInstance();
    }
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void testGetAllTraceSubscribers() {
        assertFalse(NacosTracePluginManager.getInstance().getAllTraceSubscribers().isEmpty());
        assertContainsTestPlugin();
    }
    
    @Test
    void testGetAllTraceSubscribersWhenCheckerPresent() throws Exception {
        Map<String, NacosTraceSubscriber> subscribers = getSubscribers();
        subscribers.put("enabled", new TestTraceSubscriber("enabled"));
        subscribers.put("disabled", new TestTraceSubscriber("disabled"));
        PluginStateCheckerHolder.setInstance(
            (pluginType, pluginName) -> !"disabled".equals(pluginName));
        
        Collection<NacosTraceSubscriber> result =
            NacosTracePluginManager.getInstance().getAllTraceSubscribers();
        
        assertTrue(result.stream().anyMatch(each -> "enabled".equals(each.getName())));
        assertFalse(result.stream().anyMatch(each -> "disabled".equals(each.getName())));
        assertThrows(UnsupportedOperationException.class,
            () -> NacosTracePluginManager.getInstance().getAllPlugins().clear());
    }
    
    private void assertContainsTestPlugin() {
        for (NacosTraceSubscriber each : NacosTracePluginManager.getInstance()
            .getAllTraceSubscribers()) {
            if ("trace-plugin-mock".equals(each.getName())) {
                return;
            }
        }
        fail("No found plugin named 'trace-plugin-mock'");
    }
    
    private Map<String, NacosTraceSubscriber> getSubscribers() throws Exception {
        Field field = NacosTracePluginManager.class.getDeclaredField("traceSubscribers");
        field.setAccessible(true);
        return (Map<String, NacosTraceSubscriber>) field.get(NacosTracePluginManager.getInstance());
    }
    
    private static class TestTraceSubscriber implements NacosTraceSubscriber {
        
        private final String name;
        
        private TestTraceSubscriber(String name) {
            this.name = name;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public void onEvent(TraceEvent event) {
        }
        
        @Override
        public List<Class<? extends TraceEvent>> subscribeTypes() {
            return Collections.emptyList();
        }
    }
}
