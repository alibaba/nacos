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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchChangeNotifyResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchSyncResponse;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientFuzzyWatchNotifyRequestHandlerTest {
    
    @Mock
    ConfigFuzzyWatchGroupKeyHolder configFuzzyWatchGroupKeyHolder;
    
    ClientFuzzyWatchNotifyRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new ClientFuzzyWatchNotifyRequestHandler(configFuzzyWatchGroupKeyHolder);
    }
    
    @Test
    void requestReplyWithSyncRequest() {
        ConfigFuzzyWatchSyncRequest request = new ConfigFuzzyWatchSyncRequest();
        ConfigFuzzyWatchSyncResponse response = new ConfigFuzzyWatchSyncResponse();
        when(configFuzzyWatchGroupKeyHolder.handleFuzzyWatchSyncNotifyRequest(request))
            .thenReturn(response);
        Response actual = handler.requestReply(request, null);
        assertSame(response, actual);
    }
    
    @Test
    void requestReplyWithChangeRequest() {
        ConfigFuzzyWatchChangeNotifyRequest request = new ConfigFuzzyWatchChangeNotifyRequest();
        ConfigFuzzyWatchChangeNotifyResponse response = new ConfigFuzzyWatchChangeNotifyResponse();
        when(configFuzzyWatchGroupKeyHolder.handlerFuzzyWatchChangeNotifyRequest(request))
            .thenReturn(response);
        Response actual = handler.requestReply(request, null);
        assertSame(response, actual);
    }
    
    @Test
    void requestReplyWithUnknownRequest() {
        Response response = handler.requestReply(new HealthCheckRequest(), null);
        assertNull(response);
    }
}
