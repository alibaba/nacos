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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class TpsControlPoint {
    
    private String pointName;
    
    TpsRecorder tpsRecorder;
    
    Map<String, TpsRecorder> tpsRecordForIp = new HashMap<String, TpsRecorder>();
    
    public boolean applyTps(String clientIp) {
        /**
         * 1.check ip tps.
         */
        if (tpsRecordForIp.containsKey(clientIp)) {
            TpsRecorder tpsRecorder = tpsRecordForIp.get(clientIp);
            AtomicLong currentTps = tpsRecorder.getCurrentTps();
            long maxTpsOfIp = tpsRecorder.getMaxTps();
            if (tpsRecorder.isInterceptMode() && maxTpsOfIp > 0 && currentTps.longValue() >= maxTpsOfIp) {
                return false;
            }
            currentTps.incrementAndGet();
            
        }
    
        /**
         * 2.check total tps.
         */
        long maxTps = tpsRecorder.getMaxTps();
        if (tpsRecorder.isInterceptMode() && maxTps > 0 && tpsRecorder.getCurrentTps().longValue() >= maxTps) {
            return false;
        }
        tpsRecorder.getCurrentTps().incrementAndGet();
    
        /**
         * 3.check pass.
         */
        return true;
    }
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    protected void refreshRecorder(){
    
    }
    
}
