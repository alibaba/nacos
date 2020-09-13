/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for PathUtils class.
 *
 * @author JackSun-Developer
 */
public class PathUtilsTest {
    
    private String tempNacosCacheDir = null;
    
    private String tempUserHome = null;
    
    @Before
    public void setUp() {
        tempNacosCacheDir = System.getProperty("nacos.cache.dir");
        tempUserHome = System.getProperty("user.home");
    }
    
    @Test
    public void testDefaultConfigDir() {
        if (!StringUtils.isBlank(tempNacosCacheDir)) {
            /* Case 1, "jm.log.path" is set. */
            assertEquals(PathUtils.defaultConfigDir(), tempNacosCacheDir + File.separator + "config");
        } else {
            assertEquals(PathUtils.defaultConfigDir(), tempUserHome + File.separator + "nacos"
                    + File.separator + "config");
        }
    }
    
    @Test
    public void defaultLogDir() {
        if (!StringUtils.isBlank(tempNacosCacheDir)) {
            /* Case 1, "jm.log.path" is set. */
            assertEquals(PathUtils.defaultLogDir(), tempNacosCacheDir + File.separator + "logs");
        } else {
            assertEquals(PathUtils.defaultLogDir(), tempUserHome + File.separator + "logs"
                    + File.separator + "nacos");
        }
    }
    
    @Test
    public void defaultNamingDir() {
        if (!StringUtils.isBlank(tempNacosCacheDir)) {
            /* Case 1, "jm.log.path" is set. */
            assertEquals(PathUtils.defaultNamingDir("public"),
                    tempNacosCacheDir + File.separator + "naming" + File.separator + "public");
        } else {
            assertEquals(PathUtils.defaultNamingDir("public"), tempUserHome
                    + File.separator + "nacos" + File.separator + "naming" + File.separator + "public");
        }
    }
}