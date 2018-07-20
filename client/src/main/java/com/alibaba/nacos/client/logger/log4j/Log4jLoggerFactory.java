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
package com.alibaba.nacos.client.logger.log4j;

import org.apache.log4j.LogManager;

import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.nop.NopLogger;
import com.alibaba.nacos.client.logger.support.ILoggerFactory;
import com.alibaba.nacos.client.logger.support.LogLog;

/**
 * Log4jLogger Factory
 * @author Nacos
 *
 */
public class Log4jLoggerFactory implements ILoggerFactory {

    public Log4jLoggerFactory() throws ClassNotFoundException {
        Class.forName("org.apache.log4j.Level");
    }

    public Logger getLogger(Class<?> clazz) {
        try {
            return new Log4jLogger(LogManager.getLogger(clazz));
        } catch (Throwable t) {
            LogLog.error("Failed to get Log4jLogger", t);
            return new NopLogger();
        }
    }

    public Logger getLogger(String name) {
        try {
            return new Log4jLogger(LogManager.getLogger(name));
        } catch (Throwable t) {
            LogLog.error("Failed to get Log4jLogger", t);
            return new NopLogger();
        }
    }
}
