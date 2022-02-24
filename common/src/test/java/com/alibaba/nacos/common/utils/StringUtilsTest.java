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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * String utils.
 * @author zzq
 */
public class StringUtilsTest {

    @Test
    public void testJoin() {
        ArrayList<Object> objects = new ArrayList<Object>();
        objects.add(null);
        Assert.assertNull(StringUtils.join(null, "a"));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.join(Arrays.asList(), "a"));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.join(objects, "a"));
        Assert.assertEquals("a;b;c", StringUtils.join(Arrays.asList("a", "b", "c"), ";"));
        Assert.assertEquals("abc", StringUtils.join(Arrays.asList("a", "b", "c"),  null));
    }

}
