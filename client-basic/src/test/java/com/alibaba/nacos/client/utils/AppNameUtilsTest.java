/*
 *
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
 *
 */

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.client.constant.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppNameUtilsTest {
    
    @BeforeEach
    void setUp() throws Exception {
    }
    
    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(Constants.SysEnv.PROJECT_NAME);
        System.clearProperty("jboss.server.home.dir");
        System.clearProperty("jetty.home");
        System.clearProperty("catalina.base");
    }
    
    @Test
    void testGetAppNameByDefault() {
        String appName = AppNameUtils.getAppName();
        assertEquals("unknown", appName);
    }
    
    @Test
    void testGetAppNameByProjectName() {
        System.setProperty(Constants.SysEnv.PROJECT_NAME, "testAppName");
        String appName = AppNameUtils.getAppName();
        assertEquals("testAppName", appName);
    }
    
    @Test
    void testGetAppNameByServerTypeForJboss() {
        System.setProperty("jboss.server.home.dir", "/home/admin/testAppName/");
        String appName = AppNameUtils.getAppName();
        assertEquals("testAppName", appName);
    }
    
    @Test
    void testGetAppNameByServerTypeForJetty() {
        System.setProperty("jetty.home", "/home/admin/testAppName/");
        String appName = AppNameUtils.getAppName();
        assertEquals("testAppName", appName);
    }
    
    @Test
    void testGetAppNameByServerTypeForTomcat() {
        System.setProperty("catalina.base", "/home/admin/testAppName/");
        String appName = AppNameUtils.getAppName();
        assertEquals("testAppName", appName);
    }
}