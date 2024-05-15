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

package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientConfigMetricResponseTest extends BasedConfigResponseTest {
    
    ClientConfigMetricResponse clientConfigMetricResponse;
    
    Map<String, Object> metric = new HashMap<>(16);
    
    @BeforeEach
    void before() {
        metric.put("m1", "v1");
        clientConfigMetricResponse = new ClientConfigMetricResponse();
        clientConfigMetricResponse.setMetrics(metric);
        clientConfigMetricResponse.putMetric("m2", "v2");
        requestId = injectResponseUuId(clientConfigMetricResponse);
    }
    
    @Override
    @Test
    public void testSerializeSuccessResponse() throws JsonProcessingException {
        String json = mapper.writeValueAsString(clientConfigMetricResponse);
        assertTrue(json.contains("\"success\":" + Boolean.TRUE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.SUCCESS.getCode()));
    }
    
    @Override
    public void testSerializeFailResponse() throws JsonProcessingException {
    
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"6ef9237b-24f3-448a-87fc-713f18ee06a1\","
                + "\"metrics\":{\"m1\":\"v1\",\"m2\":\"v2\"},\"success\":true}";
        ClientConfigMetricResponse actual = mapper.readValue(json, ClientConfigMetricResponse.class);
        assertTrue(actual.isSuccess());
        assertEquals(actual.getResultCode(), ResponseCode.SUCCESS.getCode());
        assertEquals(actual.getMetrics(), metric);
    }
}
