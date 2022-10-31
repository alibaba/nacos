/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.monitor.collector;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.EphemeralIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * v1 and v2 naming subscriber and publisher metrics collector.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@Service
public class NamingSubAndPubMetricsCollector {
    
    private static final long DELAY_SECONDS = 5;
    
    private static ScheduledExecutorService executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
        Thread thread = new Thread(r, "nacos.naming.monitor.NamingSubAndPubMetricsCollector");
        thread.setDaemon(true);
        return thread;
    });
    
    @Autowired
    public NamingSubAndPubMetricsCollector(ConnectionBasedClientManager connectionBasedClientManager,
            EphemeralIpPortClientManager ephemeralIpPortClientManager, PersistentIpPortClientManager persistentIpPortClientManager) {
        executorService.scheduleWithFixedDelay(() -> {
            int v1SubscriberCount = 0;
            int v1PublisherCount = 0;
            for (String clientId : ephemeralIpPortClientManager.allClientId()) {
                Client client = ephemeralIpPortClientManager.getClient(clientId);
                if (null != client) {
                    v1PublisherCount += client.getAllPublishedService().size();
                    v1SubscriberCount += client.getAllSubscribeService().size();
                }
            }
            for (String clientId : persistentIpPortClientManager.allClientId()) {
                Client client = persistentIpPortClientManager.getClient(clientId);
                if (null != client) {
                    v1PublisherCount += client.getAllPublishedService().size();
                    v1SubscriberCount += client.getAllSubscribeService().size();
                }
            }
            MetricsMonitor.getNamingSubscriber("v1").set(v1SubscriberCount);
            MetricsMonitor.getNamingPublisher("v1").set(v1PublisherCount);
    
            int v2SubscriberCount = 0;
            int v2PublisherCount = 0;
            for (String clientId : connectionBasedClientManager.allClientId()) {
                Client client = connectionBasedClientManager.getClient(clientId);
                if (null != client) {
                    v2PublisherCount += client.getAllPublishedService().size();
                    v2SubscriberCount += client.getAllSubscribeService().size();
                }
            }
            MetricsMonitor.getNamingSubscriber("v2").set(v2SubscriberCount);
            MetricsMonitor.getNamingPublisher("v2").set(v2PublisherCount);
        }, DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
