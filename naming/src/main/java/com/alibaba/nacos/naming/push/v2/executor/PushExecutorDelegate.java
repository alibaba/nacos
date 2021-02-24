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

package com.alibaba.nacos.naming.push.v2.executor;

import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delegate for push execute service.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
@Component
public class PushExecutorDelegate implements PushExecutor {
    
    private final PushExecutorRpcImpl rpcPushExecuteService;
    
    private final PushExecutorUdpImpl udpPushExecuteService;
    
    public PushExecutorDelegate(PushExecutorRpcImpl rpcPushExecuteService, PushExecutorUdpImpl udpPushExecuteService) {
        this.rpcPushExecuteService = rpcPushExecuteService;
        this.udpPushExecuteService = udpPushExecuteService;
    }
    
    @Override
    public void doPush(String clientId, Subscriber subscriber, PushDataWrapper data) {
        getPushExecuteService(clientId, subscriber).doPush(clientId, subscriber, data);
    }
    
    @Override
    public void doPushWithCallback(String clientId, Subscriber subscriber, PushDataWrapper data, PushCallBack callBack) {
        getPushExecuteService(clientId, subscriber).doPushWithCallback(clientId, subscriber, data, callBack);
    }
    
    private PushExecutor getPushExecuteService(String clientId, Subscriber subscriber) {
        Optional<SpiPushExecutor> result = SpiImplPushExecutorHolder.getInstance()
                .findPushExecutorSpiImpl(clientId, subscriber);
        if (result.isPresent()) {
            return result.get();
        }
        // use nacos default push executor
        return clientId.contains(IpPortBasedClient.ID_DELIMITER) ? udpPushExecuteService : rpcPushExecuteService;
    }
}
