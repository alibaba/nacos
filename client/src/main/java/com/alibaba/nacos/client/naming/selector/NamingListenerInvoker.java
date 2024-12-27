/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.client.selector.ListenerInvoker;

import java.util.Objects;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Naming listener invoker.
 *
 * @author lideyou
 */
public class NamingListenerInvoker implements ListenerInvoker<NamingEvent> {
    
    private final EventListener listener;
    
    public NamingListenerInvoker(EventListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void invoke(NamingEvent event) {
        logInvoke(event);
        if (listener instanceof AbstractEventListener && ((AbstractEventListener) listener).getExecutor() != null) {
            ((AbstractEventListener) listener).getExecutor().execute(() -> listener.onEvent(event));
        } else {
            listener.onEvent(event);
        }
    }
    
    private void logInvoke(NamingEvent event) {
        NAMING_LOGGER.info("Invoke event groupName: {}, serviceName: {} to Listener: {}", event.getGroupName(),
                event.getServiceName(), listener.toString());
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        if (this == o) {
            return true;
        }
        
        NamingListenerInvoker that = (NamingListenerInvoker) o;
        return Objects.equals(listener, that.listener);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(listener);
    }
}
