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

import com.alibaba.nacos.client.logging.AbstractNacosLogging;
import com.alibaba.nacos.client.logging.log4j2.Log4J2NacosLogging;
import com.alibaba.nacos.client.logging.logback.LogbackNacosLogging;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.9.0
 */
public class LogUtils {

    public static final Logger NAMING_LOGGER;

    static {
        try {
            boolean isLogback = false;
            AbstractNacosLogging nacosLogging;

            try {
                Class.forName("ch.qos.logback.classic.Logger");
                nacosLogging = new LogbackNacosLogging();
                isLogback = true;
            } catch (ClassNotFoundException e) {
                nacosLogging = new Log4J2NacosLogging();
            }

            try {
                nacosLogging.loadConfiguration();
            } catch (Throwable t) {
                if (isLogback) {
                    getLogger(LogUtils.class).warn("Load Logback Configuration of Nacos fail, message: {}",
                        t.getMessage());
                } else {
                    getLogger(LogUtils.class).warn("Load Log4j Configuration of Nacos fail, message: {}",
                        t.getMessage());
                }
            }
        } catch (Throwable t1) {
            getLogger(LogUtils.class).warn("Init Nacos Logging fail, message: {}", t1.getMessage());
        }

        NAMING_LOGGER = getLogger("com.alibaba.nacos.client.naming");
    }

    public static Logger logger(Class<?> clazz) {
        return getLogger(clazz);
    }

}
