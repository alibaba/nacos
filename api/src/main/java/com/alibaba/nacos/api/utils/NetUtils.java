/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * Net utils.
 *
 * @author xuanyin.zy
 */
public class NetUtils {
    
    private static final String CLIENT_NAMING_LOCAL_IP_PROPERTY = "com.alibaba.nacos.client.naming.local.ip";
    
    private static final String LEGAL_LOCAL_IP_PROPERTY = "java.net.preferIPv6Addresses";
    
    private static final String DEFAULT_SOLVE_FAILED_RETURN = "resolve_failed";
    
    private static String localIp;
    
    /**
     * Get local ip.
     *
     * @return local ip
     */
    public static String localIP() {
        if (!StringUtils.isEmpty(localIp)) {
            return localIp;
        }
        
        String ip = System.getProperty(CLIENT_NAMING_LOCAL_IP_PROPERTY, findFirstNonLoopbackAddress());
        
        return localIp = ip;
        
    }
    
    private static String findFirstNonLoopbackAddress() {
        InetAddress result = null;
        
        try {
            int lowest = Integer.MAX_VALUE;
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                    nics.hasMoreElements(); ) {
                NetworkInterface ifc = nics.nextElement();
                if (ifc.isUp()) {
                    if (ifc.getIndex() < lowest || result == null) {
                        lowest = ifc.getIndex();
                    } else {
                        continue;
                    }
                    
                    for (Enumeration<InetAddress> addrs = ifc.getInetAddresses(); addrs.hasMoreElements(); ) {
                        InetAddress address = addrs.nextElement();
                        boolean isLegalIpVersion =
                                Boolean.parseBoolean(System.getProperty(LEGAL_LOCAL_IP_PROPERTY))
                                        ? address instanceof Inet6Address : address instanceof Inet4Address;
                        if (isLegalIpVersion && !address.isLoopbackAddress()) {
                            result = address;
                        }
                    }
                    
                }
            }
        } catch (IOException ex) {
            //ignore
        }
        
        if (result != null) {
            return result.getHostAddress();
        }
        
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            //ignore
        }
        
        return DEFAULT_SOLVE_FAILED_RETURN;
        
    }
}
