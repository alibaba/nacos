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

package com.alibaba.nacos.core.remote;

/**
 * Enum representing different types of communication.
 *
 * <p>CommunicationType includes:</p>
 * <ul>
 *     <li>SDK: Communication between SDK and servers.</li>
 *     <li>CLUSTER: Communication between servers in a cluster.</li>
 * </ul>
 *
 * @author stone-98
 * @date 2023/12/23
 */
public enum CommunicationType {
    /**
     * Communication between SDK and servers.
     */
    SDK("sdk"),
    /**
     * Communication between servers in a cluster.
     */
    CLUSTER("cluster");
    
    private final String type;
    
    CommunicationType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
}

