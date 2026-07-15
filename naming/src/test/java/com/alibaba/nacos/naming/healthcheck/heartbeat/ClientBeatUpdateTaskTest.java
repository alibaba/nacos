/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBeatUpdateTaskTest {
    
    @Test
    void testRunUpdatesAllInstancesHeartbeatTime() {
        IpPortBasedClient client = new IpPortBasedClient(
            IpPortBasedClient.getClientId("1.1.1.1:8848", true), true);
        HealthCheckInstancePublishInfo instance =
            new HealthCheckInstancePublishInfo("1.1.1.1", 8848);
        instance.setLastHeartBeatTime(1L);
        client.addServiceInstance(Service.newService("namespace", "group", "service", true),
            instance);
        
        new ClientBeatUpdateTask(client).run();
        
        assertTrue(instance.getLastHeartBeatTime() > 1L);
    }
}
