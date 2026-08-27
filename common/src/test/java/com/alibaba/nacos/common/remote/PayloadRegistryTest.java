/*
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
 */

package com.alibaba.nacos.common.remote;

import com.alibaba.nacos.api.ai.remote.request.AgentPublishRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentUnsubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryNotifyResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentPublishRpcResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentUnsubscribeRpcResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayloadRegistryTest {
    
    @BeforeAll
    static void setUpBefore() {
        PayloadRegistry.init();
    }
    
    @Test
    void testRegisterInvalidClass() {
        PayloadRegistry.register("test", Request.class);
        assertNull(PayloadRegistry.getClassByType("test"));
    }
    
    @Test
    void testRegisterDuplicated() {
        assertThrows(RuntimeException.class, () -> {
            PayloadRegistry.register("ErrorResponse", ErrorResponse.class);
        });
    }
    
    @Test
    void testAgentPublishPayloadsRegistered() {
        assertSame(AgentPublishRpcRequest.class,
            PayloadRegistry.getClassByType("AgentPublishRpcRequest"));
        assertSame(AgentPublishRpcResponse.class,
            PayloadRegistry.getClassByType("AgentPublishRpcResponse"));
    }
    
    @Test
    void testAgentWatchPayloadsRegistered() {
        assertSame(AgentSubscribeRpcRequest.class,
            PayloadRegistry.getClassByType("AgentSubscribeRpcRequest"));
        assertSame(AgentSubscribeRpcResponse.class,
            PayloadRegistry.getClassByType("AgentSubscribeRpcResponse"));
        assertSame(AgentUnsubscribeRpcRequest.class,
            PayloadRegistry.getClassByType("AgentUnsubscribeRpcRequest"));
        assertSame(AgentUnsubscribeRpcResponse.class,
            PayloadRegistry.getClassByType("AgentUnsubscribeRpcResponse"));
        assertSame(AgentDiscoveryNotifyRequest.class,
            PayloadRegistry.getClassByType("AgentDiscoveryNotifyRequest"));
        assertSame(AgentDiscoveryNotifyResponse.class,
            PayloadRegistry.getClassByType("AgentDiscoveryNotifyResponse"));
    }
}
