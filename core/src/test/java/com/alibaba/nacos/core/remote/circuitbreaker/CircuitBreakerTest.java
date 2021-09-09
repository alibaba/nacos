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

import com.alibaba.nacos.core.remote.circuitbreaker.rules.impl.TpsConfig;
import com.alibaba.nacos.core.remote.control.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CircuitBreakerTest {

    static CircuitBreaker circuitBreaker;

    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        circuitBreaker = new CircuitBreaker();
        circuitBreaker.registerPoint("test1");
//        TimeUnit.SECONDS.sleep(1);
//        TpsControlRule rule = new TpsControlRule();
//        rule.setPointRule(new TpsControlRule.Rule(5000, TimeUnit.SECONDS, "SUM", "intercept"));
//        rule.getMonitorKeyRule()
//                .putIfAbsent("testKey:a*b", new TpsControlRule.Rule(500, TimeUnit.SECONDS, "EACH", "intercept"));
//        rule.getMonitorKeyRule()
//                .putIfAbsent("testKey:*", new TpsControlRule.Rule(2000000, TimeUnit.SECONDS, "SUM", "intercept"));
        circuitBreaker.applyRule("test1", new TpsConfig());
    }


    @Test
    public void testCircuitBreakerSpi() {
        ConnectionIdMonitorKey key = new ConnectionIdMonitorKey();
        key.setKey("123");
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(key);
        Assert.assertTrue(circuitBreaker.applyStrategy("test1", monitorKeyList));

    }

}
