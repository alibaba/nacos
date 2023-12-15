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

package com.alibaba.nacos.common.model;

import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RestResultTest {
    
    @Test
    public void testSerialization() {
        RestResult<String> result = new RestResult<>(200, "test", "content");
        String json = JacksonUtils.toJson(result);
        assertTrue(json.contains("\"code\":200"));
        assertTrue(json.contains("\"message\":\"test\""));
        assertTrue(json.contains("\"data\":\"content\""));
    }
    
    @Test
    public void testDeserialization() {
        String json = "{\"code\":200,\"message\":\"test\",\"data\":\"content\"}";
        RestResult restResult = JacksonUtils.toObj(json, RestResult.class);
        assertEquals(200, restResult.getCode());
        assertEquals("test", restResult.getMessage());
        assertEquals("content", restResult.getData());
        assertEquals("RestResult{code=200, message='test', data=content}", restResult.toString());
    }
}