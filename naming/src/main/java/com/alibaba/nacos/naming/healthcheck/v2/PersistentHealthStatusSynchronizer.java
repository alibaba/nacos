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

package com.alibaba.nacos.naming.healthcheck.v2;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import org.springframework.stereotype.Component;

/**
 * Health status synchronizer for persistent service, implementation by CP.
 *
 * @author xiweng.yy
 */
@Component
public class PersistentHealthStatusSynchronizer implements HealthStatusSynchronizer {
    
    public PersistentHealthStatusSynchronizer(PersistentClientOperationServiceImpl persistentClientOperationService) {
        this.persistentClientOperationService = persistentClientOperationService;
    }
    
    private final PersistentClientOperationServiceImpl persistentClientOperationService;
    
    @Override
    public void instanceHealthStatusChange(boolean isHealthy, Client client, Service service,
            InstancePublishInfo instance) {
        Instance updateInstance = InstanceUtil.parseToApiInstance(service, instance);
        updateInstance.setHealthy(isHealthy);
        persistentClientOperationService.registerInstance(service, updateInstance, client.getClientId());
    }
}
