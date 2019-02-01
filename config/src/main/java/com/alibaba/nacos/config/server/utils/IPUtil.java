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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ip util
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class IPUtil {

    public static boolean isIPV4(String addr) {
        if (null == addr) {
            return false;
        }
        String rexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

        Pattern pat = Pattern.compile(rexp);

        Matcher mat = pat.matcher(addr);

        boolean ipAddress = mat.find();
        return ipAddress;
    }

    public static boolean isIPV6(String addr) {
        if (null == addr) {
            return false;
        }
        String rexp = "^([\\da-fA-F]{1,4}:){7}[\\da-fA-F]{1,4}$";

        Pattern pat = Pattern.compile(rexp);

        Matcher mat = pat.matcher(addr);

        boolean ipAddress = mat.find();
        return ipAddress;
    }
}
