/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementation of external exposure.
 *
 * <p>Depends on {@link com.alibaba.nacos.naming.push.v2.NamingSubscriberServiceV2Impl namingSubscriberServiceV2Impl}
 * having listen on related {@link com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent.ServiceChangedEvent events}.
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@DependsOn("namingSubscriberServiceV2Impl")
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
@Component
public class ClientOperationServiceProxy implements ClientOperationService {
    
    private final ClientOperationService ephemeralClientOperationService;
    
    private final ClientOperationService persistentClientOperationService;
    
    public ClientOperationServiceProxy(EphemeralClientOperationServiceImpl ephemeralClientOperationService,
            PersistentClientOperationServiceImpl persistentClientOperationService) {
        this.ephemeralClientOperationService = ephemeralClientOperationService;
        this.persistentClientOperationService = persistentClientOperationService;
    }
    
    @Override
    public void registerInstance(Service service, Instance instance, String clientId) throws NacosException {
        final ClientOperationService operationService = chooseClientOperationService(instance);
        operationService.registerInstance(service, instance, clientId);
    }
    
    @Override
    public void batchRegisterInstance(Service service, List<Instance> instances, String clientId) {
        final ClientOperationService operationService = chooseClientOperationService(instances.get(0));
        operationService.batchRegisterInstance(service, instances, clientId);
    }
    
    @Override
    public void deregisterInstance(Service service, Instance instance, String clientId) {
        if (!ServiceManager.getInstance().containSingleton(service)) {
            Loggers.SRV_LOG.warn("remove instance from non-exist service: {}", service);
            return;
        }
        final ClientOperationService operationService = chooseClientOperationService(instance);
        operationService.deregisterInstance(service, instance, clientId);
    }
    
    @Override
    public void subscribeService(Service service, Subscriber subscriber, String clientId) {
        // Subscriber is an ephemeral type only, so call ephemeral client directly
        ephemeralClientOperationService.subscribeService(service, subscriber, clientId);
    }
    
    @Override
    public void unsubscribeService(Service service, Subscriber subscriber, String clientId) {
        // Subscriber is an ephemeral type only, so call ephemeral client directly
        ephemeralClientOperationService.unsubscribeService(service, subscriber, clientId);
    }
    
    private ClientOperationService chooseClientOperationService(final Instance instance) {
        return instance.isEphemeral() ? ephemeralClientOperationService : persistentClientOperationService;
    }
}
