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

package com.alibaba.nacos.console.handler.inner.core;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Implementation of ClusterHandler that handles cluster-related operations.
 *
 * @author zhangyukun
 */
@Service
public class ClusterInnerHandler implements ClusterHandler {
    
    private final ServerMemberManager memberManager;
    
    private final ClusterOperatorV2Impl clusterOperatorV2;
    
    /**
     * Constructs a new ClusterInnerHandler with the provided dependencies.
     *
     * @param memberManager     the manager for server members
     * @param clusterOperatorV2 the operator for cluster operations
     */
    @Autowired
    public ClusterInnerHandler(ServerMemberManager memberManager, ClusterOperatorV2Impl clusterOperatorV2) {
        this.memberManager = memberManager;
        this.clusterOperatorV2 = clusterOperatorV2;
    }
    
    /**
     * Retrieves a list of cluster members with an optional search keyword.
     *
     * @param ipKeyWord the search keyword for filtering members
     * @return a collection of matching members
     */
    @Override
    public Collection<Member> getNodeList(String ipKeyWord) {
        Collection<Member> members = memberManager.allMembers();
        Collection<Member> result = new ArrayList<>();
        
        members.stream().sorted().forEach(member -> {
            if (StringUtils.isBlank(ipKeyWord)) {
                result.add(member);
                return;
            }
            final String address = member.getAddress();
            if (StringUtils.equals(address, ipKeyWord) || StringUtils.startsWith(address, ipKeyWord)) {
                result.add(member);
            }
        });
        
        return result;
    }
    
    /**
     * Updates the metadata of a cluster.
     *
     * @param namespaceId    the namespace ID
     * @param serviceName    the service name
     * @param clusterName    the cluster name
     * @param clusterMetadata the metadata for the cluster
     * @throws Exception if the update operation fails
     */
    @Override
    public void updateClusterMetadata(String namespaceId, String serviceName, String clusterName, ClusterMetadata clusterMetadata) throws Exception {
        clusterOperatorV2.updateClusterMetadata(namespaceId, serviceName, clusterName, clusterMetadata);
    }
}
