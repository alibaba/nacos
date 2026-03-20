/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datafilter.spi;

import com.alibaba.nacos.plugin.datafilter.model.FilterableResource;

import java.util.List;

/**
 * SPI for filtering data resources by visibility and operability rules.
 *
 * <p>Different modules can provide their own implementations, typically paired with
 * a specific auth plugin to understand its identity model.
 *
 * @author xiweng.yy
 */
public interface DataFilterService {
    
    /**
     * Filter candidate resources based on the specified action.
     *
     * @param currentUser identity of the current authenticated user
     * @param action      {@code "r"} for read or {@code "w"} for write
     * @param candidates  candidate resources (must extend FilterableResource)
     * @param <T>         resource type
     * @return filtered list containing only permitted resources
     */
    <T extends FilterableResource> List<T> filter(String currentUser, String action, List<T> candidates);
    
    /**
     * Get the name identifier of this data filter service, e.g. "nacos-default-ai".
     *
     * @return filter service name
     */
    String getFilterServiceName();
}
