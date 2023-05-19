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
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
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

import java.sql.Timestamp;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class CapacityControllerTest {

    @InjectMocks
    CapacityController capacityController;
    
    private MockMvc mockMvc;
    
    @Mock
    private CapacityService capacityService;
    
    @Mock
    private ServletContext servletContext;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(capacityController, "capacityService", capacityService);
        mockMvc = MockMvcBuilders.standaloneSetup(capacityController).build();
    }
    
    @Test
    public void testGetCapacity() throws Exception {
    
        Capacity capacity = new Capacity();
        capacity.setId(1L);
        capacity.setMaxAggrCount(1);
        capacity.setMaxSize(1);
        capacity.setMaxAggrSize(1);
        capacity.setGmtCreate(new Timestamp(1));
        capacity.setGmtModified(new Timestamp(2));
        when(capacityService.getCapacityWithDefault(eq("test"), eq("test"))).thenReturn(capacity);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CAPACITY_CONTROLLER_PATH)
                .param("group", "test").param("tenant", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Capacity result = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(), Capacity.class);
        
        Assert.assertNotNull(result);
        Assert.assertEquals(capacity.getId(), result.getId());
        Assert.assertEquals(capacity.getMaxAggrCount(), result.getMaxAggrCount());
        Assert.assertEquals(capacity.getMaxSize(), result.getMaxSize());
        Assert.assertEquals(capacity.getMaxAggrSize(), result.getMaxAggrSize());
        Assert.assertEquals(capacity.getGmtCreate(), result.getGmtCreate());
        Assert.assertEquals(capacity.getGmtModified(), result.getGmtModified());
    }
    
    @Test
    public void testUpdateCapacity1x() throws Exception {
    
        when(capacityService.insertOrUpdateCapacity("test", "test", 1, 1, 1, 1)).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CAPACITY_CONTROLLER_PATH)
                .param("group", "test").param("tenant", "test")
                .param("quota", "1").param("maxSize", "1")
                .param("maxAggrCount", "1").param("maxAggrSize", "1");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        Assert.assertEquals("200", code);
        Assert.assertEquals("true", data);
    }
    
    @Test
    public void testUpdateCapacity2x() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CAPACITY_CONTROLLER_PATH);
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        Assert.assertEquals("400", code);
        Assert.assertEquals("false", data);
    }
    
    @Test
    public void testUpdateCapacity3x() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CAPACITY_CONTROLLER_PATH)
                .param("group", "test").param("tenant", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        Assert.assertEquals("400", code);
        Assert.assertEquals("false", data);
    }
    
    @Test
    public void testUpdateCapacity4x() throws Exception {
        
        when(capacityService.insertOrUpdateCapacity("test", "test", 1, 1, 1, 1)).thenReturn(false);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CAPACITY_CONTROLLER_PATH)
                .param("group", "test").param("tenant", "test")
                .param("quota", "1").param("maxSize", "1")
                .param("maxAggrCount", "1").param("maxAggrSize", "1");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        Assert.assertEquals("500", code);
        Assert.assertEquals("false", data);
    }
    
}
