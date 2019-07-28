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
package com.alibaba.nacos.dns.record.conversion.impl;

import org.springframework.beans.factory.annotation.Autowired;

import com.alibaba.nacos.dns.core.DnsSource;
import com.alibaba.nacos.dns.exception.DomainNotFoundException;
import com.alibaba.nacos.dns.record.conversion.RecordConversion;
import com.alibaba.nacos.naming.core.Service;

/**
 * @author paderlol
 */
public abstract class AbstractRecordConversion implements RecordConversion {

    @Autowired
    private DnsSource dnsSource;

    public Service getService(String domain) {
        if (dnsSource.isExistDomain(domain)) {
            return dnsSource.getServiceByDomainName(domain);
        } else if (dnsSource.isExistCanonicalName(domain)) {
            return dnsSource.getServiceByCanonicalName(domain);
        } else {
            throw new DomainNotFoundException("domain name " + domain + "not found");
        }
    }

    public DnsSource getDnsSource() {
        return dnsSource;
    }
}
