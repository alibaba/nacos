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

package com.alibaba.nacos.naming.core.v2.client.impl;

import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.ClientConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class IpPortBasedClientTest {
    
    private final String clientId = "127.0.0.1:80#true";
    
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private Service service;
    
    private InstancePublishInfo instancePublishInfo;
    
    @BeforeAll
    static void setUpBeforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @BeforeEach
    void setUp() throws Exception {
        ipPortBasedClient = new IpPortBasedClient(clientId, true, 123L);
        ipPortBasedClient.init();
        instancePublishInfo = new InstancePublishInfo();
    }
    
    @Test
    void testGetClientId() {
        assertEquals(clientId, ipPortBasedClient.getClientId());
    }
    
    @Test
    void testGetResponsibleId() {
        String responsibleId = "127.0.0.1:80";
        assertEquals(responsibleId, ipPortBasedClient.getResponsibleId());
    }
    
    @Test
    void testIsExpire() {
        long mustExpireTime = ipPortBasedClient.getLastUpdatedTime() + ClientConfig.getInstance().getClientExpiredTime() * 2;
        assertTrue(ipPortBasedClient.isExpire(mustExpireTime));
    }
    
    @Test
    void testGetAllInstancePublishInfo() {
        ipPortBasedClient.addServiceInstance(service, instancePublishInfo);
        Collection<InstancePublishInfo> allInstancePublishInfo = ipPortBasedClient.getAllInstancePublishInfo();
        assertEquals(1, allInstancePublishInfo.size());
        assertEquals(allInstancePublishInfo.iterator().next(), instancePublishInfo);
    }
    
    @Test
    void testRecalculateRevision() {
        assertEquals(123L, ipPortBasedClient.getRevision());
        assertEquals(-1531701243L, ipPortBasedClient.recalculateRevision());
    }
    
    @Test
    void testConstructor0() {
        IpPortBasedClient client = new IpPortBasedClient(clientId, true);
        assertEquals(0, client.getRevision());
    }
    
    @AfterEach
    void tearDown() {
        ipPortBasedClient.release();
    }
}
