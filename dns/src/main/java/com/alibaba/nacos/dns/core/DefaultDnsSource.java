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

import com.alibaba.nacos.dns.config.DnsProperties;
import com.alibaba.nacos.dns.generator.DomainGeneratorDelegate;
import com.alibaba.nacos.naming.core.Service;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.alibaba.nacos.dns.constant.DnsConstants.*;

/**
 * @author paderlol
 */
@Component
public class DefaultDnsSource implements DnsSource, InitializingBean {
    private final Map<String, Service> serviceMap = new ConcurrentHashMap<>();
    private final Map<String, String> systemConfig = new ConcurrentHashMap<>();
    private final Map<String, Integer> domainCacheTime = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<String>> domainForCanonicalName = new ConcurrentHashMap<>();
    private final Map<String, String> canonicalNameForDomain = new ConcurrentHashMap<>();

    private final DomainGeneratorDelegate domainGeneratorDelegate;
    private final DnsProperties dnsProperties;

    public DefaultDnsSource(DomainGeneratorDelegate domainGeneratorDelegate, DnsProperties dnsProperties) {
        this.domainGeneratorDelegate = domainGeneratorDelegate;
        this.dnsProperties = dnsProperties;
    }

    @Override
    public void putService(Service service) {

        List<String> domains = domainGeneratorDelegate.create(service);
        for (String domain : domains) {
            serviceMap.put(domain, service);
        }
    }

    @Override
    public Service getServiceByDomainName(String domain) {

        return serviceMap.get(domain);
    }

    @Override
    public Map<String, String> getSystemConfig() {
        return Collections.unmodifiableMap(systemConfig);
    }

    @Override
    public void updateSystemConfig(String key, String value) {
        systemConfig.put(key, value);
    }

    @Override
    public void updateCacheTime(String domain, Integer cacheTime) {
        domainCacheTime.put(domain, cacheTime);
    }

    @Override
    public int getCacheTime(String domain) {

        return domainCacheTime.getOrDefault(domain, Integer.valueOf(systemConfig.get(DEFAULT_CACHE_TIME_KEY)));
    }

    @Override
    public void putCanonicalName(String domain, String canonicalName) {
        if (serviceMap.containsKey(domain)) {

            canonicalNameForDomain.put(canonicalName, domain);
            CopyOnWriteArrayList<String> mappingNames =
                domainForCanonicalName.getOrDefault(domain, new CopyOnWriteArrayList<>());
            domainForCanonicalName.putIfAbsent(domain, mappingNames);
            mappingNames.add(canonicalName);
        }
    }

    @Override
    public List<String> getCanonicalNameByDomain(String domain) {
        return Collections.unmodifiableList(domainForCanonicalName.getOrDefault(domain, new CopyOnWriteArrayList<>()));
    }

    @Override
    public Service getServiceByCanonicalName(String canonicalName) {
        String domainName = canonicalNameForDomain.get(canonicalName);

        return StringUtils.isNotBlank(domainName) ? serviceMap.get(domainName) : null;
    }

    @Override
    public boolean isExistDomain(String domain) {
        return serviceMap.containsKey(domain);
    }

    @Override
    public boolean isExistCanonicalName(String canonicalName) {
        return canonicalNameForDomain.containsKey(canonicalName);
    }

    @Override
    public void afterPropertiesSet() {
        systemConfig.put(DEFAULT_CACHE_TIME_KEY, String.valueOf(dnsProperties.getDefaultCacheTime()));
        systemConfig.put(UPSTREAM_SERVERS_FOR_DOMAIN_SUFFIX_MAP_KEY,
            dnsProperties.getUpstreamServersForDomainSuffixMap());
        systemConfig.put(DEFAULT_UPSTREAM_SERVER_KEY, dnsProperties.getDefaultUpstreamServer());
        systemConfig.put(EDNS_ENABLED_KEY, String.valueOf(dnsProperties.isEdnsEnabled()));
    }
}
