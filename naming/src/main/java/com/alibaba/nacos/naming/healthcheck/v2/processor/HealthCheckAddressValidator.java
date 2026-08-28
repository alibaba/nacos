/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.common.utils.InternetAddressUtil;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Validator for active health check target addresses.
 *
 * @author xiweng.yy
 */
final class HealthCheckAddressValidator {
    
    private static final String URL_PREFIX = "http://";
    
    private HealthCheckAddressValidator() {
    }
    
    /**
     * Returns whether the address is a plain host without URL components controlled by the
     * instance IP field.
     *
     * @param address instance address
     * @return {@code true} if the address can be used as an active health check host
     */
    static boolean isValid(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        boolean ipv6Host = isIpv6Host(address);
        if (containsUnsafeCharacter(address, ipv6Host)) {
            return false;
        }
        if (ipv6Host) {
            return true;
        }
        try {
            URL parsed = new URL(URL_PREFIX + address);
            return address.equals(parsed.getHost()) && parsed.getUserInfo() == null
                && parsed.getPort() == -1 && parsed.getPath().isEmpty()
                && parsed.getQuery() == null && parsed.getRef() == null;
        } catch (MalformedURLException e) {
            return false;
        }
    }
    
    private static boolean isIpv6Host(String address) {
        boolean startsWithBracket = address.startsWith(InternetAddressUtil.IPV6_START_MARK);
        boolean endsWithBracket = address.endsWith(InternetAddressUtil.IPV6_END_MARK);
        if (startsWithBracket || endsWithBracket) {
            if (!startsWithBracket || !endsWithBracket || address.length() < 3) {
                return false;
            }
            return InternetAddressUtil.isIpv6(address.substring(1, address.length() - 1));
        }
        return InternetAddressUtil.isIpv6(address);
    }
    
    private static boolean containsUnsafeCharacter(String address, boolean ipv6Host) {
        for (int index = 0; index < address.length(); index++) {
            char each = address.charAt(index);
            if (Character.isWhitespace(each) || Character.isISOControl(each) || each == '/'
                || each == '?' || each == '#' || each == '@' || each == '\\') {
                return true;
            }
            if (each == '%' && !ipv6Host) {
                return true;
            }
        }
        return false;
    }
}
