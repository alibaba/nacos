/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.security;

import org.junit.Test;

import static org.junit.Assert.*;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;

public class SecurityProxyTest {

    /**
     * Just test for replace fastjson with jackson.
     *
     */
    @Test
    public void testLogin() {
        String example = "{\"accessToken\":\"ttttttttttttttttt\",\"tokenTtl\":1000}";
        JsonNode obj = JacksonUtils.toObj(example);
        if (obj.has(Constants.ACCESS_TOKEN)) {
            if (obj.has(Constants.ACCESS_TOKEN)) {
                assertEquals("ttttttttttttttttt", obj.get(Constants.ACCESS_TOKEN).asText());
                assertEquals(1000, obj.get(Constants.TOKEN_TTL).asInt());
            }
        }
    }
}
