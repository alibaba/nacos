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
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ConfigLongPollReturnChangesConfigITCase {
    
    @LocalServerPort
    private int port;
    
    private ConfigService configService;
    
    @BeforeAll
    @AfterAll
    static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigLongPollReturnChangesConfigITCase.class.getSimpleName());
    }
    
    @BeforeEach
    void init() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + port);
        properties.put(PropertyKeyConst.CONFIG_LONG_POLL_TIMEOUT, "20000");
        properties.put(PropertyKeyConst.CONFIG_RETRY_TIME, "3000");
        properties.put(PropertyKeyConst.MAX_RETRY, "5");
        configService = NacosFactory.createConfigService(properties);
    }
    
    @AfterEach
    void destroy() {
        try {
            configService.shutDown();
        } catch (NacosException ex) {
            // ignore
        }
    }
    
    @Test
    void testAdd() throws InterruptedException, NacosException {
        CountDownLatch latch = new CountDownLatch(1);
        
        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String content = "config data";
        
        configService.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                try {
                    ConfigChangeItem cci = event.getChangeItem("content");
                    assertNull(cci.getOldValue());
                    assertEquals(content, cci.getNewValue());
                    assertEquals(PropertyChangeType.ADDED, cci.getType());
                    System.out.println(cci);
                } finally {
                    latch.countDown();
                }
            }
        });
        boolean result = configService.publishConfig(dataId, group, content);
        assertTrue(result);
        
        configService.getConfig(dataId, group, 50);
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
    }
    
    @Test
    void testModify() throws InterruptedException, NacosException {
        CountDownLatch latch = new CountDownLatch(1);
        
        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String oldData = "old data";
        final String newData = "new data";
        
        boolean result = configService.publishConfig(dataId, group, oldData);
        
        assertTrue(result);
        
        configService.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                try {
                    ConfigChangeItem cci = event.getChangeItem("content");
                    assertEquals(oldData, cci.getOldValue());
                    assertEquals(newData, cci.getNewValue());
                    assertEquals(PropertyChangeType.MODIFIED, cci.getType());
                    System.out.println(cci);
                } finally {
                    latch.countDown();
                }
            }
            
        });
        configService.publishConfig(dataId, group, newData);
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
    }
    
    @Test
    void testDelete() throws InterruptedException, NacosException {
        CountDownLatch latch = new CountDownLatch(1);
        
        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String oldData = "old data";
        
        boolean result = configService.publishConfig(dataId, group, oldData);
        assertTrue(result);
        
        configService.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                try {
                    ConfigChangeItem cci = event.getChangeItem("content");
                    assertEquals(oldData, cci.getOldValue());
                    assertNull(cci.getNewValue());
                    assertEquals(PropertyChangeType.DELETED, cci.getType());
                    System.out.println(cci);
                } finally {
                    latch.countDown();
                }
            }
            
        });
        configService.removeConfig(dataId, group);
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
    }
    
}
