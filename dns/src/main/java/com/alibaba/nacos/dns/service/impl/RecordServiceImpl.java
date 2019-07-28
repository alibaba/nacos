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

import com.alibaba.nacos.dns.record.BaseRecord;
import com.alibaba.nacos.dns.record.RecordType;
import com.alibaba.nacos.dns.record.conversion.RecordConversion;
import com.alibaba.nacos.dns.record.conversion.RecordConversionFactoryImpl;
import com.alibaba.nacos.dns.service.RecordService;

/**
 * @author paderlol
 */
@org.springframework.stereotype.Service
public class RecordServiceImpl implements RecordService {

    private final RecordConversionFactoryImpl recordConversionFactory;

    public RecordServiceImpl(RecordConversionFactoryImpl recordConversionFactory) {
        this.recordConversionFactory = recordConversionFactory;
    }

    @Override
    public BaseRecord getRecord(String domain, RecordType recordType) {

        RecordConversion recordConversion = recordConversionFactory.create(recordType);
        BaseRecord baseRecord = recordConversion.transform(domain);
        baseRecord.setName(domain);
        baseRecord.setRecord(recordType);
        return baseRecord;
    }

}
