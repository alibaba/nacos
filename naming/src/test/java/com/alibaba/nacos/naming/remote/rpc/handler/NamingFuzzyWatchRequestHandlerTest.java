/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.index.NamingFuzzyWatchContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_CANCEL_WATCH;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_WATCH;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class NamingFuzzyWatchRequestHandlerTest {
    
    private static final String GROUP_KEY_PATTERN = "namespace@@group@@service*";
    
    private static final String CONNECTION_ID = "connection-1";
    
    @Mock
    private NamingFuzzyWatchContextService namingFuzzyWatchContextService;
    
    private NamingFuzzyWatchRequestHandler handler;
    
    private RequestMeta requestMeta;
    
    @BeforeEach
    void setUp() {
        handler = new NamingFuzzyWatchRequestHandler(namingFuzzyWatchContextService);
        requestMeta = new RequestMeta();
        requestMeta.setConnectionId(CONNECTION_ID);
    }
    
    @AfterEach
    void tearDown() {
        NotifyCenter.deregisterPublisher(ClientOperationEvent.ClientFuzzyWatchEvent.class);
    }
    
    @Test
    void testHandleWatchReturnsSuccess() throws Exception {
        NamingFuzzyWatchRequest request = buildRequest(WATCH_TYPE_WATCH);
        Mockito.when(namingFuzzyWatchContextService.reachToUpLimit(GROUP_KEY_PATTERN))
            .thenReturn(false);
        
        NamingFuzzyWatchResponse response = handler.handle(request, requestMeta);
        
        assertTrue(response.isSuccess());
        Mockito.verify(namingFuzzyWatchContextService)
            .syncFuzzyWatcherContext(GROUP_KEY_PATTERN, CONNECTION_ID);
        Mockito.verify(namingFuzzyWatchContextService).reachToUpLimit(GROUP_KEY_PATTERN);
    }
    
    @Test
    void testHandleWatchReturnsServiceErrorWhenSyncContextFails() throws Exception {
        NamingFuzzyWatchRequest request = buildRequest(WATCH_TYPE_WATCH);
        Mockito.doThrow(new NacosException(100, "sync failed"))
            .when(namingFuzzyWatchContextService)
            .syncFuzzyWatcherContext(GROUP_KEY_PATTERN, CONNECTION_ID);
        
        NamingFuzzyWatchResponse response = handler.handle(request, requestMeta);
        
        assertFalse(response.isSuccess());
        assertEquals(100, response.getErrorCode());
        assertEquals("sync failed", response.getMessage());
        Mockito.verify(namingFuzzyWatchContextService, Mockito.never())
            .reachToUpLimit(GROUP_KEY_PATTERN);
    }
    
    @Test
    void testHandleWatchReturnsLimitErrorWhenReachUpperLimit() throws Exception {
        NamingFuzzyWatchRequest request = buildRequest(WATCH_TYPE_WATCH);
        Mockito.when(namingFuzzyWatchContextService.reachToUpLimit(GROUP_KEY_PATTERN))
            .thenReturn(true);
        
        NamingFuzzyWatchResponse response = handler.handle(request, requestMeta);
        
        assertFalse(response.isSuccess());
        assertEquals(FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(),
            response.getErrorCode());
        assertEquals(FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getMsg(),
            response.getMessage());
    }
    
    @Test
    void testHandleCancelWatchReturnsSuccess() throws Exception {
        NamingFuzzyWatchRequest request = buildRequest(WATCH_TYPE_CANCEL_WATCH);
        
        NamingFuzzyWatchResponse response = handler.handle(request, requestMeta);
        
        assertTrue(response.isSuccess());
        Mockito.verify(namingFuzzyWatchContextService)
            .removeFuzzyWatchContext(GROUP_KEY_PATTERN, CONNECTION_ID);
    }
    
    @Test
    void testHandleUnsupportedWatchTypeThrowsException() {
        NamingFuzzyWatchRequest request = buildRequest("unknown");
        
        NacosException exception =
            assertThrows(NacosException.class, () -> handler.handle(request, requestMeta));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals("Unsupported request type unknown", exception.getErrMsg());
    }
    
    private NamingFuzzyWatchRequest buildRequest(String watchType) {
        NamingFuzzyWatchRequest result =
            new NamingFuzzyWatchRequest(GROUP_KEY_PATTERN, watchType);
        result.setInitializing(true);
        result.setReceivedGroupKeys(Collections.singleton("namespace@@group@@service"));
        return result;
    }
}
