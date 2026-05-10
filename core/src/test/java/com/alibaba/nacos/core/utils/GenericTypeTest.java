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

package com.alibaba.nacos.core.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericTypeTest {
    
    @Test
    void getTypeReturnsResolvedParameterizedType() {
        GenericType<List<String>> genericType = new GenericType<List<String>>() {
        };
        Type type = genericType.getType();
        assertEquals(List.class, ((java.lang.reflect.ParameterizedType) type).getRawType());
        assertTrue(type.getTypeName().contains("java.util.List"));
    }
    
    @Test
    void constructorThrowsWhenRuntimeTypeIsTypeVariable() {
        assertThrows(IllegalArgumentException.class, GenericTypeTest::createWithTypeVariable);
    }
    
    private static <T> void createWithTypeVariable() {
        new GenericType<T>() {
        };
    }
}
