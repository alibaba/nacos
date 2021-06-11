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

package com.alibaba.nacos.common.tls;

import com.alibaba.nacos.common.utils.InternetAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A HostnameVerifier verify ipv4 and localhost.
 *
 * @author wangwei
 */

public final class SelfHostnameVerifier implements HostnameVerifier {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelfHostnameVerifier.class);
    
    private final HostnameVerifier hv;
    
    private static ConcurrentHashMap<String, Boolean> hosts = new ConcurrentHashMap<String, Boolean>();
    
    private static final String[] LOCALHOST_HOSTNAME = new String[] {"localhost", InternetAddressUtil.localHostIP()};
    
    public SelfHostnameVerifier(HostnameVerifier hv) {
        this.hv = hv;
    }
    
    @Override
    public boolean verify(String hostname, SSLSession session) {
        if (LOCALHOST_HOSTNAME[0].equalsIgnoreCase(hostname) || LOCALHOST_HOSTNAME[1].equals(hostname)) {
            return true;
        }
        if (isIP(hostname)) {
            return true;
        }
        return hv.verify(hostname, session);
    }
    
    private static boolean isIP(String host) {
        if (host == null || host.isEmpty()) {
            LOGGER.warn("host is empty, isIP = false");
            return false;
        }
        Boolean cacheHostVerify = hosts.get(host);
        if (cacheHostVerify != null) {
            return cacheHostVerify;
        }
        boolean isIp = InternetAddressUtil.isIP(host);
        hosts.putIfAbsent(host, isIp);
        return isIp;
    }
}
