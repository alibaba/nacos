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
package com.alibaba.nacos.client.logger.log4j2;

import org.apache.logging.log4j.LogManager;

import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.nop.NopLogger;
import com.alibaba.nacos.client.logger.support.ILoggerFactory;
import com.alibaba.nacos.client.logger.support.LogLog;

/**
 * Log4j2Logger Factory
 *
 * @author Nacos
 */
public class Log4j2LoggerFactory implements ILoggerFactory {

    public Log4j2LoggerFactory() throws ClassNotFoundException {
        Class.forName("org.apache.logging.log4j.core.Logger");
    }

    @Override
    public Logger getLogger(Class<?> clazz) {
        try {
            return new Log4j2Logger(LogManager.getLogger(clazz));
        } catch (Throwable t) {
            LogLog.error("Failed to get Log4j2Logger", t);
            return new NopLogger();
        }
    }

    @Override
    public Logger getLogger(String name) {
        try {
            return new Log4j2Logger(LogManager.getLogger(name));
        } catch (Throwable t) {
            LogLog.error("Failed to get Log4j2Logger", t);
            return new NopLogger();
        }
    }
}
