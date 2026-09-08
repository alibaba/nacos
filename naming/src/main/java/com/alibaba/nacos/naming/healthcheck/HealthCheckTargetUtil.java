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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.common.utils.InternetAddressUtil;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

/**
 * Utilities for validating and building active health check targets.
 *
 * @author xiweng.yy
 */
public final class HealthCheckTargetUtil {
    
    private static final String HTTP_SCHEME = "http";
    
    private static final String URL_PREFIX = HTTP_SCHEME + "://";
    
    private static final String CONTENT_LENGTH = "Content-Length";
    
    private static final String TRANSFER_ENCODING = "Transfer-Encoding";
    
    private HealthCheckTargetUtil() {
    }
    
    /**
     * Returns whether the address is a plain host without URL components controlled by the
     * instance IP field.
     *
     * @param address instance address
     * @return {@code true} if the address can be used as an active health check host
     */
    public static boolean isValidAddress(String address) {
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
    
    /**
     * Returns whether the HTTP health check path is a relative URI reference with an optional
     * query and without a fragment.
     *
     * @param requestTarget configured HTTP health check path
     * @return {@code true} if the value cannot override the instance target
     */
    public static boolean isValidHttpRequestTarget(String requestTarget) {
        try {
            parseHttpRequestTarget(requestTarget);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
    
    /**
     * Returns whether a built-in HTTP health checker has a safe request target and headers.
     *
     * @param healthChecker HTTP health checker
     * @return {@code true} if the checker cannot override the target or inject HTTP headers
     */
    public static boolean isValidHttpHealthChecker(Http healthChecker) {
        return healthChecker != null
            && isValidHttpRequestTarget(healthChecker.getPath())
            && isValidHttpHeaders(healthChecker.getCustomHeaders());
    }
    
    /**
     * Builds an HTTP health check URI whose origin is fixed by the instance address and port.
     *
     * @param address instance address
     * @param port health check port
     * @param requestTarget configured HTTP health check path and optional query
     * @return validated HTTP health check URI
     * @throws URISyntaxException if the address or request target is invalid
     */
    public static URI buildHttpTarget(String address, int port, String requestTarget)
        throws URISyntaxException {
        if (!isValidAddress(address)) {
            throw new URISyntaxException("", "Active health check address is invalid");
        }
        URI reference = parseHttpRequestTarget(requestTarget);
        String host = formatHost(address);
        URI base = new URI(HTTP_SCHEME, host + ':' + port, "/", null, null);
        URI target = base.resolve(reference);
        if (!HTTP_SCHEME.equals(target.getScheme())
            || !base.getRawAuthority().equals(target.getRawAuthority())
            || target.getRawUserInfo() != null || target.getFragment() != null) {
            throw new URISyntaxException("", "HTTP health check target origin is invalid");
        }
        return target;
    }
    
    private static URI parseHttpRequestTarget(String requestTarget) throws URISyntaxException {
        if (requestTarget == null) {
            throw new URISyntaxException("", "HTTP health check request target is null");
        }
        URI parsed = new URI(requestTarget);
        if (requestTarget.startsWith("//") || parsed.getScheme() != null || parsed.isOpaque()
            || parsed.getRawAuthority() != null || parsed.getRawUserInfo() != null
            || parsed.getHost() != null || parsed.getPort() != -1
            || parsed.getRawFragment() != null) {
            throw new URISyntaxException("", "HTTP health check request target is invalid");
        }
        return parsed;
    }
    
    private static boolean isValidHttpHeaders(Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name.isEmpty()) {
                continue;
            }
            if (!isValidHttpHeaderName(name) || isRequestFramingHeader(name)
                || !isValidHttpHeaderValue(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isValidHttpHeaderName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char each = name.charAt(index);
            if (!isAlphaNumeric(each) && "!#$%&'*+-.^_`|~".indexOf(each) < 0) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isAlphaNumeric(char value) {
        return value >= '0' && value <= '9' || value >= 'A' && value <= 'Z'
            || value >= 'a' && value <= 'z';
    }
    
    private static boolean isRequestFramingHeader(String name) {
        return CONTENT_LENGTH.equalsIgnoreCase(name) || TRANSFER_ENCODING.equalsIgnoreCase(name);
    }
    
    private static boolean isValidHttpHeaderValue(String value) {
        for (int index = 0; index < value.length(); index++) {
            char each = value.charAt(index);
            if (each != '\t' && (each < 0x20 || each == 0x7F)) {
                return false;
            }
        }
        return true;
    }
    
    private static String formatHost(String address) {
        if (address.startsWith(InternetAddressUtil.IPV6_START_MARK)
            && address.endsWith(InternetAddressUtil.IPV6_END_MARK)) {
            return address;
        }
        return InternetAddressUtil.isIpv6(address) ? '[' + address + ']' : address;
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
