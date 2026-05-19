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

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigAlreadyExistsExceptionTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigAlreadyExistsException ex = new ConfigAlreadyExistsException();
        assertEquals(0, ex.getErrCode());
    }
    
    @Test
    void testMessageConstructor() {
        ConfigAlreadyExistsException ex =
            new ConfigAlreadyExistsException("already exists");
        assertEquals("already exists", ex.getErrMsg());
        assertEquals(NacosException.CONFIG_ALREADY_EXISTS, ex.getErrCode());
    }
    
    @Test
    void testCodeMessageConstructor() {
        ConfigAlreadyExistsException ex =
            new ConfigAlreadyExistsException(409, "conflict");
        assertEquals("conflict", ex.getErrMsg());
        assertEquals(409, ex.getErrCode());
    }
    
    @Test
    void testCodeThrowableConstructor() {
        RuntimeException cause = new RuntimeException("root");
        ConfigAlreadyExistsException ex =
            new ConfigAlreadyExistsException(500, cause);
        assertEquals(500, ex.getErrCode());
        assertNotNull(ex.getCause());
    }
    
    @Test
    void testCodeMessageThrowableConstructor() {
        RuntimeException cause = new RuntimeException("root");
        ConfigAlreadyExistsException ex =
            new ConfigAlreadyExistsException(500, "err", cause);
        assertEquals(500, ex.getErrCode());
        assertEquals("err", ex.getErrMsg());
        assertNotNull(ex.getCause());
    }
}
