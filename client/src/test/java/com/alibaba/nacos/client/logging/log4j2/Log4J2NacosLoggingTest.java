/*
 *
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
 *
 */

package com.alibaba.nacos.client.logging.log4j2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class Log4J2NacosLoggingTest {
    
    private static final String NACOS_LOGGER_PREFIX = "com.alibaba.nacos";
    
    @Test
    public void testLoadConfiguration() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration contextConfiguration = loggerContext.getConfiguration();
        Assert.assertEquals(0, contextConfiguration.getLoggers().size());
        Log4J2NacosLogging log4J2NacosLogging = new Log4J2NacosLogging();
        //when
        log4J2NacosLogging.loadConfiguration();
        //then
        loggerContext = (LoggerContext) LogManager.getContext(false);
        contextConfiguration = loggerContext.getConfiguration();
        Map<String, LoggerConfig> nacosClientLoggers = contextConfiguration.getLoggers();
        Assert.assertEquals(4, nacosClientLoggers.size());
        for (Map.Entry<String, LoggerConfig> loggerEntry : nacosClientLoggers.entrySet()) {
            String loggerName = loggerEntry.getKey();
            Assert.assertTrue(loggerName.startsWith(NACOS_LOGGER_PREFIX));
        }
        
    }
}