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
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alibaba.nacos.client.constant.Constants.Security.SECURITY_INFO_REFRESH_INTERVAL_MILLS;

/**
 * nacos lock Service.
 *
 * @author 985492783@qq.com
 * @date 2023/8/24 19:51
 */
public class NacosLockService implements LockService {
    
    private final LockGrpcClient lockGrpcClient;
    
    private final SecurityProxy securityProxy;
    
    private final NacosLockWatchdog watchdog;
    
    private final String clientId;
    
    private final AbstractServerListManager serverListManager;
    
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private ScheduledExecutorService executorService;
    
    public NacosLockService(Properties properties) throws NacosException {
        NacosClientProperties nacosClientProperties =
            NacosClientProperties.PROTOTYPE.derive(properties);
        this.serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        this.securityProxy = new SecurityProxy(serverListManager,
            NamingHttpClientManager.getInstance().getNacosRestTemplate());
        initSecurityProxy(nacosClientProperties);
        this.lockGrpcClient =
            new LockGrpcClient(nacosClientProperties, serverListManager, securityProxy);
        this.watchdog = new NacosLockWatchdog();
        this.clientId = UUID.randomUUID().toString();
    }
    
    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("NacosLockService has been shut down");
        }
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
        this.executorService.scheduleWithFixedDelay(
            () -> securityProxy.login(nacosClientPropertiesView), 0,
            SECURITY_INFO_REFRESH_INTERVAL_MILLS, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public Boolean lock(LockInstance instance) throws NacosException {
        checkNotClosed();
        return instance.lock(this);
    }
    
    @Override
    public Boolean unLock(LockInstance instance) throws NacosException {
        checkNotClosed();
        return instance.unLock(this);
    }
    
    @Override
    public Boolean remoteTryLock(LockInstance instance) throws NacosException {
        checkNotClosed();
        return lockGrpcClient.lock(instance);
    }
    
    @Override
    public Boolean remoteReleaseLock(LockInstance instance) throws NacosException {
        checkNotClosed();
        return lockGrpcClient.unLock(instance);
    }
    
    @Override
    public Boolean renew(LockInstance instance) throws NacosException {
        checkNotClosed();
        return lockGrpcClient.renew(instance);
    }
    
    /**
     * Get a JUC-style reentrant distributed lock.
     *
     * @param key lock key
     * @return NacosLock implementing java.util.concurrent.locks.Lock
     */
    public NacosLock getReentrantLock(String key) {
        checkNotClosed();
        return new NacosLock(key, LockConstants.REENTRANT_LOCK_TYPE, lockGrpcClient, watchdog,
            clientId);
    }
    
    /**
     * Get a JUC-style non-reentrant distributed lock.
     *
     * @param key lock key
     * @return NacosLock implementing java.util.concurrent.locks.Lock
     */
    public NacosLock getNonReentrantLock(String key) {
        checkNotClosed();
        return new NacosLock(key, LockConstants.NON_REENTRANT_LOCK_TYPE, lockGrpcClient, watchdog,
            clientId);
    }
    
    @Override
    public void shutdown() throws NacosException {
        if (closed.compareAndSet(false, true)) {
            lockGrpcClient.shutdown();
            watchdog.shutdown();
            serverListManager.shutdown();
            if (null != executorService) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
