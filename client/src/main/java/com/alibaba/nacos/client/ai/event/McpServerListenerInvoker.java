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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.client.selector.ListenerInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos AI module mcp server listener invoker.
 *
 * @author xiweng.yy
 */
public class McpServerListenerInvoker implements ListenerInvoker<NacosMcpServerEvent> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServerListenerInvoker.class);
    
    private final AbstractNacosMcpServerListener listener;
    
    private final AtomicBoolean invoked = new AtomicBoolean(false);
    
    public McpServerListenerInvoker(AbstractNacosMcpServerListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void invoke(NacosMcpServerEvent event) {
        invoked.set(true);
        logInvoke(event);
        if (listener.getExecutor() != null) {
            listener.getExecutor().execute(() -> listener.onEvent(event));
        } else {
            listener.onEvent(event);
        }
    }
    
    private void logInvoke(NacosMcpServerEvent event) {
        LOGGER.info("Invoke event namespaceId: {}, mcpId: {}, mcpName: {} to Listener: {}", event.getNamespaceId(),
                event.getMcpId(), event.getMcpName(), listener.toString());
    }
    
    @Override
    public boolean isInvoked() {
        return invoked.get();
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        if (this == o) {
            return true;
        }
        
        McpServerListenerInvoker that = (McpServerListenerInvoker) o;
        return Objects.equals(listener, that.listener);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(listener);
    }
}
