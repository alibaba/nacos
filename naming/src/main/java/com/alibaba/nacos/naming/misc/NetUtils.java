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

import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import org.apache.commons.lang3.StringUtils;


/**
 * @author nacos
 */
public class NetUtils {

    private static String serverAddress = null;

    public static String localServer() {
        return getLocalAddress() + UtilsAndCommons.IP_PORT_SPLITER + RunningConfig.getServerPort();
    }

    public static String getLocalAddress() {
        if (StringUtils.isNotBlank(serverAddress)) {
            return serverAddress;
        }

        serverAddress = InetUtils.getSelfIp();
        return serverAddress;
    }

    public static String num2ip(int ip) {
        int[] b = new int[4];
        String x = "";

        b[0] = (ip >> 24) & 0xff;
        b[1] = (ip >> 16) & 0xff;
        b[2] = (ip >> 8) & 0xff;
        b[3] = ip & 0xff;
        x = Integer.toString(b[0]) + "." + Integer.toString(b[1]) + "." + Integer.toString(b[2]) + "." + Integer.toString(b[3]);

        return x;
    }


}
