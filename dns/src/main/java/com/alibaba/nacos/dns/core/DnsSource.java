/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.core;

import com.alibaba.nacos.naming.core.Service;

import java.util.List;
import java.util.Map;

/**
 * @author paderlol
 * @date 2019年07月28日, 16:31:31
 */
public interface DnsSource {
    /**
     * Put service.
     *
     * @param service the service
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:31
     */
    void putService(Service service);

    /**
     * Get service by domain name
     * @param domain the domain
     * @return the service by domain name
     * @description service by domain name .
     * @author paderlol
     * @date 2019年07月28日, 16:31:31
     */
    Service getServiceByDomainName(String domain);

    /**
     * Get system config
     * @return the system config
     * @description system config .
     * @author paderlol
     * @date 2019年07月28日, 16:31:31
     */
    Map<String, String> getSystemConfig();

    /**
     * Update system config.
     *
     * @param key   the key
     * @param value the value
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:31
     */
    void updateSystemConfig(String key, String value);

    /**
     * Update cache time.
     *
     * @param domain    the domain
     * @param cacheTime the cache time
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:31
     */
    void updateCacheTime(String domain, Integer cacheTime);

    /**
     * Get cache time which is ttl
     * @param domain the domain
     * @return the cache time
     * @description cache time .
     * @author paderlol
     * @date 2019年07月28日, 16:31:32
     */
    int getCacheTime(String domain);

    /**
     * Put canonical name.
     *
     * @param domain        the domain
     * @param canonicalName the canonical name
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:32
     */
    void putCanonicalName(String domain, String canonicalName);

    /**
     * Get CNAME by domain
     * @param domain the domain
     * @return the canonical name by domain
     * @description canonical name by domain .
     * @author paderlol
     * @date 2019年07月28日, 16:31:32
     */
    List<String> getCanonicalNameByDomain(String domain);

    /**
     * Get domain by CNAME
     * @param canonicalName the canonical name
     * @return the service by canonical name
     * @description service by canonical name .
     * @author paderlol
     * @date 2019年07月28日, 16:31:32
     */
    Service getServiceByCanonicalName(String canonicalName);

    /**
     * Is exist domain boolean.
     *
     * @param domain the domain
     * @return the boolean
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:32
     */
    boolean isExistDomain(String domain);

    /**
     * Is exist canonical name boolean.
     *
     * @param canonicalName the canonical name
     * @return the boolean
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:32
     */
    boolean isExistCanonicalName(String canonicalName);

}
