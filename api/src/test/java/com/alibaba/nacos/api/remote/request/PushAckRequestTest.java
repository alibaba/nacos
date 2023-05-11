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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.Assert;
import org.junit.Test;

public class PushAckRequestTest extends BasicRequestTest {
    
    @Test
    public void testSerialize() throws Exception {
        PushAckRequest request = PushAckRequest.build("1", false);
        request.setException(new NacosRuntimeException(500, "test"));
        String json = mapper.writeValueAsString(request);
        Assert.assertNotNull(json);
        Assert.assertTrue(json.contains("\"success\":false"));
        Assert.assertTrue(json.contains("\"exception\":{"));
        Assert.assertTrue(json.contains("\"module\":\"internal\""));
        Assert.assertTrue(json.contains("\"requestId\":\"1\""));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"success\":false,"
                + "\"exception\":{\"stackTrace\":[],\"errCode\":500,\"message\":\"errCode: 500, errMsg: test \","
                + "\"localizedMessage\":\"errCode: 500, errMsg: test \",\"suppressed\":[]},\"module\":\"internal\"}";
        PushAckRequest result = mapper.readValue(json, PushAckRequest.class);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("1", result.getRequestId());
    }
}