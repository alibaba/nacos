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
package com.alibaba.nacos.test.core.auth;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

/**
 * @author nkorange
 * @since 1.2.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class NamingAuth_ITCase extends AuthBase {

    @LocalServerPort
    private int port;

    private NamingService namingService;

    @Before
    public void init() throws Exception {
        super.init(port);
    }

    @After
    public void destroy() {
        super.destroy();
    }

    @Test
    public void writeWithReadPermission() throws Exception {

        properties.put(PropertyKeyConst.USERNAME, username1);
        properties.put(PropertyKeyConst.PASSWORD, password1);
        namingService = NacosFactory.createNamingService(properties);

        try {
            namingService.registerInstance("test.1", "1.2.3.4", 80);
            fail();
        } catch (NacosException ne) {
            Assert.assertEquals(HttpStatus.SC_FORBIDDEN, ne.getErrCode());
        }

        try {
            namingService.deregisterInstance("test.1", "1.2.3.4", 80);
            fail();
        } catch (NacosException ne) {
            Assert.assertEquals(HttpStatus.SC_FORBIDDEN, ne.getErrCode());
        }
    }

    @Test
    public void readWithReadPermission() throws Exception {
        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        NamingService namingService1 = NacosFactory.createNamingService(properties);
        namingService1.registerInstance("test.1", "1.2.3.4", 80);
        TimeUnit.SECONDS.sleep(5L);

        properties.put(PropertyKeyConst.USERNAME, username1);
        properties.put(PropertyKeyConst.PASSWORD, password1);
        namingService = NacosFactory.createNamingService(properties);

        List<Instance> list = namingService.getAllInstances("test.1");
        Assert.assertEquals(1, list.size());
    }

    @Test
    public void writeWithWritePermission() throws Exception {

        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        namingService = NacosFactory.createNamingService(properties);

        namingService.registerInstance("test.1", "1.2.3.4", 80);

        TimeUnit.SECONDS.sleep(5L);

        namingService.deregisterInstance("test.1", "1.2.3.4", 80);
    }

    @Test
    public void readWithWritePermission() throws Exception {

        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        namingService = NacosFactory.createNamingService(properties);

        namingService.registerInstance("test.1", "1.2.3.4", 80);
        TimeUnit.SECONDS.sleep(5L);

        List<Instance> list = namingService.getAllInstances("test.1");

        Assert.assertEquals(0, list.size());

    }

    @Test
    public void readWriteWithFullPermission() throws Exception {

        properties.put(PropertyKeyConst.USERNAME, username3);
        properties.put(PropertyKeyConst.PASSWORD, password3);
        namingService = NacosFactory.createNamingService(properties);

        namingService.registerInstance("test.1", "1.2.3.4", 80);
        TimeUnit.SECONDS.sleep(5L);

        List<Instance> list = namingService.getAllInstances("test.1");

        Assert.assertEquals(1, list.size());
    }

}
