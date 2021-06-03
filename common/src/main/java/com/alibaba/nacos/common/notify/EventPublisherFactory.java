/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.utils.BiFunction;

/**
 * Event publisher factory.
 *
 * @author xiweng.yy
 */
public interface EventPublisherFactory extends BiFunction<Class<? extends Event>, Integer, EventPublisher> {
    
    /**
     * Build an new {@link EventPublisher}.
     *
     * @param eventType    eventType for {@link EventPublisher}
     * @param maxQueueSize max queue size for {@link EventPublisher}
     * @return new {@link EventPublisher}
     */
    @Override
    EventPublisher apply(Class<? extends Event> eventType, Integer maxQueueSize);
}
