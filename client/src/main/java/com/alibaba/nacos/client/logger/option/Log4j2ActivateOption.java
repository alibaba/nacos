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

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.*;
import org.apache.logging.log4j.core.async.ArrayBlockingQueueFactory;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;

import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LoggerHelper;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * @author zhuyong on 2017/4/13.
 */
public class Log4j2ActivateOption extends AbstractActiveOption {

    protected org.apache.logging.log4j.core.Logger logger;
    protected Configuration configuration;

    public Log4j2ActivateOption(org.apache.logging.log4j.Logger logger) {
        if (logger != null) {
            if (logger instanceof org.apache.logging.log4j.core.Logger) {
                this.logger = (org.apache.logging.log4j.core.Logger)logger;

                configuration = this.logger.getContext().getConfiguration();
            } else {
                throw new RuntimeException(
                    "logger must instanceof org.apache.logging.log4j.core.Logger, " + logger.getClass().getName());
            }
        }
    }

    @Override
    public void activateConsoleAppender(String target, String encoding) {
        org.apache.logging.log4j.core.Layout layout = org.apache.logging.log4j.core.layout.PatternLayout.newBuilder().
            withConfiguration(configuration)
            .withPattern(LoggerHelper.getPattern())
            .withCharset(Charset.forName(encoding))
            .build();
        org.apache.logging.log4j.core.appender.ConsoleAppender appender = ConsoleAppender.createAppender(layout, null,
            ConsoleAppender.Target.valueOf(target.toUpperCase().replace(".", "_")), "LoggerApiConsoleAppender", false,
            false, true);
        appender.start();
        removeAllAppenders(logger);
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAppender(String productName, String file, String encoding) {
        org.apache.logging.log4j.core.appender.RollingFileAppender appender = RollingFileAppender.newBuilder()
            .withName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender")
            .withFileName(LoggerHelper.getLogFileP(productName, file))
            .withAppend(true)
            .withBufferedIo(true)
            .setConfiguration(configuration)
            .withFilePattern(LoggerHelper.getLogFile(productName, file) + ".%d{yyyy-MM-dd}")
            .withLayout(buildLayout(encoding))
            .withCreateOnDemand(false)
            .withPolicy(TimeBasedTriggeringPolicy.createPolicy("1", "true"))
            .withStrategy(DefaultRolloverStrategy.createStrategy(null, null, "nomax", null, null, false, configuration))
            .build();

        appender.start();
        removeAllAppenders(logger);
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
        activateAppenderWithTimeAndSizeRolling(productName, file, encoding, size, "yyyy-MM-dd");
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern) {
        org.apache.logging.log4j.core.appender.RollingFileAppender appender = RollingFileAppender.newBuilder()
            .withName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender")
            .withFileName(LoggerHelper.getLogFileP(productName, file))
            .withAppend(true)
            .withBufferedIo(true)
            .setConfiguration(configuration)
            .withFilePattern(LoggerHelper.getLogFile(productName, file) + ".%d{" + datePattern + "}")
            .withLayout(buildLayout(encoding))
            .withCreateOnDemand(false)
            .withPolicy(CompositeTriggeringPolicy.createPolicy(TimeBasedTriggeringPolicy.createPolicy("1", "true"),
                SizeBasedTriggeringPolicy.createPolicy(size)))
            .withStrategy(DefaultRolloverStrategy.createStrategy(null, null, "nomax", null, null, false, configuration))
            .build();

        appender.start();
        removeAllAppenders(logger);
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern, int maxBackupIndex) {
        org.apache.logging.log4j.core.appender.RollingFileAppender appender = RollingFileAppender.newBuilder()
            .withName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender")
            .withFileName(LoggerHelper.getLogFileP(productName, file))
            .withAppend(true)
            .withBufferedIo(true)
            .setConfiguration(configuration)
            .withFilePattern(LoggerHelper.getLogFile(productName, file) + ".%d{" + datePattern + "}.%i")
            .withLayout(buildLayout(encoding))
            .withCreateOnDemand(false)
            .withPolicy(CompositeTriggeringPolicy.createPolicy(TimeBasedTriggeringPolicy.createPolicy("1", "true"),
                SizeBasedTriggeringPolicy.createPolicy(size)))
            .withStrategy(DefaultRolloverStrategy
                .createStrategy(String.valueOf(maxBackupIndex), "1", "max", null, null, false, configuration))
            .build();

        appender.start();
        removeAllAppenders(logger);
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAppenderWithSizeRolling(String productName, String file, String encoding, String size,
                                                int maxBackupIndex) {
        org.apache.logging.log4j.core.appender.RollingFileAppender appender = RollingFileAppender.newBuilder()
            .withName(productName + "." + file.replace(File.separatorChar, '.') + ".Appender")
            .withFileName(LoggerHelper.getLogFileP(productName, file))
            .withAppend(true)
            .withBufferedIo(true)
            .setConfiguration(configuration)
            .withFilePattern(LoggerHelper.getLogFile(productName, file) + ".%i")
            .withLayout(buildLayout(encoding))
            .withCreateOnDemand(false)
            .withPolicy(SizeBasedTriggeringPolicy.createPolicy(size))
            .withStrategy(DefaultRolloverStrategy
                .createStrategy(String.valueOf(maxBackupIndex), "1", "max", null, null, false, configuration))
            .build();

        appender.start();
        removeAllAppenders(logger);
        logger.addAppender(appender);

        setProductName(productName);
    }

    @Override
    public void activateAsync(int queueSize, int discardingThreshold) {
        List<Object[]> args = new ArrayList<Object[]>();

        if (queueSize != Integer.MIN_VALUE) {
            args.add(new Object[] {"setBufferSize", new Class<?>[] {int.class}, queueSize});
        }
        activateAsync(args);
    }

    @Override
    public void activateAsync(List<Object[]> args) {
        Map<String, Appender> appenders = logger.getAppenders();
        if (appenders == null) {
            throw new IllegalStateException("Activate async appender failed, no appender exist.");
        }

        AppenderRef[] refs = new AppenderRef[appenders.size()];
        int i = 0;
        for (Appender appender : appenders.values()) {
            configuration.addAppender(appender);
            refs[i++] = AppenderRef.createAppenderRef(appender.getName(), null, null);
        }

        AsyncAppender.Builder builder = AsyncAppender.newBuilder()
            .setName(productName + "." + logger.getName() + ".AsyncAppender")
            .setConfiguration(configuration)
            .setAppenderRefs(refs)
            .setBlockingQueueFactory(ArrayBlockingQueueFactory.<LogEvent>createFactory());

        invokeMethod(builder, args);

        AsyncAppender asyncAppender = builder.build();
        asyncAppender.start();

        removeAllAppenders(logger);
        logger.addAppender(asyncAppender);

        setProductName(productName);
    }

    @Override
    public void activateAppender(Logger logger) {
        if (!(logger.getDelegate() instanceof org.apache.logging.log4j.core.Logger)) {
            throw new IllegalArgumentException("logger must be org.apache.logging.log4j.core.Logger, but it's "
                + logger.getDelegate().getClass());
        }

        activateAppender(((org.apache.logging.log4j.core.Logger)logger.getDelegate()));

        setProductName(logger.getProductName());
    }

    protected void activateAppender(org.apache.logging.log4j.core.Logger logger) {
        removeAllAppenders(this.logger);

        Map<String, Appender> appenders = null;
        if ((appenders = logger.getAppenders()) != null) {
            for (Appender appender : appenders.values()) {
                this.logger.addAppender(appender);
            }
        }
    }

    @Override
    public void setLevel(Level level) {
        this.level = level;

        org.apache.logging.log4j.Level l = org.apache.logging.log4j.Level.toLevel(level.getName(),
            org.apache.logging.log4j.Level.ERROR);
        logger.setLevel(l);
        logger.getContext().getConfiguration().getLoggerConfig(this.logger.getName()).setLevel(l);
    }

    @Override
    public void setAdditivity(boolean additivity) {
        logger.setAdditive(additivity);
    }

    protected org.apache.logging.log4j.core.Layout buildLayout(String encoding) {
        org.apache.logging.log4j.core.Layout layout = org.apache.logging.log4j.core.layout.PatternLayout.newBuilder().
            withConfiguration(configuration)
            .withPattern(LoggerHelper.getPattern())
            .withCharset(Charset.forName(encoding))
            .build();
        return layout;
    }

    protected void removeAllAppenders(org.apache.logging.log4j.core.Logger logger) {
        Map<String, Appender> appenders = logger.getAppenders();
        if (appenders != null) {
            for (Appender appender : appenders.values()) {
                logger.removeAppender(appender);
            }
        }
    }
}
