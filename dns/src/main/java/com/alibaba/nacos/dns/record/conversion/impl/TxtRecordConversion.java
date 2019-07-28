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

import com.alibaba.nacos.dns.constant.DnsConstants;
import com.alibaba.nacos.dns.record.BaseRecord;
import com.alibaba.nacos.dns.record.RecordType;
import com.alibaba.nacos.dns.record.TxtRecord;
import com.alibaba.nacos.dns.record.conversion.Record;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author paderlol
 */
@Record(type = RecordType.TXT)
public class TxtRecordConversion extends AbstractRecordConversion {

    @Override
    public BaseRecord transform(String domain) {
        TxtRecord txtRecord = new TxtRecord();
        com.alibaba.nacos.naming.core.Service service = getDnsSource().getServiceByCanonicalName(domain);
        Map<String, String> text = new HashMap<>(16);
        if (Objects.isNull(service)) {
            service = getDnsSource().getServiceByDomainName(domain);
            text.put(DnsConstants.CNAME_KEY, StringUtils.join(getDnsSource().getCanonicalNameByDomain(domain), ","));
        }
        int cacheTime = getDnsSource().getCacheTime(domain);
        text.putAll(getDnsSource().getSystemConfig());
        text.put(DnsConstants.DEFAULT_CACHE_TIME_KEY, String.valueOf(cacheTime));
        text.putAll(service.getMetadata());
        txtRecord.setText(text);
        return txtRecord;
    }
}
