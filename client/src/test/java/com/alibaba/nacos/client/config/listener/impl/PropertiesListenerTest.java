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

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PropertiesListenerTest {
    
    @Test
    void testReceiveConfigInfo() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        a.receiveConfigInfo("foo=bar");
        final Properties actual = q2.poll();
        assertEquals(1, actual.size());
        assertEquals("bar", actual.getProperty("foo"));
        
    }
    
    @Test
    void testReceiveConfigInfoEmpty() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        a.receiveConfigInfo("");
        final Properties actual = q2.poll();
        assertNull(actual);
    }
    
    @Test
    void testReceiveConfigInfoIsNotProperties() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        a.receiveConfigInfo(null);
        final Properties actual = q2.poll();
        assertNull(actual);
    }
    
    @Test
    void testInnerReceive() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        Properties input = new Properties();
        input.put("foo", "bar");
        a.innerReceive(input);
        final Properties actual = q2.poll();
        assertEquals(1, actual.size());
        assertEquals("bar", actual.getProperty("foo"));
    }
    
}