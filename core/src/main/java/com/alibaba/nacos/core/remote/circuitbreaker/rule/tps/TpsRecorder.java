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

package com.alibaba.nacos.core.remote.circuitbreaker.rule.tps;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;

import java.util.ArrayList;

public class TpsRecorder extends CircuitBreakerRecorder {

    private TpsConfig config;

    /**
     * monitor/intercept.
     */
    public TpsRecorder(String pointName, long startTime, int recordSize, TpsConfig config) {
        super(pointName, startTime, recordSize, config);
        this.config = config;
        slotList = new ArrayList<>(slotSize);
        for (int i = 0; i < slotSize; i++) {
            slotList.add(isProtoModel() ? new MultiKeySlot() : new Slot());
        }
    }

    @Override
    public TpsConfig getConfig() { return config; }

    @Override
    public void setConfig(CircuitBreakerConfig config) { this.config = (TpsConfig) config; }

}


