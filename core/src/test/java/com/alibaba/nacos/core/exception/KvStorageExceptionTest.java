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

package com.alibaba.nacos.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class KvStorageExceptionTest {
    
    @Test
    void testDefaultConstructor() {
        KvStorageException ex = new KvStorageException();
        assertNotNull(ex);
    }
    
    @Test
    void testErrorCodeAndMsgConstructor() {
        KvStorageException ex =
            new KvStorageException(ErrorCode.KVStorageWriteError, "write failed");
        assertEquals(ErrorCode.KVStorageWriteError.getCode(), ex.getErrCode());
        assertEquals("write failed", ex.getErrMsg());
    }
    
    @Test
    void testErrorCodeAndThrowableConstructor() {
        Throwable cause = new RuntimeException("io error");
        KvStorageException ex = new KvStorageException(ErrorCode.KVStorageReadError, cause);
        assertEquals(ErrorCode.KVStorageReadError.getCode(), ex.getErrCode());
        assertSame(cause, ex.getCause());
    }
    
    @Test
    void testErrorCodeMsgAndThrowableConstructor() {
        Throwable cause = new RuntimeException("io error");
        KvStorageException ex =
            new KvStorageException(ErrorCode.KVStorageDeleteError, "delete failed", cause);
        assertEquals(ErrorCode.KVStorageDeleteError.getCode(), ex.getErrCode());
        assertEquals("delete failed", ex.getErrMsg());
        assertSame(cause, ex.getCause());
    }
    
    @Test
    void testIntErrCodeAndMsgConstructor() {
        KvStorageException ex = new KvStorageException(40100, "custom error");
        assertEquals(40100, ex.getErrCode());
        assertEquals("custom error", ex.getErrMsg());
    }
    
    @Test
    void testIntErrCodeAndThrowableConstructor() {
        Throwable cause = new RuntimeException("cause");
        KvStorageException ex = new KvStorageException(40101, cause);
        assertEquals(40101, ex.getErrCode());
        assertSame(cause, ex.getCause());
    }
    
    @Test
    void testIntErrCodeMsgAndThrowableConstructor() {
        Throwable cause = new RuntimeException("cause");
        KvStorageException ex = new KvStorageException(40102, "msg", cause);
        assertEquals(40102, ex.getErrCode());
        assertEquals("msg", ex.getErrMsg());
        assertSame(cause, ex.getCause());
    }
    
    @Test
    void testSetErrCodeAndSetErrMsg() {
        KvStorageException ex = new KvStorageException(ErrorCode.UnKnowError, "init");
        ex.setErrCode(40002);
        ex.setErrMsg("updated");
        assertEquals(40002, ex.getErrCode());
        assertEquals("updated", ex.getErrMsg());
    }
    
    @Test
    void testSetCauseThrowable() {
        KvStorageException ex = new KvStorageException(ErrorCode.KVStorageWriteError, "msg");
        Throwable cause = new RuntimeException("cause");
        ex.setCauseThrowable(cause);
        assertNotNull(ex.getErrMsg());
    }
    
    @Test
    void testToString() {
        KvStorageException ex =
            new KvStorageException(ErrorCode.KVStorageWriteError, "write failed");
        String s = ex.toString();
        assertNotNull(s);
    }
}
