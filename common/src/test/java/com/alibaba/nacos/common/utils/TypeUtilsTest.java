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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * type utils test.
 *
 * @author zzq
 */
class TypeUtilsTest {
    
    @Test
    void parameterize() {
        ParameterizedType stringComparableType = TypeUtils.parameterize(List.class, String.class);
        assertEquals("java.util.List<java.lang.String>", stringComparableType.toString());
        assertEquals(List.class, stringComparableType.getRawType());
        assertNull(stringComparableType.getOwnerType());
        assertEquals(1, stringComparableType.getActualTypeArguments().length);
        assertEquals(String.class, stringComparableType.getActualTypeArguments()[0]);
        
        ParameterizedType stringIntegerComparableType = TypeUtils.parameterize(Map.class, String.class, Integer.class);
        assertEquals("java.util.Map<java.lang.String, java.lang.Integer>", stringIntegerComparableType.toString());
        assertEquals(Map.class, stringIntegerComparableType.getRawType());
        assertNull(stringComparableType.getOwnerType());
        assertEquals(2, stringIntegerComparableType.getActualTypeArguments().length);
        assertEquals(String.class, stringIntegerComparableType.getActualTypeArguments()[0]);
        assertEquals(Integer.class, stringIntegerComparableType.getActualTypeArguments()[1]);
    }
    
    @Test
    void testParameterizeForNull() {
        assertThrows(NullPointerException.class, () -> {
            TypeUtils.parameterize(null, String.class);
        });
    }
    
    @Test
    void testParameterizeForNullType() {
        assertThrows(NullPointerException.class, () -> {
            TypeUtils.parameterize(List.class, (Type[]) null);
        });
    }
    
    @Test
    void testParameterizeForNullTypeArray() {
        assertThrows(IllegalArgumentException.class, () -> {
            TypeUtils.parameterize(List.class, (Type) null);
        });
    }
    
    @Test
    void testParameterizeForDiffLength() {
        assertThrows(IllegalArgumentException.class, () -> {
            TypeUtils.parameterize(List.class, String.class, Integer.class);
        });
    }
}
