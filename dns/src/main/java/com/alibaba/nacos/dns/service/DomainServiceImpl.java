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
package com.alibaba.nacos.dns.service;

import com.alibaba.nacos.dns.constant.DnsConstants;
import com.alibaba.nacos.dns.core.DnsSource;
import com.alibaba.nacos.dns.dto.UpdateDomainDto;
import com.alibaba.nacos.dns.exception.DomainNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DomainServiceImpl implements DomainService {

    final DnsSource dnsSource;

    public DomainServiceImpl(DnsSource dnsSource) {
        this.dnsSource = dnsSource;
    }

    @Override
    public Map<String, String> getConfig(String domain) {
        com.alibaba.nacos.naming.core.Service service = dnsSource.getServiceByCname(domain);
        Map<String, String> config = new HashMap<>();
        if (service == null) {
            service = dnsSource.getServiceByDomainName(domain);
            config.put(DnsConstants.CNAME_KEY, StringUtils.join(dnsSource.getMappingName(domain), ","));
        }
        if (service == null) {
            throw new DomainNotFoundException("domain name " + domain + "not found");
        }
        int cacheTime = dnsSource.getCacheTime(domain);
        config.put(DnsConstants.DEFAULT_CACHE_TIME_KEY, String.valueOf(cacheTime));
        config.putAll(service.getMetadata());
        return config;
    }

    @Override
    public boolean updateConfig(String domain, UpdateDomainDto updateDomainDto) {
        return false;
    }
}
