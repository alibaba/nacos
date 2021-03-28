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

import com.alibaba.nacos.common.utils.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Net utils.
 *
 * @author xuanyin.zy
 */
public class NetUtils {
    
    private static String localIp;
    
    /**
     * Get local ip.
     *
     * @return local ip
     */
    public static String localIP() {
        try {
            if (!StringUtils.isEmpty(localIp)) {
                return localIp;
            }
            
            String ip = System.getProperty("com.alibaba.nacos.client.naming.local.ip",
                    InetAddress.getLocalHost().getHostAddress());
            
            String ip = System.getProperty("com.alibaba.nacos.client.naming.local.ip");
            if (null != ip) {
                return localIp = ip;
            }
            
            InetAddress localAddress = null;
            localAddress = InetAddress.getLocalHost();
            if (!localAddress.isLoopbackAddress()
                    && !localAddress.isAnyLocalAddress()
                    && !localAddress.isLinkLocalAddress()) {
                return localIp = localAddress.getHostAddress();
            }
            
            InetAddress netAddress = null;
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (null != interfaces) {
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface network = interfaces.nextElement();
                        if (network.isLoopback() || network.isVirtual() || !network.isUp()) {
                            continue;
                        }
                        Enumeration<InetAddress> addresses = network.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress address = addresses.nextElement();
                            if (!address.isLoopbackAddress()
                                    && !address.isAnyLocalAddress()
                                    && !address.isLinkLocalAddress()) {
                                netAddress = address;
                                break;
                            }
                        }
                        if (null != netAddress) {
                            break;
                        }
                    }
                }
            } catch (SocketException e) {
                return localIp = localAddress.getHostAddress();
            }
            return localIp = (null != netAddress) ? netAddress.getHostAddress() : localAddress.getHostAddress();
        } catch (UnknownHostException e) {
            return "resolve_failed";
        }
    }
}
