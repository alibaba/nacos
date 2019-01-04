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
import com.alibaba.nacos.client.logger.option.ActivateOption;
import com.alibaba.nacos.client.logger.support.LoggerHelper;
import com.alibaba.nacos.client.logger.support.LoggerSupport;
import com.alibaba.nacos.client.logger.util.MessageUtil;

import java.lang.reflect.Constructor;

/**
 * slf4j logger
 *
 * @author Nacos
 */
public class Slf4jLogger extends LoggerSupport implements Logger {

    private static boolean CanUseEncoder = false;
    private static final String LOGBACK_CLASSNAME = "ch.qos.logback.classic.Logger";
    private static final String SLF4JLOG4J_CLASSNAME = "org.apache.logging.slf4j.Log4jLogger";

    static {
        try {
            // logback从0.9.19开始采用encoder，@see http://logback.qos.ch/manual/encoders.html
            Class.forName("ch.qos.logback.classic.encoder.PatternLayoutEncoder");
            CanUseEncoder = true;
        } catch (ClassNotFoundException e) {
            CanUseEncoder = false;
        }
    }

    private org.slf4j.Logger delegate;

    @SuppressWarnings("unchecked")
    public Slf4jLogger(org.slf4j.Logger delegate) {
        super(delegate);
        if (delegate == null) {
            throw new IllegalArgumentException("delegate Logger is null");
        }
        this.delegate = delegate;

        String activateOptionClass = null;
        if (LOGBACK_CLASSNAME.equals(delegate.getClass().getName())) {
            if (CanUseEncoder) {
                activateOptionClass = "com.alibaba.nacos.client.logger.option.LogbackActivateOption";
            } else {
                activateOptionClass = "com.alibaba.nacos.client.logger.option.Logback918ActivateOption";
            }
        } else if (SLF4JLOG4J_CLASSNAME.equals(delegate.getClass().getName())) {
            activateOptionClass = "com.alibaba.nacos.client.logger.option.Slf4jLog4j2AdapterActivateOption";
        }

        if (activateOptionClass == null) {
            throw new IllegalArgumentException("delegate must be logback impl or slf4j-log4j impl");
        }

        try {
            Class<ActivateOption> clazz = (Class<ActivateOption>)Class.forName(activateOptionClass);
            Constructor<ActivateOption> c = clazz.getConstructor(Object.class);
            this.activateOption = c.newInstance(delegate);
        } catch (Exception e) {
            throw new IllegalArgumentException("delegate must be logback impl or slf4j-log4j impl", e);
        }
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
            delegate.debug(MessageUtil.getMessage(context, format), args);
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
            delegate.info(MessageUtil.getMessage(context, format), args);
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
            delegate.warn(MessageUtil.getMessage(context, format), args);
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
            delegate.error(MessageUtil.getMessage(context, errorCode, format), args);
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
