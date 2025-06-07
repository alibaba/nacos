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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * manage param tool.
 *
 * @author nacos
 */
public class ParamUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ParamUtil.class);
    
    private static int connectTimeout;
    
    private static int readTimeout;
    
    private static int maxRetryTimes;
    
    private static long refreshIntervalMills;
    
    private static final String MAINTAINER_CLIENT_CONNECT_TIMEOUT_KEY = "MAINTAINER.CLIENT.CONNECT.TIMEOUT";
    
    private static final String MAINTAINER_CLIENT_READ_TIMEOUT_KEY = "MAINTAINER.CLIENT.READ.TIMEOUT";
    
    private static final String MAINTAINER_CLIENT_MAX_RETRY_TIMES_KEY = "MAINTAINER.CLIENT.MAX.RETRY.TIMES";
    
    private static final String MAINTAINER_CLIENT_REFRESH_INTERVAL_MILLS_KEY = "MAINTAINER.CLIENT.REFRESH.INTERVAL.MILLS";
    
    private static final String DEFAULT_CONNECT_TIMEOUT = "2000";
    
    private static final String DEFAULT_READ_TIMEOUT = "5000";
    
    private static final int DEFAULT_MAX_RETRY_TIMES = 3;
    
    private static final int DEFAULT_REFRESH_INTERVAL_MILLS = 5000;
    
    static {
        connectTimeout = initConnectionTimeout();
        LOGGER.info("[settings] [maintainer-http-client] connect timeout:{}", connectTimeout);
        
        readTimeout = initReadTimeout();
        LOGGER.info("[settings] [maintainer-http-client] read timeout:{}", readTimeout);
        
        maxRetryTimes = initMaxRetryTimes();
        LOGGER.info("[settings] [maintainer-http-client] max retry times:{}", maxRetryTimes);
        
        refreshIntervalMills = initRefreshIntervalMills();
        LOGGER.info("[settings] [maintainer-http-client] auth refresh interval mills:{}", refreshIntervalMills);
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
    
    private static long initRefreshIntervalMills() {
        try {
            return Long.parseLong(NacosClientProperties.PROTOTYPE.getProperty(MAINTAINER_CLIENT_REFRESH_INTERVAL_MILLS_KEY,
                    String.valueOf(DEFAULT_REFRESH_INTERVAL_MILLS)));
        } catch (NumberFormatException e) {
            final String msg = "[http-client] invalid auth refresh interval :" + refreshIntervalMills;
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
        return Constants.DEFAULT_NAMESPACE_ID;
    }
    
    public static String getDefaultGroupName() {
        return Constants.DEFAULT_GROUP;
    }
    
    public static long getRefreshIntervalMills() {
        return refreshIntervalMills;
    }
    
    /**
     * Register subType for serialization.
     *
     * <p>
     * Now these subType implementation class has registered in static code. But there are some problem for classloader.
     * The implementation class will be loaded when they are used, which will make deserialize before register.
     * </p>
     *
     * <p>
     * 子类实现类中的静态代码串中已经向Jackson进行了注册，但是由于classloader的原因，只有当 该子类被使用的时候，才会加载该类。这可能会导致Jackson先进性反序列化，再注册子类，从而导致 反序列化失败。
     * </p>
     */
    public static void initSerialization() {
        // TODO register in implementation class or remove subType
        JacksonUtils.registerSubtype(NoneSelector.class, SelectorType.none.name());
        JacksonUtils.registerSubtype(NoneSelector.class, "NoneSelector");
        JacksonUtils.registerSubtype(ExpressionSelector.class, SelectorType.label.name());
        JacksonUtils.registerSubtype(ExpressionSelector.class, "LabelSelector");
    }
}
