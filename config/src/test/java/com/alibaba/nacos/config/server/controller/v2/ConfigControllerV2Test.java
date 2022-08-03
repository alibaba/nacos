/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v2;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.model.vo.ConfigVo;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class ConfigControllerV2Test {
    
    @InjectMocks
    ConfigControllerV2 configControllerV2;
    
    private MockMvc mockmvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private ConfigServletInner inner;
    
    @Mock
    private PersistService persistService;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(configControllerV2, "persistService", persistService);
        ReflectionTestUtils.setField(configControllerV2, "inner", inner);
        mockmvc = MockMvcBuilders.standaloneSetup(configControllerV2).build();
    }
    
    @Test
    public void testGetConfig() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V2_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("tag", "");
    
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int statusCode = response.getStatus();
        Assert.assertEquals(200, statusCode);
    }
    
    @Test
    public void testPublishConfig() throws Exception {
        
        ConfigVo configVo = new ConfigVo("test", "test", "", "test", "", "", "", "", "", "", "", "", "");
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CONFIG_CONTROLLER_V2_PATH)
                .contentType(MediaType.APPLICATION_JSON).content(JacksonUtils.toJson(configVo));
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("true", actualValue);
    }
    
    @Test
    public void testDeleteConfig() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_V2_PATH)
                .param("dataId", "test")
                .param("group", "test")
                .param("tenant", "")
                .param("tag", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("true", actualValue);
    }
}
