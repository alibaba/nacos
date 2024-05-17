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
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class ConfigOpsControllerTest {
    
    @InjectMocks
    ConfigOpsController configOpsController;
    
    @Mock
    DumpService dumpService;
    
    MockedStatic<DatasourceConfiguration> datasourceConfigurationMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<ApplicationUtils> applicationUtilsMockedStatic;
    
    private MockMvc mockMvc;
    
    @Mock
    private ServletContext servletContext;
    
    @AfterEach
    void after() {
        datasourceConfigurationMockedStatic.close();
        dynamicDataSourceMockedStatic.close();
        applicationUtilsMockedStatic.close();
    }
    
    @BeforeEach
    void init() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(configOpsController, "dumpService", dumpService);
        mockMvc = MockMvcBuilders.standaloneSetup(configOpsController).build();
        
        datasourceConfigurationMockedStatic = Mockito.mockStatic(DatasourceConfiguration.class);
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
    }
    
    @Test
    void testUpdateLocalCacheFromStore() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.OPS_CONTROLLER_PATH + "/localCache");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testSetLogLevel() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.OPS_CONTROLLER_PATH + "/log").param("logName", "test")
                .param("logLevel", "test");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testDerbyOps() throws Exception {
        
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(true);
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
        assertEquals("200", JacksonUtils.toObj(actualValue).get("code").toString());
        
    }
    
    @Test
    void testImportDerby() throws Exception {
        
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(true);
        
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(DatabaseOperate.class))
                .thenReturn(Mockito.mock(DatabaseOperate.class));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(Constants.OPS_CONTROLLER_PATH + "/data/removal")
                .file(file);
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
        
    }
}
