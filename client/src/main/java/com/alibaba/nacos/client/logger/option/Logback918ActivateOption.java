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

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.*;
import ch.qos.logback.core.util.FileSize;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LogLog;
import com.alibaba.nacos.client.logger.support.LoggerHelper;

/**
 * logback 0.9.18版本及以前适用
 *
 * @author zhuyong 2014年3月20日 上午11:16:26
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class Logback918ActivateOption extends AbstractActiveOption {

    private ch.qos.logback.classic.Logger logger;

    public Logback918ActivateOption(Object logger) {
        if (logger instanceof ch.qos.logback.classic.Logger) {
            this.logger = (ch.qos.logback.classic.Logger)logger;
        } else {
            throw new IllegalArgumentException("logger must be instanceof ch.qos.logback.classic.Logger");
        }
    }

    @Override
    public void activateConsoleAppender(String target, String encoding) {
        ch.qos.logback.core.ConsoleAppender appender = new ch.qos.logback.core.ConsoleAppender();
        appender.setContext(LogbackLoggerContextUtil.getLoggerContext());
        appender.setTarget(target);
        PatternLayout layout = new PatternLayout();
        layout.setPattern(LoggerHelper.getPattern());
        layout.setContext(LogbackLoggerContextUtil.getLoggerContext());
        layout.start();
        appender.setLayout(layout);
        appender.start();

        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);
    }

    @Override
    public void activateAppender(String productName, String file, String encoding) {
        ch.qos.logback.core.Appender appender = getLogbackDailyRollingFileAppender(productName, file, encoding);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAsyncAppender(String productName, String file, String encoding) {
        activateAsyncAppender(productName, file, encoding, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public void activateAsyncAppender(String productName, String file, String encoding, int queueSize,
                                      int discardingThreshold) {
        activateAppender(productName, file, encoding);
        activateAsync(queueSize, discardingThreshold);
    }

    @Override
    public void setLevel(Level level) {
        this.level = level;
        logger.setLevel(ch.qos.logback.classic.Level.valueOf(level.getName()));
    }

    @Override
    public void setAdditivity(boolean additivity) {
        logger.setAdditive(additivity);
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size) {
        ch.qos.logback.core.Appender appender = getLogbackDailyAndSizeRollingFileAppender(productName, file, encoding,
            size);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    protected ch.qos.logback.core.Appender getLogbackDailyRollingFileAppender(String productName, String file,
                                                                              String encoding) {
        RollingFileAppender appender = new RollingFileAppender();
        appender.setContext(LogbackLoggerContextUtil.getLoggerContext());

        appender.setName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender");
        appender.setAppend(true);
        appender.setFile(LoggerHelper.getLogFile(productName, file));

        TimeBasedRollingPolicy rolling = new TimeBasedRollingPolicy();
        rolling.setParent(appender);
        rolling.setFileNamePattern(LoggerHelper.getLogFile(productName, file) + ".%d{yyyy-MM-dd}");
        rolling.setContext(LogbackLoggerContextUtil.getLoggerContext());
        rolling.start();
        appender.setRollingPolicy(rolling);

        PatternLayout layout = new PatternLayout();
        layout.setPattern(LoggerHelper.getPattern(productName));
        layout.setContext(LogbackLoggerContextUtil.getLoggerContext());
        layout.start();
        appender.setLayout(layout);

        // 启动
        appender.start();

        return appender;
    }

    protected ch.qos.logback.core.Appender getLogbackDailyAndSizeRollingFileAppender(String productName, String file,
                                                                                     String encoding, String size) {
        return getLogbackDailyAndSizeRollingFileAppender(productName, file, encoding, size, "yyyy-MM-dd", -1);
    }

    @Override
    public void activateAppender(Logger logger) {
        if (!(logger.getDelegate() instanceof ch.qos.logback.classic.Logger)) {
            throw new IllegalArgumentException(
                "logger must be ch.qos.logback.classic.Logger, but it's " + logger.getDelegate().getClass());
        }
        this.logger.detachAndStopAllAppenders();

        Iterator<ch.qos.logback.core.Appender<ILoggingEvent>> iter = ((ch.qos.logback.classic.Logger)logger
            .getDelegate()).iteratorForAppenders();
        while (iter.hasNext()) {
            ch.qos.logback.core.Appender<ILoggingEvent> appender = iter.next();
            this.logger.addAppender(appender);
        }
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern) {
        ch.qos.logback.core.Appender appender = getLogbackDailyAndSizeRollingFileAppender(productName, file, encoding,
            size, datePattern, -1);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    protected ch.qos.logback.core.Appender getLogbackDailyAndSizeRollingFileAppender(String productName, String file,
                                                                                     String encoding, String size,
                                                                                     String datePattern,
                                                                                     int maxBackupIndex) {
        RollingFileAppender appender = new RollingFileAppender();
        appender.setContext(LogbackLoggerContextUtil.getLoggerContext());

        appender.setName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender");
        appender.setAppend(true);
        appender.setFile(LoggerHelper.getLogFile(productName, file));

        TimeBasedRollingPolicy rolling = new TimeBasedRollingPolicy();
        rolling.setParent(appender);
        if (maxBackupIndex >= 0) {
            rolling.setMaxHistory(maxBackupIndex);
        }
        rolling.setFileNamePattern(LoggerHelper.getLogFile(productName, file) + ".%d{" + datePattern + "}.%i");
        rolling.setContext(LogbackLoggerContextUtil.getLoggerContext());

        SizeAndTimeBasedFNATP fnatp = new SizeAndTimeBasedFNATP();
        setMaxFileSize(fnatp, size);
        fnatp.setTimeBasedRollingPolicy(rolling);
        rolling.setTimeBasedFileNamingAndTriggeringPolicy(fnatp);

        rolling.start();
        appender.setRollingPolicy(rolling);

        PatternLayout layout = new PatternLayout();
        layout.setPattern(LoggerHelper.getPattern(productName));
        layout.setContext(LogbackLoggerContextUtil.getLoggerContext());
        layout.start();
        appender.setLayout(layout);

        // 启动
        appender.start();

        return appender;
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern, int maxBackupIndex) {
        ch.qos.logback.core.Appender appender = getLogbackDailyAndSizeRollingFileAppender(productName, file, encoding,
            size, datePattern,
            maxBackupIndex);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAppenderWithSizeRolling(String productName, String file, String encoding, String size,
                                                int maxBackupIndex) {
        ch.qos.logback.core.Appender appender = getSizeRollingAppender(productName, file, encoding, size,
            maxBackupIndex);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAsync(int queueSize, int discardingThreshold) {
        List<Object[]> args = new ArrayList<Object[]>();

        if (queueSize != Integer.MIN_VALUE) {
            args.add(new Object[] {"setQueueSize", new Class<?>[] {int.class}, queueSize});
        }

        if (discardingThreshold != Integer.MIN_VALUE) {
            args.add(new Object[] {"setDiscardingThreshold", new Class<?>[] {int.class}, discardingThreshold});
        }

        activateAsync(args);
    }

    @Override
    public void activateAsync(List<Object[]> args) {
        AsyncAppender asynAppender = new AsyncAppender();

        invokeMethod(asynAppender, args);

        asynAppender.setName(productName + "." + logger.getName() + ".AsyncAppender");
        asynAppender.setContext(LogbackLoggerContextUtil.getLoggerContext());

        Iterator<Appender<ILoggingEvent>> iterator = logger.iteratorForAppenders();
        boolean hasAppender = false;
        while (iterator.hasNext()) {
            hasAppender = true;
            asynAppender.addAppender(iterator.next());
        }

        if (!hasAppender) {
            throw new IllegalStateException("Activate async appender failed, no appender exist.");
        }

        asynAppender.start();

        iterator = logger.iteratorForAppenders();
        while (iterator.hasNext()) {
            logger.detachAppender(iterator.next());
        }

        logger.addAppender(asynAppender);

        setProductName(productName);
    }

    protected ch.qos.logback.core.Appender getSizeRollingAppender(String productName, String file, String encoding,
                                                                  String size, int maxBackupIndex) {
        RollingFileAppender appender = new RollingFileAppender();
        appender.setContext(LogbackLoggerContextUtil.getLoggerContext());

        appender.setName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender");
        appender.setAppend(true);
        appender.setFile(LoggerHelper.getLogFile(productName, file));

        SizeBasedTriggeringPolicy triggerPolicy = new SizeBasedTriggeringPolicy();
        setMaxFileSize(triggerPolicy, size);
        triggerPolicy.setContext(LogbackLoggerContextUtil.getLoggerContext());
        triggerPolicy.start();

        FixedWindowRollingPolicy rolling = new FixedWindowRollingPolicy();
        rolling.setContext(LogbackLoggerContextUtil.getLoggerContext());
        rolling.setParent(appender);
        rolling.setFileNamePattern(LoggerHelper.getLogFile(productName, file) + ".%i");
        rolling.setParent(appender);
        if (maxBackupIndex >= 0) {
            rolling.setMaxIndex(maxBackupIndex);
        }
        rolling.start();

        appender.setRollingPolicy(rolling);
        appender.setTriggeringPolicy(triggerPolicy);

        PatternLayout layout = new PatternLayout();
        layout.setPattern(LoggerHelper.getPattern(productName));
        layout.setContext(LogbackLoggerContextUtil.getLoggerContext());
        layout.start();
        appender.setLayout(layout);

        // 启动
        appender.start();
        return appender;
    }

    /**
     * logback 1.1.8开始不再支持setMaxFileSize(String)方法
     */
    protected void setMaxFileSize(Object policy, String size) {
        try {
            try {
                Method setMaxFileSizeMethod = policy.getClass().getDeclaredMethod("setMaxFileSize", String.class);
                setMaxFileSizeMethod.invoke(policy, size);
            } catch (NoSuchMethodException e) {
                Method setMaxFileSizeMethod = policy.getClass().getDeclaredMethod("setMaxFileSize", FileSize.class);
                setMaxFileSizeMethod.invoke(policy, FileSize.valueOf(size));
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to setMaxFileSize", t);
        }
    }
}
