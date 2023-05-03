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

package com.alibaba.nacos.api.exception;

import com.alibaba.nacos.api.common.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NacosExceptionTest {
    
    @Test
    public void testEmptyConstructor() {
        NacosException exception = new NacosException();
        assertEquals(0, exception.getErrCode());
        assertEquals(Constants.NULL, exception.getErrMsg());
        assertEquals("ErrCode:0, ErrMsg:", exception.toString());
        exception.setErrCode(NacosException.INVALID_PARAM);
        exception.setErrMsg("test");
        assertEquals("ErrCode:400, ErrMsg:test", exception.toString());
    }
    
    @Test
    public void testConstructorWithErrMsg() {
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, "test");
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("test", exception.getErrMsg());
        assertEquals("ErrCode:500, ErrMsg:test", exception.toString());
    }
    
    @Test
    public void testConstructorWithCause() {
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, new RuntimeException("cause test"));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("cause test", exception.getErrMsg());
        assertEquals("ErrCode:500, ErrMsg:cause test", exception.toString());
    }
    
    @Test
    public void testConstructorWithMultiCauses() {
        NacosException exception = new NacosException(NacosException.SERVER_ERROR,
                new RuntimeException("cause test", new RuntimeException("multi")));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("multi", exception.getErrMsg());
        assertEquals("ErrCode:500, ErrMsg:multi", exception.toString());
    }
    
    @Test
    public void testConstructorWithFull() {
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, "test",
                new RuntimeException("cause test"));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("test", exception.getErrMsg());
        assertEquals("ErrCode:500, ErrMsg:test", exception.toString());
    }
}