/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DumpRequestTest {
    
    @Test
    void testCreateAndGetters() {
        DumpRequest req = DumpRequest.create("d", "g", "t", 12345L, "1.1.1.1");
        assertEquals("d", req.getDataId());
        assertEquals("g", req.getGroup());
        assertEquals("t", req.getTenant());
        assertEquals(12345L, req.getLastModifiedTs());
        assertEquals("1.1.1.1", req.getSourceIp());
    }
    
    @Test
    void testSetters() {
        DumpRequest req = new DumpRequest();
        req.setDataId("d");
        req.setGroup("g");
        req.setTenant("t");
        req.setLastModifiedTs(100L);
        req.setSourceIp("2.2.2.2");
        req.setGrayName("gray1");
        assertEquals("d", req.getDataId());
        assertEquals("g", req.getGroup());
        assertEquals("t", req.getTenant());
        assertEquals(100L, req.getLastModifiedTs());
        assertEquals("2.2.2.2", req.getSourceIp());
        assertEquals("gray1", req.getGrayName());
    }
}
