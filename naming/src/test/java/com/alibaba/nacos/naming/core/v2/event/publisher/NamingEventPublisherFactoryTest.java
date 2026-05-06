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

package com.alibaba.nacos.naming.core.v2.event.publisher;

import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class NamingEventPublisherFactoryTest {
    
    private Map<String, EventPublisher> originalEventPublisherMap;
    
    @BeforeEach
    void setUp() throws Exception {
        originalEventPublisherMap = new HashMap<>(NotifyCenter.getPublisherMap());
        NotifyCenter.getPublisherMap().clear();
        // Protect other unit test publisher affect this case.
        Field field = ReflectionUtils.findField(NamingEventPublisherFactory.class, "publisher", Map.class);
        field.setAccessible(true);
        Map map = (Map) field.get(NamingEventPublisherFactory.getInstance());
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
        NamingEventPublisherFactory.getInstance().apply(TestEvent.TestEvent1.class, Byte.SIZE);
        NamingEventPublisherFactory.getInstance().apply(TestEvent.TestEvent2.class, Byte.SIZE);
        NamingEventPublisherFactory.getInstance().apply(TestEvent.class, Byte.SIZE);
        String expectedStatus =
                "Naming event publisher statues:\n" + "\tPublisher TestEvent                     : shutdown=false, queue=      0/8      \n";
        assertThat(NamingEventPublisherFactory.getInstance().getAllPublisherStatues(), is(expectedStatus));
    }
}
