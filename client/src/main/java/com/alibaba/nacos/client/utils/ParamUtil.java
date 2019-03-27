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
package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.client.config.impl.HttpSimpleClient;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Properties;

/**
 * manage param tool
 *
 * @author nacos
 */
public class ParamUtil {
    private final static Logger LOGGER = LogUtils.logger(ParamUtil.class);

    private static String defaultContextPath = "nacos";
    private static String defaultNodesPath = "serverlist";
    private static String appKey;
    private static String appName;
    private static String defaultServerPort;
    private static String clientVersion = "unknown";
    private static int connectTimeout;
    private static double perTaskConfigSize = 3000;

    static {
        // 客户端身份信息
        appKey = System.getProperty("nacos.client.appKey", "");

        appName = AppNameUtils.getAppName();

        String defaultServerPortTmp = "8848";

        defaultServerPort = System.getProperty("nacos.server.port", defaultServerPortTmp);
        LOGGER.info("[settings] [req-serv] nacos-server port:{}", defaultServerPort);

        String tmp = "1000";
        try {
            tmp = System.getProperty("NACOS.CONNECT.TIMEOUT", "1000");
            connectTimeout = Integer.parseInt(tmp);
        } catch (NumberFormatException e) {
            final String msg = "[http-client] invalid connect timeout:" + tmp;
            LOGGER.error("[settings] " + msg, e);
            throw new IllegalArgumentException(msg, e);
        }
        LOGGER.info("[settings] [http-client] connect timeout:{}", connectTimeout);

        try {
            InputStream in = HttpSimpleClient.class.getClassLoader()
                .getResourceAsStream("application.properties");
            Properties props = new Properties();
            props.load(in);
            String val = null;
            val = props.getProperty("version");
            if (val != null) {
                clientVersion = val;
            }
            LOGGER.info("NACOS_CLIENT_VERSION: {}", clientVersion);
        } catch (Exception e) {
            LOGGER.error("[500] read application.properties", e);
        }

        try {
            perTaskConfigSize = Double.valueOf(System.getProperty("PER_TASK_CONFIG_SIZE", "3000"));
            LOGGER.info("PER_TASK_CONFIG_SIZE: {}", perTaskConfigSize);
        } catch (Throwable t) {
            LOGGER.error("[PER_TASK_CONFIG_SIZE] PER_TASK_CONFIG_SIZE invalid", t);
        }
    }

    public static String getAppKey() {
        return appKey;
    }

    public static void setAppKey(String appKey) {
        ParamUtil.appKey = appKey;
    }

    public static String getAppName() {
        return appName;
    }

    public static void setAppName(String appName) {
        ParamUtil.appName = appName;
    }

    public static String getDefaultContextPath() {
        return defaultContextPath;
    }

    public static void setDefaultContextPath(String defaultContextPath) {
        ParamUtil.defaultContextPath = defaultContextPath;
    }

    public static String getClientVersion() {
        return clientVersion;
    }

    public static void setClientVersion(String clientVersion) {
        ParamUtil.clientVersion = clientVersion;
    }

    public static int getConnectTimeout() {
        return connectTimeout;
    }

    public static void setConnectTimeout(int connectTimeout) {
        ParamUtil.connectTimeout = connectTimeout;
    }

    public static double getPerTaskConfigSize() {
        return perTaskConfigSize;
    }

    public static void setPerTaskConfigSize(double perTaskConfigSize) {
        ParamUtil.perTaskConfigSize = perTaskConfigSize;
    }

    public static String getDefaultServerPort() {
        return defaultServerPort;
    }

    public static String getDefaultNodesPath() {
        return defaultNodesPath;
    }

    public static void setDefaultNodesPath(String defaultNodesPath) {
        ParamUtil.defaultNodesPath = defaultNodesPath;
    }

}
