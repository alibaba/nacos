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

package com.alibaba.nacos.core.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.springframework.boot.context.logging.LoggingApplicationListener.CONFIG_PROPERTY;
import static org.springframework.core.io.ResourceLoader.CLASSPATH_URL_PREFIX;

/**
 * For init logging configuration.
 *
 * @author horizonzy
 * @since 1.4.1
 */
public class LoggingApplicationListener implements NacosApplicationListener {
    
    private static final String DEFAULT_NACOS_LOGBACK_LOCATION = CLASSPATH_URL_PREFIX + "META-INF/logback/nacos.xml";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingApplicationListener.class);
    
    @Override
    public void starting() {
    
    }
    
    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        if (!environment.containsProperty(CONFIG_PROPERTY)) {
            System.setProperty(CONFIG_PROPERTY, DEFAULT_NACOS_LOGBACK_LOCATION);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("There is no property named \"{}\" in Spring Boot Environment, "
                                + "and whose value is {} will be set into System's Properties", CONFIG_PROPERTY,
                        DEFAULT_NACOS_LOGBACK_LOCATION);
            }
        }
    }
    
    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
    
    }
    
    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
    
    }
    
    @Override
    public void started(ConfigurableApplicationContext context) {
    
    }
    
    @Override
    public void running(ConfigurableApplicationContext context) {
    
    }
    
    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
    
    }
}
