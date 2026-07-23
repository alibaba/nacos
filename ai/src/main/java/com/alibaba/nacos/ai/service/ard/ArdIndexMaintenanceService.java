/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.ard;

/**
 * Schedules coalesced ARD index maintenance from canonical resource changes.
 *
 * @author nacos
 */
public interface ArdIndexMaintenanceService {
    
    ArdIndexMaintenanceService NOOP = (namespaceId, resourceType, resourceName) -> true;
    
    /**
     * Schedule one resource for durable index convergence.
     *
     * @return whether the task was durably recorded
     */
    boolean schedule(String namespaceId, String resourceType, String resourceName);
}
