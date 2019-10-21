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

package com.alibaba.nacos.test.lifecycle;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.naming.NacosNamingMaintainService;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Properties;

/**
 * @author liaochuntao
 * @date 2019/9/26 4:42 下午
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LifeCycle_ITCase {

    private NamingMaintainService namingMaintainService;
    private NamingService namingService;
    private ConfigService configService;

    @LocalServerPort
    private int port;

    @Test
    public void createInitialization() throws NacosException {

        String serverAddr = "127.0.0.1:" + port;

        namingMaintainService = NacosFactory.createMaintainService(serverAddr);
        namingService = NamingFactory.createNamingService(serverAddr);
        configService = NacosFactory.createConfigService(serverAddr);

        Assert.assertTrue(namingMaintainService.isStarted());
        Assert.assertTrue(namingService.isStarted());
        Assert.assertTrue(configService.isStarted());

    }

    @Test
    public void destructionService() throws NacosException {

        String serverAddr = "127.0.0.1:" + port;

        namingMaintainService = NacosFactory.createMaintainService(serverAddr);
        namingService = NamingFactory.createNamingService(serverAddr);
        configService = NacosFactory.createConfigService(serverAddr);

        Assert.assertFalse(namingMaintainService.isDestroyed());
        Assert.assertFalse(namingService.isDestroyed());
        Assert.assertFalse(configService.isDestroyed());

    }

    @Test
    public void notStartAndUseDestroy() throws NacosException {
        String serverAddr = "127.0.0.1:" + port;
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        namingService = new NacosNamingService(properties);
        namingMaintainService = new NacosNamingMaintainService(properties);
        configService = new NacosConfigService(properties);

        namingService.destroy();
        namingMaintainService.destroy();
        configService.destroy();

        Assert.assertFalse(namingMaintainService.isDestroyed());
        Assert.assertFalse(namingService.isDestroyed());
        Assert.assertFalse(configService.isDestroyed());
    }

}
