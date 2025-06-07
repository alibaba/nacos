/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.constant.Constants.Security.SECURITY_INFO_REFRESH_INTERVAL_MILLS;

/**
 * nacos lock Service.
 *
 * @author 985492783@qq.com
 * @date 2023/8/24 19:51
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosLockService implements LockService {
    
    private final LockGrpcClient lockGrpcClient;
    
    private final SecurityProxy securityProxy;
    
    private ScheduledExecutorService executorService;
    
    public NacosLockService(Properties properties) throws NacosException {
        NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        AbstractServerListManager serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        this.securityProxy = new SecurityProxy(serverListManager,
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
        initSecurityProxy(nacosClientProperties);
        this.lockGrpcClient = new LockGrpcClient(nacosClientProperties, serverListManager, securityProxy);
    }
    
    private void initSecurityProxy(NacosClientProperties properties) {
        this.executorService = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.lock.security");
            t.setDaemon(true);
            return t;
        });
        final Properties nacosClientPropertiesView = properties.asProperties();
        this.securityProxy.login(nacosClientPropertiesView);
        this.executorService.scheduleWithFixedDelay(() -> securityProxy.login(nacosClientPropertiesView), 0,
                SECURITY_INFO_REFRESH_INTERVAL_MILLS, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public Boolean lock(LockInstance instance) throws NacosException {
        return instance.lock(this);
    }
    
    @Override
    public Boolean unLock(LockInstance instance) throws NacosException {
        return instance.unLock(this);
    }
    
    @Override
    public Boolean remoteTryLock(LockInstance instance) throws NacosException {
        return lockGrpcClient.lock(instance);
    }
    
    @Override
    public Boolean remoteReleaseLock(LockInstance instance) throws NacosException {
        return lockGrpcClient.unLock(instance);
    }
    
    @Override
    public void shutdown() throws NacosException {
        lockGrpcClient.shutdown();
        if (null != executorService) {
            executorService.shutdown();
        }
    }
}
