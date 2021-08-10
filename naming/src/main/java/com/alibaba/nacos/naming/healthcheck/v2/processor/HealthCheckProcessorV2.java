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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;

/**
 * Health check processor for v2.x.
 *
 * @author nkorange
 */
public interface HealthCheckProcessorV2 {
    
    /**
     * Run check task for service.
     *
     * @param task     health check task v2
     * @param service  service of current process
     * @param metadata cluster metadata of current process
     */
    void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata);
    
    /**
     * Get check task type, refer to enum HealthCheckType.
     *
     * @return check type
     */
    String getType();
}
