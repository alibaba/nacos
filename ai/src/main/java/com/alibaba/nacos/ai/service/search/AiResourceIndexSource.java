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

/**
 * One canonical resource observed during bounded index reconciliation.
 *
 * @author nacos
 */
public class AiResourceIndexSource {
    
    private final String resourceName;
    
    private final AiResourceIndexProjection projection;
    
    private final Exception failure;
    
    private AiResourceIndexSource(String resourceName, AiResourceIndexProjection projection,
        Exception failure) {
        this.resourceName = resourceName;
        this.projection = projection;
        this.failure = failure;
    }
    
    public static AiResourceIndexSource success(String resourceName,
        AiResourceIndexProjection projection) {
        return new AiResourceIndexSource(resourceName, projection, null);
    }
    
    public static AiResourceIndexSource failed(String resourceName, Exception failure) {
        return new AiResourceIndexSource(resourceName, null, failure);
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public AiResourceIndexProjection getProjection() {
        return projection;
    }
    
    public Exception getFailure() {
        return failure;
    }
}
