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
package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.LoggerFactory;

/**
 * @author dingjoey
 */
public class LogUtils {

    static int JM_LOG_RETAIN_COUNT = 7;
    static String JM_LOG_FILE_SIZE = "10MB";
    public static final Logger LOG;

    static {
        String tmp = "7";
        try {
            tmp = System.getProperty("JM.LOG.RETAIN.COUNT", "7");
            JM_LOG_RETAIN_COUNT = Integer.parseInt(tmp);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            throw e;
        }

        JM_LOG_FILE_SIZE = System.getProperty("JM.LOG.FILE.SIZE", "10MB");

        // logger init
        LOG = LoggerFactory.getLogger("com.alibaba.nacos.client.naming");
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);
        LOG.activateAppenderWithSizeRolling("nacos", "naming.log", "UTF-8", JM_LOG_FILE_SIZE, JM_LOG_RETAIN_COUNT);
    }

    public static Logger logger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    public static void setLogLevel(String level) {
        LOG.setLevel(Level.codeOf(level.toUpperCase()));
    }

}
