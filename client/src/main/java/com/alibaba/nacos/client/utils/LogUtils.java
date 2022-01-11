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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.client.logging.NacosLogging;
import com.alibaba.nacos.client.logging.logback.LogbackNacosLogging;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Log utils.
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.9.0
 */
public class LogUtils {
    
    public static final Logger NAMING_LOGGER;
    
    static {
        NacosLogging.getInstance().loadConfiguration();
        NAMING_LOGGER = logger("com.alibaba.nacos.client.naming");
    }
    
    /**
     * get logger by name.
     * @param name logger name
     */
    public static Logger logger(String name) {
        if (NacosLogging.getInstance().isLogback()) {
            return LogbackNacosLogging.getLoggerContext().getLogger(name);
        }
        return getLogger(name);
    }
    
    /**
     * get logger by clazz.
     *
     * @param clazz logger clazz
     */
    public static Logger logger(Class<?> clazz) {
        if (NacosLogging.getInstance().isLogback()) {
            return LogbackNacosLogging.getLoggerContext().getLogger(clazz);
        }
        return getLogger(clazz);
    }
    
}
