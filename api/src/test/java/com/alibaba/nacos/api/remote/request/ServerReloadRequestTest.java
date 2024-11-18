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

package com.alibaba.nacos.api.remote.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerReloadRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        ServerReloadRequest request = new ServerReloadRequest();
        request.setReloadCount(10);
        request.setReloadServer("1.1.1.1");
        request.setRequestId("1");
        String json = mapper.writeValueAsString(request);
        System.out.println(json);
        assertNotNull(json);
        assertTrue(json.contains("\"reloadCount\":10"));
        assertTrue(json.contains("\"reloadServer\":\"1.1.1.1\""));
        assertTrue(json.contains("\"module\":\"internal\""));
        assertTrue(json.contains("\"requestId\":\"1\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"reloadCount\":10,\"reloadServer\":\"1.1.1.1\","
                + "\"module\":\"internal\"}";
        ServerReloadRequest result = mapper.readValue(json, ServerReloadRequest.class);
        assertNotNull(result);
        assertEquals(10, result.getReloadCount());
        assertEquals("1.1.1.1", result.getReloadServer());
        assertEquals("1", result.getRequestId());
        assertEquals("internal", result.getModule());
    }
}