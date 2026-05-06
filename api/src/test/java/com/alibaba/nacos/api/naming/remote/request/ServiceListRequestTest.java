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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static com.alibaba.nacos.api.common.Constants.Naming.NAMING_MODULE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceListRequestTest extends BasedNamingRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        ServiceListRequest request = new ServiceListRequest(NAMESPACE, GROUP, 1, 10);
        request.setSelector("label");
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"groupName\":\"" + GROUP + "\""));
        assertTrue(json.contains("\"namespace\":\"" + NAMESPACE + "\""));
        assertTrue(json.contains("\"module\":\"" + NAMING_MODULE + "\""));
        assertTrue(json.contains("\"selector\":\"label\""));
        assertTrue(json.contains("\"pageNo\":1"));
        assertTrue(json.contains("\"pageSize\":10"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{},\"namespace\":\"namespace\",\"serviceName\":\"\",\"groupName\":\"group\","
                + "\"pageNo\":1,\"pageSize\":10,\"selector\":\"label\",\"module\":\"naming\"}";
        ServiceListRequest actual = mapper.readValue(json, ServiceListRequest.class);
        assertEquals(GROUP, actual.getGroupName());
        assertEquals(NAMESPACE, actual.getNamespace());
        assertEquals(NAMING_MODULE, actual.getModule());
        assertEquals(1, actual.getPageNo());
        assertEquals(10, actual.getPageSize());
        assertEquals("label", actual.getSelector());
    }
}