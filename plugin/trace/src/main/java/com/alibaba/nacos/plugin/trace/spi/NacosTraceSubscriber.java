/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.trace.spi;

import com.alibaba.nacos.common.trace.event.TraceEvent;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Nacos trace event subscriber.
 *
 * @author xiweng.yy
 */
public interface NacosTraceSubscriber {
    
    /**
     * Get the plugin name, if the same name has loaded by nacos, the older one will be replaced by new one.
     *
     * @return plugin name
     */
    String getName();
    
    /**
     * Event callback.
     *
     * @param event {@link TraceEvent}
     */
    void onEvent(TraceEvent event);
    
    /**
     * Returns which trace events are this subscriber interested in.
     *
     * @return The interested event types.
     */
    List<Class<? extends TraceEvent>> subscribeTypes();
    
    /**
     * It is up to the listener to determine whether the callback is asynchronous or synchronous.
     *
     * @return {@link Executor}
     */
    default Executor executor() {
        return null;
    }
}
