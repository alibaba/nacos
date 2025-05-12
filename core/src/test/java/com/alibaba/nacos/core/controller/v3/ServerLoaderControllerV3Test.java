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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.ServerLoaderMetric;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.service.NacosServerLoaderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ServerLoaderControllerV3} unit test.
 *
 * @author yunye
 * @since 3.0.0-bate
 */
@ExtendWith(MockitoExtension.class)
class ServerLoaderControllerV3Test {
    
    @InjectMocks
    private ServerLoaderControllerV3 serverLoaderControllerV3;
    
    @Mock
    private NacosServerLoaderService serverLoaderService;
    
    @Test
    void testCurrentClients() {
        when(serverLoaderService.getAllClients()).thenReturn(new HashMap<>());
        Result<Map<String, Connection>> result = serverLoaderControllerV3.currentClients();
        assertEquals(0, result.getData().size());
    }
    
    @Test
    void testReloadCount() {
        Result<String> result = serverLoaderControllerV3.reloadCount(1, "1.1.1.1");
        verify(serverLoaderService).reloadCount(1, "1.1.1.1");
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
    }
    
    @Test
    void testSmartReload() throws NacosException {
        when(serverLoaderService.smartReload(1f)).thenReturn(true);
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        Result<String> result = serverLoaderControllerV3.smartReload(httpServletRequest, "1");
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
    }
    
    @Test
    void testReloadSingle() {
        Result<String> result = serverLoaderControllerV3.reloadSingle("111", "1.1.1.1");
        verify(serverLoaderService).reloadClient("111", "1.1.1.1");
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
    }
    
    @Test
    void testLoaderMetrics() throws NacosException {
        ServerLoaderMetric serverLoaderMetric = new ServerLoaderMetric();
        serverLoaderMetric.setCpu("4");
        serverLoaderMetric.setLoad("3");
        serverLoaderMetric.setConCount(2);
        serverLoaderMetric.setSdkConCount(1);
        serverLoaderMetric.setAddress("1.1.1.1:8848");
        ServerLoaderMetrics mock = new ServerLoaderMetrics();
        mock.setDetail(Collections.singletonList(serverLoaderMetric));
        when(serverLoaderService.getServerLoaderMetrics()).thenReturn(mock);
        Result<ServerLoaderMetrics> result = serverLoaderControllerV3.loaderMetrics();
        
        assertEquals(1, result.getData().getDetail().size());
        assertEquals(1, result.getData().getDetail().get(0).getSdkConCount());
        assertEquals(2, result.getData().getDetail().get(0).getConCount());
        assertEquals("3", result.getData().getDetail().get(0).getLoad());
        assertEquals("4", result.getData().getDetail().get(0).getCpu());
        assertEquals("1.1.1.1:8848", result.getData().getDetail().get(0).getAddress());
    }
}
