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
        
        ParameterizedType stringIntegerComparableType = TypeUtils.parameterize(Map.class, String.class, Integer.class);
        Assert.assertEquals("java.util.Map<java.lang.String, java.lang.Integer>",
                stringIntegerComparableType.toString());
    }
}
