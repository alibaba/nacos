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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class ConfigChangePublisherTest {
    
    @Test
    public void testConfigChangeNotify() throws InterruptedException {
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        NotifyCenter.registerToPublisher(ConfigDataChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                reference.set((ConfigDataChangeEvent) event);
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        // nacos is standalone mode and use embedded storage
        EnvUtil.setIsStandalone(true);
        PropertyUtil.setEmbeddedStorage(true);
        
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
        Thread.sleep(2000);
        Assert.assertNotNull(reference.get());
        reference.set(null);
        
        // nacos is standalone mode and use external storage
        EnvUtil.setIsStandalone(true);
        PropertyUtil.setEmbeddedStorage(false);
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
        Thread.sleep(2000);
        Assert.assertNotNull(reference.get());
        reference.set(null);
        
        // nacos is cluster mode and use embedded storage
        EnvUtil.setIsStandalone(false);
        PropertyUtil.setEmbeddedStorage(true);
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
        Thread.sleep(2000);
        Assert.assertNull(reference.get());
        reference.set(null);
        
        // nacos is cluster mode and use external storage
        EnvUtil.setIsStandalone(false);
        PropertyUtil.setEmbeddedStorage(false);
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
        Thread.sleep(2000);
        Assert.assertNotNull(reference.get());
        reference.set(null);
    }
    
}
