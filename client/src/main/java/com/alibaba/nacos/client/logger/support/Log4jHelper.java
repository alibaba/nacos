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

import org.apache.log4j.Appender;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhuyong on 2017/6/28.
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public class Log4jHelper {

    private static boolean Log4j = false, Log4jGT1216 = false;

    static {
        try {
            Class<?> loggerClass = Class.forName("org.apache.log4j.Logger");
            // 这里可能会加载到应用中依赖的log4j，因此需要判断classloader
            if (loggerClass.getClassLoader().equals(Log4jHelper.class.getClassLoader())) {
                LogManager.getLoggerRepository();
                try {
                    Class<?> throwableRendererClass = Class.forName("org.apache.log4j.spi.ThrowableRenderer");
                    // 这里可能会加载到应用中依赖的log4j 1.2.16版本的类，因此需要额外判断
                    if (loggerClass.getClassLoader().equals(throwableRendererClass.getClassLoader())
                        && throwableRendererClass.getClassLoader().equals(Log4jHelper.class.getClassLoader())) {
                        Log4jGT1216 = true;
                    }
                } catch (Throwable t) {
                    LogLog.warn("log4j must >= 1.2.16 for change throwable depth");
                }
                Log4j = true;
            }
        } catch (Throwable t) {
        }
    }
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public static Boolean setDepth(int depth) {
        if (Log4j && Log4jGT1216) {
            try {
                LoggerRepository repo = LogManager.getLoggerRepository();
                doSetDepth(repo, depth);
                return Boolean.TRUE;
            } catch (Throwable t) {
                // ignore
                LogLog.error("failed to set depth for log4j", t);
                return Boolean.FALSE;
            }
        }

        return null;
    }
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public static Boolean changeLevel(String name, String level) {
        if (Log4j) {
            Level l = Level.toLevel(level, Level.ERROR);
            Logger logger = LogManager.getLoggerRepository().exists(name);
            if (logger != null) {
                logger.setLevel(l);
                LogLog.info("set log4j log level success, " + name + ": " + l);
                return true;
            } else {
                Logger root = LogManager.getLoggerRepository().getRootLogger();
                if (root.getName().equals(name)) {
                    root.setLevel(l);
                    LogLog.info("set log4j log level success, " + name + ": " + l);
                    return true;
                }
            }
            LogLog.info("set log4j log level fail, no logger name exists: " + name);
            return false;
        }
        return null;
    }

    public static Map<String, LoggerInfo> getLoggers(String name) {
        Map<String, LoggerInfo> appenders = new HashMap<String, LoggerInfo>(10);
        if (!Log4j) {
            return appenders;
        }

        if (name != null && !"".equals(name.trim())) {
            Logger logger = LogManager.getLoggerRepository().exists(name);
            if (logger != null) {
                appenders.put(name, doGetLoggerInfo(logger));
            }
        } else {
            // 获取所有logger时，如果没有appender则忽略
            Enumeration<Logger> loggers = LogManager.getLoggerRepository().getCurrentLoggers();

            if (loggers != null) {
                while (loggers.hasMoreElements()) {
                    Logger logger = loggers.nextElement();
                    LoggerInfo info = doGetLoggerInfo(logger);
                    if (info.getAppenders() == null || !info.getAppenders().isEmpty()) {
                        appenders.put(logger.getName(), info);
                    }
                }
            }

            Logger root = LogManager.getLoggerRepository().getRootLogger();
            if (root != null) {
                LoggerInfo info = doGetLoggerInfo(root);
                if (info.getAppenders() == null || !info.getAppenders().isEmpty()) {
                    appenders.put(root.getName(), info);
                }
            }
        }

        return appenders;
    }

    private static LoggerInfo doGetLoggerInfo(Logger logger) {
        LoggerInfo info = new LoggerInfo(logger.getName(), logger.getAdditivity());
        Level level = logger.getLevel(), effectiveLevel = logger.getEffectiveLevel();
        if (level != null) {
        	info.setLevel(level.toString());
        }
        if (effectiveLevel != null) {
        	info.setEffectiveLevel(effectiveLevel.toString());
        }

        List<AppenderInfo> result = doGetLoggerAppenders(logger.getAllAppenders());
        info.setAppenders(result);
        return info;
    }

    private static List<AppenderInfo> doGetLoggerAppenders(Enumeration<Appender> appenders) {
        List<AppenderInfo> result = new ArrayList<AppenderInfo>();

        while (appenders.hasMoreElements()) {
            AppenderInfo info = new AppenderInfo();
            Appender appender = appenders.nextElement();

            info.setName(appender.getName());
            info.setType(appender.getClass().getName());

            result.add(info);
            if (appender instanceof FileAppender) {
                info.setFile(((FileAppender) appender).getFile());
            } else if (appender instanceof ConsoleAppender) {
                info.withDetail("target", ((ConsoleAppender) appender).getTarget());
            } else if (appender instanceof AsyncAppender) {
                List<AppenderInfo> asyncs = doGetLoggerAppenders(((AsyncAppender) appender).getAllAppenders());
                // 标明异步appender
                List<String> nestedNames = new ArrayList<String>();
                for (AppenderInfo a : asyncs) {
                    nestedNames.add(a.getName());
                    result.add(a);
                }
                info.withDetail("nestedNames", nestedNames);
            }
        }

        return result;
    }

    private static void doSetDepth(LoggerRepository repo, int depth) {
        if (repo instanceof ThrowableRendererSupport) {
            Object tr = ((ThrowableRendererSupport) repo).getThrowableRenderer();
            if (tr == null || !(tr instanceof DepthThrowableRenderer)) {
                Object ctr = new DepthThrowableRenderer(depth);
                // 自定义ThrowableRender，栈深度设置
                ((ThrowableRendererSupport) repo).setThrowableRenderer((ThrowableRenderer) ctr);
                LogLog.info("set log4j log depth success, depth: " + depth);
            } else {
                ((DepthThrowableRenderer) tr).setDepth(depth);
                LogLog.info("set log4j log depth success, depth: " + depth);
            }
        }
    }
}
