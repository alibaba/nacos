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

import com.alibaba.nacos.dns.record.BaseRecord;
import com.alibaba.nacos.dns.record.InstanceInfo;
import com.alibaba.nacos.dns.record.RecordType;
import com.alibaba.nacos.dns.record.SrvRecord;
import com.alibaba.nacos.dns.record.conversion.Record;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * @author paderlol
 */
@Record(type = RecordType.SRV)
public class SrvRecordConversion extends AbstractRecordConversion {
    @Override
    public BaseRecord transform(String domain) {
        Service service = getService(domain);
        List<Instance> instances = service.allIPs();
        SrvRecord srvRecord = new SrvRecord();
        List<InstanceInfo> instanceInfos = Lists.newArrayListWithCapacity(instances.size());
        for (Instance instance : instances) {
            InstanceInfo instanceInfo = new InstanceInfo();
            instanceInfo.setAddress(instance.getIp());
            instanceInfo.setPort(instance.getPort());
            instanceInfos.add(instanceInfo);
        }
        srvRecord.setInstances(instanceInfos);

        return srvRecord;
    }
}
