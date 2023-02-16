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
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class HistoryControllerTest {
    
    @InjectMocks
    HistoryController historyController;
    
    private MockMvc mockmvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private HistoryService historyService;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(historyController, "historyService", historyService);
        mockmvc = MockMvcBuilders.standaloneSetup(historyController).build();
    }
    
    @Test
    public void testListConfigHistory() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId("test");
        configHistoryInfo.setGroup("test");
        configHistoryInfo.setContent("test");
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        List<ConfigHistoryInfo> configHistoryInfoList = new ArrayList<>();
        configHistoryInfoList.add(configHistoryInfo);
        
        Page<ConfigHistoryInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configHistoryInfoList);
        
        when(historyService.listConfigHistory("test", "test", "", 1, 10)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.HISTORY_CONTROLLER_PATH)
                .param("search", "accurate").param("dataId", "test")
                .param("group", "test").param("tenant", "")
                .param("appName", "").param("pageNo", "1")
                .param("pageSize", "10");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        JsonNode pageItemsNode = JacksonUtils.toObj(actualValue).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigHistoryInfo resConfigHistoryInfo = JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigHistoryInfo.class);
        
        Assert.assertEquals(configHistoryInfoList.size(), resultList.size());
        Assert.assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        Assert.assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        Assert.assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId("test");
        configHistoryInfo.setGroup("test");
        configHistoryInfo.setContent("test");
        configHistoryInfo.setTenant("");
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyController.getConfigHistoryInfo("test", "test", "", 1L)).thenReturn(configHistoryInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.HISTORY_CONTROLLER_PATH)
                .param("dataId", "test")
                .param("group", "test")
                .param("tenant", "")
                .param("nid", "1");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        ConfigHistoryInfo resConfigHistoryInfo = JacksonUtils.toObj(actualValue, ConfigHistoryInfo.class);
        
        Assert.assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        Assert.assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        Assert.assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetPreviousConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId("test");
        configHistoryInfo.setGroup("test");
        configHistoryInfo.setContent("test");
        configHistoryInfo.setTenant("");
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getPreviousConfigHistoryInfo("test", "test", "", 1L)).thenReturn(configHistoryInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.HISTORY_CONTROLLER_PATH + "/previous")
                .param("dataId", "test")
                .param("group", "test")
                .param("tenant", "")
                .param("id", "1");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        ConfigHistoryInfo resConfigHistoryInfo = JacksonUtils.toObj(actualValue, ConfigHistoryInfo.class);
        
        Assert.assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        Assert.assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        Assert.assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetDataIds() throws Exception {
        
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("test");
        List<ConfigInfoWrapper> configInfoWrappers = new ArrayList<>();
        configInfoWrappers.add(configInfoWrapper);
        when(historyService.getConfigListByNamespace("test")).thenReturn(configInfoWrappers);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.HISTORY_CONTROLLER_PATH + "/configs")
                .param("tenant", "test");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        List resConfigInfoWrappers = JacksonUtils.toObj(actualValue, List.class);
        ConfigInfoWrapper resConfigInfoWrapper = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get(0).toString(), ConfigInfoWrapper.class);
        
        Assert.assertEquals(configInfoWrappers.size(), resConfigInfoWrappers.size());
        Assert.assertEquals(configInfoWrapper.getDataId(), resConfigInfoWrapper.getDataId());
        Assert.assertEquals(configInfoWrapper.getGroup(), resConfigInfoWrapper.getGroup());
        Assert.assertEquals(configInfoWrapper.getContent(), resConfigInfoWrapper.getContent());
        
    }
    
}
