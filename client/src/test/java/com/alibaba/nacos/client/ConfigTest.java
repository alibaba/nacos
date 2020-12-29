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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Ignore
public class ConfigTest {
    
    private static ConfigService configService;
    
    @Before
    public void before() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        configService = NacosFactory.createConfigService(properties);
    }
    
    @After
    public void cleanup() throws Exception {
        configService.shutDown();
    }
    
    @Test
    public void test() throws Exception {
        // set config
        final String dataId = "lessspring";
        final String group = "lessspring";
        final String content = "lessspring-" + System.currentTimeMillis();
        boolean result = configService.publishConfig(dataId, group, content);
        Assert.assertTrue(result);
        
        ThreadUtils.sleep(10000L);
        
        // set change listener
        final AtomicBoolean hasListener = new AtomicBoolean(false);
        final AtomicBoolean hasChangedCallback = new AtomicBoolean(false);
        final String[] changedTmpContent = {""};
        String response = configService.getConfigAndSignListener(dataId, group, 5000, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receiveConfigInfo:" + configInfo);
                changedTmpContent[0] = configInfo;
                hasChangedCallback.set(true);
            }
        });
        hasListener.set(true);
        Assert.assertEquals(content, response);
        
        // new thread to publish config
        final String newRawContent = "nacosnewconfig-" + System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (hasListener.get()) {
                    try {
                        configService.publishConfig(dataId, group, newRawContent);
                        hasListener.set(false);
                        break;
                    } catch (NacosException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
        
        // spin
        do {
            if (hasChangedCallback.get()) {
                System.out.println(newRawContent + "==> " + changedTmpContent[0]);
                Assert.assertEquals(newRawContent, changedTmpContent[0]);
                break;
            }
        } while (!hasChangedCallback.get());
    }
    
}
