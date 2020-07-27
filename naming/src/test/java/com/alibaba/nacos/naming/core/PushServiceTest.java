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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.net.InetSocketAddress;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class PushServiceTest extends BaseTest {
    
    @InjectMocks
    private PushService pushService;
    
    @Before
    public void before() {
        super.before();
    }
    
    @Test
    public void testGetClientsFuzzy() throws Exception {
        
        String namespaceId = "public";
        String clusters = "DEFAULT";
        String agent = "Nacos-Java-Client:v1.1.4";
        String clientIp = "localhost";
        String app = "nacos";
        int udpPort = 10000;
        
        String helloServiceName = "helloGroupName@@helloServiceName";
        int helloUdpPort = 10000;
        
        String testServiceName = "testGroupName@@testServiceName";
        int testUdpPort = 10001;
        
        pushService.addClient(namespaceId, helloServiceName, clusters, agent,
                new InetSocketAddress(clientIp, helloUdpPort), null, namespaceId, app);
        
        pushService
                .addClient(namespaceId, testServiceName, clusters, agent, new InetSocketAddress(clientIp, testUdpPort),
                        null, namespaceId, app);
        
        List<Subscriber> fuzzylist = pushService.getClientsFuzzy("hello@@hello", "public");
        Assert.assertEquals(fuzzylist.size(), 1);
        Assert.assertEquals(fuzzylist.get(0).getServiceName(), "helloGroupName@@helloServiceName");
        
        List<Subscriber> list = pushService.getClientsFuzzy("helloGroupName@@helloServiceName", "public");
        Assert.assertEquals(list.size(), 1);
        Assert.assertEquals(list.get(0).getServiceName(), "helloGroupName@@helloServiceName");
        
        List<Subscriber> noDataList = pushService.getClientsFuzzy("badGroupName@@badServiceName", "public");
        Assert.assertEquals(noDataList.size(), 0);
    }
}
