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

package com.alibaba.nacos.api.exception.runtime;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosRuntimeExceptionTest {
    
    @Test
    void testConstructorWithErrorCode() {
        NacosRuntimeException exception = new NacosRuntimeException(NacosException.INVALID_PARAM);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }
    
    @Test
    void testConstructorWithErrorCodeAndMsg() {
        NacosRuntimeException exception = new NacosRuntimeException(NacosException.INVALID_PARAM, "test");
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals("errCode: 400, errMsg: test ", exception.getMessage());
        assertNull(exception.getCause());
    }
    
    @Test
    void testConstructorWithErrorCodeAndCause() {
        NacosRuntimeException exception = new NacosRuntimeException(NacosException.INVALID_PARAM,
                new RuntimeException("test"));
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals("java.lang.RuntimeException: test", exception.getMessage());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }
    
    @Test
    void testConstructorWithFull() {
        NacosRuntimeException exception = new NacosRuntimeException(NacosException.INVALID_PARAM, "test",
                new RuntimeException("cause test"));
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals("errCode: 400, errMsg: test ", exception.getMessage());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }
}