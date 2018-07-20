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

import org.apache.log4j.*;

import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LoggerHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ActivateOption的Log4j实现
 *
 * @author zhuyong 2014年3月20日 上午10:24:36
 */
public class Log4jActivateOption extends AbstractActiveOption {

    protected org.apache.log4j.Logger logger;

    public Log4jActivateOption(org.apache.log4j.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void activateConsoleAppender(String target, String encoding) {
        org.apache.log4j.ConsoleAppender appender = new org.apache.log4j.ConsoleAppender();
        appender.setLayout(new PatternLayout(LoggerHelper.getPattern()));
        appender.setTarget(target);
        appender.setEncoding(encoding);
        appender.activateOptions();

        logger.removeAllAppenders();
        logger.addAppender(appender);
    }

    @Override
    public void activateAppender(String productName, String file, String encoding) {
        org.apache.log4j.Appender appender = getLog4jDailyRollingFileAppender(productName, file, encoding);
        logger.removeAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAsyncAppender(String productName, String file, String encoding) {
        activateAsyncAppender(productName, file, encoding, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public void activateAsyncAppender(String productName, String file, String encoding, int queueSize,
                                      int discardingThreshold) {
        activateAppender(productName, file, encoding);
        activateAsync(queueSize, discardingThreshold);
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size) {
        activateAppender(productName, file, encoding);
    }

    @Override
    public void setLevel(com.alibaba.nacos.client.logger.Level level) {
        this.level = level;
        logger.setLevel(org.apache.log4j.Level.toLevel(level.getName()));
    }

    @Override
    public void setAdditivity(boolean additivity) {
        logger.setAdditivity(additivity);
    }

    protected org.apache.log4j.Appender getLog4jDailyRollingFileAppender(String productName, String file,
                                                                         String encoding) {
        DailyRollingFileAppender appender = new DailyRollingFileAppender();
        appender.setName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender");
        appender.setLayout(new PatternLayout(LoggerHelper.getPattern(productName)));
        appender.setAppend(true);
        appender.setFile(LoggerHelper.getLogFileP(productName, file));
        appender.setEncoding(encoding);
        appender.activateOptions();

        return appender;
    }

    @Override
    public void activateAppender(Logger logger) {
        if (!(logger.getDelegate() instanceof org.apache.log4j.Logger)) {
            throw new IllegalArgumentException(
                    "logger must be org.apache.log4j.Logger, but it's " + logger.getDelegate().getClass());
        }
        activateAppender((org.apache.log4j.Logger) logger.getDelegate());

        setProductName(logger.getProductName());
    }

    protected void activateAppender(org.apache.log4j.Logger logger) {
        this.logger.removeAllAppenders();

        Enumeration<?> enums = logger.getAllAppenders();
        while (enums != null && enums.hasMoreElements()) {
            this.logger.addAppender((Appender) enums.nextElement());
        }
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern) {
        Appender appender = getLog4jRollingFileAppender(productName, file, encoding, size, datePattern, -1);

        logger.removeAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern, int maxBackupIndex) {
        Appender appender = getLog4jRollingFileAppender(productName, file, encoding, size, datePattern, maxBackupIndex);
        logger.removeAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    protected org.apache.log4j.Appender getLog4jRollingFileAppender(String productName, String file, String encoding,
                                                                    String size, String datePattern,
                                                                    int maxBackupIndex) {
        RollingFileAppender appender = new RollingFileAppender();
        appender.setName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender");
        appender.setLayout(new PatternLayout(LoggerHelper.getPattern(productName)));
        appender.setAppend(true);
        appender.setFile(LoggerHelper.getLogFileP(productName, file));
        appender.setEncoding(encoding);
        appender.setMaxFileSize(size);
        if (maxBackupIndex >= 0) {
            // 等于0表示直接truck
            appender.setMaxBackupIndex(maxBackupIndex);
        }
        appender.activateOptions();

        return appender;
    }

    @Override
    public void activateAppenderWithSizeRolling(String productName, String file, String encoding, String size,
                                                int maxBackupIndex) {
        Appender appender = getLog4jRollingFileAppender(productName, file, encoding, size, null, maxBackupIndex);
        logger.removeAllAppenders();
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAsync(int queueSize, int discardingThreshold) {
        // discardingThreshold is unused for log4j
        List<Object[]> args = new ArrayList<Object[]>();

        if (queueSize != Integer.MIN_VALUE) {
            args.add(new Object[] { "setBufferSize", new Class<?>[] { int.class }, queueSize });
        }
        activateAsync(args);
    }

    @Override
    public void activateAsync(List<Object[]> args) {
        AsyncAppender asyncAppender = new AsyncAppender();

        invokeMethod(asyncAppender, args);

        asyncAppender.setName(productName + "." + logger.getName() + ".AsyncAppender");
        Enumeration<Appender> appenders = logger.getAllAppenders();

        if (appenders == null) {
            throw new IllegalStateException("Activate async appender failed, no appender exist.");
        }

        while (appenders.hasMoreElements()) {
            asyncAppender.addAppender(appenders.nextElement());
        }

        appenders = logger.getAllAppenders();
        while (appenders.hasMoreElements()) {
            logger.removeAppender(appenders.nextElement());
        }

        logger.addAppender(asyncAppender);

        setProductName(productName);
    }
}
