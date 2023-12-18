/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.trace.event.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NamingTraceEventTest {
    
    protected static final long TIME = System.currentTimeMillis();
    
    protected static final String NAMESPACE_ID = "ns";
    
    protected static final String GROUP_NAME = "testG";
    
    protected static final String SERVICE_NAME = "testS";
    
    protected static final String CLUSTER_NAME = "test_cluster";
    
    protected static final String IP = "127.0.0.1";
    
    protected static final int PORT = 8848;
    
    protected static final String CLIENT_IP = "1.1.1.1";
    
    protected void assertBasicInfo(NamingTraceEvent event) {
        assertEquals(TIME, event.getEventTime());
        assertEquals(NAMESPACE_ID, event.getNamespace());
        assertEquals(GROUP_NAME, event.getGroup());
        assertEquals(SERVICE_NAME, event.getName());
        assertTrue(event.isPluginEvent());
    }
}
