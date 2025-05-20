/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.selector;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.selector.NamingListenerInvoker;
import com.alibaba.nacos.client.naming.selector.NamingSelectorWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AbstractSelectorWrapperTest {
    
    private MockSelectorWrapper selectorWrapper;
    
    @Mock
    NamingSelector namingSelector;
    
    @Mock
    EventListener eventListener;
    
    @BeforeEach
    void setUp() {
        selectorWrapper = new MockSelectorWrapper(namingSelector, eventListener, true, true);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void notifyListenerWithSelectableFalse() {
        selectorWrapper = new MockSelectorWrapper(namingSelector, eventListener, false, true);
        selectorWrapper.notifyListener(new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener, never()).onEvent(any());
    }
    
    @Test
    void notifyListenerWithCallableFalse() {
        selectorWrapper = new MockSelectorWrapper(namingSelector, eventListener, true, false);
        selectorWrapper.notifyListener(new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener, never()).onEvent(any());
    }
    
    @Test
    void notifyListener() {
        selectorWrapper.notifyListener(new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener).onEvent(any());
    }
    
    @Test
    void notifyIfListenerIfNotNotifiedWithSelectableFalse() {
        selectorWrapper = new MockSelectorWrapper(namingSelector, eventListener, false, true);
        selectorWrapper.notifyIfListenerIfNotNotified(
                new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener, never()).onEvent(any());
    }
    
    @Test
    void notifyIfListenerIfNotNotified() {
        selectorWrapper.notifyIfListenerIfNotNotified(
                new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener).onEvent(any());
    }
    
    @Test
    void notifyIfListenerIfNotNotifiedTwice() {
        selectorWrapper.notifyIfListenerIfNotNotified(
                new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener).onEvent(any());
        reset(eventListener);
        selectorWrapper.notifyIfListenerIfNotNotified(
                new InstancesChangeEvent("test", "test", "test", "test", null, null));
        verify(eventListener, never()).onEvent(any());
    }
    
    @Test
    void testGet() {
        assertEquals(namingSelector, selectorWrapper.getSelector());
        assertNotEquals(eventListener, selectorWrapper.getListener());
        assertEquals(new NamingListenerInvoker(eventListener), selectorWrapper.getListener());
    }
    
    @Test
    void testEquals() {
        assertEquals(selectorWrapper, selectorWrapper);
        assertNotEquals(null, selectorWrapper);
        assertNotEquals(new NamingSelectorWrapper(namingSelector, eventListener), selectorWrapper);
        MockSelectorWrapper newSelectorWrapper = new MockSelectorWrapper(namingSelector, eventListener, true, true);
        assertEquals(newSelectorWrapper, selectorWrapper);
    }
    
    private class MockSelectorWrapper
            extends AbstractSelectorWrapper<NamingSelector, NamingEvent, InstancesChangeEvent> {
        
        private final boolean selectable;
        
        private final boolean callable;
        
        private MockSelectorWrapper(NamingSelector selector, EventListener listener, boolean selectable,
                boolean callable) {
            super(selector, new NamingListenerInvoker(listener));
            this.selectable = selectable;
            this.callable = callable;
        }
        
        @Override
        protected boolean isSelectable(InstancesChangeEvent event) {
            return selectable;
        }
        
        @Override
        protected boolean isCallable(NamingEvent event) {
            return callable;
        }
        
        @Override
        protected NamingEvent buildListenerEvent(InstancesChangeEvent event) {
            return new NamingEvent(event.getServiceName(), event.getGroupName(), event.getClusters(),
                    Collections.emptyList());
        }
    }
}