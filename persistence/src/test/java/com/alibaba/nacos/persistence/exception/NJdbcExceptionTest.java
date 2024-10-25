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

package com.alibaba.nacos.persistence.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NJdbcExceptionTest {
    
    private Throwable cause;
    
    @BeforeEach
    public void setUp() {
        cause = new IllegalStateException("IllegalStateException");
    }
    
    @Test
    public void tesConstructorWithMessage() {
        String msg = "test msg";
        NJdbcException exception = new NJdbcException(msg);
        assertEquals(msg, exception.getMessage());
        assertNull(exception.getCause());
        assertNull(exception.getOriginExceptionName());
    }
    
    @Test
    public void testConstructorWithMessageAndOriginExceptionName() {
        String msg = "test msg";
        String originExceptionName = "OriginException";
        NJdbcException exception = new NJdbcException(msg, originExceptionName);
        assertEquals(msg, exception.getMessage());
        assertEquals(originExceptionName, exception.getOriginExceptionName());
    }
    
    @Test
    public void testConstructorWithMessageCauseAndOriginExceptionName() {
        String msg = "test msg";
        String originExceptionName = "OriginException";
        NJdbcException exception = new NJdbcException(msg, cause, originExceptionName);
        assertEquals("test msg; nested exception is java.lang.IllegalStateException: IllegalStateException", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(originExceptionName, exception.getOriginExceptionName());
    }
    
    @Test
    public void testConstructorWithMessageAndCause() {
        String msg = "test msg";
        NJdbcException exception = new NJdbcException(msg, cause);
        assertEquals("test msg; nested exception is java.lang.IllegalStateException: IllegalStateException", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertNull(exception.getOriginExceptionName());
    }
    
    @Test
    public void testConstructorWithCause() {
        NJdbcException exception = new NJdbcException(cause);
        assertEquals("; nested exception is java.lang.IllegalStateException: IllegalStateException", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertNull(exception.getOriginExceptionName());
    }
}