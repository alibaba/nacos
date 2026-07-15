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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;

import java.util.Optional;

/**
 * Bridge for locating visibility-aware resources by identifier.
 *
 * <p>Domain modules such as AI can provide an implementation so plugin-owned
 * visibility management APIs can verify owner and existence without taking a
 * direct compile-time dependency on domain persistence types.</p>
 *
 * @author Zhengcy05
 */
public interface VisibilityResourceLocator {
    
    /**
     * Locate one visibility-aware resource.
     *
     * @param namespaceId namespace ID
     * @param resourceType resource type
     * @param resourceName resource name
     * @return optional resource metadata
     */
    Optional<VisibilityResource> findResource(String namespaceId, String resourceType,
        String resourceName);
}
