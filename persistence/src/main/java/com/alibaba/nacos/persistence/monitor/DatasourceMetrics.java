/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

/**
 * Metrics for datasource.
 *
 * @author xiweng.yy
 */
public class DatasourceMetrics {
    
    public static Counter getDbException() {
        // TODO: After {@code NacosMeterRegistryCenter} move to more basic module, the usage can be changed.
        // TODO: Current {@code NacosMeterRegistryCenter} is in core module, but core module maybe depend persistence to save namespace.
        return Metrics.counter("nacos_exception", "module", "config", "name", "db");
    }
}
