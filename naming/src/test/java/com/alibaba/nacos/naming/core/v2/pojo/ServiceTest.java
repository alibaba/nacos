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

package com.alibaba.nacos.naming.core.v2.pojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTest {
    
    @Test
    void testAccessorsAndRevision() {
        Service service = Service.newService("namespace", "group", "name", false);
        
        assertEquals("namespace", service.getNamespace());
        assertEquals("group", service.getGroup());
        assertEquals("name", service.getName());
        assertFalse(service.isEphemeral());
        assertEquals(0L, service.getRevision());
        service.incrementRevision();
        assertEquals(1L, service.getRevision());
    }
    
    @Test
    void testGroupedNames() {
        Service service = Service.newService("namespace", "group", "name");
        
        assertEquals("group@@name", service.getGroupedServiceName());
        assertEquals("namespace@@group@@name", service.getNameSpaceGroupedServiceName());
    }
    
    @Test
    void testEquals() {
        Service service = Service.newService("namespace", "group", "name");
        
        assertTrue(service.equals(Service.newService("namespace", "group", "name", false)));
        assertFalse(service.equals("service"));
        assertFalse(service.equals(Service.newService("namespace", "group", "other")));
    }
    
    @Test
    void testHashCodeAndToString() {
        Service service = Service.newService("namespace", "group", "name");
        
        assertEquals(Service.newService("namespace", "group", "name").hashCode(),
            service.hashCode());
        assertTrue(service.toString().contains("namespace='namespace'"));
    }
}
