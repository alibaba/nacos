/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.remote.response;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReleaseAgentCardResponseTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        ReleaseAgentCardResponse response = new ReleaseAgentCardResponse();
        response.setRequestId("1");
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        // ReleaseAgentCardResponse has no additional fields, just test basic serialization
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"1\",\"success\":true}";
        ReleaseAgentCardResponse result = mapper.readValue(json, ReleaseAgentCardResponse.class);
        assertNotNull(result);
        // ReleaseAgentCardResponse has no additional fields, just test basic deserialization
    }
}