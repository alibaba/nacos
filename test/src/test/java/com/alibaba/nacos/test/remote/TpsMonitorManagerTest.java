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

import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TpsMonitorManagerTest {
    
    static TpsMonitorManager tpsMonitorManager = null;
    
    static List<String> testPoints = null;
    
    static {
        
        tpsMonitorManager = new TpsMonitorManager();
        testPoints = Arrays
                .asList("test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9", "test10");
        for (String point : testPoints) {
            tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(point));
        }
        
    }
    
    @Test
    public void runTest() {
        long start = System.currentTimeMillis();
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            tpsMonitorManager.applyTps("", testPoints.get(random.nextInt(testPoints.size())));
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Time costs:" + (end - start));
        System.out.println(tpsMonitorManager.points.get("test1").getTpsRecorder().getSlotList());
    }
}
