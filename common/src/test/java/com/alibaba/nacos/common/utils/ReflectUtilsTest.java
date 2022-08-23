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
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ReflectUtils unit test.
 *
 * @author karsonto
 * @date 2022/08/19
 */
public class ReflectUtilsTest {

    List<String> listStr;

    @Before
    public void before() {
        listStr = new ArrayList<>(2);
    }

    @Test
    public void getFieldValue() {
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementData");
        Assert.assertTrue(elementData instanceof Object[]);
        Assert.assertEquals(((Object[]) elementData).length, 2);
    }

    @Test
    public void getFieldValue2() {
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementDataxx", 3);
        Assert.assertEquals(elementData, 3);
    }

    @Test
    public void getField() {
        try {
            Field field = listStr.getClass().getDeclaredField("elementData");
            field.setAccessible(true);
            Object elementData = ReflectUtils.getField(field, listStr);
            Assert.assertTrue(elementData instanceof Object[]);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

    }

}
