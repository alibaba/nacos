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

import com.alibaba.nacos.api.ai.listener.NacosAiEvent;
import com.alibaba.nacos.api.ai.listener.NacosAiListener;
import com.alibaba.nacos.client.selector.ListenerInvoker;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos AI module abstract ai listener invoker.
 *
 * @author xiweng.yy
 */
public abstract class AbstractAiListenerInvoker<E extends NacosAiEvent, L extends NacosAiListener<E>>
        implements ListenerInvoker<E> {
    
    protected final L listener;
    
    private final AtomicBoolean invoked = new AtomicBoolean(false);
    
    public AbstractAiListenerInvoker(L listener) {
        this.listener = listener;
    }
    
    @Override
    public void invoke(E event) {
        invoked.set(true);
        logInvoke(event);
        if (listener.getExecutor() != null) {
            listener.getExecutor().execute(() -> listener.onEvent(event));
        } else {
            listener.onEvent(event);
        }
    }
    
    /**
     * log invoker be invoked.
     *
     * @param event event
     */
    protected abstract void logInvoke(E event);
    
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
        
        AbstractAiListenerInvoker that = (AbstractAiListenerInvoker) o;
        return Objects.equals(listener, that.listener);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(listener);
    }
}
