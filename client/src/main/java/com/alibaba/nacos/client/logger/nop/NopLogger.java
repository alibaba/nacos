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
package com.alibaba.nacos.client.logger.nop;

import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LoggerSupport;

/**
 * NopLogger
 *
 * @author Nacos
 */
public class NopLogger extends LoggerSupport implements Logger {

    public NopLogger() {
        super(null);
    }

    @Override
    public void debug(String context, String message) {

    }

    @Override
    public void debug(String context, String format, Object... args) {

    }

    @Override
    public void info(String context, String message) {

    }

    @Override
    public void info(String context, String format, Object... args) {

    }

    public void warn(String message, Throwable t) {

    }

    @Override
    public void warn(String context, String message) {

    }

    @Override
    public void warn(String context, String format, Object... args) {

    }

    @Override
    public void error(String context, String errorCode, String message) {

    }

    @Override
    public void error(String context, String errorCode, String message, Throwable t) {

    }

    @Override
    public void error(String context, String errorCode, String format, Object... args) {

    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    @Override
    public boolean isErrorEnabled() {
        return false;
    }
}
