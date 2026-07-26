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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.service.ConfigFuzzyWatchContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;

import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_CANCEL_WATCH;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_WATCH;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigFuzzyWatchRequestHandlerTest {
    
    @Mock
    private ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    private ConfigFuzzyWatchRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new ConfigFuzzyWatchRequestHandler(
            configFuzzyWatchContextService);
    }
    
    @Test
    void testHandleWatch() throws NacosException {
        ConfigFuzzyWatchRequest request = new ConfigFuzzyWatchRequest();
        request.setWatchType(WATCH_TYPE_WATCH);
        request.setGroupKeyPattern("test*");
        request.setReceivedGroupKeys(new HashSet<>());
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("conn1");
        when(configFuzzyWatchContextService.reachToUpLimit("test*"))
            .thenReturn(false);
        ConfigFuzzyWatchResponse response = handler.handle(request, meta);
        assertNotNull(response);
        verify(configFuzzyWatchContextService)
            .addFuzzyWatch("test*", "conn1");
    }
    
    @Test
    void testHandleWatchReachLimit() throws NacosException {
        ConfigFuzzyWatchRequest request = new ConfigFuzzyWatchRequest();
        request.setWatchType(WATCH_TYPE_WATCH);
        request.setGroupKeyPattern("test*");
        request.setReceivedGroupKeys(new HashSet<>());
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("conn1");
        when(configFuzzyWatchContextService.reachToUpLimit("test*"))
            .thenReturn(true);
        ConfigFuzzyWatchResponse response = handler.handle(request, meta);
        assertNotNull(response);
    }
    
    @Test
    void testHandleWatchException() throws NacosException {
        ConfigFuzzyWatchRequest request = new ConfigFuzzyWatchRequest();
        request.setWatchType(WATCH_TYPE_WATCH);
        request.setGroupKeyPattern("test*");
        request.setReceivedGroupKeys(new HashSet<>());
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("conn1");
        doThrow(new NacosException(500, "error"))
            .when(configFuzzyWatchContextService)
            .addFuzzyWatch(anyString(), anyString());
        ConfigFuzzyWatchResponse response = handler.handle(request, meta);
        assertNotNull(response);
    }
    
    @Test
    void testHandleCancelWatch() throws NacosException {
        ConfigFuzzyWatchRequest request = new ConfigFuzzyWatchRequest();
        request.setWatchType(WATCH_TYPE_CANCEL_WATCH);
        request.setGroupKeyPattern("test*");
        RequestMeta meta = new RequestMeta();
        meta.setConnectionId("conn1");
        ConfigFuzzyWatchResponse response = handler.handle(request, meta);
        assertNotNull(response);
        verify(configFuzzyWatchContextService)
            .removeFuzzyListen("test*", "conn1");
    }
}
