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

import org.apache.commons.lang3.StringUtils;

import java.io.File;

/**
 * App util.
 *
 * @author Nacos
 */
public class AppNameUtils {
    
    private static final String PARAM_MARKING_PROJECT = "project.name";
    
    private static final String PARAM_MARKING_JBOSS = "jboss.server.home.dir";
    
    private static final String PARAM_MARKING_JETTY = "jetty.home";
    
    private static final String PARAM_MARKING_TOMCAT = "catalina.base";
    
    private static final String LINUX_ADMIN_HOME = "/home/admin/";
    
    private static final String SERVER_JBOSS = "jboss";
    
    private static final String SERVER_JETTY = "jetty";
    
    private static final String SERVER_TOMCAT = "tomcat";
    
    private static final String SERVER_UNKNOWN = "unknown server";
    
    public static String getAppName() {
        String appName = null;
        
        appName = getAppNameByProjectName();
        if (appName != null) {
            return appName;
        }
        
        appName = getAppNameByServerHome();
        if (appName != null) {
            return appName;
        }
        
        return "unknown";
    }
    
    private static String getAppNameByProjectName() {
        return System.getProperty(PARAM_MARKING_PROJECT);
    }
    
    private static String getAppNameByServerHome() {
        String serverHome = null;
        if (SERVER_JBOSS.equals(getServerType())) {
            serverHome = System.getProperty(PARAM_MARKING_JBOSS);
        } else if (SERVER_JETTY.equals(getServerType())) {
            serverHome = System.getProperty(PARAM_MARKING_JETTY);
        } else if (SERVER_TOMCAT.equals(getServerType())) {
            serverHome = System.getProperty(PARAM_MARKING_TOMCAT);
        }
        
        if (serverHome != null && serverHome.startsWith(LINUX_ADMIN_HOME)) {
            return StringUtils.substringBetween(serverHome, LINUX_ADMIN_HOME, File.separator);
        }
        
        return null;
    }
    
    private static String getServerType() {
        String serverType = null;
        if (System.getProperty(PARAM_MARKING_JBOSS) != null) {
            serverType = SERVER_JBOSS;
        } else if (System.getProperty(PARAM_MARKING_JETTY) != null) {
            serverType = SERVER_JETTY;
        } else if (System.getProperty(PARAM_MARKING_TOMCAT) != null) {
            serverType = SERVER_TOMCAT;
        } else {
            serverType = SERVER_UNKNOWN;
        }
        return serverType;
    }
    
}
