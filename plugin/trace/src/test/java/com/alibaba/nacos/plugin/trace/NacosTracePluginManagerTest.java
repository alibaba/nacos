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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class NacosTracePluginManagerTest {
    
    @BeforeClass
    public static void setUp() {
        NacosTracePluginManager.getInstance();
    }
    
    @Test
    public void testGetAllTraceSubscribers() {
        assertFalse(NacosTracePluginManager.getInstance().getAllTraceSubscribers().isEmpty());
        assertContainsTestPlugin();
    }
    
    private void assertContainsTestPlugin() {
        for (NacosTraceSubscriber each : NacosTracePluginManager.getInstance().getAllTraceSubscribers()) {
            if ("trace-plugin-mock".equals(each.getName())) {
                return;
            }
        }
        Assert.fail("No found plugin named 'trace-plugin-mock'");
    }
}
