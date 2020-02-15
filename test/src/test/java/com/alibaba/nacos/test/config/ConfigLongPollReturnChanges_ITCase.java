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
package com.alibaba.nacos.test.config;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigLongPollReturnChanges_ITCase {
    @LocalServerPort
    private int port;

    private ConfigService configService;

    @Before
    public void init() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + port);
        properties.put(PropertyKeyConst.CONFIG_LONG_POLL_TIMEOUT, "20000");
        properties.put(PropertyKeyConst.CONFIG_RETRY_TIME, "3000");
        properties.put(PropertyKeyConst.MAX_RETRY, "5");
        configService = NacosFactory.createConfigService(properties);
    }

    @Test
    public void testAdd() throws InterruptedException, NacosException {
        CountDownLatch latch = new CountDownLatch(1);

        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String content = "config data";

        configService.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                Assert.assertEquals(null, cci.getOldValue());
                Assert.assertEquals(content, cci.getNewValue());
                Assert.assertEquals(PropertyChangeType.ADDED, cci.getType());
                System.out.println(cci);
                latch.countDown();
            }

        });
        configService.publishConfig(dataId, group, content);

        latch.await();
    }

    @Test
    public void testModify() throws InterruptedException, NacosException {
        CountDownLatch latch = new CountDownLatch(1);

        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String oldData = "old data";
        final String newData = "new data";

        configService.publishConfig(dataId, group, oldData);

        // query config immediately may return null
        String config = null;
        do {
            TimeUnit.SECONDS.sleep(1);
            config = configService.getConfig(dataId, group, 50);
        } while(null == config);

        configService.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                Assert.assertEquals(oldData, cci.getOldValue());
                Assert.assertEquals(newData, cci.getNewValue());
                Assert.assertEquals(PropertyChangeType.MODIFIED, cci.getType());
                System.out.println(cci);
                latch.countDown();
            }

        });
        configService.publishConfig(dataId, group, newData);

        latch.await();
    }

    @Test
    public void testDelete() throws InterruptedException, NacosException {
        CountDownLatch latch = new CountDownLatch(1);

        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String oldData = "old data";

        configService.publishConfig(dataId, group, oldData);

        // query config immediately may return null
        String config = null;
        do {
            TimeUnit.SECONDS.sleep(1);
            config = configService.getConfig(dataId, group, 50);
        } while(null == config);

        configService.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                Assert.assertEquals(oldData, cci.getOldValue());
                Assert.assertEquals(null, cci.getNewValue());
                Assert.assertEquals(PropertyChangeType.DELETED, cci.getType());
                System.out.println(cci);
                latch.countDown();
            }

        });
        configService.removeConfig(dataId, group);

        latch.await();
    }

}
