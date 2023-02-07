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

package com.alibaba.nacos.api.naming.pojo;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServiceTest {
    
    @Test
    public void testSetAndGet() {
        Service service = new Service();
        assertNull(service.getName());
        assertNull(service.getAppName());
        assertNull(service.getGroupName());
        assertEquals(0.0f, service.getProtectThreshold(), 0.1);
        assertTrue(service.getMetadata().isEmpty());
        service.setName("service");
        service.setGroupName("group");
        service.setAppName("app");
        service.setProtectThreshold(1.0f);
        HashMap<String, String> metadata = new HashMap<>();
        service.setMetadata(metadata);
        service.addMetadata("a", "b");
        assertEquals("service", service.getName());
        assertEquals("app", service.getAppName());
        assertEquals("group", service.getGroupName());
        assertEquals(1.0f, service.getProtectThreshold(), 0.1);
        assertEquals(1, service.getMetadata().size());
        assertEquals("b", service.getMetadata().get("a"));
    }
    
    @Test
    public void testToString() {
        Service service = new Service("service");
        service.setGroupName("group");
        service.setAppName("app");
        service.setProtectThreshold(1.0f);
        service.setMetadata(Collections.singletonMap("a", "b"));
        assertEquals("Service{name='service', protectThreshold=1.0, appName='app', groupName='group', metadata={a=b}}", service.toString());
    }
}