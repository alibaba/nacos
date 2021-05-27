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

import com.alibaba.nacos.common.utils.InternetAddressUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * System config.
 *
 * @author Nacos
 */
public class SystemConfig {
    
    public static final String LOCAL_IP = getHostAddress();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemConfig.class);
    
    private static String getHostAddress() {
        String address = System.getProperty("nacos.server.ip");
        if (StringUtils.isNotEmpty(address)) {
            return address;
        } else {
            address = InternetAddressUtil.localHostIP();
        }
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                Enumeration<InetAddress> ads = ni.getInetAddresses();
                while (ads.hasMoreElements()) {
                    InetAddress ip = ads.nextElement();
                    // Compatible group does not regulate 11 network segments
                    if (!ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1
                        /* && ip.isSiteLocalAddress() */) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("get local host address error", e);
        }
        return address;
    }
    
}
