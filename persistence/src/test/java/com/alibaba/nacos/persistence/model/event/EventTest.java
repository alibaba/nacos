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

package com.alibaba.nacos.persistence.model.event;

import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTest {
    
    @Test
    void testDerbyImportEvent() {
        DerbyImportEvent event = new DerbyImportEvent(true);
        assertTrue(event.isFinished());
    }
    
    @Test
    void testDerbyLoadEvent() {
        DerbyLoadEvent event = DerbyLoadEvent.INSTANCE;
        assertNotNull(event);
    }
    
    @Test
    void testRaftDbErrorEvent() {
        RaftDbErrorEvent event = new RaftDbErrorEvent(new Exception("test"));
        assertNotNull(event);
        String json = JacksonUtils.toJson(event);
        RaftDbErrorEvent deserialized = JacksonUtils.toObj(json, RaftDbErrorEvent.class);
        assertInstanceOf(Throwable.class, deserialized.getEx());
    }
}