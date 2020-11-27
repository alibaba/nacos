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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.core.utils.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * registry for client connection event listeners.
 *
 * @author liuzunfei
 * @version $Id: ClientConnectionEventListenerRegistry.java, v 0.1 2020年07月20日 1:47 PM liuzunfei Exp $
 */
public class ClientConnectionEventListenerRegistry {
    
    private static final ClientConnectionEventListenerRegistry INSTANCE = new ClientConnectionEventListenerRegistry();
    
    public static ClientConnectionEventListenerRegistry getInstance() {
        return INSTANCE;
    }
    
    final List<ClientConnectionEventListener> clientConnectionEventListeners = new ArrayList<>();
    
    protected ScheduledExecutorService executorService = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(getClass()), 10,
                    new NameThreadFactory("com.alibaba.nacos.remote.client.connection.notifier"));
    
    /**
     * notify where a new client connected.
     *
     * @param connection connection that new created.
     */
    public void notifyClientConnected(final Connection connection) {
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                for (ClientConnectionEventListener clientConnectionEventListener : clientConnectionEventListeners) {
                    clientConnectionEventListener.clientConnected(connection);
                }
            }
        }, 0L, TimeUnit.MILLISECONDS);
    }
    
    /**
     * notify where a new client disconnected.
     *
     * @param connection connection that disconnected.
     */
    public void notifyClientDisConnected(final Connection connection) {
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                for (ClientConnectionEventListener clientConnectionEventListener : clientConnectionEventListeners) {
                    clientConnectionEventListener.clientDisConnected(connection);
                }
            }
        }, 0L, TimeUnit.MILLISECONDS);
    }
    
    /**
     * register ClientConnectionEventListener.
     *
     * @param listener listener.
     */
    public void registerClientConnectionEventListener(ClientConnectionEventListener listener) {
        Loggers.REMOTE.info("[ClientConnectionEventListenerRegistry] registry listener - " + listener.getClass()
                .getSimpleName());
        this.clientConnectionEventListeners.add(listener);
    }
    
}
