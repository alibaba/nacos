/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.HealthCheckRequestHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

@RunWith(SpringJUnit4ClassRunner.class)
public class RemoteParamCheckFilterTest {
    
    private static RemoteParamCheckFilter remoteParamCheckFilter;
    
    private static MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeClass
    public static void init() {
        envUtilMockedStatic = mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(
                () -> EnvUtil.getProperty("nacos.core.param.check.enabled", Boolean.class, true))
                .thenReturn(Boolean.TRUE);
        envUtilMockedStatic.when(
                () -> EnvUtil.getProperty("nacos.core.param.check.checker", String.class, "default")
        ).thenReturn("default");
        remoteParamCheckFilter = new RemoteParamCheckFilter();
        
    }
    
    @AfterClass
    public static void close() {
        envUtilMockedStatic.close();
    }
    
    @Test
    public void filter() {
        Instance instance = new Instance();
        instance.setIp("11.11.11.11");
        instance.setPort(-1);
        instance.setServiceName("test");
        InstanceRequest instanceRequest = new InstanceRequest();
        instanceRequest.setInstance(instance);
        instanceRequest.setNamespace("public");
        instanceRequest.setServiceName("test");
        Response response = null;
        try {
            response = remoteParamCheckFilter.filter(instanceRequest, new RequestMeta(), HealthCheckRequestHandler.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(response.getMessage(), "Param check invalid:Param 'port' is illegal, the value should be between 0 and 65535.");
        
        BatchInstanceRequest batchInstanceRequest = new BatchInstanceRequest();
        batchInstanceRequest.setServiceName("test@@@@");
        try {
            response = remoteParamCheckFilter.filter(batchInstanceRequest, new RequestMeta(), HealthCheckRequestHandler.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(response.getMessage(), "Param check invalid:Param 'serviceName' is illegal, illegal characters should not appear in the param.");
    }
}