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

import org.junit.Assert;
import org.junit.Test;

public class ConnectResetRequestTest extends BasicRequestTest {
    
    @Test
    public void testSerialize() throws Exception {
        ConnectResetRequest request = new ConnectResetRequest();
        request.setServerIp("127.0.0.1");
        request.setServerPort("8888");
        request.setRequestId("1");
        request.setConnectionId("11111_127.0.0.1_8888");
        String json = mapper.writeValueAsString(request);
        Assert.assertNotNull(json);
        Assert.assertTrue(json.contains("\"serverIp\":\"127.0.0.1\""));
        Assert.assertTrue(json.contains("\"serverPort\":\"8888\""));
        Assert.assertTrue(json.contains("\"module\":\"internal\""));
        Assert.assertTrue(json.contains("\"requestId\":\"1\""));
        Assert.assertTrue(json.contains("\"connectionId\":\"11111_127.0.0.1_8888\""));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"serverIp\":\"127.0.0.1\",\"serverPort\":\"8888\","
                + "\"module\":\"internal\",\"connectionId\":\"11111_127.0.0.1_8888\"}";
        ConnectResetRequest result = mapper.readValue(json, ConnectResetRequest.class);
        Assert.assertNotNull(result);
        Assert.assertEquals("127.0.0.1", result.getServerIp());
        Assert.assertEquals("8888", result.getServerPort());
        Assert.assertEquals("11111_127.0.0.1_8888", result.getConnectionId());
    }
}