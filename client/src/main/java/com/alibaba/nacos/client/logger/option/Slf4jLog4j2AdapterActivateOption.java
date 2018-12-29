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

import java.lang.reflect.Field;

import com.alibaba.nacos.client.logger.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author zhuyong on 2017/4/18.
 */
public class Slf4jLog4j2AdapterActivateOption extends Log4j2ActivateOption {

    private static Field loggerField = null;

    static {
        try {
            loggerField = org.apache.logging.slf4j.Log4jLogger.class.getDeclaredField("logger");
            loggerField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("logger must be instanceof org.apache.logging.slf4j.Log4jLogger", e);
        }
    }

    public Slf4jLog4j2AdapterActivateOption(Object logger) {
        super(null);

        try {
            org.apache.logging.log4j.core.Logger log4j2Logger = (org.apache.logging.log4j.core.Logger)loggerField.get(
                logger);
            super.logger = log4j2Logger;
            super.configuration = super.logger.getContext().getConfiguration();
        } catch (Exception e) {
            throw new RuntimeException("logger must be instanceof org.apache.logging.slf4j.Log4jLogger", e);
        }
    }

    @Override
    @SuppressFBWarnings("NM_WRONG_PACKAGE")
    public void activateAppender(Logger logger) {
        if (!(logger.getDelegate() instanceof org.apache.logging.slf4j.Log4jLogger)) {
            throw new IllegalArgumentException(
                "logger must be org.apache.logging.slf4j.Log4jLogger, but it's "
                    + logger.getDelegate().getClass());
        }

        try {
            org.apache.logging.log4j.core.Logger log4j2Logger = (org.apache.logging.log4j.core.Logger)loggerField.get(
                logger.getDelegate());
            super.activateAppender(log4j2Logger);
        } catch (Exception e) {
            throw new RuntimeException("activateAppender error, ", e);
        }
    }
}
