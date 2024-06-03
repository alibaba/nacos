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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NamingSelectorWrapperTest {
    
    @Test
    public void testEquals() {
        EventListener listener = mock(EventListener.class);
        NamingSelector selector1 = mock(NamingSelector.class);
        NamingSelector selector2 = mock(NamingSelector.class);
        NamingSelectorWrapper sw1 = new NamingSelectorWrapper(selector1, listener);
        NamingSelectorWrapper sw2 = new NamingSelectorWrapper(selector2, listener);
        NamingSelectorWrapper sw3 = new NamingSelectorWrapper(selector1, listener);
        
        assertNotEquals(sw1.hashCode(), sw2.hashCode());
        assertEquals(sw1.hashCode(), sw3.hashCode());
        assertNotEquals(sw1, sw2);
        assertEquals(sw1, sw3);
        
        Set<NamingSelectorWrapper> set = new HashSet<>();
        set.add(sw1);
        assertFalse(set.contains(sw2));
        assertTrue(set.contains(sw3));
        assertTrue(set.add(sw2));
        assertFalse(set.add(sw3));
        assertTrue(set.remove(sw3));
        
        assertEquals(sw1, new NamingSelectorWrapper("a", "b", "c", selector1, listener));
    }
    
    @Test
    public void testSelectable() {
        NamingSelectorWrapper selectorWrapper = new NamingSelectorWrapper(null, null);
        assertFalse(selectorWrapper.isSelectable(null));
        InstancesChangeEvent event1 = new InstancesChangeEvent(null, null, null, null, null, null);
        assertFalse(selectorWrapper.isSelectable(event1));
        InstancesChangeEvent event2 = new InstancesChangeEvent(null, null, null, null, null, new InstancesDiff());
        assertFalse(selectorWrapper.isSelectable(event2));
        InstancesChangeEvent event3 = new InstancesChangeEvent(null, null, null, null, Collections.emptyList(), null);
        assertFalse(selectorWrapper.isSelectable(event3));
        InstancesChangeEvent event4 = new InstancesChangeEvent(null, null, null, null, Collections.emptyList(),
                new InstancesDiff());
        assertTrue(selectorWrapper.isSelectable(event4));
    }
    
    @Test
    public void testCallable() {
        NamingSelectorWrapper selectorWrapper = new NamingSelectorWrapper(null, null);
        InstancesDiff instancesDiff = new InstancesDiff(null, Collections.singletonList(new Instance()), null);
        NamingChangeEvent changeEvent = new NamingChangeEvent("serviceName", Collections.emptyList(), instancesDiff);
        assertTrue(selectorWrapper.isCallable(changeEvent));
        changeEvent.getRemovedInstances().clear();
        assertFalse(selectorWrapper.isCallable(changeEvent));
    }
    
    @Test
    public void testNotifyListener() {
        EventListener listener = mock(EventListener.class);
        NamingSelectorWrapper selectorWrapper = new NamingSelectorWrapper(
                new DefaultNamingSelector(Instance::isHealthy), listener);
        InstancesDiff diff = new InstancesDiff(null, Collections.singletonList(new Instance()), null);
        InstancesChangeEvent event = new InstancesChangeEvent(null, "serviceName", "groupName", "clusters",
                Collections.emptyList(), diff);
        selectorWrapper.notifyListener(event);
        verify(listener).onEvent(argThat(Objects::nonNull));
    }
}
