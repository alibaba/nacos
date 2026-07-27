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

package com.alibaba.nacos.api.plugin;

/**
 * Execution mode shared by implementations of one plugin type.
 *
 * @author Nacos
 */
public enum PluginExecutionMode {
    
    /**
     * One implementation is selected for the process or request scope.
     */
    EXCLUSIVE,
    
    /**
     * All enabled implementations participate in an ordered chain.
     */
    CHAIN,
    
    /**
     * Enabled implementations are candidates for domain-specific routing.
     */
    ROUTED,
    
    /**
     * All enabled implementations receive the same event or observation point.
     */
    BROADCAST
}
