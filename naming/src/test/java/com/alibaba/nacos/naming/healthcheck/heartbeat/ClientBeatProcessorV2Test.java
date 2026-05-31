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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBeatProcessorV2Test {
    
    private static final String NAMESPACE = "namespace";
    
    private static final String GROUP_NAME = "group";
    
    private static final String SERVICE_NAME = "service";
    
    private static final String GROUPED_SERVICE_NAME =
        GROUP_NAME + Constants.SERVICE_INFO_SPLITER + SERVICE_NAME;
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 10000;
    
    @Test
    void testRunMarksUnhealthyInstanceHealthy() {
        HealthCheckInstancePublishInfo instance = newInstance(IP, PORT, false);
        instance.setLastHeartBeatTime(1L);
        ClientBeatProcessorV2 processor = newProcessor(instance, newRsInfo(IP, PORT));
        
        processor.run();
        
        assertTrue(instance.isHealthy());
        assertTrue(instance.getLastHeartBeatTime() > 1L);
    }
    
    @Test
    void testRunRefreshesHealthyInstanceHeartbeat() {
        HealthCheckInstancePublishInfo instance = newInstance(IP, PORT, true);
        instance.setLastHeartBeatTime(1L);
        ClientBeatProcessorV2 processor = newProcessor(instance, newRsInfo(IP, PORT));
        
        processor.run();
        
        assertTrue(instance.isHealthy());
        assertTrue(instance.getLastHeartBeatTime() > 1L);
    }
    
    @Test
    void testRunWithDebugLogEnabled() {
        Logger logger = (Logger) Loggers.EVT_LOG;
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            HealthCheckInstancePublishInfo instance = newInstance(IP, PORT, false);
            instance.setLastHeartBeatTime(1L);
            ClientBeatProcessorV2 processor = newProcessor(instance, newRsInfo(IP, PORT));
            
            processor.run();
            
            assertTrue(instance.isHealthy());
            assertTrue(instance.getLastHeartBeatTime() > 1L);
        } finally {
            logger.setLevel(oldLevel);
        }
    }
    
    @Test
    void testRunIgnoresDifferentPort() {
        HealthCheckInstancePublishInfo instance = newInstance(IP, PORT, false);
        instance.setLastHeartBeatTime(1L);
        ClientBeatProcessorV2 processor = newProcessor(instance, newRsInfo(IP, PORT + 1));
        
        processor.run();
        
        assertEquals(1L, instance.getLastHeartBeatTime());
        assertFalse(instance.isHealthy());
    }
    
    private ClientBeatProcessorV2 newProcessor(HealthCheckInstancePublishInfo instance,
        RsInfo rsInfo) {
        IpPortBasedClient client = new IpPortBasedClient(
            IpPortBasedClient.getClientId(IP + ":" + PORT, true), true);
        client.addServiceInstance(
            Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME, true), instance);
        return new ClientBeatProcessorV2(NAMESPACE, rsInfo, client);
    }
    
    private HealthCheckInstancePublishInfo newInstance(String ip, int port, boolean healthy) {
        HealthCheckInstancePublishInfo result = new HealthCheckInstancePublishInfo(ip, port);
        result.setHealthy(healthy);
        result.setCluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        return result;
    }
    
    private RsInfo newRsInfo(String ip, int port) {
        RsInfo result = new RsInfo();
        result.setIp(ip);
        result.setPort(port);
        result.setCluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        result.setServiceName(GROUPED_SERVICE_NAME);
        result.setEphemeral(true);
        return result;
    }
}
