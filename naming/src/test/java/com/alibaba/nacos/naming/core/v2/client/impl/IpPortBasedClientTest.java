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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
    
    @Before
    public void setUp() throws Exception {
        ipPortBasedClient = new IpPortBasedClient(clientId, true);
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
        long mustExpireTime = ipPortBasedClient.getLastUpdatedTime() + Constants.DEFAULT_IP_DELETE_TIMEOUT * 2;
        assertTrue(ipPortBasedClient.isExpire(mustExpireTime));
    }
    
    @Test
    public void testGetAllInstancePublishInfo() {
        ipPortBasedClient.addServiceInstance(service, instancePublishInfo);
        Collection<InstancePublishInfo> allInstancePublishInfo = ipPortBasedClient.getAllInstancePublishInfo();
        assertEquals(allInstancePublishInfo.size(), 1);
        assertEquals(allInstancePublishInfo.iterator().next(), instancePublishInfo);
    }
    
    @After
    public void tearDown() {
        ipPortBasedClient.release();
    }
}