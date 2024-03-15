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

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExceptionUtilTest {
    
    NacosRuntimeException nacosRuntimeException;
    
    @Before
    public void setUp() {
        RuntimeException caused = new RuntimeException("I'm caused exception.");
        nacosRuntimeException = new NacosRuntimeException(500, "Test", caused);
        
    }
    
    @Test
    public void testGetAllExceptionMsg() {
        String msg = ExceptionUtil.getAllExceptionMsg(nacosRuntimeException);
        assertEquals("caused: errCode: 500, errMsg: Test ;caused: I'm caused exception.;", msg);
    }
    
    @Test
    public void testGetCause() {
        assertEquals("I'm caused exception.", ExceptionUtil.getCause(nacosRuntimeException).getMessage());
        NacosRuntimeException nreWithoutCaused = new NacosRuntimeException(500);
        assertEquals(nreWithoutCaused, ExceptionUtil.getCause(nreWithoutCaused));
    }
    
    @Test
    public void testGetStackTrace() {
        assertEquals("", ExceptionUtil.getStackTrace(null));
        String stackTrace = ExceptionUtil.getStackTrace(nacosRuntimeException);
        assertTrue(stackTrace.contains(
                "com.alibaba.nacos.api.exception.runtime.NacosRuntimeException: errCode: 500, errMsg: Test"));
        assertTrue(stackTrace.contains("at"));
        assertTrue(stackTrace.contains("Caused by: java.lang.RuntimeException: I'm caused exception."));
    }
}