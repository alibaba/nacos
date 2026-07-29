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

package com.alibaba.nacos.console.model.ai;

import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;

/**
 * Console view of one Agent Runtime Endpoint snapshot and its Naming service reference.
 *
 * @author Nacos
 */
public class ConsoleRuntimeEndpointView {
    
    private final RuntimeEndpointSnapshot runtimeEndpointSnapshot;
    
    private final NamingServiceRef namingServiceRef;
    
    public ConsoleRuntimeEndpointView(RuntimeEndpointSnapshot runtimeEndpointSnapshot,
        NamingServiceRef namingServiceRef) {
        this.runtimeEndpointSnapshot = runtimeEndpointSnapshot;
        this.namingServiceRef = namingServiceRef;
    }
    
    public RuntimeEndpointSnapshot getRuntimeEndpointSnapshot() {
        return runtimeEndpointSnapshot;
    }
    
    public NamingServiceRef getNamingServiceRef() {
        return namingServiceRef;
    }
    
    /**
     * Physical Naming service coordinates for one Agent and protocol.
     *
     * @author Nacos
     */
    public static class NamingServiceRef {
        
        private final String namespaceId;
        
        private final String groupName;
        
        private final String serviceName;
        
        public NamingServiceRef(String namespaceId, String groupName, String serviceName) {
            this.namespaceId = namespaceId;
            this.groupName = groupName;
            this.serviceName = serviceName;
        }
        
        public String getNamespaceId() {
            return namespaceId;
        }
        
        public String getGroupName() {
            return groupName;
        }
        
        public String getServiceName() {
            return serviceName;
        }
    }
}
