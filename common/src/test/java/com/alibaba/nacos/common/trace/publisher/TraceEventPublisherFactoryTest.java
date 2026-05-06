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

package com.alibaba.nacos.common.trace.publisher;

import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceEventPublisherFactoryTest {
    
    private Map<String, EventPublisher> originalEventPublisherMap;
    
    @BeforeEach
    void setUp() throws Exception {
        originalEventPublisherMap = new HashMap<>(NotifyCenter.getPublisherMap());
        NotifyCenter.getPublisherMap().clear();
        // Protect other unit test publisher affect this case.
        Field field = TraceEventPublisherFactory.class.getDeclaredField("publisher");
        field.setAccessible(true);
        Map map = (Map) field.get(TraceEventPublisherFactory.getInstance());
        map.clear();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        NotifyCenter.getPublisherMap().clear();
        NotifyCenter.getPublisherMap().putAll(originalEventPublisherMap);
        originalEventPublisherMap = null;
    }
    
    @Test
    void testApply() {
        TraceEventPublisherFactory.getInstance().apply(TraceTestEvent.TraceTestEvent1.class, Byte.SIZE);
        TraceEventPublisherFactory.getInstance().apply(TraceTestEvent.TraceTestEvent2.class, Byte.SIZE);
        TraceEventPublisherFactory.getInstance().apply(TraceTestEvent.class, Byte.SIZE);
        String expectedStatus = "Trace event publisher statues:\n"
                + "\tPublisher TraceEvent                    : shutdown=false, queue=      0/8      \n";
        assertEquals(expectedStatus, TraceEventPublisherFactory.getInstance().getAllPublisherStatues());
    }
    
    @Test
    void testApplyAfterAddEventType() {
        TraceEventPublisherFactory.getInstance().addPublisherEvent(TraceTestEvent.class);
        TraceEventPublisherFactory.getInstance().apply(TraceTestEvent.TraceTestEvent1.class, Byte.SIZE);
        TraceEventPublisherFactory.getInstance().apply(TraceTestEvent.TraceTestEvent2.class, Byte.SIZE);
        TraceEventPublisherFactory.getInstance().apply(TraceTestEvent.class, Byte.SIZE);
        String expectedStatus = "Trace event publisher statues:\n"
                + "\tPublisher TraceTestEvent                : shutdown=false, queue=      0/8      \n";
        assertEquals(expectedStatus, TraceEventPublisherFactory.getInstance().getAllPublisherStatues());
    }
}
