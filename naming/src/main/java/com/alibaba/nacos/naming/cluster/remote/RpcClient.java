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

package com.alibaba.nacos.naming.cluster.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * abstract remote client to connect to server.
 *
 * @author liuzunfei
 * @version $Id: RpcClient.java, v 0.1 2020年07月13日 9:15 PM liuzunfei Exp $
 */
public abstract class RpcClient implements Closeable, ClusterClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);
    
    protected String connectionId;
    
    protected String target;
    
    protected AtomicReference<RpcClientStatus> rpcClientStatus = new AtomicReference<RpcClientStatus>(
            RpcClientStatus.WAIT_INIT);
    
    protected ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(5, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.config.grpc.worker");
            t.setDaemon(true);
            return t;
        }
    });
    
    public RpcClient() {
    }
    
    public RpcClient(String target) {
        init(target);
    }
    
    /**
     * init server list factory.
     *
     * @param target target address
     */
    public void init(String target) {
        if (!isWaitInited()) {
            return;
        }
        this.connectionId = UUID.randomUUID().toString();
        rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITED);
        this.target = target;
        LOGGER.info("RpcClient init ,connectionId={}, target ={}", this.connectionId, target);
    }
    
    /**
     * check is this client is inited.
     *
     * @return true if is waiting init
     */
    public boolean isWaitInited() {
        return this.rpcClientStatus.get() == RpcClientStatus.WAIT_INIT;
    }
    
    /**
     * check is this client is running.
     *
     * @return true if is running
     */
    public boolean isRunning() {
        return this.rpcClientStatus.get() == RpcClientStatus.RUNNING;
    }
    
    /**
     * check is this client is in init status,have not start th client.
     *
     * @return true if is init
     */
    public boolean isInitStatus() {
        return this.rpcClientStatus.get() == RpcClientStatus.INITED;
    }
    
    /**
     * check is this client is in starting process.
     *
     * @return true if is starting
     */
    public boolean isStarting() {
        return this.rpcClientStatus.get() == RpcClientStatus.STARTING;
    }
    
    /**
     * Start this client.
     *
     * @throws NacosException nacos exception
     */
    public abstract void start() throws NacosException;
}
