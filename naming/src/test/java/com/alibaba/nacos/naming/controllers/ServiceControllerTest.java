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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.SubscribeManager;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class ServiceControllerTest extends BaseTest {
    
    @InjectMocks
    private ServiceController serviceController;
    
    @Mock
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    @Mock
    private SubscribeManager subscribeManager;
    
    @Before
    public void before() {
        super.before();
    }
    
    @Test
    public void testList() throws Exception {
        
        Mockito.when(serviceOperatorV2.listService(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Collections.singletonList("DEFAULT_GROUP@@providers:com.alibaba.nacos.controller.test:1"));
        
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter("pageNo", "1");
        servletRequest.addParameter("pageSize", "10");
        
        ObjectNode objectNode = serviceController.list(servletRequest);
        Assert.assertEquals(1, objectNode.get("count").asInt());
    }
    
    @Test
    public void testCreate() {
        try {
            String res = serviceController.create(TEST_NAMESPACE, TEST_SERVICE_NAME, 0, "", "");
            Assert.assertEquals("ok", res);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testRemove() {
        try {
            String res = serviceController.remove(TEST_NAMESPACE, TEST_SERVICE_NAME);
            Assert.assertEquals("ok", res);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testDetail() {
        try {
            ObjectNode result = Mockito.mock(ObjectNode.class);
            Mockito.when(serviceOperatorV2.queryService(Mockito.anyString(), Mockito.anyString())).thenReturn(result);
            
            ObjectNode objectNode = serviceController.detail(TEST_NAMESPACE, TEST_SERVICE_NAME);
            Assert.assertEquals(result, objectNode);
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testUpdate() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        servletRequest.addParameter("protectThreshold", "0.01");
        try {
            String res = serviceController.update(servletRequest);
            Assert.assertEquals("ok", res);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testSearchService() {
        try {
            Mockito.when(
                    serviceOperatorV2.searchServiceName(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Collections.singletonList("result"));
            
            ObjectNode objectNode = serviceController.searchService(TEST_NAMESPACE, "");
            Assert.assertEquals(1, objectNode.get("count").asInt());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        
        try {
            Mockito.when(
                    serviceOperatorV2.searchServiceName(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Arrays.asList("re1", "re2"));
            Mockito.when(serviceOperatorV2.listAllNamespace()).thenReturn(Arrays.asList("re1", "re2"));
            
            ObjectNode objectNode = serviceController.searchService(null, "");
            Assert.assertEquals(4, objectNode.get("count").asInt());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testSubscribers() {
        Mockito.when(subscribeManager.getSubscribers(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(Collections.singletonList(Mockito.mock(Subscriber.class)));
        
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        
        ObjectNode objectNode = serviceController.subscribers(servletRequest);
        Assert.assertEquals(1, objectNode.get("count").asInt());
    }
}
