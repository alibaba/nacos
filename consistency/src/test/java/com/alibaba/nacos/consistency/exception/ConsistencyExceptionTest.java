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

package com.alibaba.nacos.consistency.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsistencyExceptionTest {
    
    @Test
    void testDefaultConstructor() {
        ConsistencyException ex = new ConsistencyException();
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }
    
    @Test
    void testMessageConstructor() {
        String message = "test message";
        ConsistencyException ex = new ConsistencyException(message);
        assertEquals(message, ex.getMessage());
        assertNull(ex.getCause());
    }
    
    @Test
    void testMessageAndCauseConstructor() {
        String message = "test message";
        Throwable cause = new IllegalArgumentException("cause");
        ConsistencyException ex = new ConsistencyException(message, cause);
        assertEquals(message, ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
    
    @Test
    void testCauseConstructor() {
        Throwable cause = new IllegalArgumentException("cause");
        ConsistencyException ex = new ConsistencyException(cause);
        assertEquals(cause, ex.getCause());
        assertNotNull(ex.getMessage());
    }
    
    @Test
    void testProtectedConstructorViaAnonymousSubclass() {
        String message = "protected message";
        Throwable cause = new RuntimeException("cause");
        ConsistencyException ex = new ConsistencyException(message, cause, true, true) {
        };
        assertEquals(message, ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
