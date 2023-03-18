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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscribeServiceRequestTest extends BasedNamingRequestTest {
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        SubscribeServiceRequest request = new SubscribeServiceRequest(NAMESPACE, GROUP, SERVICE, "", true);
        String json = mapper.writeValueAsString(request);
        checkSerializeBasedInfo(json);
        assertTrue(json.contains("\"clusters\":\"\""));
        assertTrue(json.contains("\"subscribe\":true"));
    }
    
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{},\"namespace\":\"namespace\",\"serviceName\":\"service\",\"groupName\":\"group\","
                + "\"subscribe\":false,\"clusters\":\"aa,bb\",\"module\":\"naming\"}";
        SubscribeServiceRequest actual = mapper.readValue(json, SubscribeServiceRequest.class);
        checkNamingRequestBasedInfo(actual);
        assertEquals("aa,bb", actual.getClusters());
        assertFalse(actual.isSubscribe());
    }
}