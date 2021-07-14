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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * {@link TpsRecorder} unit test.
 *
 * @author chenglu
 * @date 2021-06-21 18:23
 */
public class TpsRecorderTest {
    
    TpsRecorder tpsRecorder;
    
    long start;
    
    @Before
    public void init() {
        start = System.currentTimeMillis();
        tpsRecorder = new TpsRecorder(start, TimeUnit.SECONDS, TpsControlRule.Rule.MODEL_PROTO, 10);
    }
    
    @Test
    public void testTpsSlot() {
        long current = start + 100;
        TpsRecorder.TpsSlot tpsSlot = tpsRecorder.createSlotIfAbsent(start);
        Assert.assertNotNull(tpsSlot);
    
        TpsRecorder.TpsSlot tpsSlot1 = tpsRecorder.getPoint(current);
        Assert.assertNotNull(tpsSlot1);
    }
    
    @Test
    public void testMonitorType() {
        Assert.assertTrue(tpsRecorder.isProtoModel());
    
        Assert.assertEquals(MonitorType.MONITOR.type, tpsRecorder.getMonitorType());
        
        tpsRecorder.clearLimitRule();
        
        Assert.assertFalse(tpsRecorder.isInterceptMode());
    }
}
