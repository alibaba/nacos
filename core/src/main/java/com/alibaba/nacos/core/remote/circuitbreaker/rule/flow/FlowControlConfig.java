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

package com.alibaba.nacos.core.remote.circuitbreaker.rule.flow;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;

import java.util.concurrent.TimeUnit;

public class FlowControlConfig extends CircuitBreakerConfig {

    private long maxLoad = -1;

    public FlowControlConfig() {
        this(100, TimeUnit.SECONDS, MODEL_FUZZY, "");
    }

    public FlowControlConfig(long maxCount) {
        this(maxCount, TimeUnit.SECONDS, MODEL_FUZZY, "");
    }

    public FlowControlConfig(String monitorType) {
        this(100, TimeUnit.SECONDS, MODEL_FUZZY, monitorType);
    }

    public FlowControlConfig(long maxLoad, TimeUnit period, String model, String monitorType) {
        this.setMonitorType(monitorType);
        this.maxLoad = maxLoad;
        this.setPeriod(period);
        this.setModel(model);
    }

    public void setMaxLoad(long maxCount) { this.maxLoad = maxCount; }

    public long getMaxLoad() { return maxLoad; }
}
