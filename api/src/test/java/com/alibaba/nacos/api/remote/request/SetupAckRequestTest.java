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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class SetupAckRequestTest extends BasicRequestTest {
    
    @Test
    public void testSerialize() throws Exception {
        SetupAckRequest request = new SetupAckRequest(
                Collections.singletonMap(AbilityKey.SERVER_TEST_1.getName(), Boolean.TRUE));
        request.setRequestId("1");
        String json = mapper.writeValueAsString(request);
        System.out.println(json);
        Assert.assertNotNull(json);
        Assert.assertTrue(json.contains("\"abilityTable\":{\"test_1\":true}"));
        Assert.assertTrue(json.contains("\"module\":\"internal\""));
        Assert.assertTrue(json.contains("\"requestId\":\"1\""));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        String json =
                "{\"headers\":{},\"requestId\":\"1\",\"abilityTable\":{\"test_1\":true}," + "\"module\":\"internal\"}";
        SetupAckRequest result = mapper.readValue(json, SetupAckRequest.class);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.getAbilityTable().get("test_1"));
        Assert.assertEquals("1", result.getRequestId());
        Assert.assertEquals("internal", result.getModule());
    }
}