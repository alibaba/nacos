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
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class IpPortBasedClientTest {
    
    private final String clientId = "127.0.0.1:80#true";
    
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private Service service;
    
    private InstancePublishInfo instancePublishInfo;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Before
    public void setUp() throws Exception {
        ipPortBasedClient = new IpPortBasedClient(clientId, true, 123L);
        ipPortBasedClient.init();
        instancePublishInfo = new InstancePublishInfo();
    }
    
    @Test
    public void testGetClientId() {
        assertEquals(clientId, ipPortBasedClient.getClientId());
    }
    
    @Test
    public void testGetResponsibleId() {
        String responsibleId = "127.0.0.1:80";
        assertEquals(responsibleId, ipPortBasedClient.getResponsibleId());
    }
    
    @Test
    public void testIsExpire() {
        long mustExpireTime =
                ipPortBasedClient.getLastUpdatedTime() + ClientConfig.getInstance().getClientExpiredTime() * 2;
        assertTrue(ipPortBasedClient.isExpire(mustExpireTime));
    }
    
    @Test
    public void testGetAllInstancePublishInfo() {
        ipPortBasedClient.addServiceInstance(service, instancePublishInfo);
        Collection<InstancePublishInfo> allInstancePublishInfo = ipPortBasedClient.getAllInstancePublishInfo();
        assertEquals(allInstancePublishInfo.size(), 1);
        assertEquals(allInstancePublishInfo.iterator().next(), instancePublishInfo);
    }
    
    @Test
    public void testRecalculateRevision() {
        assertEquals(123L, ipPortBasedClient.getRevision());
        assertEquals(-1531701243L, ipPortBasedClient.recalculateRevision());
    }
    
    @Test
    public void testConstructor0() {
        IpPortBasedClient client = new IpPortBasedClient(clientId, true);
        assertEquals(0, client.getRevision());
    }
    
    @After
    public void tearDown() {
        ipPortBasedClient.release();
    }
}
