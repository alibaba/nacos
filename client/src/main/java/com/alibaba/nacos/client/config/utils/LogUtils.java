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
package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.LoggerFactory;

/**
 * Log Util
 * 
 * @author Nacos
 *
 */
public class LogUtils {

	static int JM_LOG_RETAIN_COUNT = 7;
	static String JM_LOG_FILE_SIZE = "10MB";

    static {
    	String tmp = "7";
        try {
			/**
			 * change timeout from 100 to 200
			 */
            tmp = System.getProperty("JM.LOG.RETAIN.COUNT","7");
            JM_LOG_RETAIN_COUNT = Integer.parseInt(tmp);
        } catch (NumberFormatException e) {
			e.printStackTrace();
			throw e;
        }
        
        JM_LOG_FILE_SIZE = System.getProperty("JM.LOG.FILE.SIZE","10MB"); 
    	
        // logger init
        Logger logger = LoggerFactory.getLogger("com.alibaba.nacos.client.config");
        logger.setLevel(Level.INFO);
        logger.setAdditivity(false);
        logger.activateAppenderWithSizeRolling("nacos", "config.log", Constants.ENCODE, JM_LOG_FILE_SIZE, JM_LOG_RETAIN_COUNT);
    }

   public static Logger logger(Class<?> clazz) {
       return LoggerFactory.getLogger(clazz);
   }
}
