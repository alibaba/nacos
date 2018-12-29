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

import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.option.Log4j2ActivateOption;
import com.alibaba.nacos.client.logger.support.LoggerHelper;
import com.alibaba.nacos.client.logger.support.LoggerSupport;
import com.alibaba.nacos.client.logger.util.MessageUtil;

/**
 * Log4j2Logger
 *
 * @author Nacos
 */
public class Log4j2Logger extends LoggerSupport implements Logger {

    private org.apache.logging.log4j.Logger delegate;

    public Log4j2Logger(org.apache.logging.log4j.Logger delegate) {
        super(delegate);

        if (delegate == null) {
            throw new IllegalArgumentException("delegate Logger is null");
        }
        this.delegate = delegate;

        this.activateOption = new Log4j2ActivateOption(delegate);
    }

    @Override
    public void debug(String context, String message) {
        if (isDebugEnabled()) {
            message = LoggerHelper.getResourceBundleString(getProductName(), message);
            delegate.debug(MessageUtil.getMessage(context, message));
        }
    }

    @Override
    public void debug(String context, String format, Object... args) {
        if (isDebugEnabled()) {
            format = LoggerHelper.getResourceBundleString(getProductName(), format);
            delegate.debug(MessageUtil.getMessage(context, MessageUtil.formatMessage(format, args)));
        }
    }

    @Override
    public void info(String context, String message) {
        if (isInfoEnabled()) {
            message = LoggerHelper.getResourceBundleString(getProductName(), message);
            delegate.info(MessageUtil.getMessage(context, message));
        }
    }

    @Override
    public void info(String context, String format, Object... args) {
        if (isInfoEnabled()) {
            format = LoggerHelper.getResourceBundleString(getProductName(), format);
            delegate.info(MessageUtil.getMessage(context, MessageUtil.formatMessage(format, args)));
        }
    }

    @Override
    public void warn(String message, Throwable t) {
        if (isWarnEnabled()) {
            message = LoggerHelper.getResourceBundleString(getProductName(), message);
            delegate.warn(MessageUtil.getMessage(null, message), t);
        }
    }

    @Override
    public void warn(String context, String message) {
        if (isWarnEnabled()) {
            message = LoggerHelper.getResourceBundleString(getProductName(), message);
            delegate.warn(MessageUtil.getMessage(context, message));
        }
    }

    @Override
    public void warn(String context, String format, Object... args) {
        if (isWarnEnabled()) {
            format = LoggerHelper.getResourceBundleString(getProductName(), format);
            delegate.warn(MessageUtil.getMessage(context, MessageUtil.formatMessage(format, args)));
        }
    }

    @Override
    public void error(String context, String errorCode, String message) {
        if (isErrorEnabled()) {
            message = LoggerHelper.getResourceBundleString(getProductName(), message);
            delegate.error(MessageUtil.getMessage(context, errorCode, message));
        }
    }

    @Override
    public void error(String context, String errorCode, String message, Throwable t) {
        if (isErrorEnabled()) {
            message = LoggerHelper.getResourceBundleString(getProductName(), message);
            delegate.error(MessageUtil.getMessage(context, errorCode, message), t);
        }
    }

    @Override
    public void error(String context, String errorCode, String format, Object... args) {
        if (isErrorEnabled()) {
            format = LoggerHelper.getResourceBundleString(getProductName(), format);
            delegate.error(MessageUtil.getMessage(context, errorCode, MessageUtil.formatMessage(format, args)));
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }
}
