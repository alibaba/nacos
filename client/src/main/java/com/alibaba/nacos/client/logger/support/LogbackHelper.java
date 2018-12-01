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
package com.alibaba.nacos.client.logger.support;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.slf4j.ILoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author zhuyong on 2017/6/28.
 */
public class LogbackHelper {

    private static boolean Logback = false;
    private static Field f, f1;
    private static ILoggerFactory lcObject;

    static {
        try {
            Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
            // 这里可能会加载到应用中依赖的logback，因此需要判断classloader
            if (loggerClass.getClassLoader().equals(LogbackHelper.class.getClassLoader())) {
                ILoggerFactory lc = org.slf4j.LoggerFactory.getILoggerFactory();

                if (!(lc instanceof LoggerContext)) {
                    LogLog.warn(
                        "expected logback binding with SLF4J, but another log system has taken the place: " + lcObject
                            .getClass().getSimpleName());
                } else {
                    lcObject = lc;

                    f = PatternLayoutBase.class.getDeclaredField("head");
                    f.setAccessible(true);

                    f1 = ThrowableProxyConverter.class.getDeclaredField("lengthOption");
                    f1.setAccessible(true);

                    Logback = true;
                }
            }
        } catch (Throwable t) {
            LogLog.error("failed to init LogbackHelper, " + t.getMessage());
        }
    }

    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public static Boolean setDepth(int depth) {
        if (Logback) {
            if (depth == -1) {
                depth = Integer.MAX_VALUE;
            }
            try {
                LoggerContext loggerContext = (LoggerContext)lcObject;

                List<Logger> loggers = loggerContext.getLoggerList();
                for (ch.qos.logback.classic.Logger logger : loggers) {
                    Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
                    doSetDepth(iter, depth);
                }
            } catch (Throwable t) {
                LogLog.error("failed to set depth for logback", t);
                return false;
            }
            LogLog.info("set logback throwable depth success, depth: " + depth);
            return true;
        }
        return null;
    }

    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public static Boolean changeLevel(String name, String level) {
        if (Logback) {
            try {
                Level l = Level.toLevel(level, Level.ERROR);
                LoggerContext loggerContext = (LoggerContext)lcObject;

                Logger logger = loggerContext.exists(name);
                if (logger != null) {
                    logger.setLevel(l);
                    LogLog.info("set logback log level success, " + name + ": " + l);
                    return true;
                }
                LogLog.info("set logback log level fail, no logger name exists: " + name);
            } catch (Throwable t) {
                LogLog.error("failed to change level for logback, " + name + ": " + level, t);
            }
            return false;
        }
        return null;
    }

    public static Map<String, LoggerInfo> getLoggers(String name) {
        Map<String, LoggerInfo> appenders = new HashMap<String, LoggerInfo>(10);

        if (Logback) {
            LoggerContext loggerContext = (LoggerContext)lcObject;
            if (name != null && !"".equals(name.trim())) {
                Logger logger = loggerContext.exists(name);
                if (logger != null) {
                    appenders.put(name, doGetLoggerInfo(logger));
                }
            } else {
                // 获取所有logger时，如果没有appender则忽略
                List<Logger> loggers = loggerContext.getLoggerList();
                for (Logger logger : loggers) {
                    LoggerInfo info = doGetLoggerInfo(logger);
                    if (info.getAppenders() == null || !info.getAppenders().isEmpty()) {
                        appenders.put(logger.getName(), info);
                    }
                }
            }
        }

        return appenders;
    }

    private static void doSetDepth(Iterator<Appender<ILoggingEvent>> iter, int depth)
        throws IllegalAccessException {
        while (iter.hasNext()) {
            Appender<ILoggingEvent> a = iter.next();
            if (a instanceof AsyncAppenderBase) {
                Iterator<Appender<ILoggingEvent>> aiter = ((AsyncAppenderBase)a).iteratorForAppenders();
                doSetDepth(aiter, depth);
            } else if (a instanceof OutputStreamAppender) {
                OutputStreamAppender oa = (OutputStreamAppender)a;
                Encoder e = oa.getEncoder();
                Layout l = null;
                if (e instanceof PatternLayoutEncoder) {
                    l = ((PatternLayoutEncoder)e).getLayout();
                } else if (e instanceof LayoutWrappingEncoder) {
                    l = ((LayoutWrappingEncoder)e).getLayout();
                }
                if (l != null) {
                    if (l instanceof PatternLayoutBase) {
                        Converter c = (Converter)f.get(l);
                        while (c != null) {
                            if (c instanceof ThrowableProxyConverter) {
                                f1.set(c, depth);
                                break;
                            }
                            c = c.getNext();
                        }
                    }
                }
            }
        }
    }

    private static LoggerInfo doGetLoggerInfo(Logger logger) {
        LoggerInfo info = new LoggerInfo(logger.getName(), logger.isAdditive());
        Level level = logger.getLevel(), effectiveLevel = logger.getEffectiveLevel();
        if (level != null) {
            info.setLevel(level.toString());
        }
        if (effectiveLevel != null) {
            info.setEffectiveLevel(effectiveLevel.toString());
        }

        List<AppenderInfo> result = doGetLoggerAppenders(logger.iteratorForAppenders());
        info.setAppenders(result);
        return info;
    }

    private static List<AppenderInfo> doGetLoggerAppenders(Iterator<Appender<ILoggingEvent>> appenders) {
        List<AppenderInfo> result = new ArrayList<AppenderInfo>();

        while (appenders.hasNext()) {
            AppenderInfo info = new AppenderInfo();
            Appender<ILoggingEvent> appender = appenders.next();
            info.setName(appender.getName());
            info.setType(appender.getClass().getName());
            if (appender instanceof FileAppender) {
                info.setFile(((FileAppender)appender).getFile());
            } else if (appender instanceof AsyncAppender) {
                AsyncAppender aa = (AsyncAppender)appender;
                Iterator<Appender<ILoggingEvent>> iter = aa.iteratorForAppenders();
                List<AppenderInfo> asyncs = doGetLoggerAppenders(iter);
                // 标明异步appender
                List<String> nestedNames = new ArrayList<String>();
                for (AppenderInfo a : asyncs) {
                    nestedNames.add(a.getName());
                    result.add(a);
                }
                info.withDetail("nestedNames", nestedNames);
            } else if (appender instanceof ConsoleAppender) {
                info.withDetail("target", ((ConsoleAppender)appender).getTarget());
            }
            result.add(info);
        }

        return result;
    }
}
