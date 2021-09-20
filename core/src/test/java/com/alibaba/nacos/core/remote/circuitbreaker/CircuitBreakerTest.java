/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.circuitbreaker;

import com.alibaba.nacos.core.remote.circuitbreaker.rule.tps.TpsConfig;
import com.alibaba.nacos.core.remote.control.*;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class CircuitBreakerTest {

    static CircuitBreaker circuitBreaker;

    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        circuitBreaker = new CircuitBreaker();
        circuitBreaker.registerPoint("test1");
        TimeUnit.SECONDS.sleep(1);
        Map<String, CircuitBreakerConfig> monitorKeyMap = new HashMap<>();
        monitorKeyMap.put("testKey:a*b", new TpsConfig(500, TimeUnit.SECONDS, "EACH", "intercept"));
        monitorKeyMap.put("testKey:*", new TpsConfig(2000000, TimeUnit.SECONDS, "SUM", "intercept"));
        TpsConfig pointConfig = new TpsConfig(800, TimeUnit.SECONDS, "SUM", "intercept");

        circuitBreaker.applyRule("test1", pointConfig, monitorKeyMap);
    }

    @Test
    public void testCircuitBreakerMonitorKeys() {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ClientIpMonitorKey("ab"));
        monitorKeyList.add(new ClientIpMonitorKey("at"));
        monitorKeyList.add(new ConnectionIdMonitorKey("connection1"));
        Assert.assertTrue(circuitBreaker.applyStrategy("test1", monitorKeyList));
    }

    @Test
    public void testCircuitBreakerTps() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ConnectionIdMonitorKey connectionId = new ConnectionIdMonitorKey("connection1");
        for (int i = 0; i < 100; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";

            boolean pass = circuitBreaker.applyStrategy("test1", Lists.list(connectionId, new TestKey(value)));
            assertTrue(pass);
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testMonitorKeyTpsWithOverFlow() {
        ConnectionIdMonitorKey connectionId = new ConnectionIdMonitorKey("connection1");
        for (int i = 0; i < 2000; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";
            boolean pass = circuitBreaker.applyStrategy("test1", Lists.list(connectionId, new TestKey(value)));
            if (!pass) {
                return;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Assert.fail("fail to limit.");
    }

    @Test
    public void testTotalTpsWithOverFlow() {
        ConnectionIdMonitorKey connectionId = new ConnectionIdMonitorKey("connection1");
        for (int i = 0; i < 1000; i++) {
            boolean pass = circuitBreaker.applyStrategy("test1", Lists.list(connectionId));
            pass &= circuitBreaker.applyStrategy("test1", Lists.list(connectionId));
            if (!pass) {
                return;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Assert.fail("fail to limit.");
    }

    class TestKey extends MonitorKey {

        public TestKey(String key) {
            setKey(key);
        }

        @Override
        public String getType() {
            return "testKey";
        }
    }

}

