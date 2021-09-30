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

package com.alibaba.nacos.test.remote;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreaker;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;
import com.alibaba.nacos.core.remote.circuitbreaker.rule.tps.TpsConfig;
import com.alibaba.nacos.core.remote.control.*;
import org.apache.commons.collections.map.HashedMap;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Ignore("It should be Unit test, not IT")
public class NamingCircuitBreakerTest {

    static TpsMonitorManager tpsMonitorManager = null;

    static CircuitBreaker circuitBreaker = null;

    static List<String> testPoints = null;

    static {

        testPoints = Arrays
                .asList("test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9", "test10");
        circuitBreaker = new CircuitBreaker();
        for (String point : testPoints) {
            circuitBreaker.registerPoint(point);
            Map<String, CircuitBreakerConfig> monitorKeyRule = new HashedMap();
            TpsConfig pointConfig = new TpsConfig(100, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                    MonitorType.INTERCEPT.getType());

            monitorKeyRule.put(new ClientIpMonitorKey("1").build(),
                    new TpsConfig(10, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                            MonitorType.INTERCEPT.getType()));
            monitorKeyRule.put(new ClientIpMonitorKey("5").build(),
                    new TpsConfig(10, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                            MonitorType.INTERCEPT.getType()));

            circuitBreaker.applyRule(point, pointConfig, monitorKeyRule);
            System.out.println(JacksonUtils.toJson(monitorKeyRule));
        }

    }


    @Test
    public void runTest() {
        long start = System.currentTimeMillis();
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            circuitBreaker.applyStrategy(testPoints.get(random.nextInt(testPoints.size())),
                    Arrays.asList(new ClientIpMonitorKey("" + new Random().nextInt(10)),
                            new ConnectionIdMonitorKey("connectionId1")));
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Time costs:" + (end - start));
        System.out.println(circuitBreaker.getCurrentRule().getPointToMonitorMap().get("test1").getCurrentRecorder().getSlotList());
        for (Map.Entry<String, CircuitBreakerRecorder> entry : circuitBreaker.getCurrentRule().getPointToMonitorMap().get("test1").getMonitorKeyRecorders()
                .entrySet()) {
            System.out.println("Monitor pattern:" + entry.getKey());
            System.out.println(entry.getValue().getSlotList());
        }
    }
}
