/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NacosConfigExceptionTest {
    
    @Test
    void testDefaultConstructor() {
        NacosConfigException ex = new NacosConfigException();
        assertNull(ex.getMessage());
    }
    
    @Test
    void testMessageConstructor() {
        NacosConfigException ex = new NacosConfigException("test msg");
        assertEquals("test msg", ex.getMessage());
    }
    
    @Test
    void testMessageCauseConstructor() {
        RuntimeException cause = new RuntimeException("root");
        NacosConfigException ex = new NacosConfigException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
    
    @Test
    void testCauseConstructor() {
        RuntimeException cause = new RuntimeException("root");
        NacosConfigException ex = new NacosConfigException(cause);
        assertNotNull(ex.getCause());
    }
    
    @Test
    void testFullConstructor() {
        RuntimeException cause = new RuntimeException("root");
        NacosConfigException ex =
            new NacosConfigException("msg", cause, true, true);
        assertEquals("msg", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
