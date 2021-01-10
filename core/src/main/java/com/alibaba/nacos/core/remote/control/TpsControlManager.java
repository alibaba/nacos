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

package com.alibaba.nacos.core.remote.control;

import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class TpsControlManager {
    
    private Map<String, TpsControlPoint> points = new ConcurrentHashMap<String, TpsControlPoint>(16);
    
    public TpsControlManager() {
        RpcScheduledExecutor.COMMON_SERVER_EXECUTOR.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    
                    Set<Map.Entry<String, TpsControlPoint>> entries = points.entrySet();
                    long currentMillis = System.currentTimeMillis();
                    long nextSecondMillis = System.currentTimeMillis() + 1000L;
                    for (Map.Entry<String, TpsControlPoint> entry : entries) {
                        TpsControlPoint tpsControlPoint = entry.getValue();
                        TpsRecorder tpsRecorder = tpsControlPoint.tpsRecorder;
                        tpsRecorder.checkSecond(currentMillis);
                        tpsRecorder.checkSecond(nextSecondMillis);
                        Map<String, TpsRecorder> tpsRecordForIp = tpsControlPoint.tpsRecordForIp;
                        if (tpsRecordForIp != null) {
                            for (TpsRecorder tpsIp : tpsRecordForIp.values()) {
                                tpsIp.checkSecond(currentMillis);
                                tpsIp.checkSecond(nextSecondMillis);
                            }
                        }
                        
                    }
                } catch (Throwable throwable) {
                    //check point error.
                }
                
            }
        }, 0L, 1L, TimeUnit.SECONDS);
    }
}
