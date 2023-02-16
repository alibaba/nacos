/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * {@link InstanceRequestHandler} unit tests.
 *
 * @author chenglu
 * @date 2021-09-17 12:49
 */
@RunWith(MockitoJUnitRunner.class)
public class InstanceRequestHandlerTest {
    
    @InjectMocks
    private InstanceRequestHandler instanceRequestHandler;
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Test
    public void testHandle() throws NacosException {
        InstanceRequest instanceRequest = new InstanceRequest();
        instanceRequest.setType(NamingRemoteConstants.REGISTER_INSTANCE);
        Instance instance = new Instance();
        instanceRequest.setInstance(instance);
        RequestMeta requestMeta = new RequestMeta();
        instanceRequestHandler.handle(instanceRequest, requestMeta);
        Mockito.verify(clientOperationService).registerInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
    
        instanceRequest.setType(NamingRemoteConstants.DE_REGISTER_INSTANCE);
        instanceRequestHandler.handle(instanceRequest, requestMeta);
        Mockito.verify(clientOperationService).deregisterInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
        
        instanceRequest.setType("xxx");
        try {
            instanceRequestHandler.handle(instanceRequest, requestMeta);
        } catch (Exception e) {
            Assert.assertEquals(((NacosException) e).getErrCode(), NacosException.INVALID_PARAM);
        }
    }
}
