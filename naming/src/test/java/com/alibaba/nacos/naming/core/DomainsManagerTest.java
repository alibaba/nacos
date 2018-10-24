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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dungu.zpf
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class DomainsManagerTest extends BaseTest {

    private DomainsManager domainsManager;

    @Before
    public void before() {
        super.before();
        domainsManager = new DomainsManager();
    }

    @Test
    public void easyRemoveDom() throws Exception {
        domainsManager.easyRemoveDom("nacos.test.1");
    }

    @Test
    public void easyRemvIP4Dom() throws Exception {

        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.test.1");

        domainsManager.chooseDomMap().put("nacos.test.1", domain);

        IpAddress ipAddress = new IpAddress();
        ipAddress.setIp("1.1.1.1");
        List<IpAddress> ipList = new ArrayList<IpAddress>();
        ipList.add(ipAddress);
        domainsManager.addLock("nacos.test.1");
        domainsManager.easyRemvIP4Dom("nacos.test.1", ipList);
    }

    @Test
    public void searchDom() throws Exception {
        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.test.1");

        domainsManager.chooseDomMap().put("nacos.test.1", domain);

        List<Domain> list = domainsManager.searchDomains("nacos.test.*");
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("nacos.test.1", list.get(0).getName());
    }
}
