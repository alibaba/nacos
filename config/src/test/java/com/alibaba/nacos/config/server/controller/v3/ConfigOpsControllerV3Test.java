/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class ConfigOpsControllerV3Test {
    
    ConfigOpsControllerV3 configOpsControllerV3;
    
    @MockitoBean
    DumpService dumpService;
    
    MockedStatic<DatasourceConfiguration> datasourceConfigurationMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<ApplicationUtils> applicationUtilsMockedStatic;
    
    private MockMvc mockMvc;
    
    @MockitoBean
    private ServletContext servletContext;
    
    @AfterEach
    void after() {
        datasourceConfigurationMockedStatic.close();
        dynamicDataSourceMockedStatic.close();
        applicationUtilsMockedStatic.close();
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(false);
    }
    
    @BeforeEach
    void init() {
        when(servletContext.getContextPath()).thenReturn("/nacos");
        configOpsControllerV3 = new ConfigOpsControllerV3(dumpService);
        mockMvc = MockMvcBuilders.standaloneSetup(configOpsControllerV3).build();
        
        datasourceConfigurationMockedStatic = Mockito.mockStatic(DatasourceConfiguration.class);
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
    }
    
    @Test
    void testUpdateLocalCacheFromStore() throws Exception {
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/localCache");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testSetLogLevel() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .put(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/log").param("logName", "test")
            .param("logLevel", "test");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testDerbyOps() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        DynamicDataSource dataSource = Mockito.mock(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dataSource);
        LocalDataSourceServiceImpl dataSourceService =
            Mockito.mock(LocalDataSourceServiceImpl.class);
        when(dataSource.getDataSource()).thenReturn(dataSourceService);
        JdbcTemplate template = Mockito.mock(JdbcTemplate.class);
        when(dataSourceService.getJdbcTemplate()).thenReturn(template);
        when(template.queryForList("SELECT * FROM TEST")).thenReturn(new ArrayList<>());
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby")
                .param("sql", "SELECT * FROM TEST");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("0", JacksonUtils.toObj(actualValue).get("code").toString());
        
    }
    
    @Test
    void testImportDerby() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(DatabaseOperate.class))
            .thenReturn(Mockito.mock(DatabaseOperate.class));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .multipart(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby/import")
            .file(file);
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testDerbyOpsNotEmbedded() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(false);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(
                Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby")
                .param("sql", "SELECT * FROM TEST");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("30000", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testDerbyOpsNonSelectSql() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        mockLocalDataSource();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(
                Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby")
                .param("sql", "DELETE FROM TEST");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("30000", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testDerbyOpsNonSelectSqlDirectly() {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        mockLocalDataSource();
        
        assertEquals(30000,
            configOpsControllerV3.derbyOps("DELETE FROM TEST").getCode());
    }
    
    @Test
    void testDerbyOpsDisabled() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(false);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(
                Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby")
                .param("sql", "SELECT * FROM TEST");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("30000", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testUpdateLocalCacheFromStoreError() throws Exception {
        doThrow(new RuntimeException("dump error")).when(dumpService).dumpAll();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(
                Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/localCache");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("30000", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testImportDerbyNotEmbedded() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(false);
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip",
                "test".getBytes());
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .multipart(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby/import")
            .file(file);
        int actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testImportDerbyDisabled() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(false);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip",
                "test".getBytes());
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .multipart(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby/import")
            .file(file);
        int actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testDerbyOpsWithExistingLimit() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        DynamicDataSource dataSource = Mockito.mock(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dataSource);
        LocalDataSourceServiceImpl dataSourceService =
            Mockito.mock(LocalDataSourceServiceImpl.class);
        when(dataSource.getDataSource()).thenReturn(dataSourceService);
        JdbcTemplate template = Mockito.mock(JdbcTemplate.class);
        when(dataSourceService.getJdbcTemplate()).thenReturn(template);
        String sqlWithLimit =
            "SELECT * FROM TEST OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY";
        when(template.queryForList(sqlWithLimit)).thenReturn(new ArrayList<>());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(
                Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby")
                .param("sql", sqlWithLimit);
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("0", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testDerbyOpsException() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        DynamicDataSource dataSource = Mockito.mock(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dataSource);
        LocalDataSourceServiceImpl dataSourceService =
            Mockito.mock(LocalDataSourceServiceImpl.class);
        when(dataSource.getDataSource()).thenReturn(dataSourceService);
        JdbcTemplate template = Mockito.mock(JdbcTemplate.class);
        when(dataSourceService.getJdbcTemplate()).thenReturn(template);
        when(template.queryForList(Mockito.anyString()))
            .thenThrow(new RuntimeException("db error"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(
                Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby")
                .param("sql", "SELECT * FROM TEST");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("30000",
            JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testSetLogLevelError() throws Exception {
        try (MockedStatic<LogUtil> logUtilMockedStatic = Mockito.mockStatic(LogUtil.class)) {
            logUtilMockedStatic.when(() -> LogUtil.setLogLevel("test", "INVALID_LEVEL"))
                .thenThrow(new IllegalArgumentException("invalid"));
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .put(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/log")
                .param("logName", "test")
                .param("logLevel", "INVALID_LEVEL");
            String actualValue =
                mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
            
            assertEquals("30000", JacksonUtils.toObj(actualValue).get("code").toString());
        }
    }
    
    @Test
    void testImportDerbyWithSuccessCallback() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        
        DatabaseOperate mockDbOperate = Mockito.mock(DatabaseOperate.class);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(DatabaseOperate.class))
            .thenReturn(mockDbOperate);
        
        CompletableFuture<com.alibaba.nacos.common.model.RestResult<String>> future =
            new CompletableFuture<>();
        when(mockDbOperate.dataImport(any())).thenReturn(future);
        future.complete(
            com.alibaba.nacos.common.model.RestResultUtils.success("import success"));
        
        MockMultipartFile file =
            new MockMultipartFile("file", "test.sql", "text/plain",
                "INSERT INTO test VALUES(1)".getBytes());
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .multipart(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby/import")
            .file(file);
        int status = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }
    
    @Test
    void testImportDerbyWithFailureCallback() throws Exception {
        ConfigCommonConfig.getInstance().setDerbyOpsEnabled(true);
        datasourceConfigurationMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage)
            .thenReturn(true);
        
        DatabaseOperate mockDbOperate = Mockito.mock(DatabaseOperate.class);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(DatabaseOperate.class))
            .thenReturn(mockDbOperate);
        
        CompletableFuture<com.alibaba.nacos.common.model.RestResult<String>> future =
            new CompletableFuture<>();
        when(mockDbOperate.dataImport(any())).thenReturn(future);
        future.completeExceptionally(new RuntimeException("import failed"));
        
        MockMultipartFile file =
            new MockMultipartFile("file", "test.sql", "text/plain",
                "INSERT INTO test VALUES(1)".getBytes());
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .multipart(Constants.OPS_CONTROLLER_V3_ADMIN_PATH + "/derby/import")
            .file(file);
        int status = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testConvertToResultCopiesCompletedRestResult() {
        DeferredResult<RestResult<String>> restResult = new DeferredResult<>();
        DeferredResult<Result<String>> wrappedResult =
            ReflectionTestUtils.invokeMethod(configOpsControllerV3, "convertToResult", restResult);
        restResult.setResult(RestResultUtils.success("ok"));
        
        Runnable completionCallback =
            (Runnable) ReflectionTestUtils.getField(restResult, "completionCallback");
        completionCallback.run();
        
        Result<String> result = (Result<String>) wrappedResult.getResult();
        assertEquals(200, result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testConvertToResultCopiesPreCompletedRestResult() {
        DeferredResult<RestResult<String>> restResult = new DeferredResult<>();
        restResult.setResult(RestResultUtils.failed("pre-set failure"));
        
        DeferredResult<Result<String>> wrappedResult =
            ReflectionTestUtils.invokeMethod(configOpsControllerV3, "convertToResult", restResult);
        
        Result<String> result = (Result<String>) wrappedResult.getResult();
        assertEquals(500, result.getCode());
        assertEquals("pre-set failure", result.getMessage());
    }
    
    @Test
    void testConvertToResultIgnoresNullRestResult() {
        DeferredResult<RestResult<String>> restResult = new DeferredResult<>();
        DeferredResult<Result<String>> wrappedResult =
            ReflectionTestUtils.invokeMethod(configOpsControllerV3, "convertToResult", restResult);
        
        Runnable completionCallback =
            (Runnable) ReflectionTestUtils.getField(restResult, "completionCallback");
        completionCallback.run();
        
        assertNull(wrappedResult.getResult());
    }
    
    private void mockLocalDataSource() {
        DynamicDataSource dataSource = Mockito.mock(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dataSource);
        when(dataSource.getDataSource())
            .thenReturn(Mockito.mock(LocalDataSourceServiceImpl.class));
    }
}
