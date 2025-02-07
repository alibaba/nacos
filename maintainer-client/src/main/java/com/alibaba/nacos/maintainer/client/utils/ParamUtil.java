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

package com.alibaba.nacos.maintainer.client.utils;

import com.alibaba.nacos.client.env.NacosClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * manage param tool.
 *
 * @author nacos
 */
public class ParamUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ParamUtil.class);
    
    private static final String DEFAULT_GROUP_NAME = "DEFAULT_GROUP";
    
    private static final String DEFAULT_NAMESPACE_ID = "public";
    
    private static int connectTimeout;
    
    private static int readTimeout;
    
    private static int maxRetryTimes;
    
    private static final String MAINTAINER_CLIENT_CONNECT_TIMEOUT_KEY = "MAINTAINER.CLIENT.CONNECT.TIMEOUT";
    
    private static final String MAINTAINER_CLIENT_READ_TIMEOUT_KEY = "MAINTAINER.CLIENT.READ.TIMEOUT";
    
    private static final String MAINTAINER_CLIENT_MAX_RETRY_TIMES_KEY = "MAINTAINER.CLIENT.MAX.RETRY.TIMES";
    
    private static final String DEFAULT_CONNECT_TIMEOUT = "2000";
    
    private static final String DEFAULT_READ_TIMEOUT = "5000";
    
    private static final int DEFAULT_MAX_RETRY_TIMES = 3;
    
    static {
        connectTimeout = initConnectionTimeout();
        LOGGER.info("[settings] [maintainer-http-client] connect timeout:{}", connectTimeout);
        
        readTimeout = initReadTimeout();
        LOGGER.info("[settings] [maintainer-http-client] read timeout:{}", readTimeout);
        
        maxRetryTimes = initMaxRetryTimes();
        LOGGER.info("[settings] [maintainer-http-client] max retry times:{}", maxRetryTimes);
    }
    
    private static int initConnectionTimeout() {
        try {
            String connectTimeout = NacosClientProperties.PROTOTYPE.getProperty(MAINTAINER_CLIENT_CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT);
            return Integer.parseInt(connectTimeout);
        } catch (NumberFormatException e) {
            final String msg = "[http-client] invalid connect timeout:" + connectTimeout;
            LOGGER.error("[settings] {}", msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }
    
    private static int initReadTimeout() {
        try {
            String readTimeout = NacosClientProperties.PROTOTYPE.getProperty(MAINTAINER_CLIENT_READ_TIMEOUT_KEY, DEFAULT_READ_TIMEOUT);
            return Integer.parseInt(readTimeout);
        } catch (NumberFormatException e) {
            final String msg = "[http-client] invalid read timeout:" + readTimeout;
            LOGGER.error("[settings] {}", msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }
    
    private static int initMaxRetryTimes() {
        try {
            return Integer.parseInt(NacosClientProperties.PROTOTYPE.getProperty(MAINTAINER_CLIENT_MAX_RETRY_TIMES_KEY,
                    String.valueOf(DEFAULT_MAX_RETRY_TIMES)));
        } catch (NumberFormatException e) {
            final String msg = "[http-client] invalid max retry times:" + maxRetryTimes;
            LOGGER.error("[settings] {}", msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }
    
    public static int getConnectTimeout() {
        return connectTimeout;
    }
    
    public static void setConnectTimeout(int connectTimeout) {
        ParamUtil.connectTimeout = connectTimeout;
    }
    
    public static int getReadTimeout() {
        return readTimeout;
    }
    
    public static void setReadTimeout(int readTimeout) {
        ParamUtil.readTimeout = readTimeout;
    }
    
    public static int getMaxRetryTimes() {
        return maxRetryTimes;
    }
    
    public static String getDefaultNamespaceId() {
        return DEFAULT_NAMESPACE_ID;
    }
    
    public static String getDefaultGroupName() {
        return DEFAULT_GROUP_NAME;
    }
}
