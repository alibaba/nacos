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

package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.HealthCheckRequestHandler;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class TpsControlRequestFilterTest {
    
    @Mock
    private ControlManagerCenter controlManagerCenter;
    
    @Mock
    private TpsControlManager tpsControlManager;
    
    TpsControlRequestFilter tpsControlRequestFilter;
    
    MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    @Before
    public void before() {
        tpsControlRequestFilter = new TpsControlRequestFilter();
        controlManagerCenterMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        controlManagerCenterMockedStatic.when(() -> ControlManagerCenter.getInstance())
                .thenReturn(controlManagerCenter);
        Mockito.when(controlManagerCenter.getTpsControlManager()).thenReturn(tpsControlManager);
        
    }
    
    @After
    public void after() {
        controlManagerCenterMockedStatic.close();
    }
    
    /**
     * test tps check passed ,response is null.
     */
    @Test
    public void testPass() {
        RemoteTpsCheckRequestParserRegistry.register(new RemoteTpsCheckRequestParser() {
            @Override
            public TpsCheckRequest parse(Request request, RequestMeta meta) {
                return new TpsCheckRequest();
            }
            
            @Override
            public String getPointName() {
                return "HealthCheck";
            }
            
            @Override
            public String getName() {
                return "HealthCheck";
            }
        });
        HealthCheckRequest healthCheckRequest = new HealthCheckRequest();
        RequestMeta requestMeta = new RequestMeta();
        TpsCheckResponse tpsCheckResponse = new TpsCheckResponse(true, 200, "success");
        Mockito.when(tpsControlManager.check(any(TpsCheckRequest.class))).thenReturn(tpsCheckResponse);
        Response filterResponse = tpsControlRequestFilter.filter(healthCheckRequest, requestMeta,
                HealthCheckRequestHandler.class);
        Assert.assertNull(filterResponse);
    }
    
    /**
     * test tps check rejected ,response is not null.
     */
    @Test
    public void testRejected() {
        HealthCheckRequest healthCheckRequest = new HealthCheckRequest();
        RequestMeta requestMeta = new RequestMeta();
        TpsCheckResponse tpsCheckResponse = new TpsCheckResponse(false, 5031, "rejected");
        Mockito.when(tpsControlManager.check(any(TpsCheckRequest.class))).thenReturn(tpsCheckResponse);
        Response filterResponse = tpsControlRequestFilter.filter(healthCheckRequest, requestMeta,
                HealthCheckRequestHandler.class);
        Assert.assertNotNull(filterResponse);
        Assert.assertEquals(NacosException.OVER_THRESHOLD, filterResponse.getErrorCode());
        Assert.assertEquals("Tps Flow restricted:" + tpsCheckResponse.getMessage(), filterResponse.getMessage());
    }
    
    /**
     * test tps check exception ,return null skip.
     */
    @Test
    public void testTpsCheckException() {
        HealthCheckRequest healthCheckRequest = new HealthCheckRequest();
        RequestMeta requestMeta = new RequestMeta();
        Mockito.when(tpsControlManager.check(any(TpsCheckRequest.class))).thenThrow(new NacosRuntimeException(12345));
        Response filterResponse = tpsControlRequestFilter.filter(healthCheckRequest, requestMeta,
                HealthCheckRequestHandler.class);
        Assert.assertNull(filterResponse);
    }
}

