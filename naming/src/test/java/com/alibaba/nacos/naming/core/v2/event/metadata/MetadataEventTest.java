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

package com.alibaba.nacos.naming.core.v2.event.metadata;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataEventTest {
    
    @Test
    void testServiceMetadataEventReturnsFields() {
        Service service = Service.newService("namespace", "group", "service");
        
        MetadataEvent.ServiceMetadataEvent actual =
            new MetadataEvent.ServiceMetadataEvent(service, true);
        
        assertSame(service, actual.getService());
        assertTrue(actual.isExpired());
    }
    
    @Test
    void testInstanceMetadataEventReturnsFields() {
        Service service = Service.newService("namespace", "group", "service");
        
        MetadataEvent.InstanceMetadataEvent actual =
            new MetadataEvent.InstanceMetadataEvent(service, "metadataId", false);
        
        assertSame(service, actual.getService());
        assertEquals("metadataId", actual.getMetadataId());
        assertFalse(actual.isExpired());
    }
    
    @Test
    void testServiceInfoChangeEventRenewsServiceUpdateTime() {
        Service service = Service.newService("namespace", "group", "service");
        long before = service.getLastUpdatedTime();
        
        InfoChangeEvent.ServiceInfoChangeEvent actual =
            new InfoChangeEvent.ServiceInfoChangeEvent(service);
        
        assertSame(service, actual.getService());
        assertTrue(service.getLastUpdatedTime() >= before);
    }
    
    @Test
    void testInstanceInfoChangeEventReturnsFields() {
        Service service = Service.newService("namespace", "group", "service");
        Instance instance = new Instance();
        
        InfoChangeEvent.InstanceInfoChangeEvent actual =
            new InfoChangeEvent.InstanceInfoChangeEvent(service, instance);
        
        assertSame(service, actual.getService());
        assertSame(instance, actual.getInstance());
    }
}
