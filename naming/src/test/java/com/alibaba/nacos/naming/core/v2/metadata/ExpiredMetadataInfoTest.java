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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiredMetadataInfoTest {
    
    @Test
    void testExpiredServiceMetadataInfo() {
        Service service = Service.newService("namespace", "group", "service");
        ExpiredMetadataInfo info = ExpiredMetadataInfo.newExpiredServiceMetadata(service);
        
        assertEquals(service, info.getService());
        assertNull(info.getMetadataId());
        assertTrue(info.getCreateTime() > 0);
        assertTrue(info.toString().contains("service="));
    }
    
    @Test
    void testEqualsAndHashCode() {
        Service service = Service.newService("namespace", "group", "service");
        ExpiredMetadataInfo instanceInfo =
            ExpiredMetadataInfo.newExpiredInstanceMetadata(service, "1.1.1.1:8848");
        ExpiredMetadataInfo same =
            ExpiredMetadataInfo.newExpiredInstanceMetadata(service, "1.1.1.1:8848");
        ExpiredMetadataInfo different =
            ExpiredMetadataInfo.newExpiredInstanceMetadata(service, "1.1.1.2:8848");
        
        assertEquals("1.1.1.1:8848", instanceInfo.getMetadataId());
        assertEquals(instanceInfo, instanceInfo);
        assertEquals(instanceInfo, same);
        assertEquals(instanceInfo.hashCode(), same.hashCode());
        assertNotEquals(instanceInfo, different);
        assertNotEquals(instanceInfo, new Object());
        assertFalse(instanceInfo.equals(null));
    }
}
