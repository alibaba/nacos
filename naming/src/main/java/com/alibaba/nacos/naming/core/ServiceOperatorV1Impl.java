/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.stereotype.Component;

/**
 * Implementation of service operator for v1.x.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceOperatorV1Impl implements ServiceOperator {
    
    private final ServiceManager serviceManager;
    
    public ServiceOperatorV1Impl(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }
    
    @Override
    public void update(Service service, ServiceMetadata metadata) throws NacosException {
        String serviceName = service.getGroupedServiceName();
        com.alibaba.nacos.naming.core.Service serviceV1 = serviceManager.getService(service.getNamespace(), serviceName);
        if (serviceV1 == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "service " + serviceName + " not found!");
        }
        serviceV1.setProtectThreshold(metadata.getProtectThreshold());
        serviceV1.setSelector(metadata.getSelector());
        serviceV1.setMetadata(metadata.getExtendData());
        serviceV1.setLastModifiedMillis(System.currentTimeMillis());
        serviceV1.recalculateChecksum();
        serviceV1.validate();
        serviceManager.addOrReplaceService(serviceV1);
    }
}
