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
    
    private static String cacheDir;
    
    /**
     * Gets the config cache dir.
     *
     * @return the home dir of config files.
     */
    public static String defaultConfigDir() {
        cacheDir = System.getProperty("nacos.cache.dir");
        String configDir = cacheDir + File.separator + "config";
        if (StringUtils.isBlank(cacheDir)) {
            configDir = System.getProperty("user.home") + File.separator + "nacos" + File.separator + "config";
        }
        return configDir;
    }
    
    /**
     * Gets the log dir.
     *
     * @return the home dir of log files.
     */
    public static String defaultLogDir() {
        cacheDir = System.getProperty("nacos.cache.dir");
        String logDir = cacheDir + File.separator + "logs";
        if (StringUtils.isBlank(cacheDir)) {
            logDir = System.getProperty("user.home") + File.separator + "logs" + File.separator + "nacos";
        }
        return logDir;
    }
    
    /**
     * Gets the naming cache dir.
     *
     * @return the home dir of naming files.
     */
    public static String defaultNamingDir(String namespace) {
        cacheDir = System.getProperty("nacos.cache.dir");
        String namingDir = cacheDir + File.separator + "naming" + File.separator + namespace;
        if (StringUtils.isBlank(cacheDir)) {
            namingDir = System.getProperty("user.home") + File.separator + "nacos" + File.separator + "naming" 
                    + File.separator + namespace;
        }
        return namingDir;
    }
}
