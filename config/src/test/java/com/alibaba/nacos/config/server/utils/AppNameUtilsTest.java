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

package com.alibaba.nacos.config.server.utils;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppNameUtilsTest {
    
    private static final String PARAM_MARKING_PROJECT = "project.name";
    
    private static final String PARAM_MARKING_JBOSS = "jboss.server.home.dir";
    
    private static final String PARAM_MARKING_JETTY = "jetty.home";
    
    private static final String PARAM_MARKING_TOMCAT = "catalina.base";
    
    private static final String LINUX_ADMIN_HOME = "/home/admin/";
    
    private static final String SERVER_JBOSS = "jboss";
    
    private static final String SERVER_JETTY = "jetty";
    
    private static final String SERVER_TOMCAT = "tomcat";
    
    private static final String SERVER_UNKNOWN = "unknown server";
    
    private static final String DEFAULT_APP_NAME = "unknown";
    
    @Test
    void testGetAppName() {
        
        System.setProperty(PARAM_MARKING_PROJECT, SERVER_UNKNOWN);
        assertEquals(SERVER_UNKNOWN, AppNameUtils.getAppName());
        System.clearProperty(PARAM_MARKING_PROJECT);
        
        System.setProperty(PARAM_MARKING_JBOSS, LINUX_ADMIN_HOME + SERVER_JBOSS + File.separator);
        assertEquals(SERVER_JBOSS, AppNameUtils.getAppName());
        System.clearProperty(PARAM_MARKING_JBOSS);
        
        System.setProperty(PARAM_MARKING_JETTY, LINUX_ADMIN_HOME + SERVER_JETTY + File.separator);
        assertEquals(SERVER_JETTY, AppNameUtils.getAppName());
        System.clearProperty(PARAM_MARKING_JETTY);
        
        System.setProperty(PARAM_MARKING_TOMCAT, LINUX_ADMIN_HOME + SERVER_TOMCAT + File.separator);
        assertEquals(SERVER_TOMCAT, AppNameUtils.getAppName());
        System.clearProperty(PARAM_MARKING_TOMCAT);
        
        assertEquals(DEFAULT_APP_NAME, AppNameUtils.getAppName());
        
    }
}
