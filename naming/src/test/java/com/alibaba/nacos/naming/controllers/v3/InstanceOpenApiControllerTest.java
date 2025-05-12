/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.NamingResponseCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.InstanceListForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceOpenApiControllerTest {
    
    @Mock
    private InstanceOperator instanceOperator;
    
    @Mock
    private SwitchDomain switchDomain;
    
    private InstanceOpenApiController instanceOpenApiController;
    
    @BeforeEach
    void setUp() {
        instanceOpenApiController = new InstanceOpenApiController(instanceOperator, switchDomain);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void testRegister() throws NacosException {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setServiceName("test");
        instanceForm.setIp("1.1.1.1");
        instanceForm.setPort(80);
        Result<String> actual = instanceOpenApiController.register(instanceForm, false);
        verify(instanceOperator).registerInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(Constants.DEFAULT_GROUP),
                eq("test"), any(Instance.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testRegisterWithHeartBeat() throws NacosException {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setServiceName("test");
        instanceForm.setIp("1.1.1.1");
        instanceForm.setPort(80);
        when(instanceOperator.handleBeat(eq(Constants.DEFAULT_NAMESPACE_ID), eq(Constants.DEFAULT_GROUP), eq("test"),
                eq("1.1.1.1"), eq(80), eq(Constants.DEFAULT_CLUSTER_NAME), any(), any())).thenReturn(
                NamingResponseCode.RESOURCE_NOT_FOUND, NamingResponseCode.OK);
        Result<String> actual = instanceOpenApiController.register(instanceForm, true);
        assertEquals(ErrorCode.INSTANCE_NOT_FOUND.getCode(), actual.getCode());
        assertEquals(ErrorCode.INSTANCE_NOT_FOUND.getMsg(), actual.getMessage());
        actual = instanceOpenApiController.register(instanceForm, true);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testDeregister() throws NacosException {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setServiceName("test");
        instanceForm.setIp("1.1.1.1");
        instanceForm.setPort(80);
        Result<String> actual = instanceOpenApiController.deregister(instanceForm);
        verify(instanceOperator).removeInstance(eq(Constants.DEFAULT_NAMESPACE_ID), eq(Constants.DEFAULT_GROUP),
                eq("test"), any(Instance.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testList() throws Exception {
        InstanceListForm instanceForm = new InstanceListForm();
        instanceForm.setServiceName("test");
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName("test");
        serviceInfo.setHosts(Collections.singletonList(instance));
        when(instanceOperator.listInstance(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "test", null,
                Constants.DEFAULT_CLUSTER_NAME, false)).thenReturn(serviceInfo);
        Result<List<Instance>> actual = instanceOpenApiController.list(instanceForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals(1, actual.getData().size());
        assertEquals("1.1.1.1", actual.getData().get(0).getIp());
    }
}