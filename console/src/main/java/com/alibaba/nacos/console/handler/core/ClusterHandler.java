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

package com.alibaba.nacos.console.handler.core;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;

import java.util.Collection;

/**
 * Interface for handling cluster-related operations.
 *
 * @author zhangyukun
 */
public interface ClusterHandler {
    
    /**
     * Retrieve a list of cluster members with an optional search keyword.
     *
     * @param ipKeyWord the search keyword for filtering members
     * @return a collection of matching members
     */
    Collection<Member> getNodeList(String ipKeyWord);
    
    /**
     * Update the metadata of a cluster.
     *
     * @param namespaceId    the namespace ID
     * @param serviceName    the service name
     * @param clusterName    the cluster name
     * @param clusterMetadata the metadata for the cluster
     * @throws Exception if the update operation fails
     */
    void updateClusterMetadata(String namespaceId, String serviceName, String clusterName, ClusterMetadata clusterMetadata) throws Exception;
}

