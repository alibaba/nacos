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
package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.naming.boot.RunningConfig;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.alibaba.nacos.common.util.SystemUtils.PREFER_HOSTNAME_OVER_IP;

/**
 * @author nacos
 */
public class NetUtils {

    private static String serverAddress = null;

    public static String localServer() {
        try {
            if (StringUtils.isNotBlank(serverAddress)) {
                return serverAddress + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
            }

            InetAddress inetAddress = InetAddress.getLocalHost();
            serverAddress = inetAddress.getHostAddress();
            if (PREFER_HOSTNAME_OVER_IP) {
                if (inetAddress.getHostName().equals(inetAddress.getCanonicalHostName())) {
                    serverAddress = inetAddress.getHostName();
                } else {
                    serverAddress = inetAddress.getCanonicalHostName();
                }
            }
            return serverAddress + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
        } catch (UnknownHostException e) {
            return "resolve_failed";
        }
    }

    public static String num2ip(int ip) {
        int[] b = new int[4];
        String x = "";

        b[0] = (int) ((ip >> 24) & 0xff);
        b[1] = (int) ((ip >> 16) & 0xff);
        b[2] = (int) ((ip >> 8) & 0xff);
        b[3] = (int) (ip & 0xff);
        x = Integer.toString(b[0]) + "." + Integer.toString(b[1]) + "." + Integer.toString(b[2]) + "." + Integer.toString(b[3]);

        return x;
    }


}
