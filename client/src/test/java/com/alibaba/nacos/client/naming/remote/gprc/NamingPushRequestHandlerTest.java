/*
 *
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.response.NotifySubscriberResponse;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.remote.TestConnection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NamingPushRequestHandlerTest {
    
    @Test
    void testRequestReply() {
        //given
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingPushRequestHandler handler = new NamingPushRequestHandler(holder);
        ServiceInfo info = new ServiceInfo("name", "cluster1");
        Request req = NotifySubscriberRequest.buildNotifySubscriberRequest(info);
        //when
        Response response = handler.requestReply(req, new TestConnection(new RpcClient.ServerInfo()));
        //then
        assertTrue(response instanceof NotifySubscriberResponse);
        verify(holder, times(1)).processServiceInfo(info);
    }
    
    @Test
    void testRequestReplyOtherType() {
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingPushRequestHandler handler = new NamingPushRequestHandler(holder);
        assertNull(handler.requestReply(new HealthCheckRequest(), new TestConnection(new RpcClient.ServerInfo())));
    }
}