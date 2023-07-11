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

package com.alibaba.nacos.api.naming.remote.request;

import com.alibaba.nacos.api.common.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServiceQueryRequestTest extends BasedNamingRequestTest {
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        ServiceQueryRequest request = new ServiceQueryRequest(NAMESPACE, SERVICE, GROUP);
        request.setCluster(Constants.DEFAULT_CLUSTER_NAME);
        String json = mapper.writeValueAsString(request);
        checkSerializeBasedInfo(json);
        assertTrue(json.contains("\"cluster\":\"" + Constants.DEFAULT_CLUSTER_NAME + "\""));
        assertTrue(json.contains("\"healthyOnly\":false"));
        assertTrue(json.contains("\"udpPort\":0"));
    }
    
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{},\"namespace\":\"namespace\",\"serviceName\":\"service\",\"groupName\":\"group\","
                + "\"cluster\":\"DEFAULT\",\"healthyOnly\":true,\"udpPort\":0,\"module\":\"naming\"}";
        ServiceQueryRequest actual = mapper.readValue(json, ServiceQueryRequest.class);
        checkNamingRequestBasedInfo(actual);
        assertEquals(Constants.DEFAULT_CLUSTER_NAME, actual.getCluster());
        assertTrue(actual.isHealthyOnly());
        assertEquals(0, actual.getUdpPort());
    }
}