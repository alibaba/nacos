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

class ConnectResetRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        ConnectResetRequest request = new ConnectResetRequest();
        request.setServerIp("127.0.0.1");
        request.setServerPort("8888");
        request.setRequestId("1");
        request.setConnectionId("11111_127.0.0.1_8888");
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"serverIp\":\"127.0.0.1\""));
        assertTrue(json.contains("\"serverPort\":\"8888\""));
        assertTrue(json.contains("\"module\":\"internal\""));
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"connectionId\":\"11111_127.0.0.1_8888\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"serverIp\":\"127.0.0.1\",\"serverPort\":\"8888\","
                + "\"module\":\"internal\",\"connectionId\":\"11111_127.0.0.1_8888\"}";
        ConnectResetRequest result = mapper.readValue(json, ConnectResetRequest.class);
        assertNotNull(result);
        assertEquals("127.0.0.1", result.getServerIp());
        assertEquals("8888", result.getServerPort());
        assertEquals("11111_127.0.0.1_8888", result.getConnectionId());
    }
}