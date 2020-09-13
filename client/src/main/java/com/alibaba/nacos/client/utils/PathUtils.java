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

import java.io.File;

/**
 * CacheDir utils.
 * 
 * @author JackSun-Developer
 */
public class PathUtils {
    
    private static String nacosCacheDir = System.getProperty("nacos.cache.dir");
    
    private static String userHome = System.getProperty("user.home");
    
    /**
     * Gets the config cache dir.
     *
     * @return the home dir of config files.
     */
    public static String defaultConfigDir() {
        if (!StringUtils.isBlank(nacosCacheDir)) {
            return nacosCacheDir + File.separator + "config";
        }
        return userHome + File.separator + "nacos" + File.separator + "config";
    }
    
    /**
     * Gets the log dir.
     *
     * @return the home dir of log files.
     */
    public static String defaultLogDir() {
        if (!StringUtils.isBlank(nacosCacheDir)) {
            return nacosCacheDir + File.separator + "logs";
        }
        return userHome + File.separator + "logs" + File.separator + "nacos";
    }
    
    /**
     * Gets the naming cache dir.
     *
     * @return the home dir of naming files.
     */
    public static String defaultNamingDir(String namespace) {
        if (!StringUtils.isBlank(nacosCacheDir)) {
            return nacosCacheDir + File.separator + "naming" + File.separator + namespace;
        }
        return userHome + File.separator + "nacos" + File.separator + "naming" + File.separator + namespace;
    }
}
