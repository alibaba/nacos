/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientTest {
    
    private static final String INVALID_URL = "nacos://invalid";
    
    @BeforeAll
    static void beforeAll() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "false");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testRequestReturnErrorForUnsupportedUrl() {
        Map<String, String> params = new HashMap<>();
        params.put("name", "nacos");
        
        assertServerError(HttpClient.httpGet(INVALID_URL, Arrays.asList("h1", "v1"), params));
        assertServerError(HttpClient.request(INVALID_URL, Arrays.asList("h4", "v4"), params,
            "body", 1, 1, "UTF-8", "PATCH"));
    }
    
    @Test
    void testTranslateParameterMapUsesFirstValue() {
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("name", new String[] {"nacos", "ignored"});
        
        Map<String, String> result = HttpClient.translateParameterMap(parameterMap);
        
        assertEquals("nacos", result.get("name"));
    }
    
    private void assertServerError(RestResult<String> result) {
        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("nacos"));
    }
    
}
