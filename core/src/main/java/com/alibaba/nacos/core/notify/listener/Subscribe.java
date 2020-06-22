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

package com.alibaba.nacos.core.notify.listener;

import com.alibaba.nacos.core.notify.Event;

import java.util.concurrent.Executor;

/**
 * subscriber.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Subscribe<T extends Event> {
    
    /**
     * Event callback.
     *
     * @param event {@link Event}
     */
    void onEvent(T event);
    
    /**
     * Type of this subscriber's subscription.
     *
     * @return Class which extends {@link Event}
     */
    Class<? extends Event> subscribeType();
    
    /**
     * It is up to the listener to determine whether the callback is asynchronous or synchronous.
     *
     * @return {@link Executor}
     */
    default Executor executor() {
        return null;
    }
    
    /**
     * Whether to ignore expired events.
     *
     * @return default value is {@link Boolean#FALSE}
     */
    default boolean ignoreExpireEvent() {
        return false;
    }
    
}
