/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.common;

import com.alibaba.nacos.common.util.SystemUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;

/**
 * {@link SystemUtils} Test
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
public class SystemUtilsTest {

    private static final Random random = new Random();

    private static boolean standaloneMode = random.nextBoolean();

    @BeforeClass
    public static void init() {
        System.setProperty("nacos.standalone", String.valueOf(standaloneMode));
    }

    @Test
    public void testStandaloneModeConstants() {

        System.out.printf("System property \"%s\" = %s \n", "nacos.standalone", standaloneMode);

        if ("true".equalsIgnoreCase(System.getProperty("nacos.standalone"))) {
            Assert.assertTrue(SystemUtils.STANDALONE_MODE);
        } else {
            Assert.assertFalse(SystemUtils.STANDALONE_MODE);
        }

        Assert.assertEquals(standaloneMode, SystemUtils.STANDALONE_MODE);

    }
}
