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
import com.alibaba.nacos.api.naming.remote.request.PersistentInstanceRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PersistentInstanceRequestHandler} unit tests.
 *
 * @author blake.qiu
 */
@ExtendWith(MockitoExtension.class)
class PersistentInstanceRequestHandlerTest {
    
    @InjectMocks
    private PersistentInstanceRequestHandler persistentInstanceRequestHandler;
    
    @Mock
    private PersistentClientOperationServiceImpl clientOperationService;
    
    @Test
    void testHandle() throws NacosException {
        PersistentInstanceRequest instanceRequest = new PersistentInstanceRequest();
        instanceRequest.setType(NamingRemoteConstants.REGISTER_INSTANCE);
        instanceRequest.setServiceName("service1");
        instanceRequest.setGroupName("group1");
        Instance instance = new Instance();
        instanceRequest.setInstance(instance);
        RequestMeta requestMeta = new RequestMeta();
        persistentInstanceRequestHandler.handle(instanceRequest, requestMeta);
        Mockito.verify(clientOperationService).registerInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
        
        instanceRequest.setType(NamingRemoteConstants.DE_REGISTER_INSTANCE);
        persistentInstanceRequestHandler.handle(instanceRequest, requestMeta);
        Mockito.verify(clientOperationService).deregisterInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
        
        instanceRequest.setType("xxx");
        try {
            persistentInstanceRequestHandler.handle(instanceRequest, requestMeta);
        } catch (Exception e) {
            assertEquals(NacosException.INVALID_PARAM, ((NacosException) e).getErrCode());
        }
    }
}
