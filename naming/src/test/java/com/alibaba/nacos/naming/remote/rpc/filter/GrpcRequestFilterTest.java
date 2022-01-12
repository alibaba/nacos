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

package com.alibaba.nacos.naming.remote.rpc.filter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.remote.rpc.handler.InstanceRequestHandler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * {@link GrpcRequestFilter} unit test.
 *
 * @author chenglu
 * @date 2021-09-17 16:25
 */
@RunWith(MockitoJUnitRunner.class)
public class GrpcRequestFilterTest {
    
    @InjectMocks
    private GrpcRequestFilter grpcRequestFilter;
    
    @Mock
    private UpgradeJudgement upgradeJudgement;
    
    @Test
    public void testFilter() throws NacosException {
        Mockito.when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(true).thenReturn(false);
        Response response = grpcRequestFilter.filter(new InstanceRequest(), new RequestMeta(), InstanceRequestHandler.class);
        Assert.assertNull(response);
        
        try {
            grpcRequestFilter.filter(new InstanceRequest(), new RequestMeta(), InstanceRequestHandler.class);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NacosException);
        }
    }
}
