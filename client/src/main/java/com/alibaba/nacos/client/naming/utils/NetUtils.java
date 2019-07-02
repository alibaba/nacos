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

import com.alibaba.nacos.client.utils.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author xuanyin.zy
 */
public class NetUtils {
    private static String LOCAL_IP;

    public static String localIP() {
        try {
            if (!StringUtils.isEmpty(LOCAL_IP)) {
                return LOCAL_IP;
            }

            String ip = System.getProperty("com.alibaba.nacos.client.naming.local.ip", InetAddress.getLocalHost().getHostAddress());

            return LOCAL_IP = ip;
        } catch (UnknownHostException e) {
            return "resolve_failed";
        }
    }
}
