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

import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.listener.AbstractNamingChangeListener;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class NamingListenerInvokerTest {
    
    @Test
    public void testEventListener() {
        EventListener listener = mock(EventListener.class);
        NamingListenerInvoker listenerInvoker = new NamingListenerInvoker(listener);
        NamingEvent event = new NamingEvent("serviceName", Collections.emptyList());
        listenerInvoker.invoke(event);
        verify(listener).onEvent(event);
    }
    
    @Test
    public void testAbstractEventListener() {
        AbstractEventListener listener = mock(AbstractEventListener.class);
        NamingListenerInvoker listenerInvoker = new NamingListenerInvoker(listener);
        NamingEvent event = new NamingEvent("serviceName", Collections.emptyList());
        listenerInvoker.invoke(event);
        verify(listener).getExecutor();
    }
    
    @Test
    public void testAbstractNamingChaneEventListener() {
        AbstractNamingChangeListener listener = spy(AbstractNamingChangeListener.class);
        NamingListenerInvoker listenerInvoker = new NamingListenerInvoker(listener);
        NamingChangeEvent event = new NamingChangeEvent("serviceName", Collections.emptyList(), new InstancesDiff());
        listenerInvoker.invoke(event);
        verify(listener).onChange(event);
    }
    
    @Test
    public void testEquals() {
        EventListener listener1 = mock(EventListener.class);
        EventListener listener2 = mock(EventListener.class);
        NamingListenerInvoker invoker1 = new NamingListenerInvoker(listener1);
        NamingListenerInvoker invoker2 = new NamingListenerInvoker(listener1);
        NamingListenerInvoker invoker3 = new NamingListenerInvoker(listener2);
        assertEquals(invoker1.hashCode(), invoker2.hashCode());
        assertEquals(invoker1, invoker2);
        assertNotEquals(invoker1.hashCode(), invoker3.hashCode());
        assertNotEquals(invoker1, invoker3);
    }
}
