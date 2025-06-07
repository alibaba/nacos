/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.config;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.config.HistoryProxy;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * ConsoleHistoryControllerTest.
 *
 * @author zhangyukun on:2024/8/28
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsoleHistoryControllerTest {
    
    @InjectMocks
    private ConsoleHistoryController consoleHistoryController;
    
    @Mock
    private HistoryProxy historyProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consoleHistoryController).build();
    }
    
    @Test
    void testGetConfigHistoryInfo() throws Exception {
        ConfigHistoryDetailInfo configHistoryInfo = new ConfigHistoryDetailInfo();
        configHistoryInfo.setDataId("testDataId");
        configHistoryInfo.setGroupName("testGroup");
        configHistoryInfo.setCreateTime(System.currentTimeMillis());
        configHistoryInfo.setModifyTime(System.currentTimeMillis());
        
        when(historyProxy.getConfigHistoryInfo("testDataId", "testGroup", Constants.DEFAULT_NAMESPACE_ID,
                1L)).thenReturn(configHistoryInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/history")
                .param("dataId", "testDataId").param("groupName", "testGroup").param("nid", "1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<ConfigHistoryDetailInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        ConfigHistoryDetailInfo resultConfigHistoryInfo = result.getData();
        
        assertEquals("testDataId", resultConfigHistoryInfo.getDataId());
        assertEquals("testGroup", resultConfigHistoryInfo.getGroupName());
    }
    
    @Test
    void testListConfigHistory() throws Exception {
        Page<ConfigHistoryBasicInfo> page = new Page<>();
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        ConfigHistoryBasicInfo configHistoryInfo = new ConfigHistoryBasicInfo();
        configHistoryInfo.setDataId("testDataId");
        configHistoryInfo.setGroupName("testGroup");
        configHistoryInfo.setCreateTime(System.currentTimeMillis());
        configHistoryInfo.setModifyTime(System.currentTimeMillis());
        page.setPageItems(Collections.singletonList(configHistoryInfo));
        
        when(historyProxy.listConfigHistory("testDataId", "testGroup", Constants.DEFAULT_NAMESPACE_ID, 1,
                100)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/history/list")
                .param("dataId", "testDataId").param("groupName", "testGroup").param("pageNo", "1")
                .param("pageSize", "100");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Page<ConfigHistoryBasicInfo>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        Page<ConfigHistoryBasicInfo> resultPage = result.getData();
        ConfigHistoryBasicInfo resultConfigHistoryInfo = resultPage.getPageItems().get(0);
        
        assertEquals("testDataId", resultConfigHistoryInfo.getDataId());
        assertEquals("testGroup", resultConfigHistoryInfo.getGroupName());
    }
    
    @Test
    void testGetPreviousConfigHistoryInfo() throws Exception {
        ConfigHistoryDetailInfo configHistoryInfo = new ConfigHistoryDetailInfo();
        configHistoryInfo.setDataId("testDataId");
        configHistoryInfo.setGroupName("testGroup");
        configHistoryInfo.setCreateTime(System.currentTimeMillis());
        configHistoryInfo.setModifyTime(System.currentTimeMillis());
        
        when(historyProxy.getPreviousConfigHistoryInfo("testDataId", "testGroup", Constants.DEFAULT_NAMESPACE_ID,
                1L)).thenReturn(configHistoryInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/history/previous")
                .param("dataId", "testDataId").param("groupName", "testGroup").param("id", "1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<ConfigHistoryDetailInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        ConfigHistoryDetailInfo resultConfigHistoryInfo = result.getData();
        
        assertEquals("testDataId", resultConfigHistoryInfo.getDataId());
        assertEquals("testGroup", resultConfigHistoryInfo.getGroupName());
    }
    
    @Test
    void testGetConfigsByTenant() throws Exception {
        ConfigBasicInfo configInfo = new ConfigBasicInfo();
        configInfo.setDataId("testDataId");
        configInfo.setGroupName("testGroup");
        List<ConfigBasicInfo> configInfoList = Collections.singletonList(configInfo);
        
        when(historyProxy.getConfigsByTenant("testNamespaceId")).thenReturn(configInfoList);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/history/configs")
                .param("namespaceId", "testNamespaceId");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<List<ConfigBasicInfo>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        List<ConfigBasicInfo> resultConfigInfoList = result.getData();
        
        assertEquals(1, resultConfigInfoList.size());
        assertEquals("testDataId", resultConfigInfoList.get(0).getDataId());
        assertEquals("testGroup", resultConfigInfoList.get(0).getGroupName());
    }
}
