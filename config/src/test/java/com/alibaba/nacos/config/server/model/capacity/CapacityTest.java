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

package com.alibaba.nacos.config.server.model.capacity;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class CapacityTest {
    
    @Test
    void testTimestampDefensiveCopyAndNull() {
        Capacity capacity = new Capacity();
        Timestamp createTime = new Timestamp(1L);
        Timestamp modifiedTime = new Timestamp(2L);
        
        capacity.setGmtCreate(createTime);
        capacity.setGmtModified(modifiedTime);
        
        assertEquals(createTime, capacity.getGmtCreate());
        assertEquals(modifiedTime, capacity.getGmtModified());
        assertNotSame(createTime, capacity.getGmtCreate());
        assertNotSame(modifiedTime, capacity.getGmtModified());
        
        capacity.setGmtCreate(null);
        capacity.setGmtModified(null);
        
        assertNull(capacity.getGmtCreate());
        assertNull(capacity.getGmtModified());
    }
}
