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

package com.alibaba.nacos.common.remote.exception;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemoteExceptionTest {
    
    @Test
    public void testConnectionAlreadyClosedException() {
        ConnectionAlreadyClosedException exception = new ConnectionAlreadyClosedException("test message");
        
        assertEquals(600, exception.getErrCode());
        assertEquals("errCode: 600, errMsg: test message ", exception.getMessage());
        assertNull(exception.getCause());
        
        exception = new ConnectionAlreadyClosedException();
        
        assertEquals(600, exception.getErrCode());
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
        
        RuntimeException caused = new RuntimeException("test cause");
        exception = new ConnectionAlreadyClosedException(caused);
        assertEquals(600, exception.getErrCode());
        assertEquals(caused, exception.getCause());
        assertEquals("java.lang.RuntimeException: test cause", exception.getMessage());
    }
    
    @Test
    public void testConnectionBusyException() {
        String msg = "Connection is busy";
        ConnectionBusyException exception = new ConnectionBusyException(msg);
        
        assertEquals(601, exception.getErrCode());
        assertEquals("errCode: 601, errMsg: " + msg + " ", exception.getMessage());
        assertNull(exception.getCause());
        
        RuntimeException caused = new RuntimeException("test cause");
        exception = new ConnectionBusyException(caused);
        
        assertEquals(601, exception.getErrCode());
        assertEquals(caused, exception.getCause());
    }
}