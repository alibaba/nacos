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
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BatchInstanceRequestHandler} unit tests.
 *
 * @author chenhao26
 * @date 2022-07-07
 */
@RunWith(MockitoJUnitRunner.class)
public class BatchInstanceRequestHandlerTest {
    
    @InjectMocks
    private BatchInstanceRequestHandler batchInstanceRequestHandler;
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Test
    public void testHandle() throws NacosException {
        BatchInstanceRequest batchInstanceRequest = new BatchInstanceRequest();
        batchInstanceRequest.setType(NamingRemoteConstants.BATCH_REGISTER_INSTANCE);
        batchInstanceRequest.setServiceName("service1");
        batchInstanceRequest.setGroupName("group1");
        List<Instance> instanceList = new ArrayList<>();
        Instance instance = new Instance();
        instanceList.add(instance);
        batchInstanceRequest.setInstances(instanceList);
        RequestMeta requestMeta = new RequestMeta();
        batchInstanceRequestHandler.handle(batchInstanceRequest, requestMeta);
        Mockito.verify(clientOperationService).batchRegisterInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
        batchInstanceRequest.setType("google");
        try {
            batchInstanceRequestHandler.handle(batchInstanceRequest, requestMeta);
        } catch (Exception e) {
            Assert.assertEquals(((NacosException) e).getErrCode(), NacosException.INVALID_PARAM);
        }
    }
}
