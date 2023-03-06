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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * ServerStateController unit test.
 * @ClassName: ServerStateControllerTest
 * @Author: ChenHao26
 * @Date: 2022/8/13 10:54
 * @Description: TODO
 */
@RunWith(MockitoJUnitRunner.class)
public class ServerStateControllerTest {
    
    @InjectMocks
    private ServerStateController serverStateController;
    
    private MockMvc mockmvc;
    
    private static final String CONSOLE_URL = "/v1/console/server/state";
    
    @Before
    public void setUp() {
        mockmvc = MockMvcBuilders.standaloneSetup(serverStateController).build();
    }
    
    @Test
    public void serverState() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(CONSOLE_URL);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
    }
}
