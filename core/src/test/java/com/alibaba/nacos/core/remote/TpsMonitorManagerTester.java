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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.TpsControlRule;
import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;
import org.assertj.core.util.Lists;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TpsMonitorManagerTester {
    
    static TpsMonitorManager tpsMonitorManager = new TpsMonitorManager();
    
    private final static int maxCount1 = 5000;
    
    private final static int maxCount2 = 2000000;
    
    static {
        TpsMonitorPoint publish = new TpsMonitorPoint("configPublish");
        tpsMonitorManager.registerTpsControlPoint(publish);
        TpsControlRule rule = new TpsControlRule();
        rule.setPointRule(new TpsControlRule.Rule(maxCount1, TimeUnit.SECONDS, "SUM", "intercept"));
        rule.getMonitorKeyRule()
                .putIfAbsent("testKey:a*b", new TpsControlRule.Rule(maxCount1, TimeUnit.MINUTES, "EACH", "intercept"));
        rule.getMonitorKeyRule()
                .putIfAbsent("testKey:*", new TpsControlRule.Rule(maxCount2, TimeUnit.MINUTES, "SUM", "intercept"));
        
        publish.applyRule(rule);
    }
    
    @Test
    public void test() {
        for (int i = 0; i < 10000; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";
            boolean pass = tpsMonitorManager
                    .applyTps("configPublish", "testconnectionId", Lists.list(new TestKey(value)));
            try {
                if (i < maxCount1) {
                    assertTrue(pass);
                } else {
                    assertFalse(pass);
                }
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
