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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Properties;

import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;

/**
 * @author liaochuntao
 * @date 2019-07-06 00:40
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SelectServiceInShareNamespace_ITCase {

    private NamingService naming1;
    private NamingService naming2;
    @LocalServerPort
    private int port;
    @Before
    public void init() throws Exception{
        NamingBase.prepareServer(port);
        if (naming1 == null) {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
            properties.setProperty(PropertyKeyConst.SHARE_NAMESPACE, "57425802-3058-4507-9a73-3229b9f00a36");
            naming1 = NamingFactory.createNamingService(properties);

            Properties properties2 = new Properties();
            properties2.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
            properties2.setProperty(PropertyKeyConst.NAMESPACE, "57425802-3058-4507-9a73-3229b9f00a36");
            naming2 = NamingFactory.createNamingService(properties2);
        }
        while (true) {
            if (!"UP".equals(naming1.getServerStatus())) {
                Thread.sleep(1000L);
                continue;
            }
            break;
        }
    }

    @Test
    public void testSelectInstanceInShareNamespace() throws NacosException, InterruptedException {
        String service1 = randomDomainName();
        String service2 = randomDomainName();
        naming1.registerInstance(service1, "127.0.0.1", 90);
        naming2.registerInstance(service2, "127.0.0.2", 90);

        Thread.sleep(1000);

        List<Instance> instances = naming1.getAllInstances(service2);
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals(service2, NamingUtils.getServiceName(instances.get(0).getServiceName()));
    }

}
