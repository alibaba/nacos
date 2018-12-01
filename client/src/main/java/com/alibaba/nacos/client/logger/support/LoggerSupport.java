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

import java.util.List;
import java.util.Map;

import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.option.ActivateOption;

/**
 * Logger Support
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class LoggerSupport implements Logger {

    protected Object delegateLogger;
    protected ActivateOption activateOption;

    public LoggerSupport(Object delegate) {
        this.delegateLogger = delegate;
    }

    @Override
    public void debug(String message) {
        debug(null, message);
    }

    @Override
    public void debug(String format, Object... args) {
        debug(null, format, args);
    }

    @Override
    public void info(String message) {
        info(null, message);
    }

    @Override
    public void info(String format, Object... args) {
        info(null, format, args);
    }

    @Override
    public void warn(String message) {
        warn(null, message);
    }

    @Override
    public void warn(String format, Object... args) {
        warn(null, format, args);
    }

    @Override
    public void error(String errorCode, String message) {
        error(null, errorCode, message);
    }

    @Override
    public void error(String errorCode, String message, Throwable t) {
        error(null, errorCode, message, t);
    }

    @Override
    public void error(String errorCode, String format, Object... args) {
        error(null, errorCode, format, args);
    }

    public Object getDelegate() {
        return delegateLogger;
    }

    public void activateConsoleAppender(String target, String encoding) {
        if (activateOption != null) {
            activateOption.activateConsoleAppender(target, encoding);
        }
    }

    @Override
    public void activateAppender(String productName, String file, String encoding) {
        if (activateOption != null) {
            activateOption.activateAppender(productName, file, encoding);
        }
    }

    @Override
    public void setLevel(Level level) {
        if (activateOption != null) {
            activateOption.setLevel(level);
        }
    }

    @Override
    public Level getLevel() {
        if (activateOption != null) {
            return activateOption.getLevel();
        }
        return null;
    }

    @Override
    public void setAdditivity(boolean additivity) {
        if (activateOption != null) {
            activateOption.setAdditivity(additivity);
        }
    }

    @Override
    public String getProductName() {
        if (activateOption != null) {
            return activateOption.getProductName();
        }

        return null;
    }

    @Override
    public void activateAsyncAppender(String productName, String file, String encoding) {
        if (activateOption != null) {
            activateOption.activateAsyncAppender(productName, file, encoding);
        }
    }

    @Override
    public void activateAsyncAppender(String productName, String file, String encoding, int queueSize,
                                      int discardingThreshold) {
        if (activateOption != null) {
            activateOption.activateAsyncAppender(productName, file, encoding, queueSize, discardingThreshold);
        }
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size) {
        if (activateOption != null) {
            activateOption.activateAppenderWithTimeAndSizeRolling(productName, file, encoding, size);
        }
    }

    @Override
    public void activateAppender(Logger logger) {
        if (activateOption != null) {
            activateOption.activateAppender(logger);
        }
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern) {
        if (activateOption != null) {
            activateOption.activateAppenderWithTimeAndSizeRolling(productName, file, encoding, size, datePattern);
        }
    }

    @Override
    public void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                       String datePattern, int maxBackupIndex) {
        if (activateOption != null) {
            activateOption.activateAppenderWithTimeAndSizeRolling(productName, file, encoding, size, datePattern,
                maxBackupIndex);
        }
    }

    @Override
    public void activateAppenderWithSizeRolling(String productName, String file, String encoding, String size,
                                                int maxBackupIndex) {
        if (activateOption != null) {
            activateOption.activateAppenderWithSizeRolling(productName, file, encoding, size, maxBackupIndex);
        }
    }

    @Override
    public void activateAsync(int queueSize, int discardingThreshold) {
        if (activateOption != null) {
            activateOption.activateAsync(queueSize, discardingThreshold);
        }
    }

    @Override
    public void activateAsync(List<Object[]> args) {
        if (activateOption != null) {
            activateOption.activateAsync(args);
        }
    }
}
