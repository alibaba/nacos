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

import java.util.Collections;
import java.util.HashMap;

public class ConnectionSetupRequestTest extends BasicRequestTest {
    
    @Test
    public void testSerialize() throws Exception {
        ConnectionSetupRequest request = new ConnectionSetupRequest();
        request.setClientVersion("2.2.2");
        request.setAbilityTable(new HashMap<>());
        request.setTenant("testNamespaceId");
        request.setLabels(Collections.singletonMap("labelKey", "labelValue"));
        request.setRequestId("1");
        String json = mapper.writeValueAsString(request);
        Assert.assertNotNull(json);
        Assert.assertTrue(json.contains("\"clientVersion\":\"2.2.2\""));
        Assert.assertTrue(json.contains("\"tenant\":\"testNamespaceId\""));
        Assert.assertTrue(json.contains("\"labels\":{\"labelKey\":\"labelValue\"}"));
        Assert.assertTrue(json.contains("\"abilityTable\":{"));
        Assert.assertTrue(json.contains("\"module\":\"internal\""));
        Assert.assertTrue(json.contains("\"requestId\":\"1\""));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"clientVersion\":\"2.2.2\",\"abilities\":{\"remoteAbility\":"
                + "{\"supportRemoteConnection\":false},\"configAbility\":{\"supportRemoteMetrics\":false},"
                + "\"namingAbility\":{\"supportDeltaPush\":false,\"supportRemoteMetric\":false}},\"tenant\":\"testNamespaceId\","
                + "\"labels\":{\"labelKey\":\"labelValue\"},\"module\":\"internal\"}";
        ConnectionSetupRequest result = mapper.readValue(json, ConnectionSetupRequest.class);
        Assert.assertNotNull(result);
        Assert.assertEquals("2.2.2", result.getClientVersion());
        Assert.assertEquals("testNamespaceId", result.getTenant());
        Assert.assertEquals(1, result.getLabels().size());
        Assert.assertEquals("labelValue", result.getLabels().get("labelKey"));
        Assert.assertEquals("1", result.getRequestId());
    }
}