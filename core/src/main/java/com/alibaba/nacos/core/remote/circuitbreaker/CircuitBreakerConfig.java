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

/**
 * Config class for circuit breaker. Can be used as base config class.
 * TODO: design more generic configs
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月06日 12:38 PM chuzefang Exp $
 */
public class CircuitBreakerConfig {

    public static final String MODEL_FUZZY = "FUZZY";

    public static final String MODEL_PROTO = "PROTO";

    private String monitorType = "";

    private String model = MODEL_FUZZY;

    private boolean isActive = true;

    public void setIsActive(boolean isActive) { this.isActive = isActive; }

    public boolean getIsActive() { return isActive; }

    public void setModel(String model) { this.model = model; }

    public String getModel() { return model; }

    public void setMonitorType(String monitorType) { this.monitorType = monitorType; }

    public String getMonitorType() { return monitorType; }

}
