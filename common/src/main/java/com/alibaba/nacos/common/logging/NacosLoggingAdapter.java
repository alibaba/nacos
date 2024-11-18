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

package com.alibaba.nacos.common.logging;

/**
 * Nacos client logging adapter.
 *
 * @author xiweng.yy
 */
public interface NacosLoggingAdapter {
    
    /**
     * Whether current adapter is adapted for specified logger class.
     *
     * @param loggerClass {@link org.slf4j.Logger} implementation class
     * @return {@code true} if current adapter can adapt this {@link org.slf4j.Logger} implementation, otherwise {@code
     * false}
     */
    boolean isAdaptedLogger(Class<?> loggerClass);
    
    /**
     * Load Nacos logging configuration into log context.
     *
     * @param loggingProperties logging properties
     */
    void loadConfiguration(NacosLoggingProperties loggingProperties);
    
    /**
     * Whether need reload configuration into log context.
     *
     * @return {@code true} when context don't contain nacos logging configuration. otherwise {@code false}
     */
    boolean isNeedReloadConfiguration();
    
    /**
     * Get current logging default config location.
     *
     * @return default config location
     */
    String getDefaultConfigLocation();
    
    /**
     * Whether current adapter enabled, design for users which want to log nacos client into app logs.
     *
     * @return {@code true} when enabled, otherwise {@code false}, default {@code true}
     */
    default boolean isEnabled() {
        return true;
    }
}
