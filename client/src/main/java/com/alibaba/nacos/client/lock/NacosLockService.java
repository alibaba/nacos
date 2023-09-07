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
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;

import java.util.Properties;

/**
 * nacos lock Service.
 *
 * @author 985492783@qq.com
 * @date 2023/8/24 19:51
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosLockService implements LockService {
    
    private final Properties properties;
    
    private final LockGrpcClient lockGrpcClient;
    
    public NacosLockService(Properties properties) throws NacosException {
        this.properties = properties;
        NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ServerListManager serverListManager = new ServerListManager(properties);
        SecurityProxy securityProxy = new SecurityProxy(serverListManager.getServerList(),
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
        this.lockGrpcClient = new LockGrpcClient(nacosClientProperties, serverListManager, securityProxy);
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
    
    public Properties getProperties() {
        return properties;
    }
}
