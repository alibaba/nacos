/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.util;

import ch.qos.logback.classic.Level;
import com.alibaba.nacos.auth.constant.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log util.
 *
 * @author Nacos
 */
public class LogUtil {
    
    /**
     * Default log.
     */
    public static final Logger DEFAULT_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.startLog");
    
    /**
     * Fatal error log, require alarm.
     */
    public static final Logger FATAL_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.fatal");
    
    /**
     * Http client log.
     */
    public static final Logger PULL_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.pullLog");
    
    public static final Logger PULL_CHECK_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.pullCheckLog");
    
    /**
     * Dump log.
     */
    public static final Logger DUMP_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.dumpLog");
    
    public static final Logger MEMORY_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.monitorLog");
    
    public static final Logger CLIENT_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.clientLog");
    
    public static final Logger TRACE_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.traceLog");
    
    public static final Logger NOTIFY_LOG = LoggerFactory.getLogger("com.alibaba.nacos.auth.notifyLog");
    
    public static void setLogLevel(String logName, String level) {
        
        switch (logName) {
            case Constants.Log.CONFIG_SERVER:
                ((ch.qos.logback.classic.Logger) DEFAULT_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_FATAL:
                ((ch.qos.logback.classic.Logger) FATAL_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_PULL:
                ((ch.qos.logback.classic.Logger) PULL_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_PULL_CHECK:
                ((ch.qos.logback.classic.Logger) PULL_CHECK_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_DUMP:
                ((ch.qos.logback.classic.Logger) DUMP_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_MEMORY:
                ((ch.qos.logback.classic.Logger) MEMORY_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_CLIENT_REQUEST:
                ((ch.qos.logback.classic.Logger) CLIENT_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_TRACE:
                ((ch.qos.logback.classic.Logger) TRACE_LOG).setLevel(Level.valueOf(level));
                break;
            case Constants.Log.CONFIG_NOTIFY:
                ((ch.qos.logback.classic.Logger) NOTIFY_LOG).setLevel(Level.valueOf(level));
                break;
            default:
                break;
        }
        
    }
    
}
