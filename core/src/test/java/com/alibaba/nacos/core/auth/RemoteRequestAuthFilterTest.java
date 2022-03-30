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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.core.remote.RequestHandler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * {@link RemoteRequestAuthFilter} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 16:14
 */
@RunWith(MockitoJUnitRunner.class)
public class RemoteRequestAuthFilterTest {
    
    @InjectMocks
    private RemoteRequestAuthFilter remoteRequestAuthFilter;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Test
    public void testFilter() {
        Mockito.when(authConfigs.isAuthEnabled()).thenReturn(true);
        
        Request healthCheckRequest = new HealthCheckRequest();
        
        try {
            Response healthCheckResponse = remoteRequestAuthFilter
                    .filter(healthCheckRequest, new RequestMeta(), MockRequestHandler.class);
            Assert.assertNull(healthCheckResponse);
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    class MockRequestHandler extends RequestHandler {
        
        @Secured(resource = "xxx")
        @Override
        public Response handle(Request request, RequestMeta meta) throws NacosException {
            return null;
        }
    }
}
