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
import com.alibaba.nacos.dns.dto.UpdateDomainDto;
import com.alibaba.nacos.dns.exception.DomainNotFoundException;
import com.alibaba.nacos.dns.record.RecordType;
import com.alibaba.nacos.dns.record.TxtRecord;
import com.alibaba.nacos.dns.record.conversion.RecordConversion;
import com.alibaba.nacos.dns.record.conversion.RecordConversionFactoryImpl;
import com.alibaba.nacos.dns.service.DomainService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * @author paderlol
 */
@Service
public class DomainServiceImpl implements DomainService {

    private final DnsSource dnsSource;

    private final RecordConversionFactoryImpl recordConversionFactory;

    public DomainServiceImpl(DnsSource dnsSource, RecordConversionFactoryImpl recordConversionFactory) {
        this.dnsSource = dnsSource;
        this.recordConversionFactory = recordConversionFactory;
    }

    @Override
    public Map<String, String> getConfig(String domain) {
        checkDomainIsExist(domain);
        RecordConversion recordConversion = recordConversionFactory.create(RecordType.TXT);
        TxtRecord txtRecord = (TxtRecord)recordConversion.transform(domain);
        return txtRecord.getText();
    }

    @Override
    public boolean updateConfig(String domain, UpdateDomainDto updateDomainDto) {
        checkDomainIsExist(domain);
        if (Objects.nonNull(updateDomainDto.getCacheTime())) {
            dnsSource.updateCacheTime(domain, updateDomainDto.getCacheTime());
        }
        if (Objects.nonNull(updateDomainDto.getcName()) && dnsSource.isExistDomain(domain)) {
            dnsSource.putCanonicalName(domain, updateDomainDto.getcName());
        }

        return false;
    }

    private void checkDomainIsExist(String domain) {
        if (!dnsSource.isExistCanonicalName(domain) && !dnsSource.isExistDomain(domain)) {
            throw new DomainNotFoundException("domain name " + domain + " was not found");
        }
    }

}
