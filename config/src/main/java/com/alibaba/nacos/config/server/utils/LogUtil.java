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
package com.alibaba.nacos.config.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * log util
 *
 * @author Nacos
 */
public class LogUtil {

    /**
     * 默认的日志
     */
    static public final Logger defaultLog = LoggerFactory.getLogger("com.alibaba.nacos.config.startLog");

    /**
     * 致命错误，需要告警
     */
    static public final Logger fatalLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.fatal");

    /**
     * 客户端GET方法获取数据的日志
     */
    static public final Logger pullLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.pullLog");

    static public final Logger pullCheckLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.pullCheckLog");
    /**
     * 从DB dump数据的日志
     */
    static public final Logger dumpLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.dumpLog");

    static public final Logger memoryLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.monitorLog");

    static public final Logger clientLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.clientLog");

    static public final Logger sdkLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.sdkLog");

    static public final Logger traceLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.traceLog");

    static public final Logger aclLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.aclLog");

    static public final Logger notifyLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.notifyLog");

    static public final Logger appCollectorLog = LoggerFactory
        .getLogger("com.alibaba.nacos.config.appCollectorLog");
}
