/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.utils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class EnvUtilsTest {
    
    @After
    public void tearDown() throws Exception {
        System.clearProperty("nacos.home");
    }
    
    @Test
    public void test() {
        String nacosHome = EnvUtils.getNacosHome();
        Assert.assertEquals(System.getProperty("user.home") + File.separator + "nacos", nacosHome);
        
        System.setProperty("nacos.home", "test");
        String testHome = EnvUtils.getNacosHome();
        Assert.assertEquals("test", testHome);
    }
}
