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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.InstancePatchObject;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstanceControllerTest extends BaseTest {
    
    @Mock
    private InstanceOperatorClientImpl instanceServiceV2;
    
    @Mock
    private HttpServletRequest request;
    
    private SmartSubscriber subscriber;
    
    private volatile boolean eventReceived = false;
    
    @InjectMocks
    private InstanceController instanceController;
    
    @Before
    public void before() {
        super.before();
        when(switchDomain.isDefaultInstanceEphemeral()).thenReturn(true);
        subscriber = new SmartSubscriber() {
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                List<Class<? extends Event>> result = new LinkedList<>();
                result.add(RegisterInstanceTraceEvent.class);
                result.add(DeregisterInstanceTraceEvent.class);
                return result;
            }
            
            @Override
            public void onEvent(Event event) {
                eventReceived = true;
            }
        };
        NotifyCenter.registerSubscriber(subscriber);
        mockRequestParameter(CommonParams.SERVICE_NAME, TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME);
        mockRequestParameter("ip", "1.1.1.1");
        mockRequestParameter("port", "3306");
    }
    
    @After
    public void tearDown() throws Exception {
        NotifyCenter.deregisterSubscriber(subscriber);
        NotifyCenter.deregisterPublisher(RegisterInstanceTraceEvent.class);
        NotifyCenter.deregisterPublisher(DeregisterInstanceTraceEvent.class);
        eventReceived = false;
    }
    
    private void mockRequestParameter(String key, String value) {
        when(request.getParameter(key)).thenReturn(value);
    }
    
    @Test
    public void testRegister() throws Exception {
        assertEquals("ok", instanceController.register(request));
        verify(instanceServiceV2)
                .registerInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME),
                        any(Instance.class));
        TimeUnit.SECONDS.sleep(1);
        assertTrue(eventReceived);
    }
    
    @Test
    public void testDeregister() throws Exception {
        assertEquals("ok", instanceController.deregister(request));
        verify(instanceServiceV2)
                .removeInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME),
                        any(Instance.class));
        TimeUnit.SECONDS.sleep(1);
        assertTrue(eventReceived);
    }
    
    @Test
    public void testUpdate() throws Exception {
        assertEquals("ok", instanceController.update(request));
        verify(instanceServiceV2)
                .updateInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME),
                        any(Instance.class));
    }
    
    @Test
    public void testBatchUpdateInstanceMetadata() throws Exception {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(3306);
        List<Instance> mockInstance = Collections.singletonList(instance);
        String instanceJson = JacksonUtils.toJson(mockInstance);
        mockRequestParameter("instances", instanceJson);
        mockRequestParameter("metadata", "{}");
        when(instanceServiceV2.batchUpdateMetadata(eq(Constants.DEFAULT_NAMESPACE_ID), any(), anyMap()))
                .thenReturn(Collections.singletonList("1.1.1.1:3306:unknown:DEFAULT:ephemeral"));
        ObjectNode actual = instanceController.batchUpdateInstanceMetadata(request);
        assertEquals("1.1.1.1:3306:unknown:DEFAULT:ephemeral", actual.get("updated").get(0).textValue());
    }
    
    @Test
    public void testBatchDeleteInstanceMetadata() throws Exception {
        mockRequestParameter("metadata", "{}");
        when(instanceServiceV2.batchDeleteMetadata(eq(Constants.DEFAULT_NAMESPACE_ID), any(), anyMap()))
                .thenReturn(Collections.singletonList("1.1.1.1:3306:unknown:DEFAULT:ephemeral"));
        ObjectNode actual = instanceController.batchDeleteInstanceMetadata(request);
        assertEquals("1.1.1.1:3306:unknown:DEFAULT:ephemeral", actual.get("updated").get(0).textValue());
    }
    
    @Test
    public void testPatch() throws Exception {
        mockRequestParameter("metadata", "{}");
        mockRequestParameter("app", "test");
        mockRequestParameter("weight", "10");
        mockRequestParameter("healthy", "false");
        mockRequestParameter("enabled", "false");
        assertEquals("ok", instanceController.patch(request));
        verify(instanceServiceV2)
                .patchInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME),
                        any(InstancePatchObject.class));
    }
    
    @Test
    public void testList() throws Exception {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(3306);
        ServiceInfo expected = new ServiceInfo();
        expected.setHosts(Collections.singletonList(instance));
        when(instanceServiceV2
                .listInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME),
                        any(Subscriber.class), eq(StringUtils.EMPTY), eq(false))).thenReturn(expected);
        assertEquals(expected, instanceController.list(request));
    }
    
    @Test
    public void testDetail() throws Exception {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(3306);
        instance.setInstanceId("testId");
        instance.setClusterName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        when(instanceServiceV2.getInstance(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME,
                UtilsAndCommons.DEFAULT_CLUSTER_NAME, "1.1.1.1", 3306)).thenReturn(instance);
        ObjectNode actual = instanceController.detail(request);
        assertEquals(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME, actual.get("service").textValue());
        assertEquals("1.1.1.1", actual.get("ip").textValue());
        assertEquals(3306, actual.get("port").intValue());
        assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, actual.get("clusterName").textValue());
        assertEquals(1.0D, actual.get("weight").doubleValue(), 0.1);
        assertEquals(true, actual.get("healthy").booleanValue());
        assertEquals("testId", actual.get("instanceId").textValue());
        assertEquals("{}", actual.get("metadata").toString());
    }
    
    @Test
    public void testBeat() throws Exception {
        when(instanceServiceV2
                .handleBeat(eq(Constants.DEFAULT_NAMESPACE_ID), eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME),
                        eq("1.1.1.1"), eq(3306), eq(UtilsAndCommons.DEFAULT_CLUSTER_NAME), any(), any()))
                .thenReturn(200);
        when(instanceServiceV2.getHeartBeatInterval(eq(Constants.DEFAULT_NAMESPACE_ID),
                eq(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME), eq("1.1.1.1"), eq(3306),
                eq(UtilsAndCommons.DEFAULT_CLUSTER_NAME))).thenReturn(10000L);
        ObjectNode actual = instanceController.beat(request);
        assertEquals(200, actual.get("code").intValue());
        assertEquals(10000L, actual.get("clientBeatInterval").longValue());
        assertTrue(actual.get("lightBeatEnabled").booleanValue());
    }
}
