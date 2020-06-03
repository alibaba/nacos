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
package com.alibaba.nacos.config.server.controller;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;

/**
 * Created by qingliang on 2017/8/14.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class HealthControllerUnitTest {

    @InjectMocks
    HealthController healthController;

    @Mock
    DataSourceService dataSourceService;

    private MockMvc mockmvc;

    @Before
    public void setUp() throws Exception {
        mockmvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    public void testGetHealth() throws Exception {

        Mockito.when(dataSourceService.getHealth()).thenReturn("UP");
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.HEALTH_CONTROLLER_PATH);
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("UP", actualValue);

    }
}
