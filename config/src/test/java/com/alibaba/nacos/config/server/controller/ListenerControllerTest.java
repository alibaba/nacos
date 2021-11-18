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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ListenerControllerTest {
    
    @InjectMocks
    ListenerController listenerController;
    
    private MockMvc mockmvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private ConfigSubService configSubService;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(listenerController, "configSubService", configSubService);
        mockmvc = MockMvcBuilders.standaloneSetup(listenerController).build();
    }
    
    @Test
    public void testGetAllSubClientConfigByIp() throws Exception {
        
        SampleResult sampleResult = new SampleResult();
        Map<String, String> map = new HashMap<>();
        map.put("test", "test");
        sampleResult.setLisentersGroupkeyStatus(map);
        when(configSubService.getCollectSampleResultByIp("localhost", 1)).thenReturn(sampleResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.LISTENER_CONTROLLER_PATH)
                .param("ip", "localhost").param("all", "true")
                .param("tenant", "test").param("sampleTime", "1");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        GroupkeyListenserStatus groupkeyListenserStatus = JacksonUtils.toObj(actualValue, GroupkeyListenserStatus.class);
        Map<String, String> resultMap = groupkeyListenserStatus.getLisentersGroupkeyStatus();
        
        Assert.assertEquals(map.get("test"), resultMap.get("test"));
        
    }
    
}