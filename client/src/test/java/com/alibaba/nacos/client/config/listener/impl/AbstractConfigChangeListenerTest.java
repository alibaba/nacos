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

package com.alibaba.nacos.client.config.listener.impl;

import com.alibaba.nacos.api.config.ConfigChangeEvent;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

public class AbstractConfigChangeListenerTest {
    
    @Test
    public void receiveConfigInfo() {
        final Deque<String> data = new ArrayDeque<String>();
        AbstractConfigChangeListener a = new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                super.receiveConfigInfo(configInfo);
                data.offer(configInfo);
            }
        };
        a.receiveConfigInfo("foo");
        final String actual = data.poll();
        Assert.assertEquals("foo", actual);
    }
}