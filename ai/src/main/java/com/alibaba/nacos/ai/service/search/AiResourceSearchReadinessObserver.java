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

package com.alibaba.nacos.ai.service.search;

import java.util.Collection;

/**
 * Observes completeness readiness without blocking an AI resource Search request.
 *
 * @author Nacos
 */
public interface AiResourceSearchReadinessObserver {
    
    AiResourceSearchReadinessObserver NOOP = resourceTypes -> {
    };
    
    /**
     * Observe the requested resource types before serving the current index snapshot.
     *
     * @param resourceTypes requested types, or empty to observe every searchable type
     */
    void observe(Collection<String> resourceTypes);
}
