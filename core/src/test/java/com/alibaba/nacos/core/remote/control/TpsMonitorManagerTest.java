/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.control;

import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class TpsMonitorManagerTest {
    
    static TpsMonitorManager tpsMonitorManager;
    
    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        tpsMonitorManager = new TpsMonitorManager();
        TpsMonitorPoint publish = new TpsMonitorPoint("configPublish");
        tpsMonitorManager.registerTpsControlPoint(publish);
        TimeUnit.SECONDS.sleep(1);
        TpsControlRule rule = new TpsControlRule();
        rule.setPointRule(new TpsControlRule.Rule(5000, TimeUnit.SECONDS, "SUM", "intercept"));
        rule.getMonitorKeyRule()
                .putIfAbsent("testKey:a*b", new TpsControlRule.Rule(500, TimeUnit.SECONDS, "EACH", "intercept"));
        rule.getMonitorKeyRule()
                .putIfAbsent("testKey:*", new TpsControlRule.Rule(2000000, TimeUnit.SECONDS, "SUM", "intercept"));
        publish.applyRule(rule);
    }
    
    @Before
    public void setUp() throws InterruptedException {
        // make sure different case will not effect each other.
        TimeUnit.SECONDS.sleep(1);
    }
    
    @Test
    public void testApplyTps() {
        for (int i = 0; i < 100; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";
            boolean pass = tpsMonitorManager
                    .applyTps("configPublish", "testconnectionId", Lists.list(new TestKey(value)));
            assertTrue(pass);
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Test
    public void testApplyTpsWithOverFlow() {
        for (int i = 0; i < 1000; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";
            boolean pass = tpsMonitorManager
                    .applyTps("configPublish", "testconnectionId", Lists.list(new TestKey(value)));
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
