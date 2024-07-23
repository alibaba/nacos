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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ServerStateController unit test.
 *
 * @ClassName: ServerStateControllerTest
 * @Author: ChenHao26
 * @Date: 2022/8/13 10:54
 * @Description: TODO
 */
@ExtendWith(MockitoExtension.class)
class ServerStateControllerTest {
    
    private static final String CONSOLE_URL = "/v1/console/server/state";
    
    @InjectMocks
    private ServerStateController serverStateController;
    
    private MockMvc mockmvc;
    
    private ConfigurableEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        mockmvc = MockMvcBuilders.standaloneSetup(serverStateController).build();
    }
    
    @Test
    void serverState() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(CONSOLE_URL);
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        ObjectNode responseContent = JacksonUtils.toObj(response.getContentAsByteArray(), ObjectNode.class);
        assertEquals(EnvUtil.STANDALONE_MODE_CLUSTER, responseContent.get(Constants.STARTUP_MODE_STATE).asText());
        assertEquals("null", responseContent.get(Constants.FUNCTION_MODE_STATE).asText());
        assertEquals(VersionUtils.version, responseContent.get(Constants.NACOS_VERSION).asText());
    }
}
