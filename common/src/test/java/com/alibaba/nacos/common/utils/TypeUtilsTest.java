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

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * type utils test.
 *
 * @author zzq
 */
public class TypeUtilsTest {
    
    @Test
    public void parameterize() {
        ParameterizedType stringComparableType = TypeUtils.parameterize(List.class, String.class);
        Assert.assertEquals("java.util.List<java.lang.String>", stringComparableType.toString());
        Assert.assertEquals(List.class, stringComparableType.getRawType());
        Assert.assertNull(stringComparableType.getOwnerType());
        Assert.assertEquals(1, stringComparableType.getActualTypeArguments().length);
        Assert.assertEquals(String.class, stringComparableType.getActualTypeArguments()[0]);
        
        ParameterizedType stringIntegerComparableType = TypeUtils.parameterize(Map.class, String.class, Integer.class);
        Assert.assertEquals("java.util.Map<java.lang.String, java.lang.Integer>",
                stringIntegerComparableType.toString());
        Assert.assertEquals(Map.class, stringIntegerComparableType.getRawType());
        Assert.assertNull(stringComparableType.getOwnerType());
        Assert.assertEquals(2, stringIntegerComparableType.getActualTypeArguments().length);
        Assert.assertEquals(String.class, stringIntegerComparableType.getActualTypeArguments()[0]);
        Assert.assertEquals(Integer.class, stringIntegerComparableType.getActualTypeArguments()[1]);
    }
    
    @Test(expected = NullPointerException.class)
    public void testParameterizeForNull() {
        TypeUtils.parameterize(null, String.class);
    }
    
    @Test(expected = NullPointerException.class)
    public void testParameterizeForNullType() {
        TypeUtils.parameterize(List.class, (Type[]) null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testParameterizeForNullTypeArray() {
        TypeUtils.parameterize(List.class, (Type) null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testParameterizeForDiffLength() {
        TypeUtils.parameterize(List.class, String.class, Integer.class);
    }
}
