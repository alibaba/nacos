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

package com.alibaba.nacos.api.naming.listener;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NamingEventTest {
    
    private MockNamingEventListener eventListener;
    
    @Before
    public void setUp() throws Exception {
        eventListener = new MockNamingEventListener();
    }
    
    @Test
    public void testNamingEventWithSimpleConstructor() {
        NamingEvent event = new NamingEvent("serviceName", Collections.EMPTY_LIST);
        assertEquals("serviceName", event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getClusters());
        assertTrue(event.getInstances().isEmpty());
        eventListener.onEvent(event);
        assertNull(event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getClusters());
        assertNull(event.getInstances());
    }
    
    @Test
    public void testNamingEventWithFullConstructor() {
        NamingEvent event = new NamingEvent("serviceName", "group", "clusters", Collections.EMPTY_LIST);
        assertEquals("serviceName", event.getServiceName());
        assertEquals("group", event.getGroupName());
        assertEquals("clusters", event.getClusters());
        assertTrue(event.getInstances().isEmpty());
        eventListener.onEvent(event);
        assertNull(event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getClusters());
        assertNull(event.getInstances());
    }
    
    private static class MockNamingEventListener extends AbstractEventListener {
        
        @Override
        public void onEvent(Event event) {
            assertNull(getExecutor());
            NamingEvent namingEvent = (NamingEvent) event;
            namingEvent.setServiceName(null);
            namingEvent.setGroupName(null);
            namingEvent.setClusters(null);
            namingEvent.setInstances(null);
        }
    }
}