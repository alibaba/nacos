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

import java.util.Map;

/**
 * Info class in charge of storing and monitoring current server point status (tps / tps window / network flow etc.)
 * Can be extended for custom implementations
 * TODO: design a generic status implementation that contains necessary fields
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月07日 22:50 PM chuzefang Exp $
 */
public abstract class CircuitBreakerMonitor {

    public abstract CircuitBreakerRecorder getPointRecorder();

    public abstract Map<String, CircuitBreakerRecorder> getMonitorKeysRecorder();
}

