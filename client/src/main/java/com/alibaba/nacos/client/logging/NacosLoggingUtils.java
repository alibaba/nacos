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

package com.alibaba.nacos.client.logging;

import com.alibaba.nacos.client.logging.log4j2.Log4J2NacosLogging;
import com.alibaba.nacos.client.logging.logback.LogbackNacosLogging;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * nacos logging.
 *
 * @author mai.jh
 */
public final class NacosLoggingUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosLoggingUtils.class);
    
    /**
     * Load logging Configuration.
     */
    public static void loadConfiguration() {
        loadConfiguration(null);
    }
    
    /**
     * Load the log configuration file according to the specified {@link AbstractNacosLogging} implementation, If {@code
     * logging} is null, use the default policy.
     *
     * @param logging {@link AbstractNacosLogging}
     */
    public static void loadConfiguration(AbstractNacosLogging logging) {
        try {
            if (logging != null) {
                try {
                    logging.loadConfiguration();
                } catch (Exception e) {
                    LOGGER.warn("Customize the {} load Configuration fail, message: {}", logging.getClass().getName(), e.getMessage());
                    loadDefaultConfiguration();
                }
            } else {
                loadDefaultConfiguration();
            }
        } catch (Throwable ex) {
            LOGGER.warn("Init Nacos Logging fail, message: {}", ex.getMessage());
        }
    }
    
    /**
     * Load default Configuration.
     */
    private static void loadDefaultConfiguration() {
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
                LOGGER.warn("Load Logback Configuration of Nacos fail, message: {}", t.getMessage());
            } else {
                LOGGER.warn("Load Log4j Configuration of Nacos fail, message: {}", t.getMessage());
            }
        }
    }
    
}
