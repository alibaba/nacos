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
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    }

    @Test
    public void testIsSelectable() {
        NamingSelectorWrapper selectorWrapper = getSelectorWrapper();
        NamingContext context = mock(NamingContext.class);
        assertTrue(selectorWrapper.isSelectable(context));
        assertFalse(selectorWrapper.isSelectable(null));
    }

    @Test
    public void testCallable1() {
        NamingSelectorWrapper selectorWrapper = getSelectorWrapper();
        // NamingEvent
        List<Instance> instances = new ArrayList<>();
        NamingEvent namingEvent = new NamingEvent("serviceName", instances);

        assertTrue(selectorWrapper.isCallable(namingEvent));
        // repeat
        assertFalse(selectorWrapper.isCallable(namingEvent));

        Instance ins1 = InstanceBuilder.newBuilder()
                .setIp("1.1.1.1").setPort(8080).build();
        // added
        instances.add(ins1);
        assertTrue(selectorWrapper.isCallable(namingEvent));
        // repeat
        assertFalse(selectorWrapper.isCallable(namingEvent));

        // modified
        ins1.getMetadata().put("a", "b");
        assertTrue(selectorWrapper.isCallable(namingEvent));

        // removed
        instances.remove(ins1);
        assertTrue(selectorWrapper.isCallable(namingEvent));

        // added
        Instance ins2 = InstanceBuilder.newBuilder()
                .setIp("1.1.1.1").setPort(9090).build();
        instances.add(ins2);
        assertTrue(selectorWrapper.isCallable(namingEvent));
    }

    @Test
    public void testCallable2() {
        NamingSelectorWrapper selectorWrapper = getSelectorWrapper();
        // NamingChangeEvent
        InstancesDiff instancesDiff = new InstancesDiff(null, Collections.singletonList(new Instance()), null);
        NamingChangeEvent changeEvent = new NamingChangeEvent("serviceName", Collections.emptyList(), instancesDiff);
        assertTrue(selectorWrapper.isCallable(changeEvent));
        changeEvent.getRemovedInstances().clear();
        assertFalse(selectorWrapper.isCallable(changeEvent));
    }

    @Test
    public void testNotifyListener() {
        EventListener listener = mock(EventListener.class);
        NamingSelectorWrapper selectorWrapper = new NamingSelectorWrapper(new DefaultNamingSelector(Instance::isHealthy), listener);
        List<Instance> instances = Collections.singletonList(new Instance());
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getCurrentInstances()).thenReturn(instances);
        when(namingContext.getAddedInstances()).thenReturn(instances);
        selectorWrapper.notifyListener(namingContext);
        verify(listener).onEvent(argThat(Objects::nonNull));
    }

    private NamingSelectorWrapper getSelectorWrapper() {
        return new NamingSelectorWrapper(
                new DefaultNamingSelector(Instance::isHealthy),
                event -> {
                }
        );
    }
}
