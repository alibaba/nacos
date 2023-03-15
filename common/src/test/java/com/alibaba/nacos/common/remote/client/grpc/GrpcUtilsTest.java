/*
 *
 *  * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.common.remote.PayloadRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class GrpcUtilsTest {
    
    private ServiceQueryRequest request;
    
    private ClientConfigMetricResponse response;
    
    @Before
    public void setup() {
        PayloadRegistry.init();
        this.request = createRequest();
        this.response = createResponse();
    }
    
    private ClientConfigMetricResponse createResponse() {
        ClientConfigMetricResponse clientConfigMetricResponse = new ClientConfigMetricResponse();
        clientConfigMetricResponse.setMetrics(new HashMap<String, Object>() {
            {
                put("m1", "v1");
                put("m2", "v2");
                put("m3", "v3");
            }
        });
        return clientConfigMetricResponse;
    }
    
    private ServiceQueryRequest createRequest() {
        ServiceQueryRequest request = new ServiceQueryRequest();
        request.setCluster("cluster");
        request.setHealthyOnly(true);
        request.setNamespace("namespace");
        
        //headers
        request.putHeader("h1", "v1");
        request.putHeader("h2", "v2");
        request.putHeader("h3", "v3");
        return request;
    }
    
    @Test
    public void testConvertRequest() {
        Payload convert = GrpcUtils.convert(request);
        assertEquals(request.getClass().getSimpleName(), convert.getMetadata().getType());
        assertEquals("v1", convert.getMetadata().getHeadersMap().get("h1"));
        assertEquals("v2", convert.getMetadata().getHeadersMap().get("h2"));
        assertEquals("v3", convert.getMetadata().getHeadersMap().get("h3"));
    }
    
    @Test
    public void testConvertResponse() {
        Payload convert = GrpcUtils.convert(response);
        assertEquals(response.getClass().getSimpleName(), convert.getMetadata().getType());
    }
    
    @Test
    public void parse() {
        Payload requestPayload = GrpcUtils.convert(request);
    
        ServiceQueryRequest request = (ServiceQueryRequest) GrpcUtils.parse(requestPayload);
        assertEquals(this.request.getHeaders(), request.getHeaders());
        assertEquals(this.request.getCluster(), request.getCluster());
        assertEquals(this.request.isHealthyOnly(), request.isHealthyOnly());
        assertEquals(this.request.getNamespace(), request.getNamespace());
    
        Payload responsePayload = GrpcUtils.convert(response);
        ClientConfigMetricResponse response = (ClientConfigMetricResponse) GrpcUtils.parse(responsePayload);
        assertEquals(this.response.getMetrics(), response.getMetrics());
        
    }
}
