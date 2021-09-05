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

package com.alibaba.nacos.core.remote.circuitbreaker.rules.impl;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * point configs for TPS strategy. Extends from CircuitBreakerConfig.
 *
 * @author czf
 */
public class TpsConfig extends CircuitBreakerConfig {

    private long maxCount = -1;

    private TimeUnit period = TimeUnit.SECONDS;

    public TpsConfig() {
        this("", 100, TimeUnit.SECONDS, "");
    }

    public TpsConfig(long maxCount) {
        this("", maxCount, TimeUnit.SECONDS, "");
    }

    public TpsConfig(String monitorType) {
        this(monitorType, 100, TimeUnit.SECONDS, "");
    }

    public TpsConfig(String monitorType, long maxCount, TimeUnit period, String model) {
        this.setMonitorType(monitorType);
        this.maxCount = maxCount;
        this.period = period;
        this.setModel(model);
    }

    public void setMaxCount(long maxCount) { this.maxCount = maxCount; }

    public long getMaxCount() { return maxCount; }

    public void setPeriod(TimeUnit period) { this.period = period; }

    public TimeUnit getPeriod() { return period; }



}
