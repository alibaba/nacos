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

package com.alibaba.nacos.client;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Properties;
import java.util.Scanner;

@Ignore
public class ConfigTest {
    
    private static ConfigService configService;
    
    @Before
    public void before() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:28848");
        configService = NacosFactory.createConfigService(properties);
    }
    
    @After
    public void cleanup() throws Exception {
        configService.shutDown();
    }
    
    
    @Test
    public void test() throws Exception {
        
        final String dataId = "lessspring";
        final String group = "lessspring";
        final String content = "lessspring-" + System.currentTimeMillis();
        boolean result = configService.publishConfig(dataId, group, content);
        Assert.assertTrue(result);
    
        ThreadUtils.sleep(200L);
    
        ConfigListener1 listener1 = new ConfigListener1();
        ConfigListener2 listener2 = new ConfigListener2();
    
        configService.getConfigAndSignListener(dataId, group, 5000, listener1);
        configService.getConfigAndSignListener(dataId, group, 5000, listener2);
    
        configService.publishConfig(dataId, group, "testchange");
    
        configService.getConfigAndSignListener("lessspring2", group, 5000, listener1);
    
        configService.publishConfig("lessspring2", group, "lessspring2value");
    
        Scanner scanner = new Scanner(System.in);
        System.out.println("input content");
        while (scanner.hasNextLine()) {
            String s = scanner.next();
            if ("exit".equals(s)) {
                scanner.close();
                return;
            }
            configService.publishConfig(dataId, group, s);
        }
    }
    
}


class ConfigListener1 extends AbstractListener {
    
    @Override
    public void receiveConfigInfo(String configInfo) {
        System.err.println("Listener1 invoked." + configInfo);
    }
}

class ConfigListener2 extends AbstractListener {
    
    @Override
    public void receiveConfigInfo(String configInfo) {
        System.err.println("Listener2 invoked." + configInfo);
    }
}