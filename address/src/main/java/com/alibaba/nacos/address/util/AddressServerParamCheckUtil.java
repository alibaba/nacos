/*
 * Copyright (C) 2019 the original author or authors.
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
package com.alibaba.nacos.address.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides a unified tool class for address server parameter verification
 *
 * @author pbting
 * @date 2019-06-19 11:19 AM
 * @since 1.1.0
 */
public class AddressServerParamCheckUtil {

    public static final String CHECK_OK = "ok";

    public static final String ILLEGAL_IP_PREFIX = "illegal ip: ";

    private static final String IP_REGEX = "(2(5[0-5]{1}|[0-4]\\d{1})|[0-1]?\\d{1,2})(\\.(2(5[0-5]{1}|[0-4]\\d{1})|[0-1]?\\d{1,2})){3}";

    private static final Pattern IP_PATTERN = Pattern.compile(IP_REGEX);

    public static String checkIps(String... ips) {

        if (ips == null || ips.length == 0) {

            return CHECK_OK;
        }
        // illegal response
        StringBuilder illegalResponse = new StringBuilder();
        for (String ip : ips) {
            Matcher matcher = IP_PATTERN.matcher(ip);
            if (matcher.matches()) {
                continue;
            }
            illegalResponse.append(ip + ",");
        }

        if (illegalResponse.length() == 0) {
            return CHECK_OK;
        }

        return ILLEGAL_IP_PREFIX + illegalResponse.substring(0, illegalResponse.length() - 1);
    }
}
