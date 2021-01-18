/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;

/**
 * Cluster operator.
 *
 * @author xiweng.yy
 */
public interface ClusterOperator {
    
    /**
     * Update cluster metadata.
     *
     * @param namespaceId     namespace id
     * @param serviceName     service name of cluster
     * @param clusterName     cluster name
     * @param clusterMetadata cluster metadata
     * @throws NacosException exception during update metadata
     */
    void updateClusterMetadata(String namespaceId, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws NacosException;
    
}
