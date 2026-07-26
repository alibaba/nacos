/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.NamingSubscriberServiceV2Impl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingSubscriberServiceLocalImplTest {
    
    private static final String NAMESPACE = "namespace";
    
    private static final String GROUPED_SERVICE_NAME = "group@@service";
    
    private static final Service SERVICE = Service.newService(NAMESPACE, "group", "service");
    
    @Mock
    private NamingSubscriberServiceV2Impl namingSubscriberServiceV2;
    
    @Mock
    private Subscriber subscriber;
    
    private NamingSubscriberServiceLocalImpl serviceLocal;
    
    @BeforeEach
    void setUp() {
        serviceLocal = new NamingSubscriberServiceLocalImpl(namingSubscriberServiceV2);
    }
    
    @Test
    void testGetSubscribersByNamespaceAndServiceName() {
        Set<Subscriber> source = sourceSubscribers();
        when(namingSubscriberServiceV2.getSubscribers(NAMESPACE, GROUPED_SERVICE_NAME))
            .thenReturn(source);
        
        Collection<Subscriber> actual =
            serviceLocal.getSubscribers(NAMESPACE, GROUPED_SERVICE_NAME);
        
        assertCopiedSubscribers(source, actual);
    }
    
    @Test
    void testGetSubscribersByService() {
        Set<Subscriber> source = sourceSubscribers();
        when(namingSubscriberServiceV2.getSubscribers(SERVICE)).thenReturn(source);
        
        Collection<Subscriber> actual = serviceLocal.getSubscribers(SERVICE);
        
        assertCopiedSubscribers(source, actual);
    }
    
    @Test
    void testGetFuzzySubscribersByNamespaceAndServiceName() {
        Set<Subscriber> source = sourceSubscribers();
        when(namingSubscriberServiceV2.getFuzzySubscribers(NAMESPACE, GROUPED_SERVICE_NAME))
            .thenReturn(source);
        
        Collection<Subscriber> actual =
            serviceLocal.getFuzzySubscribers(NAMESPACE, GROUPED_SERVICE_NAME);
        
        assertCopiedSubscribers(source, actual);
    }
    
    @Test
    void testGetFuzzySubscribersByService() {
        Set<Subscriber> source = sourceSubscribers();
        when(namingSubscriberServiceV2.getFuzzySubscribers(SERVICE)).thenReturn(source);
        
        Collection<Subscriber> actual = serviceLocal.getFuzzySubscribers(SERVICE);
        
        assertCopiedSubscribers(source, actual);
    }
    
    private Set<Subscriber> sourceSubscribers() {
        return new HashSet<>(Collections.singleton(subscriber));
    }
    
    private void assertCopiedSubscribers(Set<Subscriber> source,
        Collection<Subscriber> actual) {
        assertEquals(source, actual);
        assertNotSame(source, actual);
        actual.clear();
        assertFalse(source.isEmpty());
    }
}
