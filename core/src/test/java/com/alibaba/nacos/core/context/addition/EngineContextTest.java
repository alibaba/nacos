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

package com.alibaba.nacos.core.context.addition;

import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EngineContextTest {
    
    EngineContext engineContext;
    
    @BeforeEach
    void setUp() {
        engineContext = new EngineContext();
    }
    
    @Test
    void testSetVersion() {
        assertEquals(VersionUtils.version, engineContext.getVersion());
        engineContext.setVersion("testVersion");
        assertEquals("testVersion", engineContext.getVersion());
    }
    
    @Test
    void testSetContext() {
        assertNull(engineContext.getContext("test"));
        assertEquals("default", engineContext.getContext("test", "default"));
        engineContext.setContext("test", "testValue");
        assertEquals("testValue", engineContext.getContext("test"));
        assertEquals("testValue", engineContext.getContext("test", "default"));
    }
}