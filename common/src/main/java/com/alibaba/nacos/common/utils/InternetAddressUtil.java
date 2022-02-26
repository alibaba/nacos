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

package com.alibaba.nacos.common.utils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ip tool.
 *
 * @author Nacos
 */
@SuppressWarnings({"checkstyle:AbbreviationAsWordInName", "PMD.ClassNamingShouldBeCamelRule"})
public class InternetAddressUtil {
    
    public static final boolean PREFER_IPV6_ADDRESSES = Boolean.parseBoolean(System.getProperty("java.net.preferIPv6Addresses"));
    
    public static final String IPV6_START_MARK = "[";
    
    public static final String IPV6_END_MARK = "]";
    
    public static final String ILLEGAL_IP_PREFIX = "illegal ip: ";
    
    public static final String IP_PORT_SPLITER = ":";
    
    public static final int SPLIT_IP_PORT_RESULT_LENGTH = 2;
    
    public static final String PERCENT_SIGN_IN_IPV6 = "%";
    
    public static final String LOCAL_HOST = "localhost";
    
    private static final String LOCAL_HOST_IP_V4 = "127.0.0.1";
    
    private static final String LOCAL_HOST_IP_V6 = "[::1]";
    
    private static final String CHECK_OK = "ok";
    
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.?");
    
    private static final String IPV4_TUPLE = "(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])";
    
    private static final Pattern IPV4_PATTERN = Pattern
            .compile("(?<!\\d)" + IPV4_TUPLE + "\\." + IPV4_TUPLE + "\\." + IPV4_TUPLE + "\\." + IPV4_TUPLE + "(?!\\d)");
    
    /**
     * get localhost ip.
     * @return java.lang.String
     */
    public static String localHostIP() {
        if (PREFER_IPV6_ADDRESSES) {
            return LOCAL_HOST_IP_V6;
        }
        return LOCAL_HOST_IP_V4;
    }
    
    /**
     * check whether the ip address is IPv4.
     *
     * @param addr ip address
     * @return boolean
     */
    public static boolean isIPv4(String addr) {
        return InetAddressValidator.isIPv4Address(addr);
    }
    
    /**
     * check whether the ip address is IPv6.
     *
     * @param addr ip address
     * @return boolean
     */
    public static boolean isIPv6(String addr) {
        return InetAddressValidator.isIPv6Address(removeBrackets(addr));
    }
    
    /**
     * check whether the str is ip address (IPv4 or IPv6).
     *
     * @param addr ip address str
     * @return boolean
     */
    public static boolean isIP(String addr) {
        return isIPv4(addr) || isIPv6(addr);
    }
    
    /**
     * Check if the address contains a port.
     * 2020/9/3 14:53
     * @param address address string
     * @return boolean
     */
    public static boolean containsPort(String address) {
        return splitIPPortStr(address).length == SPLIT_IP_PORT_RESULT_LENGTH;
    }
    
    /**
     * Split IP and port strings, support IPv4 and IPv6, IP in IPv6 must be enclosed with [].
     * Illegal IP will get abnormal results.
     *
     * @param str ip and port string
     * @return java.lang.String[]
     */
    public static String[] splitIPPortStr(String str) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException("ip and port string cannot be empty!");
        }
        String[] serverAddrArr;
        if (str.startsWith(IPV6_START_MARK) && StringUtils.containsIgnoreCase(str, IPV6_END_MARK)) {
            if (str.endsWith(IPV6_END_MARK)) {
                serverAddrArr = new String[1];
                serverAddrArr[0] = str;
            } else {
                serverAddrArr = new String[2];
                serverAddrArr[0] = str.substring(0, (str.indexOf(IPV6_END_MARK) + 1));
                serverAddrArr[1] = str.substring((str.indexOf(IPV6_END_MARK) + 2));
            }
        } else {
            serverAddrArr = str.split(":");
        }
        return serverAddrArr;
    }
    
    /**
     * Resolve the IP from the string containing the IP address.
     * @param str string containing IP address
     * @return java.lang.String
     */
    public static String getIPFromString(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        String result = "";
        if (StringUtils.containsIgnoreCase(str, IPV6_START_MARK) && StringUtils.containsIgnoreCase(str, IPV6_END_MARK)) {
            result = str.substring(str.indexOf(IPV6_START_MARK), (str.indexOf(IPV6_END_MARK) + 1));
            if (!isIPv6(result)) {
                result = "";
            }
        } else {
            Matcher m = IPV4_PATTERN.matcher(str);
            if (m.find()) {
                result = m.group();
            }
        }
        return result;
    }
    
    /**
     * Check ips.
     *
     * @param ips ips
     * @return 'ok' if check passed, otherwise illegal ip
     */
    public static String checkIPs(String... ips) {
        
        if (ips == null || ips.length == 0) {
            
            return CHECK_OK;
        }
        // illegal response
        StringBuilder illegalResponse = new StringBuilder();
        for (String ip : ips) {
            if (InternetAddressUtil.isIP(ip)) {
                continue;
            }
            illegalResponse.append(ip + ",");
        }
        
        if (illegalResponse.length() == 0) {
            return CHECK_OK;
        }
        
        return ILLEGAL_IP_PREFIX + illegalResponse.substring(0, illegalResponse.length() - 1);
    }
    
    /**
     * Check whether checkIPs result is "ok".
     * @param checkIPsResult checkIPs result
     * @return boolean
     */
    public static boolean checkOK(String checkIPsResult) {
        return CHECK_OK.equals(checkIPsResult);
    }
    
    /**
     * remove brackets "[]".
     *
     * @param str is ipv6 address
     * @return
     */
    public static String removeBrackets(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return str.replaceAll("[\\[\\]]", "");
    }
    
    /**
     * judge str is right domain.（Check only rule）
     *
     * @param str nacosIP
     * @return nacosIP is domain
     */
    public static boolean isDomain(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        if (Objects.equals(str, LOCAL_HOST)) {
            return true;
        }
        return DOMAIN_PATTERN.matcher(str).matches();
    }
    
}