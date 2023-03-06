/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

/**
 * A convenient tool to get property or env value.
 *
 * @author Pixy Yuan on 2022/3/24
 */
public class PropertyUtils {

    private PropertyUtils() {
    }
    
    private static final String PROCESSORS_ENV_NAME = "NACOS_COMMON_PROCESSORS";
    
    private static final String PROCESSORS_PROP_NAME = "nacos.common.processors";
    
    /**
     * Get system env or property value.
     *
     * <p>If {@link System#getenv()} has no value for {@code envName},
     * return {@link System#getProperty(String)}.
     */
    public static String getProperty(String propertyName, String envName) {
        return System.getenv().getOrDefault(envName, System.getProperty(propertyName));
    }
    
    /**
     * Get system env or property value.
     *
     * <p>If {@link System#getenv()} has no value for {@code envName},
     * return {@link System#getProperty(String, String)} or {@code defaultValue}.
     */
    public static String getProperty(String propertyName, String envName, String defaultValue) {
        return System.getenv().getOrDefault(envName, System.getProperty(propertyName, defaultValue));
    }
    
    /**
     * Get processors count maybe preset by env or property.
     */
    public static int getProcessorsCount() {
        int processorsCount = 0;
        String processorsCountPreSet = getProperty(PROCESSORS_PROP_NAME, PROCESSORS_ENV_NAME);
        if (processorsCountPreSet != null) {
            try {
                processorsCount = Integer.parseInt(processorsCountPreSet);
            } catch (NumberFormatException ignored) {
            }
        }
        if (processorsCount <= 0) {
            processorsCount = Runtime.getRuntime().availableProcessors();
        }
        return processorsCount;
    }
}
