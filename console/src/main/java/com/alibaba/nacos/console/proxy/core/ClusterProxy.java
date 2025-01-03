/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.console.config.ConsoleWebConfig;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.console.handler.impl.inner.core.ClusterInnerHandler;
import com.alibaba.nacos.core.cluster.Member;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy class for handling cluster-related operations.
 *
 * @author zhangyukun
 */
@Service
public class ClusterProxy {
    
    private final Map<String, ClusterHandler> clusterHandlerMap = new HashMap<>();
    
    private final ConsoleWebConfig consoleConfig;
    
    /**
     * Constructs a new ClusterProxy with the given ClusterInnerHandler and ConsoleConfig.
     *
     * @param clusterInnerHandler the default implementation of ClusterHandler
     * @param consoleConfig       the console configuration used to determine the deployment type
     */
    public ClusterProxy(ClusterInnerHandler clusterInnerHandler, ConsoleWebConfig consoleConfig) {
        this.clusterHandlerMap.put("merged", clusterInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Retrieve a list of cluster members with an optional search keyword.
     *
     * @param ipKeyWord the search keyword for filtering members
     * @return a collection of matching members
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public Collection<Member> getNodeList(String ipKeyWord) {
        ClusterHandler clusterHandler = clusterHandlerMap.get(consoleConfig.getType());
        if (clusterHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return clusterHandler.getNodeList(ipKeyWord);
    }
}

