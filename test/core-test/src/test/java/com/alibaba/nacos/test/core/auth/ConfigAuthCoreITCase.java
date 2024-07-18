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
import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for Nacos configuration with authentication.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.contextPath=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigAuthCoreITCase extends AuthBase {
    
    public static final long TIME_OUT = 2000;
    
    public ConfigService iconfig = null;
    
    @LocalServerPort
    private int port;
    
    private final String dataId = "yanlin";
    
    private final String group = "yanlin";
    
    @BeforeEach
    void init() throws Exception {
        super.init(port);
    }
    
    /**
     * Cleans up resources after each test execution.
     */
    @AfterEach
    public void destroy() {
        super.destroy();
        try {
            if (iconfig != null) {
                iconfig.shutDown();
            }
        } catch (NacosException ex) {
            // Ignored exception during shutdown
        }
    }
    
    @Test
    void writeWithReadPermission() throws Exception {
        
        // Construct configService:
        properties.put(PropertyKeyConst.USERNAME, username1);
        properties.put(PropertyKeyConst.PASSWORD, password1);
        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
        iconfig = NacosFactory.createConfigService(properties);
        
        final String content = "test";
        assertFalse(iconfig.publishConfig(dataId, group, content));
        assertFalse(iconfig.removeConfig(dataId, group));
    }
    
    @Test
    void readWithReadPermission() throws Exception {
        properties.put(PropertyKeyConst.USERNAME, username1);
        properties.put(PropertyKeyConst.PASSWORD, password1);
        iconfig = NacosFactory.createConfigService(properties);
        
        final String content = "test" + System.currentTimeMillis();
        System.out.println(content);
        
        CountDownLatch latch = new CountDownLatch(1);
        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content.equals(cci.getNewValue())) {
                    return;
                }
                latch.countDown();
            }
        });
        
        TimeUnit.SECONDS.sleep(3L);
        
        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        ConfigService configService = NacosFactory.createConfigService(properties);
        
        boolean result = configService.publishConfig(dataId, group, content);
        assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);
        
        String res = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content, res);
        
        latch.await(5L, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());
    }
    
    @Test
    void writeWithWritePermission() throws Exception {
        
        // Construct configService:
        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        iconfig = NacosFactory.createConfigService(properties);
        
        final String content = "test";
        boolean res = iconfig.publishConfig(dataId, group, content);
        assertTrue(res);
        
        res = iconfig.removeConfig(dataId, group);
        assertTrue(res);
    }
    
    @Test
    void readWithWritePermission() throws Exception {
        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        iconfig = NacosFactory.createConfigService(properties);
        
        final String content = "test" + System.currentTimeMillis();
        
        CountDownLatch latch = new CountDownLatch(1);
        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content.equals(cci.getNewValue())) {
                    return;
                }
                latch.countDown();
            }
        });
        
        TimeUnit.SECONDS.sleep(3L);
        
        boolean result = iconfig.publishConfig(dataId, group, content);
        assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);
        
        try {
            iconfig.getConfig(dataId, group, TIME_OUT);
            fail();
        } catch (NacosException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getErrCode());
        }
        
        latch.await(5L, TimeUnit.SECONDS);
        
        assertTrue(latch.getCount() > 0);
    }
    
    @Test
    void readWriteWithFullPermission() throws Exception {
        properties.put(PropertyKeyConst.USERNAME, username3);
        properties.put(PropertyKeyConst.PASSWORD, password3);
        iconfig = NacosFactory.createConfigService(properties);
        
        final String content = "test" + System.currentTimeMillis();
        
        CountDownLatch latch = new CountDownLatch(1);
        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content.equals(cci.getNewValue())) {
                    return;
                }
                latch.countDown();
            }
        });
        
        TimeUnit.SECONDS.sleep(3L);
        
        boolean result = iconfig.publishConfig(dataId, group, content);
        assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);
        
        String res = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content, res);
        
        latch.await(5L, TimeUnit.SECONDS);
        
        assertEquals(0, latch.getCount());
        
        result = iconfig.removeConfig(dataId, group);
        assertTrue(result);
    }
    
}
