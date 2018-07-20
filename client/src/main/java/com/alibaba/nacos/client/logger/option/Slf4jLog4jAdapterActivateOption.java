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
 * Slf4j-log4j12架构下的ActivateOption实现
 *
 * @author zhuyong 2014年3月20日 上午10:26:04
 */
public class Slf4jLog4jAdapterActivateOption extends Log4jActivateOption {

    private static Field loggerField = null;

    static {
        try {
            loggerField = org.slf4j.impl.Log4jLoggerAdapter.class.getDeclaredField("logger");
            loggerField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("logger must be instanceof org.slf4j.impl.Log4jLoggerAdapter", e);
        }
    }

    public Slf4jLog4jAdapterActivateOption(Object logger) {
        super(null);

        try {
            org.apache.log4j.Logger log4jLogger = (org.apache.log4j.Logger) loggerField.get(logger);
            super.logger = log4jLogger;
        } catch (Exception e) {
            throw new RuntimeException("logger must be instanceof org.slf4j.impl.Log4jLoggerAdapter", e);
        }
    }

    @Override
    @SuppressFBWarnings("NM_WRONG_PACKAGE")
    public void activateAppender(Logger logger) {
        if (!(logger.getDelegate() instanceof org.slf4j.impl.Log4jLoggerAdapter)) {
            throw new IllegalArgumentException(
                    "logger must be org.slf4j.impl.Log4jLoggerAdapter, but it's "
                            + logger.getDelegate().getClass());
        }

        try {
            org.apache.log4j.Logger log4jLogger =
                    (org.apache.log4j.Logger) loggerField.get(logger.getDelegate());
            super.activateAppender(log4jLogger);
            setProductName(logger.getProductName());
        } catch (Exception e) {
            throw new RuntimeException("activateAppender error, ", e);
        }
    }
}
