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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.plugin.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PluginPersistenceExceptionTest {
    
    @Test
    void constructorWithMessage() {
        PluginPersistenceException ex = new PluginPersistenceException("save failed");
        assertEquals("save failed", ex.getMessage());
        assertNotNull(ex);
    }
    
    @Test
    void constructorWithMessageAndCause() {
        Throwable cause = new RuntimeException("io error");
        PluginPersistenceException ex = new PluginPersistenceException("persist failed", cause);
        assertEquals("persist failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
