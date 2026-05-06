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

import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class NacosTracePluginManagerTest {
    
    @BeforeAll
    static void setUp() {
        NacosTracePluginManager.getInstance();
    }
    
    @Test
    void testGetAllTraceSubscribers() {
        assertFalse(NacosTracePluginManager.getInstance().getAllTraceSubscribers().isEmpty());
        assertContainsTestPlugin();
    }
    
    private void assertContainsTestPlugin() {
        for (NacosTraceSubscriber each : NacosTracePluginManager.getInstance().getAllTraceSubscribers()) {
            if ("trace-plugin-mock".equals(each.getName())) {
                return;
            }
        }
        fail("No found plugin named 'trace-plugin-mock'");
    }
}
