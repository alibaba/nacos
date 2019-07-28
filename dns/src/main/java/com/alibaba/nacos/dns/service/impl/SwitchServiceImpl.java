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
package com.alibaba.nacos.dns.service.impl;

import com.alibaba.nacos.dns.core.DnsSource;
import com.alibaba.nacos.dns.exception.DomainNotFoundException;
import com.alibaba.nacos.dns.exception.SystemEntryNotFoundException;
import com.alibaba.nacos.dns.service.SwitchService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author paderlol
 */
@Service
public class SwitchServiceImpl implements SwitchService {
    private final DnsSource dnsSource;

    public SwitchServiceImpl(DnsSource dnsSource) {
        this.dnsSource = dnsSource;
    }

    @Override
    public Map<String, String> getSystemConfig(String domain) {
        if (!dnsSource.isExistCanonicalName(domain) && !dnsSource.isExistDomain(domain)) {
            throw new DomainNotFoundException("Domain name " + domain + " was not found");
        }
        return dnsSource.getSystemConfig();
    }

    @Override
    public void updateSystemConfig(String entry, String value) {
        if (dnsSource.getSystemConfig().containsKey(entry)) {

            dnsSource.updateSystemConfig(entry, value);
        } else {
            throw new SystemEntryNotFoundException(" Entry " + entry + " was not in the system property");
        }
    }
}
