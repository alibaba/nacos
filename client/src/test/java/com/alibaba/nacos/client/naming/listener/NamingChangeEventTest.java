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

package com.alibaba.nacos.client.naming.listener;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class NamingChangeEventTest {
    
    private MockNamingEventListener eventListener;
    
    private InstancesDiff instancesDiff;
    
    @Before
    public void setUp() throws Exception {
        eventListener = new MockNamingEventListener();
        instancesDiff = new InstancesDiff();
        instancesDiff.setAddedInstances(Arrays.asList(new Instance(), new Instance(), new Instance()));
        instancesDiff.setRemovedInstances(Arrays.asList(new Instance(), new Instance()));
        instancesDiff.setModifiedInstances(Arrays.asList(new Instance()));
    }
    
    @Test
    public void testNamingChangeEventWithSimpleConstructor() {
        NamingChangeEvent event = new NamingChangeEvent("serviceName", Collections.EMPTY_LIST, instancesDiff);
        assertEquals("serviceName", event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getClusters());
        assertTrue(event.getInstances().isEmpty());
        assertTrue(event.isAdded());
        assertEquals(3, event.getAddedInstances().size());
        assertTrue(event.isRemoved());
        assertEquals(2, event.getRemovedInstances().size());
        assertTrue(event.isModified());
        assertEquals(1, event.getModifiedInstances().size());
        eventListener.onEvent(event);
        assertNull(event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getClusters());
        assertNull(event.getInstances());
        assertFalse(event.isAdded());
        assertEquals(0, event.getAddedInstances().size());
        assertFalse(event.isRemoved());
        assertEquals(0, event.getRemovedInstances().size());
        assertFalse(event.isModified());
        assertEquals(0, event.getRemovedInstances().size());
    }
    
    @Test
    public void testNamingChangeEventWithFullConstructor() {
        NamingChangeEvent event = new NamingChangeEvent("serviceName", "group", "clusters", Collections.EMPTY_LIST,
                instancesDiff);
        assertEquals("serviceName", event.getServiceName());
        assertEquals("group", event.getGroupName());
        assertEquals("clusters", event.getClusters());
        assertTrue(event.getInstances().isEmpty());
        assertTrue(event.isAdded());
        assertEquals(3, event.getAddedInstances().size());
        assertTrue(event.isRemoved());
        assertEquals(2, event.getRemovedInstances().size());
        assertTrue(event.isModified());
        assertEquals(1, event.getModifiedInstances().size());
        eventListener.onEvent(event);
        assertNull(event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getClusters());
        assertNull(event.getInstances());
        assertFalse(event.isAdded());
        assertEquals(0, event.getAddedInstances().size());
        assertFalse(event.isRemoved());
        assertEquals(0, event.getRemovedInstances().size());
        assertFalse(event.isModified());
        assertEquals(0, event.getRemovedInstances().size());
    }
    
    @Test
    public void testGetChanges() {
        NamingChangeEvent event = new NamingChangeEvent("serviceName", Collections.EMPTY_LIST, instancesDiff);
        assertTrue(event.isAdded());
        assertEquals(3, event.getAddedInstances().size());
        event.getAddedInstances().clear();
        assertFalse(event.isAdded());
        assertEquals(0, event.getAddedInstances().size());
        
        assertTrue(event.isRemoved());
        assertEquals(2, event.getRemovedInstances().size());
        event.getRemovedInstances().clear();
        assertFalse(event.isRemoved());
        assertEquals(0, event.getRemovedInstances().size());
        
        assertTrue(event.isModified());
        assertEquals(1, event.getModifiedInstances().size());
        event.getModifiedInstances().clear();
        assertFalse(event.isModified());
        assertEquals(0, event.getRemovedInstances().size());
    }
    
    private static class MockNamingEventListener extends AbstractNamingChangeListener {
        
        @Override
        public void onChange(NamingChangeEvent event) {
            assertNull(getExecutor());
            event.setServiceName(null);
            event.setGroupName(null);
            event.setClusters(null);
            event.setInstances(null);
            event.getAddedInstances().clear();
            event.getRemovedInstances().clear();
            event.getModifiedInstances().clear();
        }
    }
}
