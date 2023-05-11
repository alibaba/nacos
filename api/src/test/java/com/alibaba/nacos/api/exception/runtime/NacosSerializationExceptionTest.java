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

import com.alibaba.nacos.api.common.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NacosSerializationExceptionTest {
    
    @Test
    public void testEmptyConstructor() {
        NacosSerializationException exception = new NacosSerializationException();
        assertEquals(Constants.Exception.SERIALIZE_ERROR_CODE, exception.getErrCode());
        assertNull(exception.getMessage());
        assertNull(exception.getSerializedClass());
    }
    
    @Test
    public void testConstructorWithSerializedClass() {
        NacosSerializationException exception = new NacosSerializationException(NacosSerializationExceptionTest.class);
        assertEquals(Constants.Exception.SERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals(String.format("errCode: 100, errMsg: Nacos serialize for class [%s] failed.  ",
                NacosSerializationExceptionTest.class.getName()), exception.getMessage());
        assertEquals(NacosSerializationExceptionTest.class, exception.getSerializedClass());
    }
    
    @Test
    public void testConstructorWithCause() {
        NacosSerializationException exception = new NacosSerializationException(new RuntimeException("test"));
        assertEquals(Constants.Exception.SERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals("errCode: 100, errMsg: Nacos serialize failed.  ", exception.getMessage());
        assertNull(exception.getSerializedClass());
    }
    
    @Test
    public void testConstructorWithSerializedClassAndCause() {
        NacosSerializationException exception = new NacosSerializationException(NacosSerializationExceptionTest.class,
                new RuntimeException("test"));
        assertEquals(Constants.Exception.SERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals(String.format("errCode: 100, errMsg: Nacos serialize for class [%s] failed.  ",
                NacosSerializationExceptionTest.class.getName(), "test"), exception.getMessage());
        assertEquals(NacosSerializationExceptionTest.class, exception.getSerializedClass());
    }
}