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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.event.naming.UpdateInstanceTraceEvent;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.InstanceMetadataBatchOperationForm;
import com.alibaba.nacos.naming.model.vo.InstanceDetailInfoVo;
import com.alibaba.nacos.naming.model.vo.InstanceMetadataBatchOperationVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceControllerV2Test extends BaseTest {
    
    @InjectMocks
    private InstanceControllerV2 instanceControllerV2;
    
    @Mock
    private InstanceOperatorClientImpl instanceServiceV2;
    
    private MockMvc mockmvc;
    
    private SmartSubscriber subscriber;
    
    private volatile Class<? extends Event> eventReceivedClass;
    
    @BeforeEach
    public void before() {
        super.before();
        ReflectionTestUtils.setField(instanceControllerV2, "instanceServiceV2", instanceServiceV2);
        mockmvc = MockMvcBuilders.standaloneSetup(instanceControllerV2).build();
        
        subscriber = new SmartSubscriber() {
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                List<Class<? extends Event>> result = new LinkedList<>();
                result.add(UpdateInstanceTraceEvent.class);
                return result;
            }
            
            @Override
            public void onEvent(Event event) {
                eventReceivedClass = event.getClass();
            }
        };
        NotifyCenter.registerSubscriber(subscriber);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        NotifyCenter.deregisterSubscriber(subscriber);
        NotifyCenter.deregisterPublisher(UpdateInstanceTraceEvent.class);
        eventReceivedClass = null;
    }
    
    @Test
    void registerInstance() throws Exception {
        
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setNamespaceId(TEST_NAMESPACE);
        instanceForm.setGroupName("DEFAULT_GROUP");
        instanceForm.setServiceName("test-service");
        instanceForm.setIp(TEST_IP);
        instanceForm.setClusterName(TEST_CLUSTER_NAME);
        instanceForm.setPort(9999);
        instanceForm.setHealthy(true);
        instanceForm.setWeight(1.0);
        instanceForm.setEnabled(true);
        instanceForm.setMetadata(TEST_METADATA);
        instanceForm.setEphemeral(true);
        
        Result<String> result = instanceControllerV2.register(instanceForm);
        
        verify(instanceServiceV2).registerInstance(eq(TEST_NAMESPACE), eq(TEST_SERVICE_NAME), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void deregisterInstance() throws Exception {
        
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setNamespaceId(TEST_NAMESPACE);
        instanceForm.setGroupName("DEFAULT_GROUP");
        instanceForm.setServiceName("test-service");
        instanceForm.setIp(TEST_IP);
        instanceForm.setClusterName(TEST_CLUSTER_NAME);
        instanceForm.setPort(9999);
        instanceForm.setHealthy(true);
        instanceForm.setWeight(1.0);
        instanceForm.setEnabled(true);
        instanceForm.setMetadata(TEST_METADATA);
        instanceForm.setEphemeral(true);
        
        Result<String> result = instanceControllerV2.deregister(instanceForm);
        
        verify(instanceServiceV2).removeInstance(eq(TEST_NAMESPACE), eq(TEST_SERVICE_NAME), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        
    }
    
    @Test
    void updateInstance() throws Exception {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setNamespaceId(TEST_NAMESPACE);
        instanceForm.setGroupName("DEFAULT_GROUP");
        instanceForm.setServiceName("test-service");
        instanceForm.setIp(TEST_IP);
        instanceForm.setClusterName(TEST_CLUSTER_NAME);
        instanceForm.setPort(9999);
        instanceForm.setHealthy(true);
        instanceForm.setWeight(1.0);
        instanceForm.setEnabled(true);
        instanceForm.setMetadata(TEST_METADATA);
        instanceForm.setEphemeral(true);
        
        Result<String> result = instanceControllerV2.update(instanceForm);
        
        verify(instanceServiceV2).updateInstance(eq(TEST_NAMESPACE), eq(TEST_SERVICE_NAME), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        TimeUnit.SECONDS.sleep(1);
        assertEquals(UpdateInstanceTraceEvent.class, eventReceivedClass);
    }
    
    @Test
    void batchUpdateInstanceMetadata() throws Exception {
        
        InstanceMetadataBatchOperationForm form = new InstanceMetadataBatchOperationForm();
        form.setNamespaceId(TEST_NAMESPACE);
        form.setGroupName("DEFAULT");
        form.setServiceName("test-service");
        form.setConsistencyType("ephemeral");
        form.setInstances(TEST_INSTANCE_INFO_LIST);
        form.setMetadata(TEST_METADATA);
        
        ArrayList<String> ipList = new ArrayList<>();
        ipList.add(TEST_IP);
        when(instanceServiceV2.batchUpdateMetadata(eq(TEST_NAMESPACE), any(), any())).thenReturn(ipList);
        
        InstanceMetadataBatchOperationVo expectUpdate = new InstanceMetadataBatchOperationVo(ipList);
        
        Result<InstanceMetadataBatchOperationVo> result = instanceControllerV2.batchUpdateInstanceMetadata(form);
        verify(instanceServiceV2).batchUpdateMetadata(eq(TEST_NAMESPACE), any(), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(expectUpdate.getUpdated().size(), result.getData().getUpdated().size());
        assertEquals(expectUpdate.getUpdated().get(0), result.getData().getUpdated().get(0));
    }
    
    @Test
    void patch() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.patch(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("cluster", TEST_CLUSTER_NAME).param("port", "9999").param("healthy", "true").param("weight", "2.0")
                .param("enabled", "true").param("metadata", TEST_METADATA).param("ephemeral", "false");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("ok", actualValue);
    }
    
    @Test
    void listInstance() throws Exception {
        
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName("serviceInfo");
        
        when(instanceServiceV2.listInstance(eq(TEST_NAMESPACE), eq(TEST_SERVICE_NAME), any(), eq(TEST_CLUSTER_NAME), eq(false))).thenReturn(
                serviceInfo);
        
        Result<ServiceInfo> result = instanceControllerV2.list(TEST_NAMESPACE, "DEFAULT_GROUP", "test-service", TEST_CLUSTER_NAME, TEST_IP,
                9999, false, "", "", "");
        
        verify(instanceServiceV2).listInstance(eq(TEST_NAMESPACE), eq(TEST_SERVICE_NAME), any(), eq(TEST_CLUSTER_NAME), eq(false));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(serviceInfo.getName(), result.getData().getName());
    }
    
    @Test
    void detail() throws Exception {
        
        Instance instance = new Instance();
        instance.setInstanceId("test-id");
        
        when(instanceServiceV2.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, TEST_IP, 9999)).thenReturn(instance);
        
        Result<InstanceDetailInfoVo> result = instanceControllerV2.detail(TEST_NAMESPACE, "DEFAULT_GROUP", "test-service",
                TEST_CLUSTER_NAME, TEST_IP, 9999);
        
        verify(instanceServiceV2).getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, TEST_IP, 9999);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(instance.getInstanceId(), result.getData().getInstanceId());
    }
    
    @Test
    void beat() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT + "/beat")
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("clusterName", "clusterName").param("port", "0").param("beat", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertNotNull(actualValue);
    }
    
    @Test
    void listWithHealthStatus() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT + "/statuses")
                .param("key", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertNotNull(actualValue);
    }
}
