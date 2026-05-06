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

package com.alibaba.nacos.plugin.control.utils;

import com.alibaba.nacos.common.utils.StringUtils;

import java.nio.file.Paths;

/**
 * Control plugin env utils.
 *
 * @author xiweng.yy
 */
public class EnvUtils {
    
    public static final String NACOS_HOME_KEY = "nacos.home";
    
    private static final String NACOS_HOME_PROPERTY = "user.home";
    
    private static final String NACOS_HOME_ADDITIONAL_FILEPATH = "nacos";
    
    private static String nacosHomePath = null;
    
    public static String getNacosHome() {
        if (StringUtils.isBlank(nacosHomePath)) {
            String nacosHome = System.getProperty(NACOS_HOME_KEY);
            if (StringUtils.isBlank(nacosHome)) {
                nacosHome = Paths.get(System.getProperty(NACOS_HOME_PROPERTY), NACOS_HOME_ADDITIONAL_FILEPATH)
                        .toString();
            }
            return nacosHome;
        }
        return nacosHomePath;
    }
}
