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


package com.alibaba.nacos.common.pathencoder;

import com.alibaba.nacos.common.pathencoder.impl.WindowsEncoder;
import junit.framework.TestCase;
import org.junit.Assert;

import java.lang.reflect.Field;

public class PathEncoderManagerTest extends TestCase {

    public void test() throws Exception {
        // load static
        Class.forName(PathEncoderManager.class.getName());
        // remove windows impl
        Field targetEncoder = PathEncoderManager.class.getDeclaredField("targetEncoder");
        targetEncoder.setAccessible(true);
        targetEncoder.set(PathEncoderManager.class, null);
        // try to encode, non windows
        String case1 = "aa||a";
        Assert.assertEquals(PathEncoderManager.encode(case1), case1);
        String case2 = "aa%A9%%A9%a";
        Assert.assertEquals(PathEncoderManager.decode(case2), case2);
        // try to encode if in windows
        targetEncoder.set(PathEncoderManager.class, new WindowsEncoder());
        Assert.assertEquals(PathEncoderManager.encode(case1), case2);
        Assert.assertEquals(PathEncoderManager.decode(case2), case1);
    }

}
