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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.ai.auth.AiCapabilityHttpResourceParser;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiCapabilityClientControllerTest {
    
    @Test
    void shouldDeclareOnlyFiveHttpFeaturesAndNoRuntimeState() throws Exception {
        Map<String, Object> data = new AiCapabilityClientController().getCapabilities().getData();
        assertEquals(Map.of("schemaVersion", 1, "capabilities", Map.of("radV1", true,
            "mcp", true, "skill", true, "prompt", true, "agentSpec", true)), data);
        assertThrows(UnsupportedOperationException.class, () -> data.put("radReady", true));
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<?, ?>) data.get("capabilities")).clear());
    }
    
    @Test
    void shouldRequireStandardClientIdentityWithExplicitResourcelessParser() throws Exception {
        Secured secured = AiCapabilityClientController.class
            .getMethod("getCapabilities")
            .getAnnotation(Secured.class);
        assertEquals(ApiType.OPEN_API, secured.apiType());
        assertEquals(SignType.AI, secured.signType());
        assertEquals(ActionTypes.READ, secured.action());
        assertEquals("", secured.resource());
        assertEquals(AiCapabilityHttpResourceParser.class, secured.parser());
        assertArrayEquals(new String[] {Constants.Tag.ONLY_IDENTITY}, secured.tags());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void shouldLetMvcRejectUnsupportedMethods(String method) throws Exception {
        MockMvcBuilders.standaloneSetup(new AiCapabilityClientController()).build()
            .perform(request(HttpMethod.valueOf(method), "/v3/client/ai/capabilities"))
            .andExpect(status().isMethodNotAllowed());
    }
}
