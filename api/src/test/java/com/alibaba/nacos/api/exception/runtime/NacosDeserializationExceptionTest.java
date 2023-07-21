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
import com.fasterxml.jackson.databind.type.SimpleType;
import org.junit.Test;

import java.lang.reflect.Type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NacosDeserializationExceptionTest {
    
    @Test
    public void testEmptyConstructor() {
        NacosDeserializationException exception = new NacosDeserializationException();
        assertEquals(Constants.Exception.DESERIALIZE_ERROR_CODE, exception.getErrCode());
        assertNull(exception.getMessage());
        assertNull(exception.getTargetClass());
    }
    
    @Test
    public void testConstructorWithTargetClass() {
        NacosDeserializationException exception = new NacosDeserializationException(
                NacosDeserializationExceptionTest.class);
        assertEquals(Constants.Exception.DESERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals(String.format("errCode: 101, errMsg: Nacos deserialize for class [%s] failed.  ",
                NacosDeserializationExceptionTest.class.getName()), exception.getMessage());
        assertEquals(NacosDeserializationExceptionTest.class, exception.getTargetClass());
    }
    
    @Test
    public void testConstructorWithTargetType() {
        Type type = SimpleType.constructUnsafe(NacosDeserializationExceptionTest.class);
        NacosDeserializationException exception = new NacosDeserializationException(type);
        assertEquals(Constants.Exception.DESERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals(
                String.format("errCode: 101, errMsg: Nacos deserialize for class [%s] failed.  ", type.getTypeName()),
                exception.getMessage());
        assertNull(exception.getTargetClass());
    }
    
    @Test
    public void testConstructorWithCause() {
        NacosDeserializationException exception = new NacosDeserializationException(new RuntimeException("test"));
        assertEquals(Constants.Exception.DESERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals("errCode: 101, errMsg: Nacos deserialize failed.  ", exception.getMessage());
        assertNull(exception.getTargetClass());
    }
    
    @Test
    public void testConstructorWithTargetClassAndCause() {
        NacosDeserializationException exception = new NacosDeserializationException(
                NacosDeserializationExceptionTest.class, new RuntimeException("test"));
        assertEquals(Constants.Exception.DESERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals(String.format("errCode: 101, errMsg: Nacos deserialize for class [%s] failed, cause error[%s].  ",
                NacosDeserializationExceptionTest.class.getName(), "test"), exception.getMessage());
        assertEquals(NacosDeserializationExceptionTest.class, exception.getTargetClass());
    }
    
    @Test
    public void testConstructorWithTargetTypeAndCause() {
        Type type = SimpleType.constructUnsafe(NacosDeserializationExceptionTest.class);
        NacosDeserializationException exception = new NacosDeserializationException(type, new RuntimeException("test"));
        assertEquals(Constants.Exception.DESERIALIZE_ERROR_CODE, exception.getErrCode());
        assertEquals(String.format("errCode: 101, errMsg: Nacos deserialize for class [%s] failed, cause error[%s].  ",
                type.getTypeName(), "test"), exception.getMessage());
        assertNull(exception.getTargetClass());
    }
}