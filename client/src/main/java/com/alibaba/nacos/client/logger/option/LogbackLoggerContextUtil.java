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
package com.alibaba.nacos.client.logger.option;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.LogbackException;

/**
 * Logback Context Util
 *
 * @author Nacos
 */
public class LogbackLoggerContextUtil {

    private static LoggerContext loggerContext = null;

    public static LoggerContext getLoggerContext() {
        if (loggerContext == null) {
            ILoggerFactory lcObject = LoggerFactory.getILoggerFactory();

            if (!(lcObject instanceof LoggerContext)) {
                throw new LogbackException(
                    "Expected LOGBACK binding with SLF4J, but another log system has taken the place: "
                        + lcObject.getClass().getSimpleName());
            }

            loggerContext = (LoggerContext)lcObject;
        }

        return loggerContext;
    }
}
