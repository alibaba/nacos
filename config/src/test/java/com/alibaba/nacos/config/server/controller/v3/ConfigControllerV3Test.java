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

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigCloneInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.listener.ConfigListenerStateDelegate;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class ConfigControllerV3Test {
    
    ConfigControllerV3 configControllerV3;
    
    private MockMvc mockmvc;
    
    @MockitoBean
    private ServletContext servletContext;
    
    @MockitoBean
    private ConfigInfoPersistService configInfoPersistService;
    
    @MockitoBean
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @MockitoBean
    private NamespacePersistService namespacePersistService;
    
    @MockitoBean
    private ConfigOperationService configOperationService;
    
    @MockitoBean
    private ConfigListenerStateDelegate configListenerStateDelegate;
    
    @MockitoBean
    private ConfigDetailService configDetailService;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        configControllerV3 =
            new ConfigControllerV3(configOperationService,
                configInfoPersistService, configDetailService,
                configInfoGrayPersistService,
                namespacePersistService,
                configListenerStateDelegate);
        mockmvc = MockMvcBuilders.standaloneSetup(configControllerV3).build();
    }
    
    @Test
    void testPublishConfig() throws Exception {
        when(configOperationService.publishConfig(any(), any(), anyString())).thenReturn(true);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.CONFIG_ADMIN_V3_PATH)
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("content", "test")
                .param("tag", "").param("appName", "").param("src_user", "")
                .param("config_tags", "").param("desc", "")
                .param("use", "").param("effect", "").param("type", "").param("schema", "");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testGetConfig() throws Exception {
        when(configInfoPersistService.findConfigAllInfo("test", "test", "public"))
            .thenReturn(new ConfigAllInfo());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH)
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("tag", "");
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testDeleteConfig() throws Exception {
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), anyString(),
            any(), any(),
            any())).thenReturn(true);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.CONFIG_ADMIN_V3_PATH)
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("tag", "");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testDeleteConfigs() throws Exception {
        
        final List<ConfigAllInfo> resultInfos = new ArrayList<>();
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        String dataId = "dataId1123";
        String groupName = "group34567";
        String namespaceId = "tenant45678";
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(groupName);
        configAllInfo.setTenant(namespaceId);
        resultInfos.add(configAllInfo);
        Mockito.when(configInfoPersistService.findConfigInfo(eq(1L))).thenReturn(configAllInfo);
        Mockito.when(configInfoPersistService.findConfigInfo(eq(2L))).thenReturn(configAllInfo);
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.CONFIG_ADMIN_V3_PATH + "/batch")
                .param("ids", "1,2");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("0", code);
        assertEquals("true", data);
        Thread.sleep(1200L);
    }
    
    @Test
    void testGetListeners() throws Exception {
        Map<String, String> listenersGroupkeyStatus = new HashMap<>();
        listenersGroupkeyStatus.put("test", "test");
        ConfigListenerInfo sampleResult = new ConfigListenerInfo();
        sampleResult.setQueryType(ConfigListenerInfo.QUERY_TYPE_CONFIG);
        sampleResult.setListenersStatus(listenersGroupkeyStatus);
        
        when(configListenerStateDelegate.getListenerState("test", "test", "public", true))
            .thenReturn(sampleResult);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH + "/listener")
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("sampleTime", "1");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        final String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigListenerInfo configListenerInfo = JacksonUtils.toObj(data, ConfigListenerInfo.class);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_CONFIG, configListenerInfo.getQueryType());
        assertEquals(1, configListenerInfo.getListenersStatus().size());
        assertEquals("test", configListenerInfo.getListenersStatus().get("test"));
        assertEquals("0", code);
    }
    
    @Test
    void testSearchConfig() throws Exception {
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        
        when(configDetailService.findConfigInfoPage("accurate", 1, 10, "test", "test", "public",
            configAdvanceInfo)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH + "/list")
                .param("search", "accurate").param("dataId", "test").param("groupName", "test")
                .param("appName", "")
                .param("namespaceId", "").param("config_tags", "").param("pageNo", "1")
                .param("pageSize", "10")
                .param("config_detail", "");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        JsonNode pageItemsNode = JacksonUtils.toObj(data).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigBasicInfo resConfigInfo =
            JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigBasicInfo.class);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroupName());
        assertEquals(configInfo.getTenant(), resConfigInfo.getNamespaceId());
    }
    
    @Test
    void testFuzzySearchConfig() throws Exception {
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        
        when(configDetailService.findConfigInfoPage("blur", 1, 10, "test", "test", "public",
            configAdvanceInfo)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH + "/list")
                .param("search", "blur").param("dataId", "test").param("groupName", "test")
                .param("appName", "")
                .param("namespaceId", "").param("config_tags", "").param("pageNo", "1")
                .param("pageSize", "10")
                .param("config_detail", "");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        JsonNode pageItemsNode = JacksonUtils.toObj(data).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigBasicInfo resConfigInfo =
            JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigBasicInfo.class);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroupName());
        assertEquals(configInfo.getTenant(), resConfigInfo.getNamespaceId());
    }
    
    @Test
    void testStopBeta() throws Exception {
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.CONFIG_ADMIN_V3_PATH + "/beta")
                .param("beta", "true").param("dataId", "test").param("groupName", "test")
                .param("namespaceId", "");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testQueryBeta() throws Exception {
        
        ConfigInfoGrayWrapper configInfoGrayWrapper = new ConfigInfoGrayWrapper();
        configInfoGrayWrapper.setDataId("test");
        configInfoGrayWrapper.setGroup("test");
        configInfoGrayWrapper.setContent("test");
        configInfoGrayWrapper.setGrayName("beta");
        configInfoGrayWrapper.setGrayRule(
            "{\"type\":\"beta\",\"version\":\"1.0.0\",\"expr\":\"127.0.0.1,127.0.0.2\",\"priority\":-1000}");
        when(configInfoGrayPersistService.findConfigInfo4Gray("test", "test", "public", "beta"))
            .thenReturn(
                configInfoGrayWrapper);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH + "/beta")
                .param("beta", "true").param("dataId", "test").param("groupName", "test")
                .param("namespaceId", "");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigGrayInfo resConfigInfoGrayWrapper = JacksonUtils.toObj(data, ConfigGrayInfo.class);
        
        assertEquals("0", code);
        assertEquals(configInfoGrayWrapper.getDataId(), resConfigInfoGrayWrapper.getDataId());
        assertEquals(configInfoGrayWrapper.getGroup(), resConfigInfoGrayWrapper.getGroupName());
        assertEquals(configInfoGrayWrapper.getContent(), resConfigInfoGrayWrapper.getContent());
        assertEquals(configInfoGrayWrapper.getGrayName(), resConfigInfoGrayWrapper.getGrayName());
        assertEquals(
            "{\"type\":\"beta\",\"version\":\"1.0.0\",\"expr\":\"127.0.0.1,127.0.0.2\",\"priority\":-1000}",
            resConfigInfoGrayWrapper.getGrayRule());
        
    }
    
    @Test
    void testPublishGray() throws Exception {
        when(configOperationService.publishConfigGray(anyString(), any(), any())).thenReturn(true);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.CONFIG_ADMIN_V3_PATH + "/gray")
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("content", "gray")
                .param("grayName", "tagv2_gray").param("grayType", "tagv2")
                .param("grayMatchRuleExp", "region=hz&&env=prod").param("grayVersion", "1.1.0")
                .param("grayPriority", "1").param("type", "");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        
        assertEquals("0", code);
        assertEquals("true", data);
        ArgumentCaptor<ConfigRequestInfo> requestInfoCaptor =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        Mockito.verify(configOperationService).publishConfigGray(eq("tagv2"), any(),
            requestInfoCaptor.capture());
        assertEquals(Constants.HTTP, requestInfoCaptor.getValue().getSrcType());
    }
    
    @Test
    void testQueryGray() throws Exception {
        ConfigInfoGrayWrapper configInfoGrayWrapper = new ConfigInfoGrayWrapper();
        configInfoGrayWrapper.setDataId("test");
        configInfoGrayWrapper.setGroup("test");
        configInfoGrayWrapper.setContent("gray");
        configInfoGrayWrapper.setGrayName("tagv2_gray");
        configInfoGrayWrapper.setGrayRule(
            "{\"type\":\"tagv2\",\"version\":\"1.1.0\",\"expr\":\"region=hz&&env=prod\",\"priority\":1}");
        when(configInfoGrayPersistService.findConfigInfo4Gray("test", "test", "public",
            "tagv2_gray")).thenReturn(
                configInfoGrayWrapper);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH + "/gray")
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("grayName", "tagv2_gray");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigGrayInfo result = JacksonUtils.toObj(data, ConfigGrayInfo.class);
        
        assertEquals("0", code);
        assertEquals(configInfoGrayWrapper.getDataId(), result.getDataId());
        assertEquals(configInfoGrayWrapper.getGroup(), result.getGroupName());
        assertEquals(configInfoGrayWrapper.getContent(), result.getContent());
        assertEquals(configInfoGrayWrapper.getGrayName(), result.getGrayName());
    }
    
    @Test
    void testStopGray() throws Exception {
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), anyString(),
            any(), any(),
            any())).thenReturn(true);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.CONFIG_ADMIN_V3_PATH + "/gray")
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("grayName", "tagv2_gray");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testExportConfig() throws Exception {
        String dataId = "dataId2.json";
        String groupName = "group2";
        String namespaceId = "tenant234";
        String appname = "appname2";
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(groupName);
        configAllInfo.setTenant(namespaceId);
        configAllInfo.setAppName(appname);
        configAllInfo.setContent("content1234");
        List<ConfigAllInfo> dataList = new ArrayList<>();
        dataList.add(configAllInfo);
        Mockito.when(configInfoPersistService.findAllConfigInfo4Export(eq(dataId), eq(groupName),
            eq(namespaceId),
            eq(appname), eq(Arrays.asList(1L, 2L)))).thenReturn(dataList);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(Constants.CONFIG_ADMIN_V3_PATH + "/export")
            .param("dataId", dataId).param("groupName", groupName).param("namespaceId", namespaceId)
            .param("appName", appname).param("ids", "1,2");
        
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testImportAndPublishConfigV2() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        String dataId = "dataId23456.json";
        String groupName = "group132";
        String content = "content1234";
        ZipUtils.ZipItem zipItem = new ZipUtils.ZipItem(groupName + "/" + dataId, content);
        zipItems.add(zipItem);
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem configExportItem = new ConfigMetadata.ConfigExportItem();
        configExportItem.setDataId(dataId);
        configExportItem.setGroup(groupName);
        configExportItem.setType("json");
        configExportItem.setAppName("appna123");
        configMetadata.getMetadata().add(configExportItem);
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
            new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes())))
                .thenReturn(unziped);
            when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
            Map<String, Object> map = new HashMap<>();
            map.put("test", "test");
            when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
                any(),
                any())).thenReturn(map);
            
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import").file(file).param("src_user", "test")
                .param("namespace", "public").param("policy", "ABORT");
            
            String actualValue =
                mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
            
            String code = JacksonUtils.toObj(actualValue).get("code").toString();
            assertEquals("0", code);
            Map<String, Object> resultMap =
                JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                    Map.class);
            assertEquals(map.get("test"), resultMap.get("test").toString());
        }
    }
    
    @Test
    void testCloneConfig() throws Exception {
        ConfigCloneInfo cloneInfo = new ConfigCloneInfo();
        cloneInfo.setConfigId(1L);
        cloneInfo.setTargetDataId("test");
        cloneInfo.setTargetGroupName("test");
        List<ConfigCloneInfo> configBeansList = new ArrayList<>();
        configBeansList.add(cloneInfo);
        
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test");
        configAllInfo.setGroup("test");
        List<ConfigAllInfo> queryedDataList = new ArrayList<>();
        queryedDataList.add(configAllInfo);
        
        List<Long> idList = new ArrayList<>(configBeansList.size());
        idList.add(cloneInfo.getConfigId());
        
        when(configInfoPersistService.findAllConfigInfo4Export(null, null, null, null, idList))
            .thenReturn(
                queryedDataList);
        
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(
            configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any()))
            .thenReturn(map);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.CONFIG_ADMIN_V3_PATH + "/clone")
                .param("clone", "true").param("src_user", "test").param("namespaceId", "public")
                .param("policy", "ABORT").content(JacksonUtils.toJson(configBeansList))
                .contentType(MediaType.APPLICATION_JSON);
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        Map<String, Object> resultMap =
            JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        assertEquals(map.get("test"), resultMap.get("test").toString());
    }
    
    @Test
    void testGetConfigNotFound() throws Exception {
        when(configInfoPersistService.findConfigAllInfo("test", "test", "public"))
            .thenReturn(null);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH)
                .param("dataId", "test").param("groupName", "test")
                .param("namespaceId", "");
        try {
            mockmvc.perform(builder);
        } catch (Exception e) {
            assertEquals("Config not exist, please publish Config first.",
                e.getCause().getMessage());
        }
    }
    
    @Test
    void testPublishConfigMetadata() throws Exception {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(
                Constants.CONFIG_ADMIN_V3_PATH + "/metadata")
                .param("dataId", "test").param("groupName", "test")
                .param("namespaceId", "")
                .param("config_tags", "tag1,tag2").param("desc", "desc");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
    }
    
    @Test
    void testDeleteConfigsWithNullConfigInfo() throws Exception {
        when(configInfoPersistService.findConfigInfo(eq(1L))).thenReturn(null);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(
                Constants.CONFIG_ADMIN_V3_PATH + "/batch")
                .param("ids", "1");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
    }
    
    @Test
    void testDeleteConfigsWithException() throws Exception {
        when(configInfoPersistService.findConfigInfo(eq(1L)))
            .thenThrow(new RuntimeException("error"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(
                Constants.CONFIG_ADMIN_V3_PATH + "/batch")
                .param("ids", "1");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("30000", code);
    }
    
    @Test
    void testQueryBetaNotFound() throws Exception {
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "test", "test", "public", "beta")).thenReturn(null);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.CONFIG_ADMIN_V3_PATH + "/beta")
                .param("dataId", "test").param("groupName", "test")
                .param("namespaceId", "");
        try {
            mockmvc.perform(builder);
        } catch (Exception e) {
            assertEquals("Config is not in beta.",
                e.getCause().getMessage());
        }
    }
    
    @Test
    void testStopBetaWithException() throws Exception {
        doThrow(new RuntimeException("error"))
            .when(configInfoGrayPersistService).removeConfigInfoGray(
                any(), any(), any(), any(), any(), any());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(
                Constants.CONFIG_ADMIN_V3_PATH + "/beta")
                .param("dataId", "test").param("groupName", "test")
                .param("namespaceId", "");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("30000", code);
    }
    
    @Test
    void testSearchConfigWithAdvanceInfo() throws Exception {
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(0);
        page.setPageNumber(1);
        page.setPagesAvailable(0);
        page.setPageItems(new ArrayList<>());
        when(configDetailService.findConfigInfoPage(
            eq("accurate"), eq(1), eq(10), eq("test"), eq("test"),
            eq("public"), any())).thenReturn(page);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(
                Constants.CONFIG_ADMIN_V3_PATH + "/list")
                .param("search", "accurate")
                .param("dataId", "test").param("groupName", "test")
                .param("appName", "myApp")
                .param("namespaceId", "")
                .param("configTags", "tag1")
                .param("type", "json")
                .param("configDetail", "someDetail")
                .param("pageNo", "1").param("pageSize", "10");
        int status = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }
    
    @Test
    void testImportConfigWithNullFile() throws Exception {
        MockMultipartHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import")
                .param("src_user", "test")
                .param("namespaceId", "public")
                .param("policy", "ABORT");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("100005", code);
    }
    
    @Test
    void testImportConfigNamespaceNotExist() throws Exception {
        when(namespacePersistService.tenantInfoCountByTenantId("ns1"))
            .thenReturn(0);
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.zip", "application/zip", "test".getBytes());
        MockMultipartHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import")
                .file(file)
                .param("src_user", "test")
                .param("namespaceId", "ns1")
                .param("policy", "ABORT");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("22001", code);
    }
    
    @Test
    void testImportConfigWithFileReadException() throws Exception {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("broken"));
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        
        Result<Map<String, Object>> result = configControllerV3.importAndPublishConfig(
            new MockHttpServletRequest(), "test", "public", SameConfigPolicy.ABORT, file);
        
        assertEquals(100004, result.getCode());
        assertEquals(0, result.getData().get("succCount"));
    }
    
    @Test
    void testCloneConfigEmpty() throws Exception {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(
                Constants.CONFIG_ADMIN_V3_PATH + "/clone")
                .param("src_user", "test")
                .param("namespaceId", "public")
                .param("policy", "ABORT")
                .content("[]")
                .contentType(MediaType.APPLICATION_JSON);
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("100006", code);
    }
    
    @Test
    void testCloneConfigNamespaceNotExist() throws Exception {
        ConfigCloneInfo info = new ConfigCloneInfo();
        info.setConfigId(1L);
        when(namespacePersistService.tenantInfoCountByTenantId("ns1"))
            .thenReturn(0);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(
                Constants.CONFIG_ADMIN_V3_PATH + "/clone")
                .param("src_user", "test")
                .param("namespaceId", "ns1")
                .param("policy", "ABORT")
                .content(JacksonUtils.toJson(Collections.singletonList(info)))
                .contentType(MediaType.APPLICATION_JSON);
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("22001", code);
    }
    
    @Test
    void testImportConfigWithUnrecognizedItems() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        String dataId = "dataId1.json";
        String groupName = "group1";
        String content = "content123";
        zipItems.add(new ZipUtils.ZipItem(groupName + "/" + dataId, content));
        zipItems.add(new ZipUtils.ZipItem("badNameNoSlash", "data"));
        zipItems.add(new ZipUtils.ZipItem("unknownGroup/unknownDataId", "data2"));
        
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem item = new ConfigMetadata.ConfigExportItem();
        item.setDataId(dataId);
        item.setGroup(groupName);
        item.setType("json");
        configMetadata.getMetadata().add(item);
        ConfigMetadata.ConfigExportItem item2 = new ConfigMetadata.ConfigExportItem();
        item2.setDataId("noFileDataId");
        item2.setGroup("noFileGroup");
        item2.setType("text");
        configMetadata.getMetadata().add(item2);
        
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
            new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes())))
                .thenReturn(unziped);
            when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
            Map<String, Object> map = new HashMap<>();
            map.put("succCount", 1);
            when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
                any(), any())).thenReturn(map);
            
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import").file(file).param("src_user", "test")
                .param("namespaceId", "public").param("policy", "ABORT");
            
            String actualValue =
                mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
            String code = JacksonUtils.toObj(actualValue).get("code").toString();
            assertEquals("0", code);
            JsonNode data = JacksonUtils.toObj(actualValue).get("data");
            assertEquals("3", data.get("unrecognizedCount").toString());
        }
    }
    
    @Test
    void testImportConfigWithInvalidMetadata() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        zipItems.add(new ZipUtils.ZipItem("group/data.json", "content"));
        
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem badItem = new ConfigMetadata.ConfigExportItem();
        badItem.setDataId("");
        badItem.setGroup("group");
        badItem.setType("json");
        configMetadata.getMetadata().add(badItem);
        
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
            new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes())))
                .thenReturn(unziped);
            
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import").file(file).param("src_user", "test")
                .param("namespaceId", "").param("policy", "ABORT");
            
            String actualValue =
                mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
            String code = JacksonUtils.toObj(actualValue).get("code").toString();
            assertEquals("100002", code);
        }
    }
    
    @Test
    void testImportConfigWithMissingConfigFile() throws Exception {
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem item = new ConfigMetadata.ConfigExportItem();
        item.setDataId("missing.json");
        item.setGroup("group");
        item.setType("json");
        configMetadata.getMetadata().add(item);
        
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(new ArrayList<>(),
            new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes())))
                .thenReturn(unziped);
            when(namespacePersistService.tenantInfoCountByTenantId("public"))
                .thenReturn(1);
            
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import").file(file).param("src_user", "test")
                .param("namespaceId", "public").param("policy", "ABORT");
            
            String actualValue =
                mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
            String code = JacksonUtils.toObj(actualValue).get("code").toString();
            assertEquals("100005", code);
        }
    }
    
    @Test
    void testCloneConfigEmptyQueryResult() throws Exception {
        ConfigCloneInfo info = new ConfigCloneInfo();
        info.setConfigId(1L);
        when(namespacePersistService.tenantInfoCountByTenantId("public"))
            .thenReturn(1);
        when(configInfoPersistService.findAllConfigInfo4Export(
            any(), any(), any(), any(), anyList()))
            .thenReturn(new ArrayList<>());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(
                Constants.CONFIG_ADMIN_V3_PATH + "/clone")
                .param("src_user", "test")
                .param("namespaceId", "public")
                .param("policy", "ABORT")
                .content(JacksonUtils.toJson(Collections.singletonList(info)))
                .contentType(MediaType.APPLICATION_JSON);
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("100005", code);
    }
    
    @Test
    void testCloneConfigWithAppNameAndBlankSrcUser() throws Exception {
        ConfigCloneInfo cloneInfo = new ConfigCloneInfo();
        cloneInfo.setConfigId(1L);
        cloneInfo.setTargetDataId("cloned");
        cloneInfo.setTargetGroupName("clonedGroup");
        
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("orig");
        configAllInfo.setGroup("origGroup");
        configAllInfo.setAppName("myApp");
        configAllInfo.setEncryptedDataKey("cipherKey");
        configAllInfo.setId(1L);
        List<ConfigAllInfo> queryedDataList = new ArrayList<>();
        queryedDataList.add(configAllInfo);
        
        when(configInfoPersistService.findAllConfigInfo4Export(
            any(), any(), any(), any(), anyList()))
            .thenReturn(queryedDataList);
        
        Map<String, Object> map = new HashMap<>();
        map.put("succCount", 1);
        when(configInfoPersistService.batchInsertOrUpdate(
            anyList(), anyString(), anyString(), any(), any()))
            .thenReturn(map);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.CONFIG_ADMIN_V3_PATH + "/clone")
                .param("src_user", "").param("namespaceId", "public")
                .param("policy", "ABORT")
                .content(JacksonUtils.toJson(Collections.singletonList(cloneInfo)))
                .contentType(MediaType.APPLICATION_JSON);
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
    }
    
    @Test
    void testCloneConfigDuplicateConfigId() throws Exception {
        ConfigCloneInfo info1 = new ConfigCloneInfo();
        info1.setConfigId(1L);
        info1.setTargetDataId("data1");
        ConfigCloneInfo info2 = new ConfigCloneInfo();
        info2.setConfigId(1L);
        info2.setTargetDataId("data2");
        List<ConfigCloneInfo> cloneInfos = Arrays.asList(info1, info2);
        
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("orig");
        configAllInfo.setGroup("origGroup");
        configAllInfo.setId(1L);
        when(configInfoPersistService.findAllConfigInfo4Export(
            any(), any(), any(), any(), anyList()))
            .thenReturn(Collections.singletonList(configAllInfo));
        
        Map<String, Object> map = new HashMap<>();
        map.put("succCount", 1);
        when(configInfoPersistService.batchInsertOrUpdate(
            anyList(), anyString(), anyString(), any(), any()))
            .thenReturn(map);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.CONFIG_ADMIN_V3_PATH + "/clone")
                .param("src_user", "test").param("namespaceId", "public")
                .param("policy", "ABORT")
                .content(JacksonUtils.toJson(cloneInfos))
                .contentType(MediaType.APPLICATION_JSON);
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
    }
    
    @Test
    void testImportConfigWithBlankSrcUser() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        zipItems.add(new ZipUtils.ZipItem("group/data.json", "content"));
        
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem item = new ConfigMetadata.ConfigExportItem();
        item.setDataId("data.json");
        item.setGroup("group");
        item.setType("json");
        configMetadata.getMetadata().add(item);
        
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
            new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip",
                "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMock = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMock.when(() -> ZipUtils.unzip(eq(file.getBytes())))
                .thenReturn(unziped);
            when(namespacePersistService.tenantInfoCountByTenantId("public"))
                .thenReturn(1);
            Map<String, Object> map = new HashMap<>();
            map.put("succCount", 1);
            when(configInfoPersistService.batchInsertOrUpdate(
                anyList(), anyString(), anyString(), any(), any()))
                .thenReturn(map);
            
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import").file(file)
                .param("src_user", "")
                .param("namespaceId", "public").param("policy", "ABORT");
            
            String actualValue =
                mockmvc.perform(builder).andReturn().getResponse()
                    .getContentAsString();
            String code = JacksonUtils.toObj(actualValue).get("code").toString();
            assertEquals("0", code);
        }
    }
    
    @Test
    void testImportConfigWithEmptyMetadataList() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        zipItems.add(new ZipUtils.ZipItem("group/data.json", "content"));
        
        ConfigMetadata emptyMetadata = new ConfigMetadata();
        emptyMetadata.setMetadata(new ArrayList<>());
        
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
            new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(emptyMetadata)));
        MockMultipartFile file =
            new MockMultipartFile("file", "test.zip", "application/zip",
                "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMock = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMock.when(() -> ZipUtils.unzip(eq(file.getBytes())))
                .thenReturn(unziped);
            when(namespacePersistService.tenantInfoCountByTenantId("public"))
                .thenReturn(1);
            
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(
                Constants.CONFIG_ADMIN_V3_PATH + "/import").file(file)
                .param("src_user", "test")
                .param("namespaceId", "public").param("policy", "ABORT");
            
            String actualValue =
                mockmvc.perform(builder).andReturn().getResponse()
                    .getContentAsString();
            String code = JacksonUtils.toObj(actualValue).get("code").toString();
            assertEquals("100002", code);
        }
    }
}
