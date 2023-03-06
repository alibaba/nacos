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
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.ArrayList;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ConfigOpsControllerTest {
    
    @InjectMocks
    ConfigOpsController configOpsController;
    
    private MockMvc mockMvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    DumpService dumpService;
    
    @Before
    public void init() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(configOpsController, "dumpService", dumpService);
        mockMvc = MockMvcBuilders.standaloneSetup(configOpsController).build();
    }
    
    @Test
    public void testUpdateLocalCacheFromStore() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post(Constants.OPS_CONTROLLER_PATH + "/localCache");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testSetLogLevel() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.OPS_CONTROLLER_PATH + "/log")
                .param("logName", "test").param("logLevel", "test");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testDerbyOps() throws Exception {
        MockedStatic<PropertyUtil> propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        
        propertyUtilMockedStatic.when(PropertyUtil::isEmbeddedStorage).thenReturn(true);
        DynamicDataSource dataSource = Mockito.mock(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dataSource);
        LocalDataSourceServiceImpl dataSourceService = Mockito.mock(LocalDataSourceServiceImpl.class);
        when(dataSource.getDataSource()).thenReturn(dataSourceService);
        JdbcTemplate template = Mockito.mock(JdbcTemplate.class);
        when(dataSourceService.getJdbcTemplate()).thenReturn(template);
        when(template.queryForList("SELECT * FROM TEST")).thenReturn(new ArrayList<>());
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.OPS_CONTROLLER_PATH + "/derby")
                .param("sql", "SELECT * FROM TEST");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("200", JacksonUtils.toObj(actualValue).get("code").toString());
        propertyUtilMockedStatic.close();
        dynamicDataSourceMockedStatic.close();
    }
    
    @Test
    public void testImportDerby() throws Exception {
        MockedStatic<PropertyUtil> propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        MockedStatic<ApplicationUtils> applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        
        propertyUtilMockedStatic.when(PropertyUtil::isEmbeddedStorage).thenReturn(true);
        
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(DatabaseOperate.class))
                .thenReturn(Mockito.mock(DatabaseOperate.class));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .multipart(Constants.OPS_CONTROLLER_PATH + "/data/removal").file(file);
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        Assert.assertEquals(200, actualValue);
        
        propertyUtilMockedStatic.close();
        applicationUtilsMockedStatic.close();
    }
}
