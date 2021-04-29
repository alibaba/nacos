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
import com.alibaba.nacos.core.remote.control.ClientIpMonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorType;
import com.alibaba.nacos.core.remote.control.TpsControlRule;
import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;
import com.alibaba.nacos.core.remote.control.TpsRecorder;
import org.apache.commons.collections.map.HashedMap;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Ignore("It should be Unit test, not IT")
public class NamingTpsMonitorManagerTest {
    
    static TpsMonitorManager tpsMonitorManager = null;
    
    static List<String> testPoints = null;
    
    static {
        
        tpsMonitorManager = new TpsMonitorManager();
        testPoints = Arrays
                .asList("test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9", "test10");
        for (String point : testPoints) {
            TpsMonitorPoint tpsMonitorPoint = new TpsMonitorPoint(point);
            TpsControlRule tpsControlRule = new TpsControlRule();
            Map<String, TpsControlRule.Rule> monitorKeyRule = new HashedMap();
            monitorKeyRule.put(new ClientIpMonitorKey("1").build(),
                    new TpsControlRule.Rule(10, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                            MonitorType.INTERCEPT.getType()));
            monitorKeyRule.put(new ClientIpMonitorKey("5").build(),
                    new TpsControlRule.Rule(10, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                            MonitorType.INTERCEPT.getType()));
            tpsControlRule.setMonitorKeyRule(monitorKeyRule);
            tpsControlRule.setPointRule(new TpsControlRule.Rule(100, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_FUZZY,
                    MonitorType.INTERCEPT.getType()));
            tpsMonitorManager.registerTpsControlPoint(tpsMonitorPoint);
            System.out.println(JacksonUtils.toJson(tpsControlRule));
            tpsMonitorPoint.applyRule(tpsControlRule);
            
        }
        
    }
    
    
    @Test
    public void printlnJson() {
        TpsControlRule tpsControlRule = new TpsControlRule();
        Map<String, TpsControlRule.Rule> monitorKeyRule = new HashedMap();
        monitorKeyRule.put(new ClientIpMonitorKey("1").build(),
                new TpsControlRule.Rule(10, TimeUnit.SECONDS, "SUM", MonitorType.INTERCEPT.getType()));
        monitorKeyRule.put(new ClientIpMonitorKey("5").build(),
                new TpsControlRule.Rule(10, TimeUnit.SECONDS, "SUM", MonitorType.INTERCEPT.getType()));
        tpsControlRule.setMonitorKeyRule(monitorKeyRule);
        tpsControlRule
                .setPointRule(new TpsControlRule.Rule(100, TimeUnit.SECONDS, "SUM", MonitorType.INTERCEPT.getType()));
        System.out.println(JacksonUtils.toJson(tpsControlRule));
    }
    
    @Test
    public void runTest() {
        long start = System.currentTimeMillis();
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            tpsMonitorManager.applyTps(testPoints.get(random.nextInt(testPoints.size())), "connectionId1",
                    Arrays.asList(new ClientIpMonitorKey("" + new Random().nextInt(10))));
            //tpsMonitorManager.applyTpsForClientIp(testPoints.get(random.nextInt(testPoints.size())), "", "");
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Time costs:" + (end - start));
        System.out.println(tpsMonitorManager.points.get("test1").getTpsRecorder().getSlotList());
        for (Map.Entry<String, TpsRecorder> entry : tpsMonitorManager.points.get("test1").monitorKeysRecorder
                .entrySet()) {
            System.out.println("Monitor pattern:" + entry.getKey());
            System.out.println(entry.getValue().getSlotList());
        }
    }
}
