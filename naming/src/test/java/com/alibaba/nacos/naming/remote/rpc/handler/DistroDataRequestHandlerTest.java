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
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.naming.cluster.remote.request.DistroDataRequest;
import com.alibaba.nacos.naming.cluster.remote.response.DistroDataResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static com.alibaba.nacos.consistency.DataOperation.ADD;
import static com.alibaba.nacos.consistency.DataOperation.DELETE;
import static com.alibaba.nacos.consistency.DataOperation.QUERY;
import static com.alibaba.nacos.consistency.DataOperation.SNAPSHOT;
import static com.alibaba.nacos.consistency.DataOperation.VERIFY;

/**
 * {@link DistroDataRequestHandler} unit tests.
 *
 * @author chenglu
 * @date 2021-09-17 20:50
 */
@RunWith(MockitoJUnitRunner.class)
public class DistroDataRequestHandlerTest {
    
    @InjectMocks
    private DistroDataRequestHandler distroDataRequestHandler;
    
    @Mock
    private DistroProtocol distroProtocol;
    
    @Test
    public void testHandle() throws NacosException {
        Mockito.when(distroProtocol.onVerify(Mockito.any(), Mockito.anyString())).thenReturn(false);
        DistroDataRequest distroDataRequest = new DistroDataRequest();
        distroDataRequest.setDataOperation(VERIFY);
        RequestMeta requestMeta = new RequestMeta();
        DistroDataResponse response = distroDataRequestHandler.handle(distroDataRequest, requestMeta);
        Assert.assertEquals(response.getErrorCode(), ResponseCode.FAIL.getCode());
    
        DistroData distroData = new DistroData();
        Mockito.when(distroProtocol.onSnapshot(Mockito.any())).thenReturn(distroData);
        distroDataRequest.setDataOperation(SNAPSHOT);
        DistroDataResponse response1 = distroDataRequestHandler.handle(distroDataRequest, requestMeta);
        Assert.assertEquals(response1.getDistroData(), distroData);
        
        distroDataRequest.setDataOperation(DELETE);
        Mockito.when(distroProtocol.onReceive(Mockito.any())).thenReturn(false);
        DistroDataResponse response2 = distroDataRequestHandler.handle(distroDataRequest, requestMeta);
        Assert.assertEquals(response2.getErrorCode(), ResponseCode.FAIL.getCode());
        
        distroDataRequest.setDataOperation(QUERY);
        Mockito.when(distroProtocol.onQuery(Mockito.any())).thenReturn(distroData);
        distroDataRequest.setDistroData(new DistroData());
        DistroDataResponse response3 = distroDataRequestHandler.handle(distroDataRequest, requestMeta);
        Assert.assertEquals(response3.getDistroData(), distroData);
        
        distroDataRequest.setDataOperation(ADD);
        DistroDataResponse response4 = distroDataRequestHandler.handle(distroDataRequest, requestMeta);
        Assert.assertNull(response4.getDistroData());
    }
}
