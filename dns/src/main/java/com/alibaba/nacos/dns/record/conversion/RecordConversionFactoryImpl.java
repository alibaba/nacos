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
package com.alibaba.nacos.dns.record.conversion;

import com.alibaba.nacos.dns.record.RecordType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author paderlol
 */
@Component
public class RecordConversionFactoryImpl implements RecordConversionFactory, ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;

    private Map<RecordType, RecordConversion> recordConversionMap;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public RecordConversion create(RecordType recordType) {
        return recordConversionMap.get(recordType);
    }

    @Override
    public void afterPropertiesSet() {
        Map<RecordType, RecordConversion> temp = new HashMap<>(16);
        Map<String, RecordConversion> beans = this.applicationContext.getBeansOfType(RecordConversion.class);
        for (RecordConversion recordConversion : beans.values()) {
            Record record = recordConversion.getClass().getAnnotation(Record.class);
            temp.put(record.type(), recordConversion);
        }
        recordConversionMap = Collections.unmodifiableMap(temp);
    }
}
