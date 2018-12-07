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
package com.alibaba.nacos.client.logger.slf4j;

import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.nop.NopLogger;
import com.alibaba.nacos.client.logger.support.ILoggerFactory;
import com.alibaba.nacos.client.logger.support.LogLog;

/**
 * Slf4jLogger Factory
 *
 * @author Nacos
 */
public class Slf4jLoggerFactory implements ILoggerFactory {

    public Slf4jLoggerFactory() throws ClassNotFoundException {
        Class.forName("org.slf4j.impl.StaticLoggerBinder");
    }

    public Logger getLogger(String name) {
        try {
            return new Slf4jLogger(org.slf4j.LoggerFactory.getLogger(name));
        } catch (Throwable t) {
            LogLog.error("Failed to get Slf4jLogger", t);
            return new NopLogger();
        }
    }

    public Logger getLogger(Class<?> clazz) {
        try {
            return new Slf4jLogger(org.slf4j.LoggerFactory.getLogger(clazz));
        } catch (Throwable t) {
            LogLog.error("Failed to get Slf4jLogger", t);
            return new NopLogger();
        }
    }
}
